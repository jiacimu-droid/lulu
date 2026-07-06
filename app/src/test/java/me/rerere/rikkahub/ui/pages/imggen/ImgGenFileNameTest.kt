package me.rerere.rikkahub.ui.pages.imggen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImgGenFileNameTest {
    @Test
    fun `image model names are safe for generated file names`() {
        val safe = "[0.08]米/gpt-image-2".toSafeFileNamePart()

        assertFalse(safe.contains('/'))
        assertFalse(safe.contains('\\'))
        assertTrue(safe.contains("gpt-image-2"))
    }
}
