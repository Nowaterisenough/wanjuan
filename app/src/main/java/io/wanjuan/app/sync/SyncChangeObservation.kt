package io.wanjuan.app.sync

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Refresh owns the final capture/upload. Also wake after cancellation so edits are not stranded. */
@OptIn(FlowPreview::class)
internal fun Flow<*>.afterBookshelfRefresh(
    refreshing: StateFlow<Boolean>,
    debounceMillis: Long = 1000L
): Flow<Unit> = combine(refreshing) { _, active -> active }
    .debounce(debounceMillis)
    .filter { !it }
    .map { Unit }
