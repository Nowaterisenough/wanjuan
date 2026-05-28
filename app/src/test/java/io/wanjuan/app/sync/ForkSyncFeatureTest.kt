package io.wanjuan.app.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ForkSyncFeatureTest {

    @Test
    fun pageTurnPanelExposesAnimationSpeedConfig() {
        val preferKey = repoFile("app/src/main/java/io/wanjuan/app/constant/PreferKey.kt").readText()
        val appConfig = repoFile("app/src/main/java/io/wanjuan/app/help/config/AppConfig.kt").readText()
        val readView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/ReadView.kt").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val layout = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()

        assertTrue(preferKey.contains("pageAnimationSpeed"))
        assertTrue(appConfig.contains("var pageAnimationSpeed: Int"))
        assertTrue(readView.contains("get() = AppConfig.pageAnimationSpeed"))
        assertTrue(layout.contains("panel_page_anim_speed"))
        assertTrue(readMenu.contains("panelPageAnimSpeed.setOnClickListener"))
    }

    @Test
    fun searchResultsKeepOriginBookUrlsForSourceSelection() {
        val searchBook = repoFile("app/src/main/java/io/wanjuan/app/data/entities/SearchBook.kt").readText()
        val searchBookDao = repoFile("app/src/main/java/io/wanjuan/app/data/dao/SearchBookDao.kt").readText()
        val searchModel = repoFile("app/src/main/java/io/wanjuan/app/model/webBook/SearchModel.kt").readText()
        val searchAdapter = repoFile("app/src/main/java/io/wanjuan/app/ui/book/search/SearchAdapter.kt").readText()
        val searchActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/search/SearchActivity.kt").readText()

        assertTrue(searchBook.contains("originBookUrls()"))
        assertTrue(searchBookDao.contains("getByBookUrls(bookUrls: List<String>)"))
        assertTrue(searchModel.contains("addOrigin(nBook.origin, nBook.bookUrl)"))
        assertTrue(searchAdapter.contains("showBookSourceSelector"))
        assertTrue(searchActivity.contains("override fun showBookSourceSelector(book: SearchBook)"))
    }

    @Test
    fun restoreUsesJournalAndSanitizesLegacyBookConfig() {
        val restore = repoFile("app/src/main/java/io/wanjuan/app/help/storage/Restore.kt").readText()
        val journal = repoFile("app/src/main/java/io/wanjuan/app/help/storage/RestoreJournal.kt")

        assertTrue(journal.exists())
        assertTrue(restore.contains("RestoreJournal.begin"))
        assertTrue(restore.contains("sanitizeReadConfigJson"))
        assertTrue(restore.contains("normalizeStringPrefs"))
    }

    @Test
    fun epubLayoutHandlesIntrinsicWidthsAndLineBreaks() {
        val engine = repoFile("app/src/main/java/io/wanjuan/app/model/localBook/EpubLayoutEngine.kt").readText()
        val readBook = repoFile("app/src/main/java/io/wanjuan/app/model/ReadBook.kt").readText()

        assertTrue(engine.contains("intrinsicContentWidth"))
        assertTrue(engine.contains("if (char == '\\n')"))
        assertTrue(readBook.contains("chapterLayoutKeys"))
    }

    @Test
    fun remainingUiSyncKeepsTouchAndSourceSelectionImprovements() {
        val bookInfo = repoFile("app/src/main/java/io/wanjuan/app/ui/book/info/BookInfoActivity.kt").readText()
        val sourceSelect = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/SourceSelectDialog.kt").readText()
        val noIntercept = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/NoParentInterceptNestedScrollView.kt")
        val bookInfoLayout = repoFile("app/src/main/res/layout/activity_book_info.xml").readText()

        assertTrue(bookInfo.contains("keepDetailTouchInside"))
        assertTrue(sourceSelect.contains("searchHint: String?"))
        assertTrue(sourceSelect.contains("setOnCloseListener"))
        assertTrue(noIntercept.exists())
        assertTrue(bookInfoLayout.contains("NoParentInterceptNestedScrollView"))
    }

    @Test
    fun remainingOptionalSyncIncludesMediaMangaWidgetsAndEpubResources() {
        val imageProvider = repoFile("app/src/main/java/io/wanjuan/app/model/ImageProvider.kt").readText()
        val epubFile = repoFile("app/src/main/java/io/wanjuan/app/model/localBook/EpubFile.kt").readText()
        val textPage = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/entities/TextPage.kt").readText()
        val readBook = repoFile("app/src/main/java/io/wanjuan/app/model/ReadBook.kt").readText()
        val videoActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/video/VideoPlayerActivity.kt").readText()
        val videoLayout = repoFile("app/src/main/res/layout/activity_video_player.xml").readText()
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val mangaDialog = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/config/MangaEpaperDialog.kt").readText()
        val rowUi = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/RowUiViewFactory.kt").readText()
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(imageProvider.contains("cacheImageAsync"))
        assertTrue(epubFile.contains("NATIVE_LAYOUT_DISK_CACHE_VERSION = 4"))
        assertTrue(textPage.contains("invalidateEpubResource"))
        assertTrue(readBook.contains("invalidateEpubResource"))
        assertTrue(videoActivity.contains("COLLAPSED_PANEL_HEIGHT_DP"))
        assertTrue(videoLayout.contains("bottom_panel_container"))
        assertTrue(mangaActivity.contains("setAutoPageEnabled"))
        assertTrue(mangaDialog.contains("enableOnConfirm"))
        assertTrue(rowUi.contains("getDropDownView"))
        assertTrue(manifest.contains("ReadGoalWidgetProvider"))
        assertTrue(repoFile("app/src/main/java/io/wanjuan/app/receiver/ReadGoalWidgetProvider.kt").exists())
        assertTrue(repoFile("app/src/main/java/io/wanjuan/app/receiver/ReadRankWidgetProvider.kt").exists())
        assertTrue(repoFile("app/src/main/res/layout/widget_read_goal.xml").exists())
        assertTrue(repoFile("app/src/main/res/layout/widget_read_rank.xml").exists())
    }

    @Test
    fun aboutPageChecksUpdatesAgainstMaintainerGithubReleases() {
        val aboutXml = repoFile("app/src/main/res/xml/about.xml").readText()
        val aboutFragment = repoFile("app/src/main/java/io/wanjuan/app/ui/about/AboutFragment.kt").readText()
        val appUpdateGitHub = repoFile("app/src/main/java/io/wanjuan/app/help/update/AppUpdateGitHub.kt").readText()
        val buildGradle = repoFile("app/build.gradle").readText()
        val strings = repoFile("app/src/main/res/values/strings.xml").readText()
        val nonTranslat = repoFile("app/src/main/res/values/non_translat.xml").readText()

        assertTrue(aboutXml.contains("android:key=\"check_update\""))
        assertTrue(aboutFragment.contains("\"update_log\" -> showUpdateLog()"))
        assertTrue(aboutFragment.contains("\"check_update\" -> checkUpdate()"))
        assertTrue(aboutFragment.contains("AppUpdateGitHub"))
        assertTrue(appUpdateGitHub.contains("getChangeLog"))
        assertTrue(appUpdateGitHub.contains("BuildConfig.GITHUB_REPO"))
        assertTrue(buildGradle.contains("'Nowaterisenough/wanjuan'"))
        assertTrue(strings.contains("https://github.com/Nowaterisenough/wanjuan/releases"))
        assertTrue(nonTranslat.contains("https://github.com/Nowaterisenough/wanjuan/graphs/contributors"))
    }

    @Test
    fun refgdRecommendedSyncKeepsSmallBehaviorFixes() {
        val searchModel = repoFile("app/src/main/java/io/wanjuan/app/model/webBook/SearchModel.kt").readText()
        val cacheManifest = repoFile("app/src/main/java/io/wanjuan/app/help/book/CacheManifestHelper.kt").readText()
        val cacheBook = repoFile("app/src/main/java/io/wanjuan/app/model/CacheBook.kt").readText()
        val bookInfoActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/info/BookInfoActivity.kt").readText()
        val bookInfoViewModel = repoFile("app/src/main/java/io/wanjuan/app/ui/book/info/BookInfoViewModel.kt").readText()
        val contextExtensions = repoFile("app/src/main/java/io/wanjuan/app/utils/ContextExtensions.kt").readText()
        val fragmentExtensions = repoFile("app/src/main/java/io/wanjuan/app/utils/FragmentExtensions.kt").readText()
        val baseSource = repoFile("app/src/main/java/io/wanjuan/app/data/entities/BaseSource.kt").readText()
        val loginJsExtensions = repoFile("app/src/main/java/io/wanjuan/app/ui/login/SourceLoginJsExtensions.kt").readText()

        assertTrue(searchModel.contains("val matchKey = searchKey"))
        assertTrue(searchModel.contains("""substringBeforeLast("@")"""))
        assertTrue(searchModel.contains("mergeItems(items, precision, matchKey)"))
        assertTrue(searchModel.contains("name.contains(matchKey)"))

        assertTrue(cacheManifest.contains("fun refresh("))
        assertTrue(cacheManifest.contains("ExoPlayerHelper.isMediaCached"))
        assertTrue(cacheBook.contains("CacheManifestHelper.refresh(book)"))

        assertTrue(bookInfoActivity.contains("relinkLocalBookAfterFolderSelect"))
        assertTrue(bookInfoActivity.contains("selectLocalBookDir"))
        assertTrue(bookInfoViewModel.contains("FileNotFoundException"))
        assertTrue(bookInfoViewModel.contains("""actionLive.postValue("selectLocalBookDir")"""))
        assertTrue(contextExtensions.contains("ClassCastException"))
        assertTrue(contextExtensions.contains("toPrefStringSet"))
        assertTrue(fragmentExtensions.contains("requireContext().getPrefStringSet"))

        assertTrue(baseSource.contains("if (json.isBlank())"))
        assertTrue(loginJsExtensions.contains("activity.runOnUiThread"))
        assertTrue(loginJsExtensions.contains("activity.isFinishing || activity.isDestroyed"))
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
