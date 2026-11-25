package com.backlogquest.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Play achievements, read from your public Exophase profile.
 *
 * Google doesn't let one app read another game's Play Games achievements, but Exophase
 * reads public Play Games profiles from Google's servers. So: link Google Play on
 * exophase.com once, put your Exophase username in Setup → Achievements, and this reads
 * what Exophase has recorded.
 *
 * Exophase sits behind Cloudflare, so everything goes through the invisible browser
 * ([PageReader]). The scripts call the same JSON endpoints Exophase's own pages use:
 * - /public/player/{profileId}/games?page=N     your games, 50 a page
 * - /public/player/{gamePlayerId}/game/{gameId}/earned   what you've unlocked in one game
 */
object Exophase {

    /** One Google Play game on your Exophase profile. */
    class ExoGame(
        val title: String,
        val gameId: Long,
        val playerId: Long,
        val earned: Int,
        val total: Int,
        val awardsUrl: String,
    )

    class Profile(val games: List<ExoGame>, val otherPlatforms: List<String>)

    private const val API = "https://api.exophase.com/public/player/"

    /** Accepts a username or a profile link (exophase.com/user/name/). */
    fun userName(raw: String): String? {
        val t = raw.trim().trimEnd('/')
        val name = Regex("exophase\\.com/user/([^/?#]+)", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1) ?: t
        return name.takeIf { it.matches(Regex("[A-Za-z0-9_.\\-]{2,40}")) }
    }

    // Reading your whole game list takes a few seconds, so it's kept for a while
    // when looking up lots of games in a row.
    private var cached: Triple<String, Long, Profile>? = null
    private const val CACHE_MS = 10 * 60_000L

    /**
     * Runs on your profile page: finds your profile number, then pages through your games
     * and keeps the Google Play ones. Returns "" until the page is ready.
     */
    private val PROFILE_SCRIPT = """
        (function(){ ${Achievements.READY}
          if (window.__bqOut) return window.__bqOut;
          var pid = window.playerProfileId;
          if (!pid) {
            var m = document.documentElement.innerHTML.match(/playerProfileId\s*=\s*['"]?(\d+)/);
            pid = m ? m[1] : null;
          }
          if (!pid) {
            window.__bqTries = (window.__bqTries || 0) + 1;
            return window.__bqTries > 8 ? JSON.stringify({error: 'noprofile'}) : '';
          }
          function isGoogle(g){
            var meta = g.meta || {}, env = String(meta.environment_slug || '').toLowerCase();
            if (/google|android/.test(env)) return true;
            var plats = (meta.platforms || []).map(function(p){ return String(p.name || p.slug || ''); }).join(' ');
            if (/android|google/i.test(plats)) return true;
            return /\/(google|android)[^\/]*\/games\//i.test(String(g.resource_tile || g.resource_standard || ''));
          }
          var out = [], envs = {}, page = 1;
          while (page <= 80) {
            var x = new XMLHttpRequest();
            x.open('GET', '$API' + pid + '/games?page=' + page + '&environment=&sort=1&showHidden=0', false);
            x.setRequestHeader('Accept', 'application/json');
            try { x.send(null); } catch (e) { break; }
            if (x.status !== 200) {
              if (page === 1) { window.__bqOut = JSON.stringify({error: 'http' + x.status}); return window.__bqOut; }
              break;
            }
            var d; try { d = JSON.parse(x.responseText); } catch (e) { break; }
            var games = (d && d.games) || [];
            games.forEach(function(g){
              var meta = g.meta || {};
              envs[String(meta.environment_slug || '?')] = 1;
              if (!isGoogle(g)) return;
              out.push({t: meta.title || '', m: g.master_id || meta.master_id, p: g.master_playerid || meta.master_playerid,
                        e: g.earned_awards || 0, n: g.total_awards || meta.total_awards || 0, u: meta.endpoint_awards || ''});
            });
            if (games.length < 50) break;
            page++;
          }
          window.__bqOut = JSON.stringify({games: out, envs: Object.keys(envs)});
          return window.__bqOut;
        })()
    """.trimIndent()

    suspend fun profile(ctx: Context, raw: String): Profile {
        val name = userName(raw)
            ?: throw Achievements.AchError("That Exophase username doesn't look right. Check it in Setup → Achievements.")
        cached?.let { (n, at, p) -> if (n == name && System.currentTimeMillis() - at < CACHE_MS) return p }
        val read = PageReader.read(ctx, "https://www.exophase.com/user/$name/", PROFILE_SCRIPT, timeoutMs = 60_000)
            ?: throw Achievements.AchError("Couldn't reach Exophase. Try again in a moment.")
        val o = JSONObject(read)
        when (val err = o.optString("error")) {
            "" -> Unit
            "noprofile" -> throw Achievements.AchError("Couldn't find the Exophase profile \"$name\". Check exophase.com/user/$name/ opens.")
            else -> throw Achievements.AchError("Exophase didn't share your games ($err). Try again later.")
        }
        val arr = o.optJSONArray("games") ?: JSONArray()
        val games = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.mapNotNull {
            val id = it.optLong("m")
            if (id == 0L) null else ExoGame(
                it.optString("t"), id, it.optLong("p"), it.optInt("e"), it.optInt("n"), it.optString("u"),
            )
        }
        val envs = o.optJSONArray("envs")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
        return Profile(games, envs).also { cached = Triple(name, System.currentTimeMillis(), it) }
    }

    /**
     * Runs on a game's achievements page: reads every achievement on it (locked ones too),
     * then asks Exophase which you've unlocked. If the page's list can't be read, the
     * unlocked ones alone come back.
     */
    private fun awardsScript(playerId: Long, gameId: Long) = """
        (function(){ ${Achievements.READY}
          if (window.__bqOut) return window.__bqOut;
          function txt(e){ return e ? (e.innerText || e.textContent || '').replace(/\s+/g, ' ').trim() : ''; }
          var rows = document.querySelectorAll('[data-award-id], li.award, ul.achievement > li, ul.trophy > li, ul.challenge > li');
          var list = [], seen = {};
          rows.forEach(function(el){
            var n = txt(el.querySelector('.award-title a, .award-title, h3 a, h3, a'));
            if (!n || seen[n]) return; seen[n] = 1;
            var d = txt(el.querySelector('.award-description p, .award-description, p'));
            var id = el.getAttribute('data-award-id') || ((el.id || '').match(/\d+/) || [''])[0];
            var pts = (txt(el.querySelector('.award-points, .points')).match(/\d+/) || [''])[0];
            list.push({n: n, d: d, i: id, k: pts ? pts + ' pts' : ''});
          });
          if (!list.length) {
            window.__bqTries = (window.__bqTries || 0) + 1;
            if (window.__bqTries < 6) return '';
          }
          var earned = [];
          var x = new XMLHttpRequest();
          x.open('GET', '$API$playerId/game/$gameId/earned', false);
          x.setRequestHeader('Accept', 'application/json');
          try { x.send(null); var d = JSON.parse(x.responseText); earned = (d && d.list) || []; } catch (e) {}
          var got = earned.map(function(a){ return {s: a.slug || '', i: String(a.awardid || a.masterAwardId || '')}; });
          window.__bqOut = JSON.stringify({list: list, earned: got});
          return window.__bqOut;
        })()
    """.trimIndent()

    private fun slugOf(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** A game's full achievement list with your unlocks ticked. */
    suspend fun find(ctx: Context, title: String, s: AppState): Achievements.Found {
        if (s.exophaseUser.isBlank()) {
            throw Achievements.AchError("Add your Exophase username in Setup → Achievements to get Google Play achievements.")
        }
        val profile = profile(ctx, s.exophaseUser)
        if (profile.games.isEmpty()) {
            throw Achievements.AchError(
                if (profile.otherPlatforms.isEmpty()) "Your Exophase profile has no games yet."
                else "No Google Play games on your Exophase profile. Link Google Play on exophase.com and make your Play Games profile public.",
            )
        }
        val q = normTitle(title)
        val game = profile.games.firstOrNull { normTitle(it.title) == q }
            ?: profile.games.firstOrNull { normTitle(it.title).startsWith(q) || q.startsWith(normTitle(it.title)) }
            ?: throw Achievements.AchError("Couldn't find this game in your Google Play games on Exophase.")
        if (game.total == 0) throw Achievements.AchError("${game.title} has no Google Play achievements.")

        val url = game.awardsUrl.ifBlank { "https://www.exophase.com/" }
        val read = PageReader.read(ctx, url, awardsScript(game.playerId, game.gameId), timeoutMs = 45_000)
            ?: throw Achievements.AchError("Couldn't load ${game.title}'s achievements from Exophase.")
        val o = JSONObject(read)
        val earned = o.optJSONArray("earned")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()
        val earnedIds = earned.map { it.optString("i") }.filter { it.isNotBlank() }.toSet()
        val earnedSlugs = earned.map { it.optString("s") }.filter { it.isNotBlank() }.toSet()
        val page = o.optJSONArray("list")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

        val list = if (page.isNotEmpty()) {
            page.map {
                val name = it.optString("n")
                val got = it.optString("i").let { id -> id.isNotBlank() && id in earnedIds } || slugOf(name) in earnedSlugs
                Trophy(name, it.optString("d"), it.optString("k"), got)
            }
        } else {
            // Couldn't read the page's list: at least show what you've unlocked.
            earnedSlugs.map { slug ->
                Trophy(slug.split('-').joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }, earned = true)
            }
        }
        if (list.isEmpty()) throw Achievements.AchError("Exophase didn't list any achievements for ${game.title}.")
        return Achievements.Found(game.title, list)
    }

    /** Google Play achievements unlocked across your whole Exophase profile. */
    suspend fun total(ctx: Context, s: AppState): Int = profile(ctx, s.exophaseUser).games.sumOf { it.earned }
}
