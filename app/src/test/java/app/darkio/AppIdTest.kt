package app.darkio

import app.darkio.data.appId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppIdTest {
    @Test
    fun idIsPackagePlusActivityPlusUser() {
        assertEquals("com.a/com.a.Main#0", appId("com.a", "com.a.Main", 0))
    }

    @Test
    fun differentActivityOrUserGivesDifferentId() {
        val base = appId("com.a", "com.a.Main", 0)
        assertNotEquals(base, appId("com.a", "com.a.Other", 0))
        assertNotEquals(base, appId("com.a", "com.a.Main", 10))
    }
}
