package com.heartnote.app.share

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSaverTest {
    private val now = LocalDateTime.of(2026, 11, 18, 14, 5)

    @Test
    fun fileNameFollowsThePattern() {
        assertEquals("Heartnote_Ma_s_birthday_20261118_1405.mp4", DeviceSaver.fileName("Ma's birthday", "mp4", now))
        assertEquals("Heartnote_you_20261118_1405.m4a", DeviceSaver.fileName("you", "m4a", now))
    }

    @Test
    fun sanitisesUnsafeCharactersAndKeepsLetters() {
        assertEquals("a_b_c", DeviceSaver.sanitize(" a/b\\c:*?\"<>| "))
        assertEquals("Grand-père", DeviceSaver.sanitize("Grand-père"))
        assertEquals("note", DeviceSaver.sanitize("///"))
        assertEquals(40, DeviceSaver.sanitize("x".repeat(80)).length)
    }
}
