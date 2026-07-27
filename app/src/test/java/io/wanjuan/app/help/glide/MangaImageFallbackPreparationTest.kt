package io.wanjuan.app.help.glide

import com.script.rhino.rhinoContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException

class MangaImageFallbackPreparationTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("manga-fallback-preparation").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun preparationPassesOriginalModelAndCandidateBytesInsideRequestedScriptContext() {
        val context = SupervisorJob()
        val model = "https://primary.example/a.jpg?token=query-secret," +
            "{\"headers\":{\"Cookie\":\"cookie-secret\"}}"
        val bytes = "encrypted-image".toByteArray()
        val candidate = candidate("bytes-on-disk".toByteArray())
        var observedModel: String? = null
        var observedJob: Job? = null
        var observedFile: File? = null
        val files = object : MangaImagePreparationFiles {
            override fun read(file: File): ByteArray {
                observedFile = file
                return bytes
            }

            override fun write(bytes: ByteArray): File = error("not used")
        }

        val decoded = decodeMangaImageWithContext(context, model, candidate, files) { src, input ->
            observedModel = src
            observedJob = rhinoContext.coroutineContext?.get(Job)
            input + "-decoded".toByteArray()
        }

        assertEquals(model, observedModel)
        assertSame(context, observedJob)
        assertSame(candidate.file, observedFile)
        assertArrayEquals("encrypted-image-decoded".toByteArray(), decoded)
    }

    @Test
    fun preparationDecoderObservesCancellationFromRequestedContext() {
        val context = SupervisorJob().apply { cancel() }
        val candidate = candidate("encrypted-image".toByteArray())

        assertThrows(CancellationException::class.java) {
            decodeMangaImageWithContext(
                context,
                "https://primary.example/a.jpg",
                candidate,
                directPreparationFiles,
            ) { _, _ ->
                requireNotNull(rhinoContext.coroutineContext).ensureActive()
                byteArrayOf(1)
            }
        }
    }

    @Test
    fun preparationRethrowsCancellationWhenDecoderConvertsInterruptionToNull() {
        val context = SupervisorJob().apply { cancel() }
        val candidate = candidate("encrypted-image".toByteArray())

        assertThrows(CancellationException::class.java) {
            decodeMangaImageWithContext(
                context,
                "https://primary.example/a.jpg",
                candidate,
                directPreparationFiles,
            ) { _, _ -> null }
        }
    }

    private val directPreparationFiles = object : MangaImagePreparationFiles {
        override fun read(file: File): ByteArray = file.readBytes()

        override fun write(bytes: ByteArray): File = error("not used")
    }

    private fun candidate(bytes: ByteArray): DownloadedMangaImage {
        val file = File(tempDir, "candidate.img").apply { writeBytes(bytes) }
        return DownloadedMangaImage(file, "https://mirror.example/a.jpg")
    }
}
