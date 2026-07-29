package io.wanjuan.app.ui.widget.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestCoverCropCalculator {
    @Test
    fun wideImageAlignsItsRightEdgeWithTheCoverView() {
        val result = requireNotNull(CoverCropCalculator.calculate(300, 400, 1600, 900))

        assertEquals(400f / 900f, result.scale, 0.0001f)
        assertEquals(300f - 1600f * result.scale, result.translateX, 0.0001f)
        assertEquals(0f, result.translateY, 0.0001f)
    }

    @Test
    fun portraitImageWithoutHorizontalOverflowRemainsCentered() {
        val result = requireNotNull(CoverCropCalculator.calculate(300, 400, 600, 1000))

        assertEquals(0.5f, result.scale, 0.0001f)
        assertEquals(0f, result.translateX, 0.0001f)
        assertEquals(-50f, result.translateY, 0.0001f)
    }

    @Test
    fun wideThumbnailPreservesItsAspectRatioInsideTheCacheBounds() {
        val result = requireNotNull(CoverCropCalculator.fitWithin(240, 320, 1600, 900))

        assertEquals(240, result.width)
        assertEquals(135, result.height)
    }

    @Test
    fun portraitThumbnailPreservesItsAspectRatioInsideTheCacheBounds() {
        val result = requireNotNull(CoverCropCalculator.fitWithin(240, 320, 600, 1000))

        assertEquals(192, result.width)
        assertEquals(320, result.height)
    }

    @Test
    fun invalidDimensionsDoNotProduceATransform() {
        assertNull(CoverCropCalculator.calculate(0, 400, 1600, 900))
        assertNull(CoverCropCalculator.calculate(300, 400, -1, 900))
    }

    @Test
    fun coverImageViewUsesTheAdaptiveMatrixForLoadedCovers() {
        val source = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/widget/image/CoverImageView.kt"
        ).readText()
        val loadedCoverRequest = source.substringAfter("builder\n                .priority(Priority.HIGH)")
            .substringBefore("                .into(this)")

        assertTrue(source.contains("scaleType = ScaleType.MATRIX"))
        assertTrue(source.contains("private fun applyAdaptiveCropMatrix()"))
        assertTrue(source.contains("override fun setImageDrawable(drawable: Drawable?)"))
        assertFalse(loadedCoverRequest.contains(".centerCrop()"))
    }

    @Test
    fun thumbnailCacheKeepsSourceAspectRatioAndInvalidatesStretchedEntries() {
        val source = repoFile(
            "app/src/main/java/io/wanjuan/app/help/CoverThumbnailCache.kt"
        ).readText()

        assertTrue(source.contains("cover_thumbs_v3"))
        assertTrue(source.contains("CoverCropCalculator.fitWithin("))
        assertFalse(source.contains("Bitmap.createScaledBitmap(source, thumbWidth, thumbHeight"))
    }

    @Test
    fun calculationsDoNotLeakAlignmentBetweenImages() {
        val wide = requireNotNull(CoverCropCalculator.calculate(300, 400, 1600, 900))
        val portrait = requireNotNull(CoverCropCalculator.calculate(300, 400, 900, 1200))

        assertEquals(300f - 1600f * wide.scale, wide.translateX, 0.0001f)
        assertEquals(0f, portrait.translateX, 0.0001f)
        assertEquals(0f, portrait.translateY, 0.0001f)
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
