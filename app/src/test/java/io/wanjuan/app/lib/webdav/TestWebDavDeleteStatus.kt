package io.wanjuan.app.lib.webdav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestWebDavDeleteStatus {

    @Test
    fun missingDeleteTargetIsAlreadySuccessful() {
        assertTrue(isSuccessfulWebDavDeleteStatus(204))
        assertTrue(isSuccessfulWebDavDeleteStatus(404))
        assertFalse(isSuccessfulWebDavDeleteStatus(401))
        assertFalse(isSuccessfulWebDavDeleteStatus(500))
    }

    @Test
    fun onlyNotFoundMeansResourceIsMissing() {
        assertTrue(webDavResourceStatus(200) == WebDavResourceStatus.EXISTS)
        assertTrue(webDavResourceStatus(207) == WebDavResourceStatus.EXISTS)
        assertTrue(webDavResourceStatus(404) == WebDavResourceStatus.MISSING)
        assertTrue(webDavResourceStatus(401) == WebDavResourceStatus.ERROR)
        assertTrue(webDavResourceStatus(500) == WebDavResourceStatus.ERROR)
    }
}
