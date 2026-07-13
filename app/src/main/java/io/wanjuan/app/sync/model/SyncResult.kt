package io.wanjuan.app.sync.model

data class SyncResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = failed == 0

    class Mutable {
        var uploaded: Int = 0
        var downloaded: Int = 0
        var inserted: Int = 0
        var updated: Int = 0
        var deleted: Int = 0
        var skipped: Int = 0
        var failed: Int = 0
        var errorMessage: String? = null

        fun fail(message: String) {
            failed += 1
            if (errorMessage == null) {
                errorMessage = message
            }
        }

        fun toResult(): SyncResult = SyncResult(
            uploaded = uploaded,
            downloaded = downloaded,
            inserted = inserted,
            updated = updated,
            deleted = deleted,
            skipped = skipped,
            failed = failed,
            errorMessage = errorMessage
        )
    }
}
