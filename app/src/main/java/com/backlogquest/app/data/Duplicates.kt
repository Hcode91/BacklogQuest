package com.backlogquest.app.data

/**
 * Finds imported wishlist games that are already there.
 *
 * Two entries are the same game when their titles match (ignoring case and
 * punctuation, so "Pac-Man" and "PacMan" match) and they share a platform.
 * An entry with no platform matches any platform. The same title on two
 * different platforms is two different games.
 */
object Duplicates {

    private fun same(a: String, b: String) = normTitle(a) == normTitle(b)

    fun platformsOverlap(a: List<String>, b: List<String>): Boolean {
        val x = a.filter { it.isNotBlank() }
        val y = b.filter { it.isNotBlank() }
        return x.isEmpty() || y.isEmpty() || x.any { p -> y.any { same(p, it) } }
    }

    /** One game found more than once: the copy to keep, and the imported copies that repeat it. */
    class Group(val keep: WishItem, val extras: List<WishItem>)

    /**
     * Every game that's in your wishlist more than once, where at least one copy
     * was imported. The copy kept is one from your data file if there is one,
     * else one with a picture, else the first imported. Only imported copies are
     * offered for deleting; your data file's entries are never touched.
     */
    fun groups(items: List<WishItem>, hidden: Set<String>, pictures: Map<String, String>): List<Group> {
        val visible = items.filter { it.id !in hidden && normTitle(it.title).isNotEmpty() }
        val out = ArrayList<Group>()
        for ((_, sameTitle) in visible.groupBy { normTitle(it.title) }) {
            if (sameTitle.size < 2 || sameTitle.none { it.imported }) continue
            // Split by platform: copies join a cluster when they share a platform with one of its members.
            val clusters = ArrayList<MutableList<WishItem>>()
            for (w in sameTitle) {
                val home = clusters.firstOrNull { c -> c.any { platformsOverlap(it.platforms, w.platforms) } }
                if (home != null) home += w else clusters += mutableListOf(w)
            }
            for (c in clusters) {
                if (c.size < 2) continue
                val ranked = c.withIndex().sortedWith(
                    compareBy<IndexedValue<WishItem>> { if (it.value.imported) 1 else 0 }
                        .thenBy { pic -> if (pictures[pic.value.id].let { it.isNullOrBlank() || it == WishImport.NO_IMAGE }) 1 else 0 }
                        .thenBy { it.index },
                ).map { it.value }
                val extras = ranked.drop(1).filter { it.imported }
                if (extras.isNotEmpty()) out += Group(ranked.first(), extras)
            }
        }
        return out.sortedBy { it.keep.title.lowercase() }
    }

    /** Titles of every game in your games list and history. */
    fun libraryTitles(s: AppState, ref: RefData): Set<String> =
        (s.games.map { it.title } + ref.history.map { it.title }).map(::normTitle).filter { it.isNotEmpty() }.toSet()

    /** Imported wishlist games you already have in your games list or history. */
    fun inLibrary(items: List<WishItem>, hidden: Set<String>, library: Set<String>): List<WishItem> =
        items.filter { it.imported && it.id !in hidden && normTitle(it.title) in library }
            .sortedBy { it.title.lowercase() }

    /** For checking games about to be imported against what you already have. */
    class Index(items: List<WishItem>, hidden: Set<String>, private val library: Set<String>) {
        private class Entry(val platforms: List<String>, val source: String, val imported: Boolean)

        private val byTitle: Map<String, List<Entry>> = items.filter { it.id !in hidden }
            .groupBy({ normTitle(it.title) }, { Entry(it.platforms, it.source, it.imported) })

        /**
         * True when a game called [title] on [platform] is already in your games or wishlist.
         * Imported entries from the lists in [replacing] (source to platform, lower case)
         * don't count, because importing again replaces those lists.
         */
        fun known(title: String, platform: String, replacing: Set<Pair<String, String>> = emptySet()): Boolean {
            val key = normTitle(title)
            if (key.isEmpty()) return false
            if (key in library) return true
            return byTitle[key].orEmpty().any { e ->
                val beingReplaced = e.imported && e.platforms.any { (e.source.lowercase() to it.lowercase()) in replacing }
                !beingReplaced && platformsOverlap(e.platforms, listOf(platform))
            }
        }
    }
}
