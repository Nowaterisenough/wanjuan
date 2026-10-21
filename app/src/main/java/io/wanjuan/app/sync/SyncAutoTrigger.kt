package io.wanjuan.app.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

class SyncAutoTrigger(private val debounceMillis: Long = 1000L) {
    @OptIn(FlowPreview::class)
    suspend fun observe(
        fingerprints: Flow<String>,
        isEnabled: () -> Boolean,
        hasPendingChanges: suspend () -> Boolean,
        requestSync: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        fingerprints.distinctUntilChanged().drop(1).debounce(debounceMillis).collect {
            if (!isEnabled()) return@collect
            try {
                if (hasPendingChanges()) requestSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
}
