package io.wanjuan.app.help.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.view.Menu
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import io.wanjuan.app.R
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.lib.theme.ThemeStore
import io.wanjuan.app.lib.theme.bottomBackground
import io.wanjuan.app.lib.theme.getSecondaryTextColor
import io.wanjuan.app.utils.ColorUtils
import io.wanjuan.app.utils.FileUtils
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.SvgUtils
import io.wanjuan.app.utils.compress.ZipUtils
import io.wanjuan.app.utils.externalFiles
import io.wanjuan.app.utils.fromJsonObject
import io.wanjuan.app.utils.getFile
import io.wanjuan.app.utils.getPrefBoolean
import io.wanjuan.app.utils.getPrefString
import io.wanjuan.app.utils.normalizeFileName
import io.wanjuan.app.utils.putPrefBoolean
import io.wanjuan.app.utils.putPrefString
import splitties.init.appCtx
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.min

object NavigationBarIconConfig {

    const val MODE_DAY = "day"
    const val MODE_NIGHT = "night"
    const val STATE_NORMAL = "normal"
    const val STATE_SELECTED = "selected"
    const val DEFAULT_DIR_NAME = "default"
    private const val packageFileName = "navigation.json"
    private const val activeDayKey = "navigationBarPackageDay"
    private const val activeNightKey = "navigationBarPackageNight"
    private const val legacyMigratedDayKey = "navigationBarLegacyMigratedDay"
    private const val legacyMigratedNightKey = "navigationBarLegacyMigratedNight"

    val rootDir: File
        get() = appCtx.externalFiles.getFile("navigationBarPackages")

    private val legacyRootDir: File
        get() = appCtx.externalFiles.getFile("navigationIcons")

    private val tempDir: File
        get() = appCtx.externalFiles.getFile("navigationBarTemp").apply { mkdirs() }

    data class NavItem(
        val key: String,
        @StringRes val titleRes: Int,
        @IdRes val menuId: Int,
        @DrawableRes val defaultIconRes: Int
    )

    @Keep
    data class Config(
        var name: String,
        var isNightMode: Boolean,
        var layoutMode: String = "floating",
        var sidebarGravity: String = "start",
        var effectMode: String = "glass",
        var opacity: Int = 72,
        var updatedAt: Long = System.currentTimeMillis(),
        var sidebarBackgroundPath: String? = null,
        var icons: MutableMap<String, String> = linkedMapOf()
    )

    data class Entry(
        val config: Config,
        val dirName: String,
        val localDir: File? = null
    )

    val items = listOf(
        NavItem("bookshelf", R.string.bookshelf, R.id.menu_bookshelf, R.drawable.ic_lucide_books),
        NavItem("discovery", R.string.discovery, R.id.menu_discovery, R.drawable.ic_lucide_compass),
        NavItem("rss", R.string.rss, R.id.menu_rss, R.drawable.ic_lucide_rss),
        NavItem("readRecord", R.string.side_nav_stats, R.id.menu_read_record, R.drawable.ic_lucide_chart_bar),
        NavItem("my", R.string.setting, R.id.menu_my_config, R.drawable.ic_lucide_settings),
        NavItem("ai", R.string.side_nav_assistant, R.id.menu_ai, R.drawable.ic_bottom_ai_assistant)
    )

    fun activeDirName(isNight: Boolean): String {
        return appCtx.getPrefString(if (isNight) activeNightKey else activeDayKey, DEFAULT_DIR_NAME)
            ?.ifBlank { DEFAULT_DIR_NAME }
            ?: DEFAULT_DIR_NAME
    }

    suspend fun loadEntries(isNight: Boolean): List<Entry> {
        migrateLegacyIconsIfNeeded(isNight)
        val entries = linkedMapOf<String, Entry>()
        entries[DEFAULT_DIR_NAME] = defaultEntry(isNight)
        loadLocal(isNight).forEach { entry ->
            entries[entry.dirName] = entry
        }
        return entries.values.sortedWith(
            compareBy<Entry> { it.dirName != DEFAULT_DIR_NAME }
                .thenByDescending { it.config.updatedAt }
                .thenBy { it.config.name }
                .thenBy { it.dirName }
        )
    }

    fun currentEntry(isNight: Boolean): Entry {
        val dirName = activeDirName(isNight)
        if (dirName == DEFAULT_DIR_NAME) return defaultEntry(isNight)
        return readEntry(localDir(isNight, dirName)) ?: defaultEntry(isNight)
    }

    fun apply(entry: Entry) {
        val config = normalizeConfig(entry.config)
        val key = if (config.isNightMode) activeNightKey else activeDayKey
        appCtx.putPrefString(key, entry.dirName)
        AppConfig.bottomBarLayoutMode = config.layoutMode
        AppConfig.bottomBarSidebarGravity = config.sidebarGravity
        AppConfig.bottomBarEffectMode = config.effectMode
        AppConfig.liquidGlassLevel = config.opacity
        AppConfig.frostedGlassLevel = config.opacity
        if (config.layoutMode == "sidebar") {
            appCtx.putPrefBoolean(PreferKey.mergeDiscoveryRss, false)
        }
    }

    fun applyCurrentBottomConfig(isNight: Boolean) {
        val config = normalizeConfig(currentEntry(isNight).config)
        AppConfig.bottomBarLayoutMode = config.layoutMode
        AppConfig.bottomBarSidebarGravity = config.sidebarGravity
        AppConfig.bottomBarEffectMode = config.effectMode
        AppConfig.liquidGlassLevel = config.opacity
        AppConfig.frostedGlassLevel = config.opacity
        if (config.layoutMode == "sidebar") {
            appCtx.putPrefBoolean(PreferKey.mergeDiscoveryRss, false)
        }
    }

    fun addOrUpdate(config: Config, oldEntry: Entry? = null): Entry {
        val name = config.name.trim().ifBlank { defaultName(config.isNightMode) }
        val keepOldDir = oldEntry != null &&
            oldEntry.dirName.isNotBlank() &&
            oldEntry.dirName != DEFAULT_DIR_NAME
        val dirName = if (keepOldDir) {
            oldEntry!!.dirName
        } else {
            name.normalizeFileName().ifBlank { "navigation_${System.currentTimeMillis()}" }
        }
        if (!keepOldDir && readEntry(localDir(config.isNightMode, dirName)) != null) {
            throw IllegalArgumentException(appCtx.getString(R.string.navigation_bar_name_exists))
        }
        val dir = localDir(config.isNightMode, dirName).apply { mkdirs() }
        val source = normalizeConfig(config)
        val normalized = source.copy(
            name = name,
            layoutMode = source.layoutMode,
            sidebarGravity = source.sidebarGravity,
            effectMode = source.effectMode,
            opacity = source.opacity.coerceIn(0, 100),
            updatedAt = System.currentTimeMillis(),
            icons = source.icons.toMutableMap()
        )
        File(dir, packageFileName).writeText(GSON.toJson(normalized))
        return Entry(normalized, dirName, localDir = dir)
    }

    fun deleteLocal(entry: Entry) {
        if (entry.dirName == DEFAULT_DIR_NAME) return
        FileUtils.delete(entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName), deleteRootDir = true)
        resetActiveIfNeeded(entry)
    }

    suspend fun exportZip(entry: Entry): File {
        val dir = entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName)
        val zipFile = tempDir.getFile("${entry.dirName}.zip")
        if (zipFile.exists()) zipFile.delete()
        ZipUtils.zipFile(dir, zipFile)
        return zipFile
    }

    fun importZip(zipFile: File): Entry {
        return importZipInternal(zipFile)
    }

    fun saveIconToPackage(
        context: Context,
        uri: Uri,
        entry: Entry,
        itemKey: String,
        selected: Boolean,
        targetSize: Int
    ): Entry {
        if (entry.dirName == DEFAULT_DIR_NAME) {
            throw IllegalArgumentException(context.getString(R.string.navigation_bar_default_readonly))
        }
        val bitmap = decodeIconBitmap(context, uri, targetSize) ?: throw IllegalArgumentException(context.getString(R.string.navigation_icon_decode_failed))
        val output = drawCenteredIcon(bitmap, targetSize)
        if (bitmap !== output && !bitmap.isRecycled) bitmap.recycle()
        val dirName = entry.dirName.ifBlank {
            entry.config.name.normalizeFileName().ifBlank { "navigation_${System.currentTimeMillis()}" }
        }
        val dir = entry.localDir ?: localDir(entry.config.isNightMode, dirName).apply { mkdirs() }
        val state = if (selected) STATE_SELECTED else STATE_NORMAL
        val fileName = "${itemKey}_$state.png"
        val file = File(dir, fileName)
        FileOutputStream(file).use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!output.isRecycled) output.recycle()
        val config = entry.config.copy(icons = entry.config.icons.toMutableMap())
        config.icons[iconKey(itemKey, state)] = fileName
        return addOrUpdate(config, entry)
    }

    fun clearIcon(entry: Entry, itemKey: String, selected: Boolean): Entry {
        if (entry.dirName == DEFAULT_DIR_NAME) return entry
        val state = if (selected) STATE_SELECTED else STATE_NORMAL
        val config = entry.config.copy(icons = entry.config.icons.toMutableMap())
        config.icons.remove(iconKey(itemKey, state))
        return addOrUpdate(config, entry)
    }

    fun saveSidebarBackgroundToPackage(
        context: Context,
        sourcePath: String,
        entry: Entry
    ): Entry {
        if (entry.dirName == DEFAULT_DIR_NAME) {
            throw IllegalArgumentException(context.getString(R.string.navigation_bar_default_readonly))
        }
        val source = File(sourcePath)
        if (!source.exists()) {
            throw IllegalArgumentException(
                context.getString(R.string.image_crop_failed, context.getString(R.string.unknown))
            )
        }
        val dirName = entry.dirName.ifBlank {
            entry.config.name.normalizeFileName().ifBlank { "navigation_${System.currentTimeMillis()}" }
        }
        val dir = entry.localDir ?: localDir(entry.config.isNightMode, dirName).apply { mkdirs() }
        val suffix = source.name.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() }
            ?.let { ".${it.lowercase(Locale.ROOT)}" }
            ?: ".jpg"
        val fileName = "sidebar_background$suffix"
        source.copyTo(File(dir, fileName), overwrite = true)
        val config = entry.config.copy(icons = entry.config.icons.toMutableMap())
        config.sidebarBackgroundPath = fileName
        return addOrUpdate(config, entry)
    }

    fun clearSidebarBackground(entry: Entry): Entry {
        if (entry.dirName == DEFAULT_DIR_NAME) return entry
        entry.config.sidebarBackgroundPath?.let { name ->
            File(entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName), name)
                .takeIf { it.exists() }
                ?.delete()
        }
        val config = entry.config.copy(icons = entry.config.icons.toMutableMap())
        config.sidebarBackgroundPath = null
        return addOrUpdate(config, entry)
    }

    fun applyTo(menu: Menu, context: Context, isNight: Boolean): Boolean {
        val entry = currentEntry(isNight)
        val hasCustom = entry.dirName != DEFAULT_DIR_NAME && entry.config.icons.isNotEmpty()
        items.forEach { item ->
            menu.findItem(item.menuId)?.icon = createMenuDrawable(context, entry, item)
        }
        return hasCustom
    }

    fun previewDrawable(context: Context, entry: Entry, item: NavItem, selected: Boolean): Drawable? {
        val state = if (selected) STATE_SELECTED else STATE_NORMAL
        return loadDrawable(context, iconPath(entry, item.key, state))
            ?: loadDrawable(context, iconPath(entry, item.key, STATE_NORMAL))
            ?: ContextCompat.getDrawable(context, item.defaultIconRes)
    }

    fun currentDrawable(context: Context, itemKey: String, selected: Boolean): Drawable? {
        val item = items.firstOrNull { it.key == itemKey } ?: return null
        return previewDrawable(context, currentEntry(AppConfig.isNightTheme), item, selected)
    }

    fun currentMenuDrawable(context: Context, itemKey: String): Drawable? {
        val item = items.firstOrNull { it.key == itemKey } ?: return null
        return createMenuDrawable(context, currentEntry(AppConfig.isNightTheme), item)
    }

    fun currentSidebarBackgroundPath(isNight: Boolean): String? {
        return resolveSidebarBackgroundPath(currentEntry(isNight))
    }

    fun getIconFileName(entry: Entry, itemKey: String, selected: Boolean): String? {
        val state = if (selected) STATE_SELECTED else STATE_NORMAL
        return entry.config.icons[iconKey(itemKey, state)]?.let { File(it).name }
    }

    private fun defaultEntry(isNight: Boolean): Entry {
        return Entry(
            Config(
                name = defaultName(isNight),
                isNightMode = isNight,
                layoutMode = "floating",
                sidebarGravity = "start",
                effectMode = "glass",
                opacity = 76,
                updatedAt = 0L
            ),
            DEFAULT_DIR_NAME
        )
    }

    private fun loadLocal(isNight: Boolean): List<Entry> {
        return typeDir(isNight).listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readEntry(it) }
            .orEmpty()
    }

    private fun readEntry(dir: File): Entry? {
        val config = readConfig(dir) ?: return null
        return Entry(config, dir.name, localDir = dir)
    }

    private fun readConfig(dir: File): Config? {
        val file = File(dir, packageFileName)
        if (!file.exists()) return null
        return GSON.fromJsonObject<Config>(file.readText()).getOrNull()?.let(::normalizeConfig)
    }

    private fun importZipInternal(zipFile: File): Entry {
        val unzipDir = tempDir.getFile("import_${System.currentTimeMillis()}").apply {
            if (exists()) FileUtils.delete(this, deleteRootDir = true)
            mkdirs()
        }
        return try {
            ZipUtils.unZipToPath(zipFile, unzipDir)
            val packageFile = unzipDir.walkTopDown().firstOrNull { it.isFile && it.name == packageFileName }
                ?: throw IllegalArgumentException(appCtx.getString(R.string.navigation_bar_config_missing))
            val config = normalizeConfig(GSON.fromJsonObject<Config>(packageFile.readText()).getOrThrow())
            if (config.name.normalizeFileName() == DEFAULT_DIR_NAME) {
                config.name = "${config.name}_${appCtx.getString(R.string.navigation_bar_import_suffix)}"
            }
            config.opacity = config.opacity.coerceIn(0, 100)
            config.updatedAt = System.currentTimeMillis()
            val dirName = config.name.normalizeFileName().ifBlank { "navigation_${System.currentTimeMillis()}" }
            val targetDir = localDir(config.isNightMode, dirName)
            if (targetDir.exists()) {
                FileUtils.delete(targetDir, deleteRootDir = true)
            }
            targetDir.mkdirs()
            packageFile.parentFile?.copyRecursively(targetDir, overwrite = true)
            File(targetDir, packageFileName).writeText(GSON.toJson(config))
            Entry(config, dirName, localDir = targetDir)
        } finally {
            FileUtils.delete(unzipDir, deleteRootDir = true)
        }
    }

    private fun createMenuDrawable(context: Context, entry: Entry, item: NavItem): Drawable {
        val defaultColor = defaultIconColor(context)
        val selectedColor = ThemeStore.accentColor(context)
        val normal = loadDrawable(context, iconPath(entry, item.key, STATE_NORMAL))
            ?: defaultDrawable(context, item.defaultIconRes, defaultColor)
        val selected = loadDrawable(context, iconPath(entry, item.key, STATE_NORMAL))
            ?: defaultDrawable(context, item.defaultIconRes, selectedColor)
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), selected)
            addState(intArrayOf(android.R.attr.state_selected), selected)
            addState(intArrayOf(), normal)
        }
    }

    private fun iconPath(entry: Entry, itemKey: String, state: String): String? {
        if (entry.dirName == DEFAULT_DIR_NAME) return null
        val value = entry.config.icons[iconKey(itemKey, state)] ?: return null
        val file = File(value)
        return if (file.isAbsolute) value else File(entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName), value).absolutePath
    }

    private fun resolveSidebarBackgroundPath(entry: Entry): String? {
        if (entry.dirName == DEFAULT_DIR_NAME) return null
        val value = entry.config.sidebarBackgroundPath ?: return null
        val file = File(value)
        return if (file.isAbsolute) value else File(
            entry.localDir ?: localDir(entry.config.isNightMode, entry.dirName),
            value
        ).absolutePath
    }

    private fun iconKey(itemKey: String, state: String): String = "${itemKey}_$state"

    private fun defaultDrawable(context: Context, @DrawableRes resId: Int, color: Int): Drawable {
        val drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
        DrawableCompat.setTint(drawable, color)
        return drawable
    }

    private fun defaultIconColor(context: Context): Int {
        val bgColor = context.bottomBackground
        val textIsDark = ColorUtils.isColorLight(bgColor)
        return context.getSecondaryTextColor(textIsDark)
    }

    private fun loadDrawable(context: Context, path: String?): Drawable? {
        if (path.isNullOrBlank()) return null
        val bitmap = BitmapFactory.decodeFile(path) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun decodeIconBitmap(context: Context, uri: Uri, targetSize: Int): Bitmap? {
        val name = uri.lastPathSegment.orEmpty().lowercase(Locale.ROOT)
        return if (name.endsWith(".svg")) {
            context.contentResolver.openInputStream(uri)?.use {
                SvgUtils.createBitmap(it, targetSize, targetSize)
            }
        } else if (name.endsWith(".ico")) {
            context.contentResolver.openInputStream(uri)?.use {
                decodeIcoPng(it.readBytes())
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        }
    }

    private fun decodeIcoPng(bytes: ByteArray): Bitmap? {
        if (bytes.size < 22) return null
        val type = leShort(bytes, 2)
        val count = leShort(bytes, 4)
        if (type != 1 || count <= 0) return null
        var bestOffset = 0
        var bestSize = 0
        var bestPixels = -1
        repeat(count) { index ->
            val entry = 6 + index * 16
            if (entry + 16 > bytes.size) return@repeat
            val width = bytes[entry].toInt().and(0xff).let { if (it == 0) 256 else it }
            val height = bytes[entry + 1].toInt().and(0xff).let { if (it == 0) 256 else it }
            val size = leInt(bytes, entry + 8)
            val offset = leInt(bytes, entry + 12)
            if (size <= 0 || offset <= 0 || offset + size > bytes.size) return@repeat
            val pixels = width * height
            if (pixels > bestPixels) {
                bestPixels = pixels
                bestOffset = offset
                bestSize = size
            }
        }
        if (bestSize <= 0) return null
        return BitmapFactory.decodeByteArray(bytes, bestOffset, bestSize)
    }

    private fun leShort(bytes: ByteArray, offset: Int): Int {
        return bytes[offset].toInt().and(0xff) or
            (bytes[offset + 1].toInt().and(0xff) shl 8)
    }

    private fun leInt(bytes: ByteArray, offset: Int): Int {
        return bytes[offset].toInt().and(0xff) or
            (bytes[offset + 1].toInt().and(0xff) shl 8) or
            (bytes[offset + 2].toInt().and(0xff) shl 16) or
            (bytes[offset + 3].toInt().and(0xff) shl 24)
    }

    private fun drawCenteredIcon(source: Bitmap, targetSize: Int): Bitmap {
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.TRANSPARENT)
        val scale = min(targetSize.toFloat() / source.width, targetSize.toFloat() / source.height)
        val width = source.width * scale
        val height = source.height * scale
        val left = (targetSize - width) / 2f
        val top = (targetSize - height) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), paint)
        return output
    }

    private fun localDir(isNight: Boolean, dirName: String): File = typeDir(isNight).getFile(dirName)

    private fun typeDir(isNight: Boolean): File {
        return rootDir.getFile(if (isNight) MODE_NIGHT else MODE_DAY).apply { mkdirs() }
    }

    private fun defaultName(isNight: Boolean): String {
        return appCtx.getString(if (isNight) R.string.navigation_bar_night_default_name else R.string.navigation_bar_day_default_name)
    }

    private fun normalizeConfig(config: Config): Config {
        val layoutMode = runCatching { config.layoutMode }.getOrNull()
            ?.takeIf { it in setOf("floating", "sidebar") }
            ?: "floating"
        val sidebarGravity = runCatching { config.sidebarGravity }.getOrNull()
            ?.takeIf { it in setOf("start", "end") }
            ?: "start"
        val effectMode = runCatching { config.effectMode }.getOrNull()
            ?.takeIf { it in setOf("solid", "glass", "frosted") }
            ?: "glass"
        val icons = runCatching { config.icons }.getOrNull() ?: linkedMapOf()
        val sidebarBackgroundPath = runCatching { config.sidebarBackgroundPath }.getOrNull()
        config.layoutMode = layoutMode
        config.sidebarGravity = sidebarGravity
        config.effectMode = effectMode
        config.opacity = config.opacity.coerceIn(0, 100)
        config.sidebarBackgroundPath = sidebarBackgroundPath
        config.icons = icons.toMutableMap()
        return config
    }

    private fun resetActiveIfNeeded(entry: Entry) {
        if (activeDirName(entry.config.isNightMode) == entry.dirName) {
            appCtx.putPrefString(if (entry.config.isNightMode) activeNightKey else activeDayKey, DEFAULT_DIR_NAME)
        }
    }

    private fun legacyMigratedKey(isNight: Boolean): String {
        return if (isNight) legacyMigratedNightKey else legacyMigratedDayKey
    }

    private fun migrateLegacyIconsIfNeeded(isNight: Boolean) {
        val migratedKey = legacyMigratedKey(isNight)
        if (appCtx.getPrefBoolean(migratedKey, false)) return
        if (!legacyRootDir.exists()) {
            appCtx.putPrefBoolean(migratedKey, true)
            return
        }
        val mode = if (isNight) MODE_NIGHT else MODE_DAY
        val hasLegacy = items.any { item ->
            File(legacyRootDir, "${mode}_${item.key}_$STATE_NORMAL.png").exists() ||
                File(legacyRootDir, "${mode}_${item.key}_$STATE_SELECTED.png").exists()
        }
        if (!hasLegacy) {
            appCtx.putPrefBoolean(migratedKey, true)
            return
        }
        val customName = appCtx.getString(R.string.navigation_bar_custom_name)
        if (readEntry(localDir(isNight, customName.normalizeFileName())) != null) {
            appCtx.putPrefBoolean(migratedKey, true)
            return
        }
        val dir = localDir(isNight, customName.normalizeFileName()).apply { mkdirs() }
        val icons = linkedMapOf<String, String>()
        items.forEach { item ->
            arrayOf(STATE_NORMAL, STATE_SELECTED).forEach { state ->
                val source = File(legacyRootDir, "${mode}_${item.key}_$state.png")
                if (source.exists()) {
                    val name = "${item.key}_$state.png"
                    source.copyTo(File(dir, name), overwrite = true)
                    icons[iconKey(item.key, state)] = name
                }
            }
        }
        addOrUpdate(
            Config(
                name = customName,
                isNightMode = isNight,
                effectMode = AppConfig.bottomBarEffectMode,
                opacity = if (AppConfig.bottomBarEffectMode == "frosted") AppConfig.frostedGlassLevel else AppConfig.liquidGlassLevel,
                icons = icons
            )
        )
        appCtx.putPrefBoolean(migratedKey, true)
    }
}
