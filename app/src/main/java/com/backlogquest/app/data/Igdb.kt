package com.backlogquest.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Upcoming and just-released games from IGDB (igdb.com), the database most game-tracking apps use.
 *
 * IGDB is reached with a free Twitch developer app: its client ID and secret buy an access token,
 * valid for about two months. Both are typed into Setup and stay on this phone.
 */
object Igdb {
    class IgdbError(message: String) : Exception(message)

    /** An access token and when it stops working (milliseconds). */
    data class Token(val value: String, val expiresAt: Long)

    /** A platform as IGDB lists it. */
    data class IgdbPlatform(val id: Long, val name: String, val abbreviation: String, val alternative: String)

    /** One game's release in the window asked for, with every one of your platforms it comes out on. */
    data class Release(
        val gameId: Long,
        val title: String,
        val slug: String,
        val cover: String,
        /** Its first release date in the window (seconds since 1970, UTC). */
        val date: Long,
        /** IGDB's own wording of the date ("Nov 28, 2026", "Q1 2027"). */
        val human: String,
        /** Your platforms it comes out on, in your list's names. */
        val platforms: List<String>,
        val genres: List<String>,
        /** How many IGDB users are waiting for it (follows before release). */
        val hype: Int,
    ) {
        val url: String get() = "https://www.igdb.com/games/$slug"
    }

    private const val API = "https://api.igdb.com/v4"

    /** Gets a new access token from Twitch for the app's client ID and secret. */
    fun token(clientId: String, secret: String, now: Long = System.currentTimeMillis()): Token {
        val r = Http.call(
            "https://id.twitch.tv/oauth2/token?" + Http.form(
                "client_id" to clientId, "client_secret" to secret, "grant_type" to "client_credentials",
            ),
            method = "POST", body = "",
        )
        if (r.code == 400 || r.code == 403) throw IgdbError("Twitch didn't accept that client ID and secret. Check them in Setup → Releases.")
        if (r.code !in 200..299) throw IgdbError("Twitch answered ${r.code}. Try again in a moment.")
        val o = JSONObject(r.body)
        val value = o.optString("access_token")
        if (value.isBlank()) throw IgdbError("Twitch didn't send an access token.")
        // Renew a day early, so a token never runs out halfway through.
        return Token(value, now + (o.optLong("expires_in", 0) - 86_400).coerceAtLeast(3_600) * 1000)
    }

    /** One query to IGDB, in its Apicalypse language. */
    private fun query(clientId: String, token: String, endpoint: String, body: String): JSONArray {
        val r = Http.call(
            "$API/$endpoint", method = "POST",
            headers = mapOf("Client-ID" to clientId, "Authorization" to "Bearer $token", "Accept" to "application/json"),
            body = body,
        )
        when {
            r.code == 401 || r.code == 403 -> throw TokenExpired()
            r.code == 429 -> throw IgdbError("IGDB is busy (too many requests). Try again in a few seconds.")
            r.code !in 200..299 -> throw IgdbError("IGDB answered ${r.code}: ${r.body.take(160)}")
        }
        return JSONArray(r.body)
    }

    /** The token was refused: get a new one and ask again. */
    class TokenExpired : Exception("IGDB refused the access token.")

    fun platforms(clientId: String, token: String): List<IgdbPlatform> {
        val out = ArrayList<IgdbPlatform>()
        var offset = 0
        while (true) {
            val page = query(clientId, token, "platforms", "fields id,name,abbreviation,alternative_name; sort id asc; limit 500; offset $offset;")
            for (i in 0 until page.length()) {
                val o = page.getJSONObject(i)
                out += IgdbPlatform(o.optLong("id"), o.optString("name"), o.optString("abbreviation"), o.optString("alternative_name"))
            }
            if (page.length() < 500) break
            offset += 500
        }
        return out
    }

    /**
     * IGDB's platforms that are yours: IGDB id → your platform's name. A platform is matched by its
     * name, abbreviation or alternative name, the same way games are matched to your platforms.
     */
    fun mapPlatforms(igdb: List<IgdbPlatform>, mine: List<String>): Map<Long, String> {
        val m = Platforms.Matcher(mine)
        return igdb.mapNotNull { p ->
            val mineName = m.of(p.name) ?: m.of(p.abbreviation) ?: p.alternative.split(',', '/').firstNotNullOfOrNull { a -> m.of(a.trim()) }
            mineName?.let { p.id to it }
        }.toMap()
    }

    /** DLC, bundles, mods, episodes, seasons, forks, packs and updates aren't new games. */
    private val NOT_GAMES = setOf(1, 3, 5, 6, 7, 12, 13, 14)

    /**
     * Release dates on [platforms] (IGDB ids) between [from] and [to] (seconds), grouped into one
     * entry per game. [watchedOnly] keeps games at least one person follows on IGDB, which cuts out
     * most of the thousands of small store releases each month.
     */
    fun releases(
        clientId: String,
        token: String,
        platforms: Map<Long, String>,
        from: Long,
        to: Long,
        newestFirst: Boolean,
        watchedOnly: Boolean,
        maxRows: Int = 1500,
    ): List<Release> {
        if (platforms.isEmpty()) return emptyList()
        val rows = JSONArray()
        var offset = 0
        val where = buildString {
            append("platform = (${platforms.keys.joinToString(",")}) & date >= $from & date < $to & game.version_parent = null")
            if (watchedOnly) append(" & game.hypes > 0")
        }
        while (offset < maxRows) {
            val page = query(
                clientId, token, "release_dates",
                "fields date,human,platform,game.name,game.slug,game.cover.image_id,game.genres.name,game.hypes,game.game_type; " +
                    "where $where; sort date ${if (newestFirst) "desc" else "asc"}; limit 500; offset $offset;",
            )
            for (i in 0 until page.length()) rows.put(page.get(i))
            if (page.length() < 500) break
            offset += 500
        }
        return parse(rows, platforms, newestFirst)
    }

    /** Turns IGDB's release_dates rows into one release per game. */
    fun parse(rows: JSONArray, platforms: Map<Long, String>, newestFirst: Boolean): List<Release> {
        class Acc(val game: JSONObject, var date: Long, var human: String, val platforms: LinkedHashSet<String>)
        val byGame = LinkedHashMap<Long, Acc>()
        for (i in 0 until rows.length()) {
            val r = rows.optJSONObject(i) ?: continue
            val game = r.optJSONObject("game") ?: continue
            val id = game.optLong("id")
            if (id == 0L || game.optString("name").isBlank()) continue
            // game_type is an id, or an object when expanded.
            val type = game.optJSONObject("game_type")?.optInt("id") ?: game.optInt("game_type", 0)
            if (type in NOT_GAMES) continue
            val date = r.optLong("date")
            val plat = platforms[r.optLong("platform")] ?: continue
            val acc = byGame.getOrPut(id) { Acc(game, date, r.optString("human"), LinkedHashSet()) }
            acc.platforms += plat
            // Keep the game's first date in the window (in the order asked for, the first row seen
            // is the nearest; for "just released", the earliest is when it actually came out).
            if (date < acc.date) { acc.date = date; acc.human = r.optString("human") }
        }
        val list = byGame.map { (id, a) ->
            val g = a.game
            val image = g.optJSONObject("cover")?.optString("image_id").orEmpty()
            val genres = g.optJSONArray("genres")?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name") } }.orEmpty()
            Release(
                gameId = id, title = g.optString("name"), slug = g.optString("slug"),
                cover = if (image.isBlank()) "" else "https://images.igdb.com/igdb/image/upload/t_cover_big/$image.jpg",
                date = a.date, human = a.human, platforms = a.platforms.toList(), genres = genres,
                hype = g.optInt("hypes", 0),
            )
        }
        return if (newestFirst) list.sortedByDescending { it.date } else list.sortedBy { it.date }
    }
}
