package io.wanjuan.app.sync.delta

import io.wanjuan.app.sync.local.SyncDeltaBlob
import io.wanjuan.app.sync.local.SyncDeltaPayloadCodec
import org.junit.Assert.*
import org.junit.Test

class TestDeltaPayloadStorage {
    @Test fun largeUnicodePayloadUsesSmallDatabaseRowsAndRoundTrips() {
        val value = "{\"value\":\"" + "\u4e66\ud83d\udcd6".repeat(220000) + "\"}"
        val parts = arrayListOf<SyncDeltaBlob>()
        val encoded = SyncDeltaPayloadCodec.encode(value) { parts += it }!!
        assertTrue(encoded.startsWith(SyncDeltaPayloadCodec.PREFIX))
        assertTrue(encoded.length < 128)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.content.length < 100000 })
        assertEquals(value, SyncDeltaPayloadCodec.decode(encoded) { parts })
        assertTrue(runCatching { SyncDeltaPayloadCodec.decode(encoded) { parts.dropLast(1) } }.isFailure)
        assertTrue(runCatching { SyncDeltaPayloadCodec.decode(encoded) {
            parts.mapIndexed { i, part -> if (i == 0) part.copy(content = "eA==") else part }
        } }.isFailure)
    }

    @Test fun smallValuesAndNullStayInline() {
        assertNull(SyncDeltaPayloadCodec.encode(null) { error("Unexpected part") })
        assertEquals("{}", SyncDeltaPayloadCodec.encode("{}") { error("Unexpected part") })
        assertEquals("{}", SyncDeltaPayloadCodec.decode("{}") { error("Unexpected read") })
    }
}
