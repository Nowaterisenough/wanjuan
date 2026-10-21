package io.wanjuan.app.sync.remote

import io.wanjuan.app.sync.model.SyncBookPayload
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class SyncRemoteTarget(val identity: String, val store: SyncRemoteStore)

/** Pin a target for an entire sync, even if settings are edited while it is running. */
class SelectedSyncRemoteStore(
    private val targetProvider: () -> SyncRemoteTarget
) : SyncRemoteStore {
    private class Session(val target: SyncRemoteTarget) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Session>
    }

    suspend fun <T> withTarget(block: suspend (SyncRemoteTarget) -> T): T {
        val target = currentCoroutineContext()[Session]?.target ?: targetProvider()
        return withContext(Session(target)) { block(target) }
    }

    private suspend fun store(): SyncRemoteStore =
        currentCoroutineContext()[Session]?.target?.store ?: targetProvider().store

    override suspend fun ensureDirs() = store().ensureDirs()
    override suspend fun list(relativeDir: String) = store().list(relativeDir)
    override suspend fun downloadJson(relativePath: String) = store().downloadJson(relativePath)
    override suspend fun uploadJson(relativePath: String, json: String) = store().uploadJson(relativePath, json)
    override suspend fun delete(relativePath: String) = store().delete(relativePath)
    override suspend fun updateBook(id: String, transform: (SyncBookPayload?) -> SyncBookPayload?) =
        store().updateBook(id, transform)
}
