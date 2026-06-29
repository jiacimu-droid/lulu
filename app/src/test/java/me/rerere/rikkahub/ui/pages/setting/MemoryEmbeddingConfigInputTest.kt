package me.rerere.rikkahub.ui.pages.setting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryEmbeddingConfigInputTest {
    @Test
    fun parseMemoryEmbeddingDimensionsInputAllowsBlankOrPositiveInteger() {
        assertNull(parseMemoryEmbeddingDimensionsInput(""))
        assertNull(parseMemoryEmbeddingDimensionsInput("  "))
        assertNull(parseMemoryEmbeddingDimensionsInput("0"))
        assertNull(parseMemoryEmbeddingDimensionsInput("-12"))
        assertEquals(1536, parseMemoryEmbeddingDimensionsInput("1536"))
    }

    @Test
    fun parseMemoryEmbeddingBatchSizeInputClampsToServiceRange() {
        assertEquals(1, parseMemoryEmbeddingBatchSizeInput(""))
        assertEquals(1, parseMemoryEmbeddingBatchSizeInput("0"))
        assertEquals(12, parseMemoryEmbeddingBatchSizeInput("12"))
        assertEquals(64, parseMemoryEmbeddingBatchSizeInput("200"))
    }
}
