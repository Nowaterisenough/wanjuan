package io.wanjuan.app.ci

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GithubWorkflowSigningTest {

    @Test
    fun releaseWorkflowFallsBackToStableCiSigningKey() {
        val workflow = repoFile(".github/workflows/release.yml").readText()
        val fallback = workflow
            .substringAfter("Release signing secrets are not configured")
            .substringBefore("fi")

        assertStableCiSigningFallback(fallback)
    }

    @Test
    fun testWorkflowUsesStableCiSigningKeyForInstallableArtifacts() {
        val workflow = repoFile(".github/workflows/test.yml").readText()

        assertStableCiSigningFallback(workflow)
    }

    @Test
    fun ciSigningKeystoreIsCommittedForFallbackSigning() {
        val keystore = repoFile("app/ci-release.keystore")

        assertTrue(keystore.isFile)
        assertFalse(gitIgnores("app/ci-release.keystore"))
    }

    @Test
    fun bookSourceSampleTestsAreNonBlocking() {
        listOf(
            ".github/workflows/test.yml",
            ".github/workflows/release.yml"
        ).forEach { workflowPath ->
            val workflow = repoFile(workflowPath).readText()
            val bookSourceStep = workflow
                .substringAfter("- name: Run book source sample tests")
                .substringBefore("script: |")

            assertTrue(
                "$workflowPath should keep book source sample test failures visible but non-blocking",
                bookSourceStep.contains("continue-on-error: true")
            )
        }
    }

    @Test
    fun releaseWorkflowWritesGeneratedChangelogIntoBundledUpdateLogBeforeBuild() {
        val workflow = repoFile(".github/workflows/release.yml").readText()
        val buildJob = workflow.substringAfter("  build:")
            .substringBefore("  publish:")
        val publishJob = workflow.substringAfter("  publish:")

        assertTrue(buildJob.contains("echo \"## Wanjuan \${VERSION}\" > release_notes.md"))
        assertTrue(buildJob.contains("python3 .github/scripts/generate_changelog.py >> release_notes.md"))
        assertTrue(buildJob.contains("cp release_notes.md app/src/main/assets/updateLog.md"))
        assertTrue(buildJob.indexOf("cp release_notes.md app/src/main/assets/updateLog.md") <
                buildJob.indexOf("- name: Build release APK"))
        assertTrue(buildJob.contains("name: wanjuan.release-notes"))
        assertTrue(publishJob.contains("name: wanjuan.release-notes"))
        assertTrue(publishJob.contains("path: ."))
        assertTrue(publishJob.contains("body_path: release_notes.md"))
        assertFalse(publishJob.contains("python3 .github/scripts/generate_changelog.py >> release_notes.md"))
    }

    private fun assertStableCiSigningFallback(script: String) {
        assertTrue(script.contains("RELEASE_STORE_FILE=./ci-release.keystore"))
        assertTrue(script.contains("RELEASE_KEY_ALIAS=wanjuan-ci-release"))
        assertTrue(script.contains("RELEASE_STORE_PASSWORD=wanjuan-ci-release"))
        assertTrue(script.contains("RELEASE_KEY_PASSWORD=wanjuan-ci-release"))
        assertFalse(script.contains("keytool -genkeypair"))
        assertFalse(script.contains("ci-debug.keystore"))
        assertFalse(script.contains("RELEASE_STORE_FILE=./wanjuan.jks"))
    }

    private fun repoFile(path: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, path) }
            .firstOrNull { it.exists() }
            ?: error("$path not found")
    }

    private fun gitIgnores(path: String): Boolean {
        val process = ProcessBuilder("git", "check-ignore", "-q", path)
            .redirectErrorStream(true)
            .start()
        return process.waitFor() == 0
    }
}
