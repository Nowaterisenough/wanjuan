package io.wanjuan.app.sync.merge

import io.wanjuan.app.sync.model.SyncVersion

enum class SyncWinner {
    None,
    LocalObject,
    RemoteObject,
    LocalDelete,
    RemoteDelete
}

object SyncConflictResolver {

    fun choose(
        localObject: SyncVersion?,
        remoteObject: SyncVersion?,
        localDelete: SyncVersion?,
        remoteDelete: SyncVersion?
    ): SyncWinner {
        return listOfNotNull(
            localObject?.let { SyncWinner.LocalObject to it },
            remoteObject?.let { SyncWinner.RemoteObject to it },
            localDelete?.let { SyncWinner.LocalDelete to it },
            remoteDelete?.let { SyncWinner.RemoteDelete to it }
        ).maxWithOrNull(
            compareBy<Pair<SyncWinner, SyncVersion>> { it.second }
                .thenBy { it.first.ordinal }
        )?.first ?: SyncWinner.None
    }
}
