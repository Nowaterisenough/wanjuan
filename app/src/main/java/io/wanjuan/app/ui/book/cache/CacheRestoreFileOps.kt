package io.wanjuan.app.ui.book.cache

import java.io.File

object CacheRestoreFileOps {

    fun copyPayloadToStaging(
        payloadDir: File,
        targetDir: File,
        emptyMessage: String = "No cache payload",
        failedMessage: String = "Cache restore failed"
    ): File {
        require(payloadDir.exists() && payloadDir.isDirectory) { failedMessage }
        val parent = targetDir.parentFile ?: throw IllegalStateException(failedMessage)
        parent.mkdirs()
        val stagingDir = File(parent, "${targetDir.name}.restore_${System.currentTimeMillis()}")
        if (stagingDir.exists()) {
            stagingDir.deleteRecursively()
        }
        stagingDir.mkdirs()
        payloadDir.listFiles()?.forEach { file ->
            val target = File(stagingDir, file.name)
            if (file.isDirectory) {
                file.copyRecursively(target, overwrite = true)
            } else {
                file.copyTo(target, overwrite = true)
            }
        }
        if (stagingDir.listFiles().isNullOrEmpty()) {
            stagingDir.deleteRecursively()
            throw IllegalStateException(emptyMessage)
        }
        return stagingDir
    }

    fun replaceDirectory(
        targetDir: File,
        replacementDir: File,
        failedMessage: String = "Cache restore failed"
    ) {
        val parent = targetDir.parentFile ?: throw IllegalStateException(failedMessage)
        parent.mkdirs()
        val backupDir = File(parent, "${targetDir.name}.backup_${System.currentTimeMillis()}")
        val hadOld = targetDir.exists()
        if (hadOld && !targetDir.renameTo(backupDir)) {
            replacementDir.deleteRecursively()
            throw IllegalStateException(failedMessage)
        }
        try {
            if (!replacementDir.renameTo(targetDir)) {
                replacementDir.copyRecursively(targetDir, overwrite = true)
                replacementDir.deleteRecursively()
            }
            if (hadOld) {
                backupDir.deleteRecursively()
            }
        } catch (e: Throwable) {
            targetDir.deleteRecursively()
            if (hadOld && backupDir.exists()) {
                backupDir.renameTo(targetDir)
            }
            throw e
        }
    }
}
