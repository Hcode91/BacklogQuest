package com.backlogquest.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One line of the achievements total: where they were earned and how many. */
class AchPart(val label: String, val short: String, val count: Int, val wholeAccount: Boolean)

class AchSummary(val parts: List<AchPart>) {
    val total: Int get() = parts.sumOf { it.count }
}

/**
 * Every achievement you've earned, not just in the games listed in this app.
 *
 * - Steam and RetroAchievements are read from your whole account (needs the keys in Setup).
 * - PlayStation and Xbox have no way to read an account, so those count the
 *   trophies you've ticked on games in this app.
 */
object AchievementTotals {
    class TotalsError(message: String) : Exception(message)

    /** What to show in the status panel and Profile, from what's saved on the phone. */
    fun summary(s: AppState): AchSummary {
        fun ticked(source: String) =
            s.games.filter { it.achSource == source }.sumOf { g -> g.achievements.count { it.earned } }
        val parts = listOf(
            AchPart("Steam", "Steam", s.achTotals[Achievements.STEAM] ?: ticked(Achievements.STEAM), Achievements.STEAM in s.achTotals),
            AchPart("RetroAchievements", "RA", s.achTotals[Achievements.RA] ?: ticked(Achievements.RA), Achievements.RA in s.achTotals),
            AchPart("PlayStation", "PSN", ticked(Achievements.PSN), false),
            AchPart("Xbox", "Xbox", ticked(Achievements.XBOX), false),
        )
        return AchSummary(parts.filter { it.count > 0 || it.wholeAccount })
    }

    fun canReadSteam(s: AppState) = s.steamKey.isNotBlank() && s.steamProfile.isNotBlank()
    fun canReadRetro(s: AppState) = s.raKey.isNotBlank() && s.raUser.isNotBlank()

    // ---------------- RetroAchievements ----------------

    /** Achievements earned across one page of API_GetUserCompletionProgress. */
    fun retroEarned(page: JSONObject): Int {
        val rows = page.optJSONArray("Results") ?: return 0
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            .sumOf { maxOf(it.optInt("NumAwarded"), it.optInt("NumAwardedHardcore")) }
    }

    suspend fun retro(s: AppState): Int = withContext(Dispatchers.IO) {
        if (!canReadRetro(s)) throw TotalsError("Add your RetroAchievements username and API key in Setup first.")
        var earned = 0
        var offset = 0
        while (true) {
            val r = Http.call(
                "https://retroachievements.org/API/API_GetUserCompletionProgress.php" +
                    "?y=${Http.enc(s.raKey)}&u=${Http.enc(s.raUser)}&c=500&o=$offset",
            )
            if (r.code == 401 || r.code == 403) throw TotalsError("RetroAchievements didn't accept your API key.")
            if (r.code !in 200..299) throw TotalsError("RetroAchievements couldn't be reached.")
            val page = JSONObject(r.body)
            earned += retroEarned(page)
            val count = page.optInt("Count")
            offset += count
            if (count == 0 || offset >= page.optInt("Total")) break
        }
        earned
    }

    // ---------------- Steam ----------------

    private const val STEAM_API = "https://api.steampowered.com"

    /** What one game's GetPlayerAchievements answer means. */
    sealed class SteamGame {
        class Earned(val count: Int) : SteamGame()
        /** Your profile's game details aren't public, so Steam won't say. */
        object Private : SteamGame()
        /** A hiccup (busy, rate limited): try this game again next time. */
        object Retry : SteamGame()
    }

    fun steamGame(code: Int, body: String): SteamGame {
        val stats = try {
            JSONObject(body).optJSONObject("playerstats")
        } catch (e: Exception) {
            null
        }
        val error = stats?.optString("error").orEmpty()
        return when {
            error.contains("not public", ignoreCase = true) -> SteamGame.Private
            stats != null && stats.optBoolean("success") -> {
                val list = stats.optJSONArray("achievements") ?: JSONArray()
                SteamGame.Earned((0 until list.length()).count { list.optJSONObject(it)?.optInt("achieved") == 1 })
            }
            // "Requested app has no stats": the game simply has no achievements.
            code == 400 || error.contains("no stats", ignoreCase = true) -> SteamGame.Earned(0)
            else -> SteamGame.Retry
        }
    }

    /**
     * Counts unlocked achievements in every Steam game you've played. Steam only
     * answers one game at a time, so results are kept in [cache] and a game is
     * asked about again only when its play time has changed.
     */
    suspend fun steam(s: AppState, cache: File, progress: (String) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        if (!canReadSteam(s)) throw TotalsError("Add your Steam Web API key and Steam profile in Setup first.")
        val key = Http.enc(s.steamKey.trim())
        val raw = s.steamProfile.trim().trimEnd('/').substringAfterLast("/")
        val id = if (raw.matches(Regex("\\d{17}"))) raw else {
            val r = Http.call("$STEAM_API/ISteamUser/ResolveVanityURL/v1/?key=$key&vanityurl=${Http.enc(raw)}")
            if (r.code == 401 || r.code == 403) throw TotalsError("Steam didn't accept your Web API key. Check it in Setup.")
            runCatching { JSONObject(r.body).optJSONObject("response")?.optString("steamid") }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: throw TotalsError("Couldn't find that Steam profile.")
        }
        val owned = Http.call(
            "$STEAM_API/IPlayerService/GetOwnedGames/v1/?key=$key&steamid=$id&include_played_free_games=true",
        )
        if (owned.code == 401 || owned.code == 403) throw TotalsError("Steam didn't accept your Web API key. Check it in Setup.")
        if (owned.code !in 200..299) throw TotalsError("Steam couldn't be reached.")
        val games = JSONObject(owned.body).optJSONObject("response")?.optJSONArray("games") ?: JSONArray()
        if (games.length() == 0) throw TotalsError("Steam didn't list any games. Is your profile's game details setting public?")
        // Only games you've actually played can have unlocks.
        val played = (0 until games.length()).mapNotNull { games.optJSONObject(it) }
            .filter { it.optInt("playtime_forever") > 0 }
            .map { it.optInt("appid") to it.optInt("playtime_forever") }

        val saved = try {
            if (cache.exists()) JSONObject(cache.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
        // A cache from a different Steam account is no use.
        val known = if (saved.optString("steamid") == id) saved.optJSONObject("games") ?: JSONObject() else JSONObject()
        val fresh = JSONObject()
        val todo = mutableListOf<Pair<Int, Int>>()
        for ((app, minutes) in played) {
            val entry = known.optJSONArray(app.toString())
            if (entry != null && entry.optInt(0) == minutes) fresh.put(app.toString(), entry) else todo += app to minutes
        }

        var done = 0
        var private = false
        val gate = Semaphore(6)
        coroutineScope {
            todo.map { (app, minutes) ->
                async {
                    val result = gate.withPermit {
                        try {
                            val r = Http.call("$STEAM_API/ISteamUserStats/GetPlayerAchievements/v1/?key=$key&steamid=$id&appid=$app")
                            steamGame(r.code, r.body)
                        } catch (e: Exception) {
                            SteamGame.Retry
                        }
                    }
                    synchronized(fresh) {
                        when (result) {
                            is SteamGame.Earned -> fresh.put(app.toString(), JSONArray().put(minutes).put(result.count))
                            SteamGame.Private -> private = true
                            // Keep the older count for now rather than dropping the game.
                            SteamGame.Retry -> known.optJSONArray(app.toString())?.let { old ->
                                fresh.put(app.toString(), JSONArray().put(-1).put(old.optInt(1)))
                            }
                        }
                        done++
                        if (done % 10 == 0 || done == todo.size) progress("Checking Steam games… $done of ${todo.size}")
                    }
                }
            }.awaitAll()
        }
        if (private && fresh.length() == 0) {
            throw TotalsError("Steam won't share your achievements. Set your profile's game details to public.")
        }
        cache.writeText(JSONObject().put("steamid", id).put("games", fresh).toString())
        fresh.keys().asSequence().sumOf { fresh.optJSONArray(it)?.optInt(1) ?: 0 }
    }
}
