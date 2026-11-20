package com.backlogquest.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DataUrlTest {
    private fun load(url: String): String {
        val dir = Files.createTempDirectory("bq").toFile()
        File(dir, "state.json").writeText(org.json.JSONObject().put("dataUrl", url).toString())
        return Store.loadState(dir)!!.dataUrl
    }

    @Test fun oldDefaultMovesToTheNewAccount() {
        assertEquals(DEFAULT_DATA_URL, load(OLD_DATA_URL))
        assertEquals("https://raw.githubusercontent.com/Hcode91/BacklogQuest/main/backlog-data.json", DEFAULT_DATA_URL)
    }

    @Test fun yourOwnAddressIsKept() {
        val mine = "https://example.com/my-backlog.json"
        assertEquals(mine, load(mine))
        assertEquals(DEFAULT_DATA_URL, load(""))
    }
}
