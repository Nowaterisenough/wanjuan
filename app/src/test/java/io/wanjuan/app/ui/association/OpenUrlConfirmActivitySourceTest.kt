package io.wanjuan.app.ui.association

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenUrlConfirmActivitySourceTest {

    @Test
    fun javdbSourceBypassesAppConfirmDialog() {
        val activity = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/association/OpenUrlConfirmActivity.kt"
        ).readText()

        assertTrue(activity.contains("private const val JAVDB_SOURCE_ORIGIN = \"https://javdb.com/\""))
        assertTrue(activity.contains("private fun shouldSkipAppConfirm(sourceOrigin: String?): Boolean"))
        assertTrue(activity.contains("sourceOrigin == JAVDB_SOURCE_ORIGIN"))
        assertTrue(activity.contains("if (shouldSkipAppConfirm(sourceOrigin))"))
        assertTrue(activity.contains("OpenUrlLauncher.open(this, uri, mimeType)"))
        assertTrue(activity.contains("finish()"))
    }

    @Test
    fun nonJavdbSourcesStillShowConfirmDialog() {
        val activity = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/association/OpenUrlConfirmActivity.kt"
        ).readText()

        assertTrue(
            activity.contains(
                "showDialogFragment(OpenUrlConfirmDialog(uri, mimeType, sourceOrigin, sourceName, sourceType))"
            )
        )
    }

    @Test
    fun dialogAndDirectOpenShareSameLauncher() {
        val dialog = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/association/OpenUrlConfirmDialog.kt"
        ).readText()

        assertTrue(dialog.contains("OpenUrlLauncher.open(requireContext(), viewModel.uri, viewModel.mimeType)"))
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
