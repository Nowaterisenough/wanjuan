package io.wanjuan.app.help.update

import io.wanjuan.app.exception.NoStackTraceException
import org.junit.Assert.*
import org.junit.Test

class TestReleaseSelection {
    @Test fun versionsCompareNumerically() {
        assertTrue(ReleaseVersion.compare("4.10.1", "4.9.9") > 0)
        assertEquals(0, ReleaseVersion.compare("4.10", "4.10.0"))
    }

    @Test fun legacyHourAndMinuteVersionsUseTheSameClock() {
        assertEquals(0, ReleaseVersion.compare("4.26.102311", "4.26.10231100"))
        assertTrue(ReleaseVersion.compare("4.26.102311", "4.26.10231059") > 0)
        assertTrue(ReleaseVersion.compare("4.26.10231101", "4.26.102311") > 0)
    }

    @Test fun extractsVersionsFromBothCiAndLocalAssetNames() {
        assertEquals("4.26.10231129", ReleaseVersion.fromAssetName("wanjuan_app_4.26.10231129.apk"))
        assertEquals("4.26.10231129", ReleaseVersion.fromAssetName("万卷_app_4.26.10231129_29878769.apk"))
        assertEquals("4.10.1", ReleaseVersion.fromAssetName("wanjuan_app_4.10.1.APK"))
        assertEquals("", ReleaseVersion.fromAssetName("mapping.txt"))
    }

    @Test fun equalOrOlderCloudVersionIsANormalResult() {
        assertNull(AppUpdateGitHub.selectUpdate(release("4.26.10231033"), "4.26.10231033"))
        assertNull(AppUpdateGitHub.selectUpdate(release("4.26.10231033"), "4.26.10231129"))
    }

    @Test fun highestVersionWinsInsteadOfUploadTime() {
        val release = release("4.9.9").copy(assets = listOf(asset("4.9.9"), asset("4.10.1")))
        assertEquals("4.10.1", AppUpdateGitHub.selectUpdate(release, "4.9.0")!!.tagName)
    }

    @Test fun octetStreamApkIsAcceptedButArbitraryFilesAreNot() {
        assertTrue(asset("4.10.1").copy(contentType = "application/octet-stream").isValid)
        assertFalse(asset("4.10.1").copy(name = "mapping.txt").isValid)
    }

    @Test fun missingApkAndPreviewAreNotReportedAsUpToDate() {
        assertThrows(NoStackTraceException::class.java) {
            AppUpdateGitHub.selectUpdate(GithubRelease(emptyList(), "", false), "4.10.1")
        }
        assertThrows(NoStackTraceException::class.java) {
            AppUpdateGitHub.selectUpdate(release("4.10.1").copy(isPreRelease = true), "4.9.0")
        }
    }

    private fun release(version: String) = GithubRelease(listOf(asset(version)), "notes", false)
    private fun asset(version: String) = Asset("https://example.org/app.apk",
        "application/vnd.android.package-archive", "2026-10-23T00:00:00Z", 0, 1,
        "wanjuan_app_$version.apk", "uploaded", "https://example.org/asset")
}
