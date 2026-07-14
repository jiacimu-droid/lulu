package me.rerere.rikkahub.ui.pages.imggen

import org.junit.Assert.assertEquals
import org.junit.Test

class ImgGenReferenceImagesTest {
    @Test
    fun assistantFaceReferenceIsMergedFirstBeforeManualReferences() {
        val merged = mergeEffectiveReferenceImages(
            assistantFaceReference = "file:///face.png",
            manualReferences = listOf("file:///manual-a.png", "file:///face.png", "file:///manual-b.png"),
            maxImages = 3,
        )

        assertEquals(
            listOf("file:///face.png", "file:///manual-a.png", "file:///manual-b.png"),
            merged,
        )
    }
}
