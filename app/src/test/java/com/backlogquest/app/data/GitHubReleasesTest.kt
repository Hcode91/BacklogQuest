package com.backlogquest.app.data

import com.backlogquest.app.data.GitHubReleases.Asset
import com.backlogquest.app.data.GitHubReleases.Release
import com.backlogquest.app.data.GitHubReleases.Status
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleasesTest {
    private fun rel(tag: String, name: String = "", vararg apks: String) =
        Release(tag, name, "https://github.com/o/r/releases/tag/$tag", "2026-11-01", false, apks.map { Asset(it, "https://x/$it", 1) }, "")

    @Test fun `GitHub links in any common form give owner and project`() {
        assertEquals("azahar-emu/azahar", GitHubReleases.repoFrom("https://github.com/azahar-emu/azahar"))
        assertEquals("azahar-emu/azahar", GitHubReleases.repoFrom("https://github.com/azahar-emu/azahar/releases/latest"))
        assertEquals("o/r", GitHubReleases.repoFrom("github.com/o/r/"))
        assertEquals("o/r.js", GitHubReleases.repoFrom("https://www.github.com/o/r.js.git"))
        assertEquals("owner/repo", GitHubReleases.repoFrom("owner/repo"))
        assertNull(GitHubReleases.repoFrom("https://gitlab.com/o/r"))
        assertNull(GitHubReleases.repoFrom("just words"))
    }

    private val list = JSONArray(
        """[
          {"tag_name":"nightly-77","name":"Nightly","draft":false,"prerelease":true,"html_url":"https://github.com/o/r/releases/tag/nightly-77","published_at":"2026-11-10T03:00:00Z","assets":[]},
          {"tag_name":"v3.0.0","name":"Draft","draft":true,"prerelease":false,"assets":[]},
          {"tag_name":"v2.3.1","name":"Version 2.3.1","draft":false,"prerelease":false,"html_url":"https://github.com/o/r/releases/tag/v2.3.1","published_at":"2026-10-02T12:00:00Z","body":"## Changes\n- **Faster** [link](https://x)","assets":[
             {"name":"app-arm64-v8a.apk","browser_download_url":"https://github.com/o/r/releases/download/v2.3.1/app-arm64-v8a.apk","size":52428800},
             {"name":"windows.zip","browser_download_url":"https://github.com/o/r/releases/download/v2.3.1/windows.zip","size":10}]}
        ]""",
    )

    @Test fun `the newest full release is used, skipping drafts and, unless asked, pre-releases`() {
        val stable = GitHubReleases.newest(list, includePre = false)!!
        assertEquals("v2.3.1", stable.version)
        assertEquals("2026-10-02", stable.published)
        assertEquals(listOf("app-arm64-v8a.apk", "windows.zip"), stable.assets.map { it.name })
        assertEquals(52428800L, stable.assets[0].size)
        assertEquals("nightly-77", GitHubReleases.newest(list, includePre = true)!!.version)
        assertNull(GitHubReleases.newest(JSONArray("[]"), includePre = true))
    }

    @Test fun `versions compare by their numbers`() {
        assertEquals(Status.UP_TO_DATE, GitHubReleases.compare("2.3.1", rel("v2.3.1")))
        assertEquals(Status.UP_TO_DATE, GitHubReleases.compare("1.19.1_GIT", rel("v1.19.1")))
        assertEquals(Status.UPDATE, GitHubReleases.compare("2.3", rel("v2.3.1")))
        assertEquals(Status.UPDATE, GitHubReleases.compare("2.9.0", rel("2.10.0")))
        assertEquals(Status.UP_TO_DATE, GitHubReleases.compare("2.11.0", rel("2.10.0")))
        assertEquals(Status.UP_TO_DATE, GitHubReleases.compare("Azahar 2123", rel("2123", "Azahar 2123")))
        assertEquals(Status.UPDATE, GitHubReleases.compare("2122", rel("2123")))
    }

    @Test fun `when versions can't be compared, it says so instead of guessing`() {
        assertEquals(Status.UNKNOWN, GitHubReleases.compare("", rel("v1.0")))
        assertEquals(Status.UNKNOWN, GitHubReleases.compare("nightly", rel("latest")))
        assertEquals(Status.UP_TO_DATE, GitHubReleases.compare("main-a1b2c3d", rel("build-a1b2c3d4e5")))
    }

    @Test fun `the right Android download is picked, or none when it can't tell`() {
        val arm = listOf("arm64-v8a")
        assertEquals("only.apk", GitHubReleases.pickApk(rel("v1", "", "only.apk"), arm)!!.name)
        val split = rel("v1", "", "app-armeabi-v7a.apk", "app-arm64-v8a.apk", "app-x86_64.apk")
        assertEquals("app-arm64-v8a.apk", GitHubReleases.pickApk(split, arm)!!.name)
        assertEquals("app-x86_64.apk", GitHubReleases.pickApk(split, listOf("x86_64"))!!.name)
        val universal = rel("v1", "", "app-universal-release.apk", "app-debug.apk")
        assertEquals("app-universal-release.apk", GitHubReleases.pickApk(universal, arm)!!.name)
        val variants = rel("v1", "", "emu-mainline.apk", "emu-legacy.apk")
        assertNull(GitHubReleases.pickApk(variants, arm))
        assertEquals("emu-legacy.apk", GitHubReleases.pickApk(variants, arm, hint = "legacy")!!.name)
        assertNull(GitHubReleases.pickApk(rel("v1", "", "windows.zip"), arm))
    }

    @Test fun `release notes are shown as plain text`() {
        assertEquals("Changes\n- Faster link", GitHubReleases.plainNotes("## Changes\n- **Faster** [link](https://x)"))
    }
}
