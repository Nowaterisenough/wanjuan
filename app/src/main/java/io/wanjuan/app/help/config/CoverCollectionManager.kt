package io.wanjuan.app.help.config

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.SearchBook
import io.wanjuan.app.utils.FileUtils
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.compress.ZipUtils
import io.wanjuan.app.utils.externalFiles
import io.wanjuan.app.utils.fromJsonArray
import io.wanjuan.app.utils.getFile
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.normalizeFileName
import io.wanjuan.app.utils.putPrefString
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object CoverCollectionManager {

    const val MODE_RANDOM = "random"
    const val MODE_SEQUENCE = "sequence"
    const val MODE_MIXED = "mixed"

    private const val indexFileName = "collections.json"
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "bmp")
    @Volatile
    private var dayCache: List<Collection>? = null
    @Volatile
    private var nightCache: List<Collection>? = null
    private val assignmentCache = hashMapOf<String, MutableList<Assignment>>()

    val rootDir: File
        get() = appCtx.externalFiles.getFile("coverCollections")

    suspend fun load(isNight: Boolean): List<Collection> = withContext(IO) {
        loadIndex(isNight).sortedByDescending { it.updatedAt }
    }

    suspend fun get(isNight: Boolean, id: String?): Collection? = withContext(IO) {
        if (id.isNullOrBlank()) return@withContext null
        loadIndex(isNight).firstOrNull { it.id == id }
    }

    suspend fun create(name: String, isNight: Boolean): Collection = withContext(IO) {
        val cleanName = name.trim().ifBlank {
            if (isNight) "Night collection" else "Day collection"
        }
        val id = UUID.randomUUID().toString()
        val collection = Collection(
            id = id,
            name = cleanName,
            dirName = buildDirName(cleanName, id),
            isNight = isNight,
            updatedAt = System.currentTimeMillis()
        )
        collectionDir(collection).mkdirs()
        saveIndex(isNight, loadIndex(isNight) + collection)
        collection
    }

    suspend fun importZip(context: Context, zipFile: File, isNight: Boolean): Collection = withContext(IO) {
        val name = zipFile.nameWithoutExtension.ifBlank { "Imported collection" }
        val collection = create(name, isNight)
        val dir = collectionDir(collection)
        val files = ZipUtils.unZipToPath(zipFile, dir) {
            it.substringAfterLast('.', "").lowercase() in imageExtensions
        }
        val images = files.filter { it.isFile && it.extension.lowercase() in imageExtensions }
            .sortedBy { it.name }
            .map { it.absolutePath }
        if (images.isEmpty()) {
            delete(collection)
            throw IllegalArgumentException("No usable images in ZIP")
        }
        update(collection.copy(images = images, updatedAt = System.currentTimeMillis()))
    }

    suspend fun addImages(context: Context, collection: Collection, uris: List<Uri>): Collection = withContext(IO) {
        val dir = collectionDir(collection).apply { mkdirs() }
        val added = arrayListOf<String>()
        uris.forEachIndexed { index, uri ->
            val ext = resolveExtension(context, uri)
            val file = File(dir, "${System.currentTimeMillis()}_$index.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            if (file.exists() && file.length() > 0L) {
                added.add(file.absolutePath)
            } else {
                file.delete()
            }
        }
        update(
            collection.copy(
                images = (collection.images + added).distinct(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun update(collection: Collection): Collection = withContext(IO) {
        val list = loadIndex(collection.isNight).toMutableList()
        val index = list.indexOfFirst { it.id == collection.id }
        if (index >= 0) {
            list[index] = collection
        } else {
            list.add(collection)
        }
        saveIndex(collection.isNight, list)
        collection
    }

    suspend fun delete(collection: Collection) = withContext(IO) {
        saveIndex(collection.isNight, loadIndex(collection.isNight).filterNot { it.id == collection.id })
        FileUtils.delete(collectionDir(collection), deleteRootDir = true)
        clearAssignments(collection.id)
    }

    fun isMixedMode(): Boolean {
        val isNight = AppConfig.isNightTheme
        return appCtx.getPrefString(
            if (isNight) PreferKey.coverCollectionModeNight else PreferKey.coverCollectionModeDay,
            MODE_RANDOM
        ) == MODE_MIXED
    }

    fun selectionKey(): String {
        val isNight = AppConfig.isNightTheme
        val collectionId = appCtx.getPrefString(
            if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay
        ).orEmpty()
        val mode = appCtx.getPrefString(
            if (isNight) PreferKey.coverCollectionModeNight else PreferKey.coverCollectionModeDay,
            MODE_RANDOM
        ).orEmpty()
        return "$isNight:$collectionId:$mode"
    }

    fun selectedCollectionCover(book: Book): String? {
        return selectedCollectionCover(
            bookKey = coverKey(book),
            hasOriginalCover = book.getDisplayCover().isRealCoverPath()
        )
    }

    fun selectedCollectionCover(searchBook: SearchBook): String? {
        return selectedCollectionCover(
            bookKey = coverKey(searchBook),
            hasOriginalCover = searchBook.coverUrl.isRealCoverPath()
        )
    }

    fun coverKey(book: Book): String {
        return book.bookUrl.ifBlank {
            "${book.origin}|${book.name}|${book.author}"
        }
    }

    fun coverKey(searchBook: SearchBook): String {
        return searchBook.bookUrl.ifBlank {
            "${searchBook.origin}|${searchBook.name}|${searchBook.author}"
        }
    }

    fun stableImageIndex(bookKey: String, imageCount: Int): Int {
        require(imageCount > 0) { "imageCount must be positive" }
        return (bookKey.hashCode() and Int.MAX_VALUE) % imageCount
    }

    fun String?.isRealCoverPath(): Boolean {
        val value = this?.trim().orEmpty()
        if (value.isBlank() || value.equals("use_default_cover", ignoreCase = true)) {
            return false
        }
        val lowerValue = value.lowercase()
        return when {
            lowerValue.startsWith("http://") ||
                    lowerValue.startsWith("https://") ||
                    lowerValue.startsWith("content://") ||
                    lowerValue.startsWith("android.resource://") ||
                    lowerValue.startsWith("file:///android_asset/") -> true
            lowerValue.startsWith("file://") -> runCatching {
                File(Uri.parse(value).path.orEmpty()).isFile
            }.getOrDefault(false)
            File(value).isAbsolute -> File(value).isFile
            else -> true
        }
    }

    private fun selectedCollectionCover(bookKey: String, hasOriginalCover: Boolean): String? {
        val isNight = AppConfig.isNightTheme
        val collectionId = appCtx.getPrefString(
            if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay
        ) ?: return null
        val collection = loadIndex(isNight).firstOrNull { it.id == collectionId } ?: return null
        if (collection.images.isEmpty()) return null
        val mode = appCtx.getPrefString(
            if (isNight) PreferKey.coverCollectionModeNight else PreferKey.coverCollectionModeDay,
            MODE_RANDOM
        ) ?: MODE_RANDOM
        if (mode == MODE_MIXED && hasOriginalCover) {
            return null
        }
        val path = when (mode) {
            MODE_SEQUENCE -> assignSequential(collection, bookKey)
            else -> collection.images[stableImageIndex(bookKey, collection.images.size)]
        }
        return path.takeIf { File(it).exists() }
    }

    fun setSelected(isNight: Boolean, collectionId: String?) {
        val key = if (isNight) PreferKey.coverCollectionNight else PreferKey.coverCollectionDay
        appCtx.putPrefString(key, collectionId.orEmpty())
    }

    fun collectionDir(collection: Collection): File {
        return typeDir(collection.isNight).getFile(collection.dirName)
    }

    private fun typeDir(isNight: Boolean): File {
        return rootDir.getFile(if (isNight) "night" else "day").apply { mkdirs() }
    }

    private fun indexFile(isNight: Boolean): File {
        return typeDir(isNight).getFile(indexFileName)
    }

    private fun loadIndex(isNight: Boolean): List<Collection> {
        (if (isNight) nightCache else dayCache)?.let { return it }
        val file = indexFile(isNight)
        if (!file.exists()) return emptyList()
        val list = GSON.fromJsonArray<Collection>(file.readText()).getOrDefault(emptyList())
            .map { collection ->
                collection.copy(images = collection.images.filter { File(it).exists() })
            }
        if (isNight) {
            nightCache = list
        } else {
            dayCache = list
        }
        return list
    }

    private fun saveIndex(isNight: Boolean, list: List<Collection>) {
        val file = indexFile(isNight)
        file.parentFile?.mkdirs()
        file.writeText(GSON.toJson(list))
        if (isNight) {
            nightCache = list
        } else {
            dayCache = list
        }
    }

    private fun assignmentsFile(collectionId: String): File {
        return rootDir.getFile("assignments").apply { mkdirs() }.getFile("$collectionId.json")
    }

    @Synchronized
    private fun assignSequential(collection: Collection, bookKey: String): String {
        val assignments = assignmentCache.getOrPut(collection.id) {
            readAssignments(collection.id).toMutableList()
        }
        assignments.firstOrNull { it.bookUrl == bookKey }?.let {
            return collection.images.getOrNull(it.index % collection.images.size) ?: collection.images.first()
        }
        val index = assignments.size % collection.images.size
        assignments.add(Assignment(bookKey, index))
        saveAssignments(collection.id, assignments)
        return collection.images[index]
    }

    private fun readAssignments(collectionId: String): List<Assignment> {
        val file = assignmentsFile(collectionId)
        return GSON.fromJsonArray<Assignment>(
            file.takeIf { it.exists() }?.readText()
        ).getOrDefault(emptyList())
    }

    private fun saveAssignments(collectionId: String, assignments: List<Assignment>) {
        assignmentsFile(collectionId).writeText(GSON.toJson(assignments))
    }

    @Synchronized
    private fun clearAssignments(collectionId: String) {
        assignmentCache.remove(collectionId)
        assignmentsFile(collectionId).delete()
    }

    private fun resolveExtension(context: Context, uri: Uri): String {
        val last = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()
        if (last in imageExtensions) return last!!
        val type = context.contentResolver.getType(uri).orEmpty()
        return when {
            type.contains("png", true) -> "png"
            type.contains("webp", true) -> "webp"
            type.contains("bmp", true) -> "bmp"
            else -> "jpg"
        }
    }

    fun buildDirName(name: String, id: String): String {
        val safeName = name.normalizeFileName().ifBlank { "collection" }
        val safeId = id.normalizeFileName().ifBlank { "default" }
        return "${safeName}_$safeId"
    }

    @Keep
    data class Collection(
        val id: String = "",
        val name: String = "",
        val dirName: String = "",
        val isNight: Boolean = false,
        val mode: String = MODE_RANDOM,
        val images: List<String> = emptyList(),
        val updatedAt: Long = 0L
    )

    @Keep
    private data class Assignment(
        val bookUrl: String = "",
        val index: Int = 0
    )
}
