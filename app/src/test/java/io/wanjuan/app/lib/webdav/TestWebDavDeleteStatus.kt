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
}
