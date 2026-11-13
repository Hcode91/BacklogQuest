package com.backlogquest.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Filling in pictures, achievements and prices for many games at once.
 *
 * Each game tried without a result is remembered (see [AppState.notFound]),
 * so pressing the button again moves on to the next games instead of trying
 * the same ones forever.
 */
object BulkFetch {
    /** How many games one press fills in. 0 means all of them. */
    val COUNTS = listOf(10, 25, 50, 100, 0)

    /** A game to find a picture for, and where the picture goes. */
    sealed class Pic(val key: String, val title: String) {
        /** One of your games: the picture becomes its cover. */
        class Own(val id: String, title: String) : Pic("pic:g:$id", title)
        /** A game from your history: kept in [AppState.histCovers]. */
        class Hist(val histKey: String, title: String) : Pic("pic:h:$histKey", title)
        /** A wishlist game: from its link if it has one, else by title. */
        class Wish(val id: String, title: String, val url: String) : Pic("pic:w:$id", title)
    }

    fun achKey(gameId: String) = "ach:$gameId"
    fun priceKey(title: String) = "price:" + normTitle(title)

    /** The first [count] of [missing], or all of them when [count] is 0. */
    fun <T> first(missing: List<T>, count: Int): List<T> = if (count <= 0) missing else missing.take(count)

    /**
     * Runs one lookup in [scope], giving up after [ms]. A site that never answers would
     * otherwise hold up a whole batch; this way it counts as a failure and the batch
     * moves on. The lookup itself is left to finish (or fail) on its own.
     */
    suspend fun <T> withinLimit(scope: CoroutineScope, ms: Long, block: suspend () -> T): T? {
        val lookup = scope.async(Dispatchers.IO) { block() }
        return try {
            withTimeoutOrNull(ms) { lookup.await() }
        } finally {
            if (!lookup.isCompleted) lookup.cancel()
        }
    }

    /** "10 OF 37", or just "37" when every one is included. */
    fun label(count: Int, missing: Int): String =
        if (count <= 0 || count >= missing) "%,d".format(missing) else "%,d OF %,d".format(count, missing)
}
