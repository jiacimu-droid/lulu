package me.rerere.rikkahub.service

import me.rerere.common.android.Logging
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatErrorLoggingTest {
    @Before
    fun setUp() {
        Logging.clear()
    }

    @After
    fun tearDown() {
        Logging.clear()
    }

    @Test
    fun `persistChatError should store chat error immediately`() {
        val error = ChatError(
            title = "Generation failed",
            error = IllegalStateException("bad state"),
        )

        persistChatError(error)

        val logs = Logging.getTextLogs()
        assertEquals(1, logs.size)
        assertEquals("Generation failed", logs.single().tag)
        assertTrue(logs.single().message.contains("bad state"))
        assertTrue(logs.single().message.contains("IllegalStateException"))
    }
}
