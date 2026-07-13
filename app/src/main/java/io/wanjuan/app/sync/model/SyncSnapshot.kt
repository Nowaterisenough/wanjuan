package io.wanjuan.app.sync.model

data class SyncSnapshot(
    val objectType: String,
    val objectId: String,
    val contentHash: String,
    val payloadJson: String,
    val version: SyncVersion
)
