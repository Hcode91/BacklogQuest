package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditPortsTests {
    private val listing = """
        {"data":{"children":[
          {"kind":"t3","data":{"id":"a1","subreddit":"AndroidNativePorts","title":"[PS2] Okami recomp running natively",
            "permalink":"/r/AndroidNativePorts/comments/a1/okami/","created_utc":1764000000.0,"link_flair_text":"Release",
            "url":"https://github.com/someone/OkamiRecomp-Android","selftext":"APK in releases","removed_by_category":null}},
          {"kind":"t3","data":{"id":"a2","subreddit":"AndroidNativePorts","title":"Which port should I try?",
            "permalink":"/r/AndroidNativePorts/comments/a2/x/","created_utc":1764000100.0,
            "url":"https://www.reddit.com/r/AndroidNativePorts/comments/a2/x/","selftext":"no links here"}},
          {"kind":"t3","data":{"id":"a3","subreddit":"decomps","title":"Ship of Harkinian update",
            "permalink":"/r/decomps/comments/a3/soh/","created_utc":1764000200.0,
            "url":"https://www.reddit.com/r/decomps/comments/a3/soh/",
            "selftext":"Source: https://github.com/HarbourMasters/Shipwright.git and https://github.com/orgs/foo"}},
          {"kind":"t3","data":{"id":"a4","subreddit":"decomps","title":"removed","permalink":"/r/decomps/comments/a4/r/",
            "url":"https://github.com/x/y","removed_by_category":"moderator"}}
        ]}}
    """.trimIndent()

    @Test fun `only posts with a GitHub project are kept`() {
        val posts = RedditPorts.parseJson(listing, "AndroidNativePorts")
        assertEquals(listOf("a1", "a3"), posts.map { it.id })
        assertEquals(listOf("someone/OkamiRecomp-Android"), posts[0].repos)
        assertEquals(listOf("HarbourMasters/Shipwright"), posts[1].repos)
        assertEquals("https://www.reddit.com/r/AndroidNativePorts/comments/a1/okami/", posts[0].link)
    }

    @Test fun `GitHub pages that aren't projects are ignored`() {
        assertTrue(RedditPorts.githubRepos("see github.com/topics/decomp and https://github.com/sponsors/me").isEmpty())
        assertEquals(listOf("a/b"), RedditPorts.githubRepos("https://github.com/a/b/releases/tag/v1 and github.com/A/B."))
    }

    @Test fun `reads the feed form`() {
        val rss = """
            <feed><entry><category term="AndroidNativePorts" label="r/AndroidNativePorts"/>
            <content type="html">&lt;p&gt;Get it &lt;a href=&quot;https://github.com/dev/Port&quot;&gt;here&lt;/a&gt;&lt;/p&gt;</content>
            <id>t3_zz9</id><link href="https://www.reddit.com/r/AndroidNativePorts/comments/zz9/x/" />
            <published>2026-11-20T10:00:00+00:00</published><title>N64 game ported</title></entry>
            <entry><content type="html">nothing</content><id>t3_zz8</id><title>chat</title></entry></feed>
        """.trimIndent()
        val posts = RedditPorts.parseRss(rss, "AndroidNativePorts")
        assertEquals(1, posts.size)
        assertEquals("zz9", posts[0].id)
        assertEquals(listOf("dev/Port"), posts[0].repos)
        assertEquals("N64 game ported", posts[0].title)
        assertTrue(posts[0].created > 0)
    }

    @Test fun `posts join the list`() {
        val (_, list) = Ports.parse(
            """{"games":[{"id":"soh","title":"Ocarina of Time","platform":"N64","type":"decompilation",
               "repo":"HarbourMasters/Shipwright","url":"https://github.com/HarbourMasters/Shipwright"}]}""",
        )
        val posts = RedditPorts.parseJson(listing, "AndroidNativePorts")
        val merged = RedditPorts.merge(list, posts, emptyMap())
        assertEquals(2, merged.size)
        val soh = merged.first { it.id == "soh" }
        assertEquals("decomps", soh.redditSub)
        assertFalse(soh.fromReddit)
        val okami = merged.first { it.fromReddit }
        assertEquals("PS2", okami.platform)
        assertEquals("static-recomp", okami.type)
        assertTrue(okami.android)
        // GitHub says the project doesn't exist: it's left out.
        val gone = RedditPorts.merge(list, posts, mapOf("someone/okamirecomp-android" to RepoInfo(exists = false)))
        assertEquals(1, gone.size)
        assertEquals(listOf("someone/OkamiRecomp-Android"), RedditPorts.toCheck(posts, list, emptyMap(), 0, 10))
    }

    @Test fun `saved posts and lookups read back`() {
        val posts = RedditPorts.parseJson(listing, "AndroidNativePorts")
        assertEquals(posts, RedditPorts.postsFromJson(RedditPorts.postsToJson(posts)))
        val info = mapOf("a/b" to RepoInfo(true, 5, "2026-11-01", "d", false, "v1", "2026-11-02", "u", 9))
        assertEquals(info, RedditPorts.infoFromJson(RedditPorts.infoToJson(info)))
    }
}
