package io.wanjuan.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ReadMenuLayoutTest {

    @Test
    fun readMenuViewBindingIdCountStaysBelowJavaParameterLimit() {
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val bindingIdCount = Regex("android:id=\"@\\+id/").findAll(layoutXml).count()

        assertTrue(
            "view_read_menu.xml has $bindingIdCount binding IDs; keep it below the generated Java constructor limit",
            bindingIdCount <= 245
        )
    }

    @Test
    fun debugBuildUsesWanjuanBranding() {
        val zhDebugStrings = repoFile("app/src/debug/res/values-zh/strings.xml").readText()
        val defaultDebugStrings = repoFile("app/src/debug/res/values/strings.xml").readText()

        assertTrue(zhDebugStrings.contains("<string name=\"app_name\">涓囧嵎路D</string>"))
        assertTrue(zhDebugStrings.contains("<string name=\"receiving_shared_label\">涓囧嵎路D路鎼滅储</string>"))
        assertTrue(defaultDebugStrings.contains("<string name=\"app_name\">Wanjuan路D</string>"))
        assertTrue(defaultDebugStrings.contains("<string name=\"receiving_shared_label\">Wanjuan路D路search</string>"))
    }

    @Test
    fun mainBottomNavigationMatchesReaderIslandStyleAndKeepsSearchSeparate() {
        val mainLayout = parseXml(repoFile("app/src/main/res/layout/activity_main.xml"))
        val dimens = repoFile("app/src/main/res/values/dimens.xml").readText()
        val readMenu = parseXml(repoFile("app/src/main/res/layout/view_read_menu.xml"))
        val mainActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/main/MainActivity.kt").readText()
        val bottomNavigationGlass = mainLayout.elementById("bottom_navigation_glass")
        val searchButtonContainer = mainLayout.elementById("search_button_container")
        val bottomNavigation = mainLayout.elementById("bottom_navigation_view")
        val indicatorContainer = mainLayout.elementById("bottom_navigation_indicator_container")
        val readerNavigation = readMenu.elementById("read_bottom_primary_nav")
        val readerIndicator = readMenu.elementById("bottom_tab_indicator_container")
        val updateIndicator = mainActivity.substringAfter("private fun updateBottomNavigationIndicator(")
            .substringBefore("private fun findBottomNavigationItemView")

        assertEquals("@id/search_button_container", bottomNavigationGlass.appAttr("layout_constraintEnd_toStartOf"))
        assertEquals("@dimen/main_bottom_bar_gap", bottomNavigationGlass.androidAttr("layout_marginEnd"))
        assertEquals("@dimen/main_search_button_size", searchButtonContainer.androidAttr("layout_width"))
        assertEquals("@dimen/main_search_button_size", searchButtonContainer.androidAttr("layout_height"))
        assertFalse(searchButtonContainer.hasAncestor(bottomNavigationGlass))
        assertFalse(bottomNavigationGlass.hasAncestor(searchButtonContainer))

        assertEquals("@dimen/main_bottom_bar_height", bottomNavigationGlass.androidAttr("layout_height"))
        assertEquals("@dimen/main_bottom_bar_height", bottomNavigation.androidAttr("minHeight"))
        assertTrue(dimens.contains("<dimen name=\"main_bottom_bar_height\">56dp</dimen>"))
        assertTrue(dimens.contains("<dimen name=\"main_search_button_size\">56dp</dimen>"))
        assertTrue(dimens.contains("<dimen name=\"main_bottom_bar_corner_radius\">28dp</dimen>"))
        assertTrue(dimens.contains("<dimen name=\"main_search_button_corner_radius\">28dp</dimen>"))
        assertEquals(readerNavigation.appAttr("itemIconSize"), bottomNavigation.appAttr("itemIconSize"))
        assertEquals(readerNavigation.androidAttr("paddingStart"), bottomNavigation.androidAttr("paddingStart"))
        assertEquals(readerNavigation.androidAttr("paddingEnd"), bottomNavigation.androidAttr("paddingEnd"))
        assertEquals("selected", bottomNavigation.appAttr("labelVisibilityMode"))

        assertEquals("@dimen/main_bottom_indicator_width", indicatorContainer.androidAttr("layout_width"))
        assertEquals("@dimen/main_bottom_indicator_height", indicatorContainer.androidAttr("layout_height"))
        assertEquals("68dp", readerIndicator.androidAttr("layout_width"))
        assertTrue(dimens.contains("<dimen name=\"main_bottom_indicator_width\">68dp</dimen>"))
        assertEquals(readerIndicator.androidAttr("layout_height"), "48dp")
        assertTrue(dimens.contains("<dimen name=\"main_bottom_indicator_height\">48dp</dimen>"))
        assertEquals(readerIndicator.androidAttr("background"), indicatorContainer.androidAttr("background"))
        assertEquals("invisible", indicatorContainer.androidAttr("visibility"))
        assertTrue(updateIndicator.contains("bottomNavigationIndicatorContainer.isVisible = true"))
        assertTrue(updateIndicator.contains("val maxWidth = 68.dpToPx()"))
        assertTrue(updateIndicator.contains("val horizontalInset = 4.dpToPx()"))
        assertTrue(updateIndicator.contains("val minWidth = 48.dpToPx()"))
        assertFalse(updateIndicator.contains("bottomNavigationIndicatorContainer.isVisible = false"))
        assertTrue(mainActivity.contains("bottomNavigationIndicatorContainer.background = createBottomNavigationIndicatorBackground()"))
        assertTrue(mainActivity.contains("cornerRadius = resources.getDimension(R.dimen.main_bottom_indicator_corner_radius)"))
        assertTrue(mainActivity.contains("setColor(primaryColor)"))
        assertTrue(mainActivity.contains("bottomNavigationView.restoreThemeIconTint()"))
        assertFalse(mainActivity.contains("bottomNavigationView.itemIconTintList = ColorStateList.valueOf(Color.WHITE)"))
        assertFalse(mainActivity.contains("bottomNavigationView.itemTextColor = ColorStateList.valueOf(Color.WHITE)"))
        assertFalse(mainActivity.contains("bottomNavigationIndicatorGlassView"))
    }

    @Test
    fun mainBottomNavigationSelectedLabelUsesWhiteOnThemeIndicator() {
        val bottomNavigationView = repoFile(
            "app/src/main/java/io/wanjuan/app/lib/theme/view/ThemeBottomNavigationVIew.kt"
        ).readText()
        val colorStateList = bottomNavigationView.substringAfter("fun createThemeColorStateList(): ColorStateList")
            .substringBefore("fun restoreThemeIconTint()")

        assertTrue(colorStateList.contains("val textColor = if (AppConfig.isNightTheme) Color.WHITE else Color.BLACK"))
        assertTrue(colorStateList.contains(".setDefaultColor(textColor)"))
        assertTrue(colorStateList.contains(".setSelectedColor(Color.WHITE)"))
        assertFalse(colorStateList.contains("ThemeStore.accentColor(context)"))
        assertFalse(colorStateList.contains("getSecondaryTextColor"))
        assertFalse(colorStateList.contains("ColorUtils.isColorLight"))
    }

    @Test
    fun mainBottomNavigationSelectionKeepsOutlineIconAndUsesReaderLabelAnimation() {
        val mainLayout = parseXml(repoFile("app/src/main/res/layout/activity_main.xml"))
        val mainActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/main/MainActivity.kt").readText()
        val navConfig = repoFile("app/src/main/java/io/wanjuan/app/help/config/NavigationBarIconConfig.kt").readText()
        val bottomNavigation = mainLayout.elementById("bottom_navigation_view")
        val pageSelected = mainActivity.substringAfter("override fun onPageSelected(position: Int)")
            .substringBefore("\n        }\n\n    }")
        val animationBlock = mainActivity.substringAfter("private fun animateBottomNavigationSelectedItem(")
            .substringBefore("private fun resetBottomNavigationItemAnimations")
        val menuDrawableBlock = navConfig.substringAfter("private fun createMenuDrawable(")
            .substringBefore("private fun iconPath")

        assertEquals("selected", bottomNavigation.appAttr("labelVisibilityMode"))
        assertTrue(mainActivity.contains("private fun setBottomNavigationSelection(itemId: Int, animate: Boolean)"))
        assertTrue(mainActivity.contains("val wasSelectedItemId = checkedBottomNavigationItemId(nav)"))
        assertTrue(mainActivity.contains("animateBottomNavigationSelectedItem(nav, itemId, animate && wasSelectedItemId != itemId)"))
        assertTrue(mainActivity.contains("private fun checkedBottomNavigationItemId(nav: BottomNavigationView): Int?"))
        assertTrue(mainActivity.contains("private fun resetBottomNavigationItemAnimations(nav: BottomNavigationView)"))
        assertTrue(pageSelected.contains("setBottomNavigationSelection(getBottomNavigationItemId(position), animate = true)"))
        assertTrue(animationBlock.contains("doOnPreDraw"))
        assertTrue(animationBlock.contains("com.google.android.material.R.id.navigation_bar_item_content_container"))
        assertTrue(animationBlock.contains("com.google.android.material.R.id.navigation_bar_item_labels_group"))
        assertTrue(animationBlock.contains("contentContainer.translationY = startOffset"))
        assertTrue(animationBlock.contains(".translationY(0f)"))
        assertTrue(animationBlock.contains("labelsGroup?.animate()"))
        assertFalse(menuDrawableBlock.contains("STATE_SELECTED"))
    }

    @Test
    fun mainBottomNavigationMyTabUsesGearIcon() {
        val mainMenu = parseXml(repoFile("app/src/main/res/menu/main_bnv.xml"))
        val mainLayout = parseXml(repoFile("app/src/main/res/layout/activity_main.xml"))
        val myFragmentLayout = parseXml(repoFile("app/src/main/res/layout/fragment_my_config.xml"))
        val navConfig = repoFile("app/src/main/java/io/wanjuan/app/help/config/NavigationBarIconConfig.kt").readText()
        val myItem = mainMenu.elementById("menu_my_config")
        val sideNavMyConfig = mainLayout.elementById("side_nav_my_config")
        val sideNavMyConfigText = mainLayout.elementById("side_nav_my_config_text")
        val myTitleBar = myFragmentLayout.elementById("title_bar")

        assertEquals("@drawable/ic_lucide_settings", myItem.androidAttr("icon"))
        assertEquals("@string/setting", myItem.androidAttr("title"))
        assertEquals("@string/setting", sideNavMyConfig.androidAttr("contentDescription"))
        assertEquals("@string/setting", sideNavMyConfigText.androidAttr("text"))
        assertEquals("@string/setting", myTitleBar.appAttr("title"))
        assertTrue(navConfig.contains(
            "NavItem(\"my\", R.string.setting, R.id.menu_my_config, R.drawable.ic_lucide_settings)"
        ))
    }

    @Test
    fun readRecordOpensFromBookshelfShortcutWithActivityBackInsteadOfBottomTab() {
        val mainMenu = parseXml(repoFile("app/src/main/res/menu/main_bnv.xml"))
        val mainActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/main/MainActivity.kt").readText()
        val readRecordLayout = parseXml(repoFile("app/src/main/res/layout/activity_read_record.xml"))
        val readRecordActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/about/ReadRecordActivity.kt").readText()
        val bottomItemIds = mainMenu.childElements("item").map { it.androidAttr("id") }

        assertFalse(bottomItemIds.contains("@+id/menu_read_record"))
        assertFalse(mainActivity.contains("val showReadRecord = AppConfig.showReadRecord"))
        assertFalse(mainActivity.contains("realPositions[index] = idReadRecord"))
        assertTrue(mainActivity.contains("fun openReadRecordPage() {\n        startActivity(Intent(this, ReadRecordActivity::class.java))\n    }"))
        assertEquals("@string/read_record", readRecordLayout.elementById("title_bar").appAttr("title"))
        assertTrue(readRecordActivity.contains("binding.titleBar.setNavigationOnClickListener"))
        assertTrue(readRecordActivity.contains("finish()"))
    }

    @Test
    fun readRecordActivityTintsTitleBarActionsFromCurrentThemeTextColor() {
        val readRecordActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/about/ReadRecordActivity.kt").readText()

        assertTrue(readRecordActivity.contains("private fun applyTitleBarColor()"))
        assertTrue(readRecordActivity.contains("binding.titleBar.setColorFilter(primaryTextColor)"))
        assertTrue(readRecordActivity.contains("binding.titleBar.setTextColor(primaryTextColor)"))
        assertTrue(readRecordActivity.contains("applyTitleBarColor()"))
    }

    @Test
    fun bookInfoTopBarOpensBookUrlInAppWebViewAndUsesThemeTint() {
        val bookInfoMenu = parseXml(repoFile("app/src/main/res/menu/book_info.xml"))
        val bookInfoActivity =
            repoFile("app/src/main/java/io/wanjuan/app/ui/book/info/BookInfoActivity.kt").readText()
        val openBookUrl = bookInfoMenu.elementById("menu_open_book_url")
        val openCurrentBookUrlBlock = bookInfoActivity
            .substringAfter("private fun openCurrentBookUrl()")
            .substringBefore("override fun observeLiveBus()")

        assertEquals("@drawable/ic_lucide_link_2", openBookUrl.androidAttr("icon"))
        assertEquals("@string/open_in_app_webview", openBookUrl.androidAttr("title"))
        assertEquals("always", openBookUrl.appAttr("showAsAction"))
        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_link_2.xml").isFile)

        assertTrue(bookInfoActivity.contains("toolBarTheme = Theme.Auto"))
        assertFalse(bookInfoActivity.contains("toolBarTheme = Theme.Dark"))
        assertTrue(bookInfoActivity.contains("private fun applyTitleBarColor()"))
        assertTrue(bookInfoActivity.contains("binding.titleBar.setColorFilter(primaryTextColor)"))
        assertTrue(bookInfoActivity.contains("binding.titleBar.setTextColor(primaryTextColor)"))
        assertTrue(bookInfoActivity.contains("applyTitleBarColor()"))

        assertTrue(bookInfoActivity.contains("R.id.menu_open_book_url -> openCurrentBookUrl()"))
        assertTrue(openCurrentBookUrlBlock.contains("val book = viewModel.getBook() ?: return"))
        assertTrue(openCurrentBookUrlBlock.contains("val url = book.bookUrl.takeIf { it.isNotBlank() } ?: return"))
        assertTrue(openCurrentBookUrlBlock.contains("startActivity<WebViewActivity>"))
        assertTrue(openCurrentBookUrlBlock.contains("putExtra(\"url\", url)"))
        assertTrue(openCurrentBookUrlBlock.contains("putExtra(\"sourceOrigin\", source?.bookSourceUrl)"))
        assertTrue(openCurrentBookUrlBlock.contains("putExtra(\"sourceName\", source?.bookSourceName)"))
        assertTrue(openCurrentBookUrlBlock.contains("putExtra(\"sourceType\", source?.getSourceType())"))
        assertTrue(openCurrentBookUrlBlock.contains("putExtra(\"sourceVerificationEnable\", source != null)"))
        assertTrue(openCurrentBookUrlBlock.contains("putExtra(\"refetchAfterSuccess\", false)"))
        assertFalse(openCurrentBookUrlBlock.contains(".let(::openUrl)"))
    }

    @Test
    fun textChapterPagesUseSnapshotForMinimapReads() {
        val textChapter =
            repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/entities/TextChapter.kt")
                .readText()
        val textChapterLayout =
            repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/provider/TextChapterLayout.kt")
                .readText()
        val getContentBlock = textChapter.substringAfter("fun getContent(): String")
            .substringBefore("fun getUnRead")
        val onPageCompletedBlock = textChapterLayout.substringAfter("private fun onPageCompleted()")
            .substringBefore("private fun onCompleted()")

        assertTrue(textChapter.contains("private val textPagesLock = Any()"))
        assertTrue(textChapter.contains("val pages: List<TextPage> get() = pageSnapshot()"))
        assertTrue(textChapter.contains("private fun pageSnapshot(): List<TextPage> = synchronized(textPagesLock)"))
        assertTrue(textChapter.contains("fun appendPage(page: TextPage): Int = synchronized(textPagesLock)"))
        assertTrue(textChapter.contains("fun nextPageIndex(): Int = synchronized(textPagesLock)"))
        assertTrue(textChapter.contains("fun lastPageForLayout(): TextPage? = synchronized(textPagesLock)"))
        assertTrue(getContentBlock.contains("pageSnapshot().forEach"))
        assertTrue(onPageCompletedBlock.contains("textPage.index = textChapter.nextPageIndex()"))
        assertTrue(onPageCompletedBlock.contains("?: textChapter.lastPageForLayout()?.let"))
        assertTrue(onPageCompletedBlock.contains("val pageIndex = textChapter.appendPage(textPage)"))
        assertTrue(onPageCompletedBlock.contains("listener?.onLayoutPageCompleted(pageIndex, textPage)"))
        assertFalse(onPageCompletedBlock.contains("textPages.add(textPage)"))
    }

    @Test
    fun discoverTopActionLayoutToggleUsesLucideIconsBetweenSearchAndMenu() {
        val layout = parseXml(repoFile("app/src/main/res/layout/fragment_explore.xml"))
        val toggle = layout.elementById("btn_discover_layout_toggle")
        val settings = layout.elementById("btn_discover_tag_filter")

        assertTrue(layout.elementById("btn_discover_source_search").isBefore(toggle))
        assertTrue(toggle.isBefore(settings))
        assertTrue(toggle.isBefore(layout.elementById("btn_discover_more")))
        assertEquals("@string/switchLayout", toggle.androidAttr("contentDescription"))
        assertEquals(
            "@drawable/ic_lucide_search",
            layout.elementById("btn_discover_source_search").androidAttr("src")
        )
        assertEquals(
            "@drawable/ic_lucide_chevron_down",
            layout.elementById("iv_discover_source_arrow").androidAttr("src")
        )
        assertEquals("@drawable/ic_lucide_layout_grid", toggle.androidAttr("src"))
        assertEquals("@drawable/ic_lucide_settings", settings.androidAttr("src"))
        assertEquals(
            "@drawable/ic_lucide_user",
            layout.elementById("btn_discover_more").androidAttr("src")
        )
        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_user.xml").exists())
        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_layout_grid.xml").exists())
        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_layout_list.xml").exists())
    }

    @Test
    fun discoverSettingsButtonOnlyShowsWhenSettingsExist() {
        val exploreFragment =
            repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val updateButtonBlock = exploreFragment
            .substringAfter("private fun updateDiscoverTagFilterButtonState()")
            .substringBefore("private fun buildDiscoverSettingItems()")

        assertTrue(updateButtonBlock.contains("val enabled = discoverSettingItems.isNotEmpty()"))
        assertFalse(updateButtonBlock.contains("normalizeDiscoverBookLayout(AppConfig.modernDiscoveryLayout) == DISCOVER_LAYOUT_GRID"))
    }

    @Test
    fun discoverLoginActionFallsBackToInAppWebViewForSourcesWithoutLoginUrl() {
        val exploreFragment =
            repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val openLoginBlock = exploreFragment
            .substringAfter("private fun openSelectedSourceLogin()")
            .substringBefore("private fun updateDiscoverLoginButtonState()")
        val updateButtonBlock = exploreFragment
            .substringAfter("private fun updateDiscoverLoginButtonState()")
            .substringBefore("private fun switchDiscoverBookLayout()")

        assertTrue(openLoginBlock.contains("openSelectedDiscoverPageInWebView(source)"))
        assertFalse(openLoginBlock.contains("toastOnUi(R.string.source_no_login)"))
        assertTrue(updateButtonBlock.contains("setImageResource("))
        assertTrue(updateButtonBlock.contains("R.drawable.ic_lucide_link_2"))
        assertTrue(updateButtonBlock.contains("R.drawable.ic_lucide_user"))
        assertTrue(exploreFragment.contains("startActivity<WebViewActivity>"))
        assertTrue(exploreFragment.contains("putExtra(\"sourceOrigin\", source.bookSourceUrl)"))
        assertTrue(exploreFragment.contains("putExtra(\"sourceType\", SourceType.book)"))
    }

    @Test
    fun readerTopBarsUseLucideIconsAndSharedIconMetrics() {
        val bookReadMenu = parseXml(repoFile("app/src/main/res/menu/book_read.xml"))
        val bookMangaMenu = parseXml(repoFile("app/src/main/res/menu/book_manga.xml"))
        val mainExploreMenu = parseXml(repoFile("app/src/main/res/menu/main_explore.xml"))
        val actionButton = parseXml(repoFile("app/src/main/res/layout/view_action_button.xml"))
        val titleBar = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/TitleBar.kt").readText()
        val toolbarExtensions = repoFile("app/src/main/java/io/wanjuan/app/utils/ToolBarExtensions.kt").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val readActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()

        assertEquals(
            "@drawable/ic_lucide_shuffle",
            bookReadMenu.elementById("menu_change_source").androidAttr("icon")
        )
        assertEquals(
            "@drawable/ic_lucide_refresh_cw",
            bookReadMenu.elementById("menu_refresh").androidAttr("icon")
        )
        assertEquals(
            "@drawable/ic_lucide_download",
            bookReadMenu.elementById("menu_download").androidAttr("icon")
        )
        assertEquals(
            "@drawable/ic_lucide_list",
            bookReadMenu.elementById("menu_toc_regex").androidAttr("icon")
        )
        assertEquals(
            "@drawable/ic_lucide_languages",
            bookReadMenu.elementById("menu_set_charset").androidAttr("icon")
        )
        assertEquals(
            "@drawable/ic_lucide_link_2",
            bookReadMenu.elementById("menu_login").androidAttr("icon")
        )
        assertEquals("@string/login", bookReadMenu.elementById("menu_login").androidAttr("title"))
        assertEquals("always", bookReadMenu.elementById("menu_login").appAttr("showAsAction"))
        assertEquals(
            "@drawable/ic_lucide_link_2",
            bookMangaMenu.elementById("menu_login").androidAttr("icon")
        )
        assertEquals("@string/login", bookMangaMenu.elementById("menu_login").androidAttr("title"))
        assertEquals("always", bookMangaMenu.elementById("menu_login").appAttr("showAsAction"))
        assertEquals(
            "@drawable/ic_lucide_tags",
            mainExploreMenu.elementById("menu_group").androidAttr("icon")
        )
        assertEquals("@dimen/read_top_bar_button_size", actionButton.androidAttr("layout_width"))
        assertEquals("@dimen/read_top_bar_button_size", actionButton.androidAttr("layout_height"))
        assertEquals("@dimen/read_top_bar_icon_padding", actionButton.androidAttr("padding"))
        assertTrue(titleBar.contains("R.drawable.ic_lucide_arrow_left"))
        assertTrue(titleBar.contains("R.drawable.ic_lucide_more_vertical"))
        assertTrue(toolbarExtensions.contains("fun Toolbar.applyTopBarIconMetrics"))
        assertTrue(readMenu.contains("private fun applyTopBarIconColor()"))
        assertTrue(readMenu.contains("binding.titleBar.setColorFilter(Color.WHITE)"))
        assertTrue(readMenu.contains("binding.titleBar.toolbar.applyTopBarIconMetrics(Color.WHITE)"))
        assertTrue(readMenu.contains("fun refreshMenuColorFilter()"))
        assertTrue(readActivity.contains("menu.findItem(R.id.menu_login)?.isVisible =\n            onLine && ReadBook.bookSource != null"))
        assertTrue(readActivity.contains("R.drawable.ic_lucide_user"))
        assertTrue(readActivity.contains("R.string.open_in_app_webview"))
        assertTrue(readActivity.contains("R.id.menu_login -> showLogin()"))
        assertTrue(mangaActivity.contains("menu.findItem(R.id.menu_login)?.isVisible =\n            ReadManga.bookSource != null"))
        assertTrue(mangaActivity.contains("R.drawable.ic_lucide_user"))
        assertTrue(mangaActivity.contains("R.string.open_in_app_webview"))
        assertTrue(mangaActivity.contains("R.id.menu_login -> {\n                showLogin()\n            }"))
        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_languages.xml").exists())
        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_tags.xml").exists())
    }

    @Test
    fun lucideDrawableIntrinsicSizeMatchesReaderBottomIconSize() {
        val drawableDir = repoFile("app/src/main/res/drawable")
        val lucideDrawables = drawableDir.listFiles { file ->
            file.name.startsWith("ic_lucide_") && file.extension == "xml"
        }.orEmpty()

        assertTrue("Expected lucide drawables to exist", lucideDrawables.isNotEmpty())
        lucideDrawables.forEach { file ->
            val vector = parseXml(file)
            assertEquals("${file.name} width", "22dp", vector.androidAttr("width"))
            assertEquals("${file.name} height", "22dp", vector.androidAttr("height"))
        }
    }

    @Test
    fun discoverTopActionLayoutToggleSwitchesBetweenListAndGridOnly() {
        val exploreFragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()

        assertTrue(exploreFragment.contains("binding.btnDiscoverLayoutToggle.setOnClickListener"))
        assertTrue(exploreFragment.contains("private fun switchDiscoverBookLayout()"))
        assertTrue(exploreFragment.contains("const val DISCOVER_LAYOUT_LIST = 0"))
        assertTrue(exploreFragment.contains("const val DISCOVER_LAYOUT_GRID = 1"))
        assertTrue(exploreFragment.contains("R.drawable.ic_lucide_layout_grid"))
        assertTrue(exploreFragment.contains("R.drawable.ic_lucide_layout_list"))
        assertTrue(exploreFragment.contains("DISCOVER_LAYOUT_GRID -> GridLayoutManager(requireContext(), AppConfig.modernDiscoveryGridColumns)"))
        assertFalse(exploreFragment.contains("DISCOVER_LAYOUT_COUNT = 3"))
    }

    @Test
    fun bookshelfAndDiscoverLayoutTogglesOpenColumnSliderOnLongPress() {
        val bookshelfFragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/BookshelfFragment1.kt").readText()
        val exploreFragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val sliderPopup = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/GridColumnsPopup.kt").readText()
        val bookshelfConfig = repoFile("app/src/main/res/layout/dialog_bookshelf_config.xml").readText()
        val defaultStrings = repoFile("app/src/main/res/values/strings.xml").readText()
        val zhStrings = repoFile("app/src/main/res/values-zh/strings.xml").readText()

        assertTrue(bookshelfFragment.contains("binding.btnBookshelfLayoutToggle.setOnLongClickListener"))
        assertTrue(bookshelfFragment.contains("showBookshelfGridColumnsPopup(it)"))
        assertTrue(bookshelfFragment.contains("GridColumnsPopup.show("))
        assertTrue(bookshelfFragment.contains("minColumns = BOOKSHELF_GRID_COLUMNS_MIN"))
        assertTrue(bookshelfFragment.contains("maxColumns = BOOKSHELF_GRID_COLUMNS_MAX"))
        assertTrue(bookshelfFragment.contains("private const val BOOKSHELF_GRID_COLUMNS_MIN = 2"))
        assertTrue(bookshelfFragment.contains("private const val BOOKSHELF_GRID_COLUMNS_MAX = 7"))
        assertTrue(bookshelfFragment.contains("initialSpacing = AppConfig.bookshelfMargin"))
        assertTrue(bookshelfFragment.contains("spacingTitleRes = R.string.margin"))
        assertTrue(bookshelfFragment.contains("onSpacingChanging = ::setBookshelfGridSpacing"))
        assertTrue(bookshelfFragment.contains("onSpacingChanged = ::setBookshelfGridSpacing"))
        assertTrue(bookshelfFragment.contains("fragment.updateBookshelfLayout(gridColumns)"))
        assertTrue(bookshelfFragment.contains("fragment.updateBookshelfSpacing(spacing)"))
        assertFalse(bookshelfFragment.contains("activity?.recreate()"))

        assertTrue(exploreFragment.contains("binding.btnDiscoverLayoutToggle.setOnLongClickListener"))
        assertTrue(exploreFragment.contains("showDiscoverGridColumnsPopup(it)"))
        assertTrue(exploreFragment.contains("GridColumnsPopup.show("))
        assertTrue(exploreFragment.contains("minColumns = DISCOVER_GRID_COLUMNS_MIN"))
        assertTrue(exploreFragment.contains("maxColumns = DISCOVER_GRID_COLUMNS_MAX"))

        assertTrue(sliderPopup.contains("object GridColumnsPopup"))
        assertTrue(sliderPopup.contains("initialSpacing: Int? = null"))
        assertTrue(sliderPopup.contains("onSpacingChanging: ((Int) -> Unit)? = null"))
        assertTrue(sliderPopup.contains("addSlider("))
        assertTrue(sliderPopup.contains("spacingTitleRes"))
        assertTrue(sliderPopup.contains("max = maxColumns - minColumns"))
        assertTrue(sliderPopup.contains("progress = (initialColumns - minColumns).coerceIn(0, max)"))
        assertTrue(sliderPopup.contains("valueFormat = { (it + minColumns).toString() }"))

        assertTrue(bookshelfConfig.contains("@string/layout_grid7"))
        assertTrue(defaultStrings.contains("<string name=\"layout_grid7\">Grid-7</string>"))
        assertTrue(zhStrings.contains("<string name=\"layout_grid7\">网格七列</string>"))
    }

    @Test
    fun bookshelfStyle1LayoutSwitchSkipsDestroyedChildViews() {
        val booksFragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/books/BooksFragment.kt")
            .readText()
            .replace("\r\n", "\n")
        val updateLayout = booksFragment.substringAfter("fun updateBookshelfLayout(layout: Int)")
            .substringBefore("fun updateBookshelfSpacing(spacing: Int)")
        val updateSpacing = booksFragment.substringAfter("fun updateBookshelfSpacing(spacing: Int)")
            .substringBefore("private fun upFastScrollerBar")
        val lifecycleGuardIndex = updateLayout.indexOf("if (view == null) return")
        val layoutManagerIndex = updateLayout.indexOf("updateLayoutManager()")
        val spacingLifecycleGuardIndex = updateSpacing.indexOf("if (view == null) return")
        val invalidateDecorationsIndex = updateSpacing.indexOf("binding.rvBookshelf.invalidateItemDecorations()")

        assertTrue(
            "BooksFragment must remember the selected layout before skipping a destroyed view",
            updateLayout.contains("bookshelfLayout = newLayout\n        if (view == null) return")
        )
        assertTrue(
            "BooksFragment must not access binding/updateLayoutManager after its view is destroyed",
            lifecycleGuardIndex in 0 until layoutManagerIndex
        )
        assertTrue(
            "BooksFragment must remember the selected spacing before skipping a destroyed view",
            updateSpacing.contains("bookshelfMargin = newSpacing\n        if (view == null) return")
        )
        assertTrue(
            "BooksFragment must not access binding/invalidateItemDecorations after its view is destroyed",
            spacingLifecycleGuardIndex in 0 until invalidateDecorationsIndex
        )
    }

    @Test
    fun bookshelfLayoutSwitchPreservesGridColumnsAndSpacingPreferences() {
        val style1Fragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/BookshelfFragment1.kt")
            .readText()
            .replace("\r\n", "\n")
        val style2Fragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BookshelfFragment2.kt")
            .readText()
            .replace("\r\n", "\n")
        val appConfig = repoFile("app/src/main/java/io/wanjuan/app/help/config/AppConfig.kt")
            .readText()
            .replace("\r\n", "\n")
        val preferKey = repoFile("app/src/main/java/io/wanjuan/app/constant/PreferKey.kt").readText()

        assertTrue(preferKey.contains("const val bookshelfGridColumns = \"bookshelfGridColumns\""))
        assertTrue(appConfig.contains("var bookshelfGridColumns: Int"))
        assertTrue(
            appConfig.contains(
                "get() = appCtx.getPrefInt(\n" +
                    "            PreferKey.bookshelfGridColumns,\n" +
                    "            appCtx.getPrefInt(PreferKey.bookshelfLayout, 2).coerceAtLeast(2)\n" +
                    "        ).coerceIn(2, 7)"
            )
        )
        assertTrue(appConfig.contains("set(value) = appCtx.putPrefInt(PreferKey.bookshelfGridColumns, value.coerceIn(2, 7))"))

        listOf(style1Fragment, style2Fragment).forEach { fragment ->
            val switchBlock = fragment.substringAfter("private fun switchBookshelfLayout()")
                .substringBefore("private fun showBookshelfGridColumnsPopup")
            val columnsBlock = fragment.substringAfter("private fun setBookshelfGridColumns(columns: Int)")
                .substringBefore("private fun setBookshelfGridSpacing")
            val spacingBlock = fragment.substringAfter("private fun setBookshelfGridSpacing(spacing: Int)")
                .substringBefore("private fun updateBookshelfLayoutToggleIcon")

            assertTrue(switchBlock.contains("val currentLayout = AppConfig.bookshelfLayout"))
            assertTrue(switchBlock.contains("AppConfig.bookshelfGridColumns = currentLayout"))
            assertTrue(switchBlock.contains("else AppConfig.bookshelfGridColumns"))
            assertFalse(switchBlock.contains("else 2"))
            assertTrue(fragment.contains("initialColumns = AppConfig.bookshelfGridColumns"))
            assertTrue(columnsBlock.contains("AppConfig.bookshelfGridColumns = gridColumns"))
            assertTrue(columnsBlock.contains("AppConfig.bookshelfLayout = gridColumns"))
            assertTrue(spacingBlock.contains("AppConfig.bookshelfMargin = gridSpacing"))
        }
    }

    @Test
    fun bookshelfFolderStyleKeepsLayoutTogglePopupAndPrimaryTextOverflow() {
        val layout = parseXml(repoFile("app/src/main/res/layout/fragment_bookshelf2.xml"))
        val fragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BookshelfFragment2.kt").readText()
        val toggle = layout.elementById("btn_bookshelf_layout_toggle")

        assertEquals("@string/switchLayout", toggle.androidAttr("contentDescription"))
        assertEquals("@drawable/ic_lucide_layout_grid", toggle.androidAttr("src"))
        assertEquals("@color/primaryText", toggle.androidAttr("tint"))
        assertEquals("@dimen/discover_top_action_button_size", toggle.androidAttr("layout_marginEnd"))
        assertEquals("parent", toggle.appAttr("layout_constraintEnd_toEndOf"))
        assertTrue(fragment.contains("import io.wanjuan.app.lib.theme.primaryTextColor"))
        assertTrue(fragment.contains("binding.titleBar.setColorFilter(primaryTextColor)"))
        assertTrue(fragment.contains("binding.btnBookshelfLayoutToggle.setOnClickListener"))
        assertTrue(fragment.contains("binding.btnBookshelfLayoutToggle.setOnLongClickListener"))
        assertTrue(fragment.contains("showBookshelfGridColumnsPopup(it)"))
        assertTrue(fragment.contains("GridColumnsPopup.show("))
        assertTrue(fragment.contains("initialSpacing = AppConfig.bookshelfMargin"))
        assertTrue(fragment.contains("onColumnsChanging = ::setBookshelfGridColumns"))
        assertTrue(fragment.contains("onSpacingChanging = ::setBookshelfGridSpacing"))
        assertTrue(fragment.contains("fun updateBookshelfLayout(layout: Int)"))
        assertTrue(fragment.contains("fun updateBookshelfSpacing(spacing: Int)"))
        assertFalse(fragment.contains("activity?.recreate()"))
    }

    @Test
    fun bookshelfTopBarsShowReadRecordShortcutInBothStyles() {
        val style1Layout = parseXml(repoFile("app/src/main/res/layout/fragment_bookshelf1.xml"))
        val style2Layout = parseXml(repoFile("app/src/main/res/layout/fragment_bookshelf2.xml"))
        val style1Fragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style1/BookshelfFragment1.kt").readText()
        val style2Fragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/bookshelf/style2/BookshelfFragment2.kt").readText()
        val mainActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/main/MainActivity.kt").readText()
        val style1ReadRecord = style1Layout.elementById("btn_bookshelf_read_record")
        val style2ReadRecord = style2Layout.elementById("btn_bookshelf_read_record")

        listOf(style1ReadRecord, style2ReadRecord).forEach { button ->
            assertEquals("@string/read_record", button.androidAttr("contentDescription"))
            assertEquals("@drawable/ic_lucide_chart_bar", button.androidAttr("src"))
            assertEquals("@color/primaryText", button.androidAttr("tint"))
            assertEquals("@dimen/discover_top_action_button_size", button.androidAttr("layout_width"))
            assertEquals("@dimen/discover_top_action_button_size", button.androidAttr("layout_height"))
        }
        assertEquals(
            "@id/btn_bookshelf_read_record",
            style1Layout.elementById("title_row").appAttr("layout_constraintEnd_toStartOf")
        )
        assertEquals(
            "@id/btn_bookshelf_layout_toggle",
            style1ReadRecord.appAttr("layout_constraintEnd_toStartOf")
        )
        assertEquals(
            "@id/btn_bookshelf_layout_toggle",
            style2ReadRecord.appAttr("layout_constraintEnd_toStartOf")
        )
        assertTrue(style1Fragment.contains("binding.btnBookshelfReadRecord.setOnClickListener"))
        assertTrue(style2Fragment.contains("binding.btnBookshelfReadRecord.setOnClickListener"))
        assertTrue(style1Fragment.contains("openReadRecordPage()"))
        assertTrue(style2Fragment.contains("openReadRecordPage()"))
        assertTrue(mainActivity.contains("fun openReadRecordPage()"))
        assertTrue(mainActivity.contains("ReadRecordActivity::class.java"))
    }

    @Test
    fun mangaReaderToolbarUsesCompactLucideActionsMatchingDiscoverIconScale() {
        val mangaMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/MangaMenu.kt").readText()
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val bookMangaMenu = parseXml(repoFile("app/src/main/res/menu/book_manga.xml"))
        val dimens = repoFile("app/src/main/res/values/dimens.xml").readText()

        assertTrue(mangaMenu.contains("R.dimen.read_top_bar_button_size"))
        assertTrue(mangaMenu.contains("R.dimen.read_top_bar_icon_size"))
        assertTrue(mangaMenu.contains("titleBar.toolbar.updateLayoutParams<ViewGroup.LayoutParams>"))
        assertTrue(mangaMenu.contains("height = topBarButtonSize()"))
        assertTrue(mangaMenu.contains("titleBar.toolbar.minimumHeight = topBarButtonSize()"))
        assertTrue(mangaMenu.contains("R.drawable.ic_lucide_arrow_left"))
        assertTrue(mangaMenu.contains("R.drawable.ic_lucide_more_vertical"))
        assertTrue(mangaMenu.contains("private fun syncToolbarActionIconSize()"))
        assertTrue(mangaMenu.contains("val iconSize = topBarIconSize()"))
        assertTrue(mangaMenu.contains("toolbarIconColor = Color.WHITE"))
        assertTrue(mangaMenu.contains("fun refreshMenuColorFilter()"))
        assertTrue(mangaActivity.contains("binding.mangaMenu.refreshMenuColorFilter()"))
        assertTrue(dimens.contains("<dimen name=\"discover_top_action_button_size\">48dp</dimen>"))
        assertTrue(dimens.contains("<dimen name=\"discover_top_action_button_padding\">13dp</dimen>"))

        assertEquals(
            "@drawable/ic_lucide_shuffle",
            bookMangaMenu.elementById("menu_change_source").androidAttr("icon")
        )
        assertEquals(
            "@drawable/ic_lucide_refresh_cw",
            bookMangaMenu.elementById("menu_refresh").androidAttr("icon")
        )
        assertEquals(
            "@drawable/ic_lucide_list",
            bookMangaMenu.elementById("menu_catalog").androidAttr("icon")
        )
        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_shuffle.xml").exists())
        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_refresh_cw.xml").exists())
        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_more_vertical.xml").exists())
    }

    @Test
    fun mangaReaderMovesBookTitleBelowToolbarLikeTextReader() {
        val mangaMenuLayout = parseXml(repoFile("app/src/main/res/layout/view_manga_menu.xml"))
        val mangaMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/MangaMenu.kt").readText()

        assertEquals("18sp", mangaMenuLayout.elementById("tv_chapter_name").androidAttr("textSize"))
        assertEquals("12sp", mangaMenuLayout.elementById("tv_chapter_url").androidAttr("textSize"))
        assertTrue(mangaMenu.contains("titleBar.title = null"))
        assertFalse(mangaMenu.contains("titleBar.title = ReadManga.book?.name"))
        assertTrue(mangaMenu.contains("tvChapterName.text = ReadManga.book?.name.orEmpty()"))
        assertTrue(mangaMenu.contains("tvChapterUrl.text = it.chapter.title"))
        assertTrue(mangaMenu.contains("tvChapterUrl.tag = it.chapter.getAbsoluteURL()"))
        assertTrue(mangaMenu.contains("val url = tvChapterUrl.tag as? String ?: return@OnClickListener"))
        assertTrue(mangaMenu.contains("ReadManga.bookSource"))
        assertFalse(mangaMenu.contains("val url = tvChapterUrl.text.toString().trim()"))
    }

    @Test
    fun discoverGridLayoutColumnsAreConfigurableFromSettings() {
        val exploreFragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val exploreShowAdapter = repoFile("app/src/main/java/io/wanjuan/app/ui/book/explore/ExploreShowAdapter.kt").readText()
        val appConfig = repoFile("app/src/main/java/io/wanjuan/app/help/config/AppConfig.kt").readText()
        val preferKey = repoFile("app/src/main/java/io/wanjuan/app/constant/PreferKey.kt").readText()
        val defaultStrings = repoFile("app/src/main/res/values/strings.xml").readText()
        val zhStrings = repoFile("app/src/main/res/values-zh/strings.xml").readText()

        assertTrue(preferKey.contains("const val modernDiscoveryGridColumns = \"modernDiscoveryGridColumns\""))
        assertTrue(appConfig.contains("var modernDiscoveryGridColumns: Int"))
        assertTrue(appConfig.contains("get() = appCtx.getPrefInt(PreferKey.modernDiscoveryGridColumns, 2).coerceIn(2, 7)"))
        assertTrue(appConfig.contains("set(value) = appCtx.putPrefInt(PreferKey.modernDiscoveryGridColumns, value.coerceIn(2, 7))"))
        assertTrue(exploreFragment.contains("private const val DISCOVER_GRID_COLUMNS_MIN = 2"))
        assertTrue(exploreFragment.contains("private const val DISCOVER_GRID_COLUMNS_MAX = 7"))
        assertTrue(exploreFragment.contains("addDiscoverGridColumnsSeekBar"))
        assertTrue(exploreFragment.contains("discoverBookAdapter.gridColumns = AppConfig.modernDiscoveryGridColumns"))
        assertTrue(exploreShowAdapter.contains("var gridColumns: Int = 2"))
        assertTrue(exploreShowAdapter.contains("val isCompact = gridColumns >= 3"))
        assertTrue(defaultStrings.contains("<string name=\"discover_grid_columns\">Items per row</string>"))
        assertTrue(zhStrings.contains("<string name=\"discover_grid_columns\">每行数量</string>"))
    }

    @Test
    fun discoverSourcePickerHighlightsAndScrollsToSelectedSource() {
        val exploreFragment = repoFile("app/src/main/java/io/wanjuan/app/ui/main/explore/ExploreFragment.kt").readText()
        val sourceSelectDialog = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/SourceSelectDialog.kt").readText()

        assertTrue(exploreFragment.contains("selectedKey = selectedDiscoverSourcePart?.bookSourceUrl"))
        assertTrue(sourceSelectDialog.contains("val selectedIndex = filteredItems.indexOfFirst"))
        assertTrue(sourceSelectDialog.contains("recyclerView.scrollToPosition(selectedIndex)"))
        assertTrue(sourceSelectDialog.contains("holder.bind(displayName(item), selected)"))
        assertTrue(sourceSelectDialog.contains("val accentColor = context.accentColor"))
        assertTrue(sourceSelectDialog.contains("ColorUtils.adjustAlpha(accentColor, 0.16f)"))
        assertFalse(sourceSelectDialog.contains("selectedPrefix + displayName(item)"))
    }

    @Test
    fun primaryReadBottomMenuUsesOptionAOrder() {
        val menu = parseXml(repoFile("app/src/main/res/menu/read_bottom_primary.xml"))
        val items = menu.childElements("item")

        assertEquals(
            listOf(
                "@+id/menu_read_search",
                "@+id/menu_read_toc",
                "@+id/menu_read_aloud",
                "@+id/menu_read_interface",
                "@+id/menu_read_settings"
            ),
            items.map { it.androidAttr("id") }
        )
        assertEquals("@drawable/ic_lucide_search", items.first().androidAttr("icon"))
        assertEquals("@string/search", items.first().androidAttr("title"))
    }

    @Test
    fun interfaceReadBottomMenuUsesOptionAOrder() {
        val menu = parseXml(repoFile("app/src/main/res/menu/read_bottom_interface.xml"))
        val items = menu.childElements("item")

        assertEquals(
            listOf(
                "@+id/menu_read_interface_back",
                "@+id/menu_read_layout",
                "@+id/menu_read_page_turn",
                "@+id/menu_read_background",
                "@+id/menu_read_theme"
            ),
            items.map { it.androidAttr("id") }
        )
        assertEquals("@drawable/ic_lucide_chevron_left", items.first().androidAttr("icon"))
        assertEquals("@string/text_return", items.first().androidAttr("title"))
        assertEquals("@drawable/ic_lucide_list", items[1].androidAttr("icon"))
        assertEquals("@string/compose_type", items[1].androidAttr("title"))
        assertEquals("@string/read_style_page", items[2].androidAttr("title"))
        assertEquals("@drawable/ic_lucide_image", items[3].androidAttr("icon"))
        assertEquals("@string/background", items[3].androidAttr("title"))
    }

    @Test
    fun primaryAndInterfaceBottomBarsUseMatchingFiveItemSpacing() {
        val layout = readMenuLayout()
        val primaryNav = layout.elementById("read_bottom_primary_nav")
        val interfaceNav = layout.elementById("read_bottom_interface_nav")
        val interfaceMenu = parseXml(repoFile("app/src/main/res/menu/read_bottom_interface.xml"))
        val dimens = repoFile("app/src/main/res/values/dimens.xml").readText()

        assertEquals("@dimen/read_bottom_nav_horizontal_padding", primaryNav.androidAttr("paddingStart"))
        assertEquals("@dimen/read_bottom_nav_horizontal_padding", primaryNav.androidAttr("paddingEnd"))
        assertEquals("@dimen/read_bottom_nav_horizontal_padding", interfaceNav.androidAttr("paddingStart"))
        assertEquals("@dimen/read_bottom_nav_horizontal_padding", interfaceNav.androidAttr("paddingEnd"))
        assertEquals("", interfaceNav.androidAttr("layout_marginStart"))
        assertFalse(repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
            .contains("android:id=\"@+id/read_bottom_interface_back\""))
        assertEquals(5, interfaceMenu.childElements("item").size)
        assertTrue(dimens.contains("<dimen name=\"read_bottom_nav_horizontal_padding\">18dp</dimen>"))
    }

    @Test
    fun readBottomTabBarIsCompactAndOnlySelectedItemShowsLabel() {
        val layout = readMenuLayout()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val bottomTabBar = layout.elementById("bottom_tab_bar")
        val expandedPanel = layout.elementById("fl_expanded_panel")
        val viewport = layout.elementById("bottom_tab_nav_viewport")
        val indicator = layout.elementById("bottom_tab_indicator_container")
        val primaryNav = layout.elementById("read_bottom_primary_nav")
        val interfaceNav = layout.elementById("read_bottom_interface_nav")

        assertEquals("56dp", bottomTabBar.androidAttr("layout_height"))
        assertEquals("56dp", expandedPanel.androidAttr("layout_marginBottom"))
        assertEquals("56dp", viewport.androidAttr("layout_height"))
        assertEquals("48dp", indicator.androidAttr("layout_height"))
        assertEquals("selected", primaryNav.appAttr("labelVisibilityMode"))
        assertEquals("selected", interfaceNav.appAttr("labelVisibilityMode"))
        assertEquals("56dp", primaryNav.androidAttr("minHeight"))
        assertEquals("56dp", interfaceNav.androidAttr("minHeight"))
        assertEquals("", primaryNav.androidAttr("translationY"))
        assertEquals("", interfaceNav.androidAttr("translationY"))
        assertTrue(readMenu.contains("private fun bottomTabCollapsedHeight(): Int = 56.dpToPx()"))
        assertFalse(layoutXml.contains("app:labelVisibilityMode=\"labeled\""))
    }

    @Test
    fun backgroundTabRemovesTextureStrengthControls() {
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertFalse(layoutXml.contains("background_texture_panel"))
        assertFalse(layoutXml.contains("texture_none"))
        assertFalse(readMenu.contains("setTextureStrength"))
        assertFalse(readMenu.contains("updateTextureButtons"))
    }

    @Test
    fun themePanelRemovesBrightnessAndContrastControls() {
        val layout = readMenuLayout()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(layout.elementById("panel_theme").tagName == "ScrollView")
        assertFalse(layoutXml.contains("seek_theme_brightness"))
        assertFalse(layoutXml.contains("seek_theme_contrast"))
        assertFalse(layoutXml.contains("theme_tone_panel"))
        assertFalse(readMenu.contains("applyTextContrast"))
    }

    @Test
    fun readMenuSearchPrimaryItemOpensInlinePanel() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val primaryNavEvents = readMenu.substringAfter("readBottomPrimaryNav.setOnItemSelectedListener")
        val searchBranch = primaryNavEvents.substringAfter("R.id.menu_read_search ->")
            .substringBefore("R.id.menu_read_toc ->")

        assertTrue(readMenu.contains("BottomTab.Search"))
        assertTrue(searchBranch.contains("toggleBottomTab(BottomTab.Search)"))
        assertFalse(searchBranch.contains("openSearchActivity"))
        assertEquals("panel_search", layout.elementById("panel_search").androidAttr("id").substringAfter("@+id/"))
        assertEquals("rv_panel_search_results", layout.elementById("rv_panel_search_results").androidAttr("id").substringAfter("@+id/"))
    }

    @Test
    fun readMenuAloudPrimaryItemOpensInlinePanel() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val primaryNavEvents = readMenu.substringAfter("readBottomPrimaryNav.setOnItemSelectedListener")
        val aloudBranch = primaryNavEvents.substringAfter("R.id.menu_read_aloud ->")
            .substringBefore("R.id.menu_read_interface ->")
        val aloudPanel = layout.elementByTag("panel_aloud")

        assertTrue(readMenu.contains("BottomTab.Aloud"))
        assertTrue(aloudBranch.contains("toggleBottomTab(BottomTab.Aloud)"))
        assertFalse(aloudBranch.contains("showReadAloudDialog"))
        assertEquals("panel_aloud", aloudPanel.androidAttr("tag"))
        assertTrue(layout.elementByTag("iv_aloud_play_pause").hasAncestor(aloudPanel))
        assertTrue(layout.elementByTag("seek_aloud_timer").hasAncestor(aloudPanel))
        assertTrue(readMenu.contains("taggedView(\"panel_aloud\").gone(tab != BottomTab.Aloud)"))
    }

    @Test
    fun readMenuSettingsPrimaryItemOpensInlinePanel() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val primaryNavEvents = readMenu.substringAfter("readBottomPrimaryNav.setOnItemSelectedListener")
        val settingsBranch = primaryNavEvents.substringAfter("R.id.menu_read_settings ->")
            .substringBefore("else -> false")
        val settingsPanel = layout.elementByTag("panel_settings")

        assertTrue(readMenu.contains("BottomTab.Settings"))
        assertTrue(settingsBranch.contains("toggleBottomTab(BottomTab.Settings)"))
        assertFalse(settingsBranch.contains("showMoreSetting"))
        assertEquals("panel_settings", settingsPanel.androidAttr("tag"))
        assertTrue(layout.elementByTag("panel_settings_list").hasAncestor(settingsPanel))
        assertTrue(readMenu.contains("taggedView(\"panel_settings\").gone(tab != BottomTab.Settings)"))
        assertTrue(readMenu.contains("renderSettingsPanel()"))
        assertFalse(readMenu.contains("MoreConfigDialog.ReadPreferenceFragment"))
    }

    @Test
    fun aloudPanelButtonsReserveReadableTextAndBorders() {
        val layout = readMenuLayout()

        assertEquals("56dp", layout.elementByTag("aloud_transport_panel").androidAttr("layout_height"))
        assertEquals("68dp", layout.elementByTag("tv_aloud_prev_chapter").androidAttr("layout_width"))
        assertEquals("68dp", layout.elementByTag("tv_aloud_next_chapter").androidAttr("layout_width"))
        assertEquals("42dp", layout.elementByTag("tv_aloud_prev_chapter").androidAttr("layout_height"))
        assertEquals("13sp", layout.elementByTag("tv_aloud_prev_chapter").androidAttr("textSize"))
        assertEquals("36dp", layout.elementByTag("iv_aloud_play_prev").androidAttr("layout_width"))
        assertEquals("42dp", layout.elementByTag("iv_aloud_play_pause").androidAttr("layout_width"))
        assertEquals("36dp", layout.elementByTag("iv_aloud_stop").androidAttr("layout_width"))
        assertEquals("36dp", layout.elementByTag("iv_aloud_play_next").androidAttr("layout_width"))
    }

    @Test
    fun tocPanelAddsBookmarkPageInsideSameFrostedSheet() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val tocPanel = layout.elementById("panel_toc")
        val tocHeader = layout.elementById("toc_header")
        val tabPanel = layout.elementByTag("toc_page_tabs")
        val contentHost = layout.elementByTag("toc_content_host")

        assertTrue(tabPanel.hasAncestor(tocPanel))
        assertTrue(tabPanel.hasAncestor(tocHeader))
        assertTrue(tabPanel.isBefore(layout.elementById("tv_panel_toc_count")))
        assertEquals("", tabPanel.androidAttr("background"))
        assertTrue(layout.elementByTag("tv_toc_tab_chapters").hasAncestor(tabPanel))
        assertTrue(layout.elementByTag("tv_toc_tab_bookmarks").hasAncestor(tabPanel))
        assertEquals("@string/chapter_list", layout.elementByTag("tv_toc_tab_chapters").androidAttr("text"))
        assertEquals("@string/bookmark", layout.elementByTag("tv_toc_tab_bookmarks").androidAttr("text"))
        assertTrue(layout.elementById("rv_panel_toc").hasAncestor(contentHost))
        assertTrue(layout.elementByTag("rv_panel_bookmarks").hasAncestor(contentHost))
        assertEquals("gone", layout.elementByTag("rv_panel_bookmarks").androidAttr("visibility"))
        assertEquals("@string/read_menu_bookmark_empty", layout.elementByTag("tv_panel_bookmarks_empty").androidAttr("text"))
        assertTrue(readMenu.contains("private enum class TocPanelPage"))
        assertTrue(readMenu.contains("ReadMenuBookmarkAdapter(::openBookmark)"))
        assertTrue(readMenu.contains("loadBookmarkPanel()"))
        assertTrue(readMenu.contains("appDb.bookmarkDao.getByBook(book.name, book.author)"))
    }

    @Test
    fun tocPanelListsUseFastDragScrollbars() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val tocRecycler = layout.elementById("rv_panel_toc")
        val bookmarkRecycler = layout.elementByTag("rv_panel_bookmarks")
        val fastScrollRecyclerView =
            "io.wanjuan.app.ui.widget.recycler.scroller.FastScrollRecyclerView"

        listOf(tocRecycler, bookmarkRecycler).forEach { recycler ->
            assertEquals(fastScrollRecyclerView, recycler.tagName)
            assertEquals("none", recycler.androidAttr("scrollbars"))
            assertEquals("false", recycler.appAttr("fadeScrollbar"))
            assertEquals("true", recycler.appAttr("showTrack"))
            assertEquals("false", recycler.appAttr("showBubble"))
            assertEquals("18dp", recycler.androidAttr("paddingEnd"))
        }
        assertTrue(readMenu.contains("rvPanelToc.setFastScrollHandlePadding(10.dpToPx(), 0)"))
        assertTrue(readMenu.contains("?.setFastScrollHandlePadding(10.dpToPx(), 0)"))
    }

    @Test
    fun readMenuFastScrollRecyclerViewsAllHaveIdsForFastScrollerAnchors() {
        val layout = readMenuLayout()
        val fastScrollRecyclerView =
            "io.wanjuan.app.ui.widget.recycler.scroller.FastScrollRecyclerView"

        val missingIds = layout.elementsByName(fastScrollRecyclerView)
            .filter { it.androidAttr("id").isBlank() }
            .map { it.androidAttr("tag").ifBlank { it.tagName } }

        assertTrue(
            "FastScrollRecyclerView must have android:id for FastScroller anchor. Missing: $missingIds",
            missingIds.isEmpty()
        )
    }

    @Test
    fun fastScrollerHidesHandleWhenContentDoesNotOverflow() {
        val fastScroller =
            repoFile("app/src/main/java/io/wanjuan/app/ui/widget/recycler/scroller/FastScroller.kt")
                .readText()
        val fastScrollRecyclerView =
            repoFile("app/src/main/java/io/wanjuan/app/ui/widget/recycler/scroller/FastScrollRecyclerView.kt")
                .readText()

        assertTrue(fastScroller.contains("private fun isRecyclerScrollable(): Boolean"))
        assertTrue(fastScroller.contains("return recyclerView.computeVerticalScrollRange() > scrollExtent"))
        assertTrue(fastScroller.contains("private fun syncScrollbarVisibility()"))
        assertTrue(fastScroller.contains("val scrollable = isEnabled && isRecyclerScrollable()"))
        assertTrue(fastScroller.contains("mScrollbar.visibility = View.INVISIBLE"))
        assertTrue(fastScroller.contains("if (!isRecyclerScrollable()) {\n                    syncScrollbarVisibility()\n                    return false\n                }"))
        assertTrue(fastScroller.contains("adapter?.registerAdapterDataObserver(mAdapterDataObserver)"))
        assertTrue(fastScrollRecyclerView.contains("mFastScroller.onRecyclerViewAdapterChanged()"))
        assertTrue(fastScrollRecyclerView.contains("fun setFastScrollHandlePadding(start: Int, end: Int)"))
    }

    @Test
    fun settingsPanelUsesNativeGlassRowsInsteadOfEmbeddedPreferenceSurface() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val settingsPanel = layout.elementByTag("panel_settings")
        val settingsScroll = layout.elementByTag("panel_settings_scroll")
        val settingsList = layout.elementByTag("panel_settings_list")

        assertTrue(settingsScroll.hasAncestor(settingsPanel))
        assertTrue(settingsList.hasAncestor(settingsScroll))
        assertEquals("", settingsScroll.androidAttr("background"))
        assertEquals("false", settingsScroll.androidAttr("clipToPadding"))
        assertTrue(readMenu.contains("private fun renderSettingsPanel()"))
        assertTrue(readMenu.contains("addReadSettingSwitch("))
        assertTrue(readMenu.contains("addReadSettingChoice("))
        assertTrue(readMenu.contains("addReadSettingAction("))
        assertTrue(readMenu.contains("row.background = readSettingRowBackground()"))
        assertFalse(readMenu.contains("PreferenceFragment"))
        assertFalse(readMenu.contains("ReadPreferenceFragment"))
    }

    @Test
    fun brightnessControlLivesInThemePanelInsteadOfSideRail() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val brightness = layout.elementById("ll_brightness")
        val layoutPanel = layout.elementById("panel_layout")
        val themePanel = layout.elementById("panel_theme")

        assertTrue(brightness.hasAncestor(themePanel))
        assertFalse(brightness.hasAncestor(layoutPanel))
        assertTrue(layout.elementById("seek_brightness").hasAncestor(brightness))
        assertEquals("io.wanjuan.app.lib.theme.view.ThemeSeekBar", layout.elementById("seek_brightness").tagName)
        assertFalse(layoutXml.contains("vw_brightness_pos_adjust"))
        assertFalse(readMenu.contains("upBrightnessVwPos"))
        assertFalse(readMenu.contains("R.string.show_brightness_view"))
        assertFalse(readMenu.contains("binding.llBrightness.visible(showBrightnessView)"))
    }

    @Test
    fun bottomTabSelectedIndicatorDoesNotAutoHideActivePrimaryTab() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("private fun flashBottomTabIndicator(nav: BottomNavigationView, itemId: Int)"))
        assertTrue(readMenu.contains("showBottomTabIndicator(nav, itemId, animate = true, autoHide = false)"))
        assertFalse(readMenu.contains("showBottomTabIndicator(nav, itemId, animate = true, autoHide = true)"))
    }

    @Test
    fun bottomTabSelectionLabelSurvivesClosingAnimation() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val hideExpandedPanel = readMenu.substringAfter("private fun hideExpandedPanel(")
            .substringBefore("private fun handleBackgroundDismiss()")
        val selectionSync = readMenu.substringAfter("private fun syncBottomNavigationSelection()")
            .substringBefore("private fun setBottomNavigationSelection")
        val setSelection = readMenu.substringAfter("private fun setBottomNavigationSelection(")
            .substringBefore("private fun clearBottomNavigationSelection")
        val colorBlock = readMenu.substringAfter("private fun applyBottomNavigationColors()")
            .substringBefore("private fun syncBottomNavigationSelection()")

        assertTrue(readMenu.contains("private var bottomTabSelectionAnchor: BottomTab? = null"))
        assertTrue(readMenu.contains("bottomTabSelectionAnchor = tab"))
        assertTrue(hideExpandedPanel.contains("val closingTab = activeBottomTab"))
        assertTrue(hideExpandedPanel.contains("bottomTabSelectionAnchor = closingTab"))
        assertTrue(hideExpandedPanel.contains("clearBottomTabSelectionAnchor(closingTab)"))
        assertTrue(readMenu.contains("private fun clearBottomTabSelectionAnchor(tab: BottomTab?)"))
        assertTrue(selectionSync.contains("val selectedTab = activeBottomTab ?: bottomTabSelectionAnchor"))
        assertTrue(colorBlock.contains("val selectedContentColor = if (activeBottomTab == null)"))
        assertTrue(colorBlock.contains("bottomTabContentColor()"))
        assertTrue(colorBlock.contains("bottomTabSelectedContentColor()"))
        assertTrue(colorBlock.contains("val selectedLabelColor = if (activeBottomTab == null)"))
        assertTrue(colorBlock.contains("bottomTabSelectedLabelColor()"))
        assertTrue(colorBlock.contains(".setSelectedColor(selectedContentColor)"))
        assertTrue(colorBlock.contains(".setCheckedColor(selectedContentColor)"))
        assertTrue(colorBlock.contains(".setSelectedColor(selectedLabelColor)"))
        assertTrue(colorBlock.contains(".setCheckedColor(selectedLabelColor)"))
        assertTrue(setSelection.contains("} else {\n                clearBottomNavigationSelection(nav)\n                nav.menu.findItem(itemId)?.isChecked = true"))
        assertFalse(setSelection.contains("nav.selectedItemId = itemId"))
    }

    @Test
    fun bottomTabSelectedLabelAnimatesIconIntoPlace() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val setSelection = readMenu.substringAfter("private fun setBottomNavigationSelection(")
            .substringBefore("private fun clearBottomNavigationSelection")
        val animationBlock = readMenu.substringAfter("private fun animateBottomNavigationSelectedItem(")
            .substringBefore("private fun resetBottomNavigationItemAnimations")

        assertTrue(setSelection.contains("val wasSelectedItemId = checkedBottomNavigationItemId(nav)"))
        assertTrue(setSelection.contains("animateBottomNavigationSelectedItem(nav, itemId, wasSelectedItemId != itemId)"))
        assertTrue(readMenu.contains("private fun checkedBottomNavigationItemId(nav: BottomNavigationView): Int?"))
        assertTrue(readMenu.contains("private fun animateBottomNavigationSelectedItem("))
        assertTrue(readMenu.contains("private fun resetBottomNavigationItemAnimations(nav: BottomNavigationView)"))
        assertTrue(animationBlock.contains("doOnPreDraw"))
        assertTrue(animationBlock.contains("com.google.android.material.R.id.navigation_bar_item_content_container"))
        assertTrue(animationBlock.contains("com.google.android.material.R.id.navigation_bar_item_labels_group"))
        assertTrue(animationBlock.contains("contentContainer.translationY = startOffset"))
        assertTrue(animationBlock.contains(".translationY(0f)"))
        assertTrue(animationBlock.contains("labelsGroup?.animate()"))
    }

    @Test
    fun bottomTabIndicatorIgnoresStalePostedRequests() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val showIndicator = readMenu.substringAfter("private fun showBottomTabIndicator(")
            .substringBefore("private fun hideBottomTabIndicator()")
        val hideIndicator = readMenu.substringAfter("private fun hideBottomTabIndicator()")
            .substringBefore("private fun fadeBottomTabIndicator()")

        assertTrue(readMenu.contains("private var bottomTabIndicatorRequestToken: Int = 0"))
        assertTrue(readMenu.contains("private fun nextBottomTabIndicatorRequestToken(): Int"))
        assertTrue(readMenu.contains("private fun isBottomTabIndicatorRequestCurrent("))
        assertTrue(showIndicator.contains("val requestToken = nextBottomTabIndicatorRequestToken()"))
        assertTrue(showIndicator.contains("if (requestToken != bottomTabIndicatorRequestToken ||"))
        assertTrue(showIndicator.contains("!isBottomTabIndicatorRequestCurrent(nav, itemId)"))
        assertTrue(showIndicator.contains("return@post"))
        assertTrue(showIndicator.contains("val maxWidth = 68.dpToPx()"))
        assertTrue(showIndicator.contains("val horizontalInset = 4.dpToPx()"))
        assertTrue(hideIndicator.contains("nextBottomTabIndicatorRequestToken()"))
    }

    @Test
    fun interfaceModeDoesNotKeepPrimaryInterfaceItemSelected() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val selectionSync = readMenu.substringAfter("private fun syncBottomNavigationSelection()")
            .substringBefore("private fun setBottomNavigationSelection")

        assertFalse(selectionSync.contains("null -> if (bottomTabMode == BottomTabMode.Interface)"))
        assertFalse(selectionSync.contains("R.id.menu_read_interface"))
    }

    @Test
    fun readMenuTopBarUsesFrostedGlassMenuSurface() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val initBlock = readMenu.substringAfter("private fun initView")
            .substringBefore("if (AppConfig.isEInkMode)")

        assertTrue(initBlock.contains("titleBar.setBackgroundColor(Color.TRANSPARENT)"))
        assertTrue(readMenu.contains("private fun configureTopBarFrostedGlass()"))
        assertTrue(readMenu.contains("binding.titleBar.applyStatusBarPadding(withInitialPadding = true)"))
        assertFalse(initBlock.contains("} else if (reset) {"))
    }

    @Test
    fun readerTopBarShowsBookAndChapterInAdditionWithLoginIconAction() {
        val layout = readMenuLayout()
        val mangaLayout = parseXml(repoFile("app/src/main/res/layout/view_manga_menu.xml"))
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val mangaMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/MangaMenu.kt").readText()
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val shell = layout.elementById("title_bar_shell")
        val mangaShell = mangaLayout.elementById("title_bar_shell")
        val titleAddition = layout.elementById("title_bar_addition")
        val mangaTitleAddition = mangaLayout.elementById("title_bar_addition")
        val titleInfo = layout.elementById("ll_title_info")
        val mangaTitleInfo = mangaLayout.elementById("ll_title_info")
        val sourceAction = layout.elementById("tv_source_action")
        val mangaSourceAction = mangaLayout.elementById("tv_source_action")

        assertTrue(repoFile("app/src/main/res/drawable/ic_lucide_link_2.xml").exists())
        assertEquals(mangaShell.androidAttr("layout_width"), shell.androidAttr("layout_width"))
        assertEquals(mangaShell.androidAttr("clipChildren"), shell.androidAttr("clipChildren"))
        assertEquals(mangaShell.androidAttr("clipToPadding"), shell.androidAttr("clipToPadding"))
        assertEquals(mangaShell.appAttr("layout_constraintStart_toStartOf"), shell.appAttr("layout_constraintStart_toStartOf"))
        assertEquals(mangaShell.appAttr("layout_constraintEnd_toEndOf"), shell.appAttr("layout_constraintEnd_toEndOf"))
        assertEquals(mangaTitleAddition.androidAttr("layout_height"), titleAddition.androidAttr("layout_height"))
        assertEquals(mangaTitleAddition.androidAttr("paddingStart"), titleAddition.androidAttr("paddingStart"))
        assertEquals(mangaTitleAddition.androidAttr("paddingTop"), titleAddition.androidAttr("paddingTop"))
        assertEquals(mangaTitleAddition.androidAttr("paddingEnd"), titleAddition.androidAttr("paddingEnd"))
        assertEquals(mangaTitleAddition.androidAttr("paddingBottom"), titleAddition.androidAttr("paddingBottom"))
        assertEquals(mangaTitleInfo.androidAttr("minHeight"), titleInfo.androidAttr("minHeight"))
        assertEquals(mangaTitleInfo.appAttr("layout_constraintVertical_bias"), titleInfo.appAttr("layout_constraintVertical_bias"))
        assertEquals("18sp", layout.elementById("tv_chapter_name").androidAttr("textSize"))
        assertEquals("12sp", layout.elementById("tv_chapter_url").androidAttr("textSize"))
        assertEquals(mangaSourceAction.androidAttr("layout_height"), sourceAction.androidAttr("layout_height"))
        assertEquals(mangaSourceAction.androidAttr("minWidth"), sourceAction.androidAttr("minWidth"))
        assertEquals(mangaSourceAction.appAttr("radius"), sourceAction.appAttr("radius"))
        assertTrue(readMenu.contains("setupTopBarLoginAction()"))
        assertTrue(readMenu.contains("titleBar.menu.add(0, R.id.menu_login"))
        assertTrue(readMenu.contains("setIcon(R.drawable.ic_lucide_link_2)"))
        assertTrue(readMenu.contains("R.drawable.ic_lucide_user"))
        assertTrue(readMenu.contains("R.string.open_in_app_webview"))
        assertTrue(readMenu.contains("callBack.showLogin()"))
        assertTrue(mangaMenu.contains("setupTopBarLoginAction()"))
        assertTrue(mangaMenu.contains("titleBar.menu.add(0, R.id.menu_login"))
        assertTrue(mangaMenu.contains("setIcon(R.drawable.ic_lucide_link_2)"))
        assertTrue(mangaMenu.contains("R.drawable.ic_lucide_user"))
        assertTrue(mangaMenu.contains("R.string.open_in_app_webview"))
        assertTrue(mangaMenu.contains("callBack.showLogin()"))
        assertTrue(mangaMenu.contains("val source = ReadManga.bookSource"))
        assertTrue(mangaMenu.contains("isVisible = source != null"))
        assertTrue(readMenu.contains("binding.tvChapterUrl.tag = it.chapter.getAbsoluteURL()"))
        assertTrue(readMenu.contains("context.startActivity<WebViewActivity>"))
        assertTrue(readMenu.contains("putExtra(\"url\", url)"))
        assertTrue(mangaActivity.contains("override fun showLogin()"))
        assertTrue(mangaActivity.contains("startActivity<WebViewActivity>"))
        assertTrue(mangaActivity.contains("ReadManga.curMangaChapter?.chapter?.getAbsoluteURL()"))
        assertTrue(mangaActivity.contains("putExtra(\"url\", url)"))
        assertTrue(readMenu.contains("private fun updateTopBarSourceAction()"))
        assertTrue(readMenu.contains("ReadBook.bookSource?.bookSourceName ?: context.getString(R.string.book_source)"))
        assertTrue(readMenu.contains("tvSourceAction.visible()"))
        assertTrue(readMenu.contains("updateTopBarSourceAction()"))
        assertTrue(readMenu.contains("binding.titleBar.title = null"))
        assertTrue(readMenu.contains("binding.tvChapterName.text = ReadBook.book?.name.orEmpty()"))
        assertTrue(readMenu.contains("binding.tvChapterUrl.text = it.title"))
        assertTrue(readMenu.contains("binding.tvChapterUrl.tag = it.chapter.getAbsoluteURL()"))
        assertTrue(readMenu.contains("val url = tvChapterUrl.tag as? String ?: return@OnClickListener"))
        assertFalse(readMenu.contains("binding.titleBar.title = ReadBook.curTextChapter?.title"))
    }

    @Test
    fun readerNavigationSpacerUsesBookBackground() {
        val activityLayout = parseXml(repoFile("app/src/main/res/layout/activity_book_read.xml"))
        val baseReadActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/BaseReadBookActivity.kt").readText()

        assertEquals("", activityLayout.elementById("navigation_bar").androidAttr("background"))
        assertTrue(baseReadActivity.contains("binding.navigationBar.setBackgroundColor("))
        assertTrue(baseReadActivity.contains("private fun updateNavigationSpacerBackground(color: Int = ReadBookConfig.bgMeanColor)"))
        assertTrue(baseReadActivity.contains("ColorUtils.withAlpha(color, 1f)"))
    }

    @Test
    fun readerNavigationBarAlwaysUsesBookBackgroundWithoutSystemContrastScrim() {
        val baseReadActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/BaseReadBookActivity.kt").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val navBlock = baseReadActivity.substringAfter("override fun upNavigationBarColor()")
            .substringBefore("@SuppressLint(\"RtlHardcoded\")")

        assertTrue(navBlock.contains("updateReaderNavigationBarColor()"))
        assertFalse(navBlock.contains("super.upNavigationBarColor()"))
        assertTrue(baseReadActivity.contains("window.isNavigationBarContrastEnforced = false"))
        assertTrue(baseReadActivity.contains("private fun updateReaderNavigationBarColor()"))
        assertTrue(baseReadActivity.contains("val navigationColor = ReadBookConfig.bgMeanColor"))
        assertTrue(baseReadActivity.contains("setNavigationBarColorAuto(navigationColor)"))
        assertFalse(readMenu.contains("fun navigationBarSpacerColor(): Int"))
        assertFalse(readMenu.contains("bottomTabNavigationSurfaceColor"))
    }

    @Test
    fun readerPageFooterNavigationSpacerDoesNotAddExtraBottomPadding() {
        val pageView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/PageView.kt").readText()
        val viewExtensions = repoFile("app/src/main/java/io/wanjuan/app/utils/ViewExtensions.kt").readText()

        assertTrue(viewExtensions.contains("fun View.applyNavigationBarPadding("))
        assertTrue(pageView.contains("binding.vwNavigationBar.applyNavigationBarPadding(extraPaddingDp = 0)"))
        assertFalse(pageView.contains("binding.vwNavigationBar.applyNavigationBarPadding()"))
    }

    @Test
    fun tocDownloadButtonShowsPendingStateAndRefreshesWhenDownloadFinishes() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val readBook = repoFile("app/src/main/java/io/wanjuan/app/model/ReadBook.kt").readText()
        val cacheBook = repoFile("app/src/main/java/io/wanjuan/app/model/CacheBook.kt").readText()

        assertTrue(readMenu.contains("private val downloadingChapters = mutableSetOf<Int>()"))
        assertTrue(readMenu.contains("val downloading = chapter.index in downloadingChapters"))
        assertTrue(readMenu.contains("context.getString(R.string.downloading)"))
        assertTrue(readMenu.contains("downloadingChapters.add(chapter.index)"))
        assertTrue(readMenu.contains("notifyTocChapterChanged(chapter.index)"))
        assertTrue(readMenu.contains("downloadingChapters.remove(chapter.index)"))
        assertTrue(readBook.contains("download(\n                    downloadScope,\n                    chapter,\n                    resetPageOffset,\n                    success = success"))
        assertTrue(cacheBook.contains("finish: (() -> Unit)? = null"))
        assertTrue(cacheBook.contains("finish?.invoke()"))
    }

    @Test
    fun layoutPanelIncludesFontSamplesWithoutDuplicateFontTitle() {
        val layout = readMenuLayout()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val fontPanel = layout.elementById("panel_layout_font")
        val fontRow = layout.elementById("ll_font_sample_row")

        assertFalse(layoutXml.contains("tv_theme_font_label"))
        assertTrue(fontRow.hasAncestor(fontPanel))
        assertTrue(readMenu.contains("private fun newFontSampleCard(withEndMargin: Boolean = true): ViewReadThemeCardBinding"))
        assertTrue(readMenu.contains("ViewReadThemeCardBinding.inflate("))
        assertTrue(readMenu.contains("binding.llFontSampleRow.addView(card.root, params)"))
        assertTrue(readMenu.contains("private fun currentBackgroundColorFromConfig(): Int?"))
        assertTrue(readMenu.contains("ReadBookConfig.durConfig.curBgDrawable(72.dpToPx(), 72.dpToPx())"))
        assertTrue(readMenu.contains("private fun backgroundColorFromDrawable(drawable: Drawable?): Int?"))
        assertTrue(readMenu.contains("is BitmapDrawable -> backgroundColorFromBitmapDrawable(drawable)"))
        assertTrue(readMenu.contains("private fun backgroundColorFromRenderedDrawable(drawable: Drawable): Int?"))
        assertTrue(readMenu.contains("drawable.draw(canvas)"))
        assertFalse(layoutXml.contains("font_card_source"))
        assertFalse(layoutXml.contains("font_card_add_custom"))
    }

    @Test
    fun interfacePageTurnTabUsesDedicatedPanel() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val pageTurnPanel = layout.elementById("panel_page_turn")

        assertTrue(readMenu.contains("BottomTab.PageTurn"))
        assertTrue(readMenu.contains("R.id.menu_read_page_turn -> BottomTab.PageTurn"))
        assertTrue(readMenu.contains("panelPageTurn.gone(tab != BottomTab.PageTurn)"))
        listOf(
            "hsv_page_anim_cards",
            "ll_page_anim_card_row",
            "panel_page_auto_page",
            "panel_page_volume_key",
            "panel_page_mouse_wheel",
            "panel_page_touch_slop"
        ).forEach { id ->
            assertTrue(layout.elementById(id).hasAncestor(pageTurnPanel))
        }
    }

    @Test
    fun pageTurnPanelUsesAnimationCardsInsteadOfSecondaryMenu() {
        val layout = readMenuLayout()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val pageTurnPanel = layout.elementById("panel_page_turn")

        listOf("hsv_page_anim_cards", "ll_page_anim_card_row").forEach { id ->
            assertTrue(layout.elementById(id).hasAncestor(pageTurnPanel))
        }
        listOf(
            "R.string.btn_default_s, null",
            "R.string.page_anim_cover, PageAnim.coverPageAnim",
            "R.string.page_anim_linked_cover, PageAnim.linkedCoverPageAnim",
            "R.string.page_anim_slide, PageAnim.slidePageAnim",
            "R.string.page_anim_simulation, PageAnim.simulationPageAnim",
            "R.string.page_anim_scroll, PageAnim.scrollPageAnim",
            "R.string.page_anim_none, PageAnim.noAnim"
        ).forEach { sampleConfig ->
            assertTrue(readMenu.contains(sampleConfig))
        }
        assertFalse(layoutXml.contains("android:id=\"@+id/panel_page_anim\""))
        assertFalse(layoutXml.contains("android:id=\"@+id/page_anim_card_"))
        assertTrue(readMenu.contains("private data class PageAnimSample"))
        assertTrue(readMenu.contains("private val pageAnimSampleBindings by lazy"))
        assertTrue(readMenu.contains("private fun newPageAnimSampleCard(withEndMargin: Boolean = true): ViewReadThemeCardBinding"))
        assertTrue(readMenu.contains("ViewReadThemeCardBinding.inflate("))
        assertTrue(readMenu.contains("binding.llPageAnimCardRow.addView(card.root, params)"))
        assertTrue(readMenu.contains("PageAnimPreviewDrawable"))
        assertTrue(readMenu.contains("private fun applyPageAnimSample(anim: Int?)"))
        assertTrue(readMenu.contains("selectedStrokeWidth = 2.dpToPx().toFloat()"))
        assertTrue(readMenu.contains("paint.strokeWidth = if (selected) selectedStrokeWidth else defaultStrokeWidth"))
        assertFalse(readMenu.contains("panelPageAnim.setOnClickListener"))
        assertFalse(readMenu.contains("showPageAnimConfig"))
    }

    @Test
    fun pageAnimCardsApplySelectionToReadViewImmediately() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val applyPageAnimSample = readMenu
            .substringAfter("private fun applyPageAnimSample(anim: Int?) {")
            .substringBefore("\n    private fun updateThemeControlsFromConfig")

        assertTrue(applyPageAnimSample.contains("ReadBookConfig.pageAnim = pageAnim"))
        assertTrue(applyPageAnimSample.contains("ReadBookConfig.save()"))
        assertTrue(applyPageAnimSample.contains("ReadBook.book?.setPageAnim(null)"))
        assertTrue(applyPageAnimSample.contains("ReadBook.saveRead()"))
        assertTrue(applyPageAnimSample.contains("ReadBook.callBack?.upPageAnim(true)"))
    }

    @Test
    fun legacyPageAnimSelectorPersistsBookOverrideImmediately() {
        val activity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/BaseReadBookActivity.kt").readText()
        val selectorBlock = activity.substringAfter("fun showPageAnimConfig(success: () -> Unit) {")
            .substringBefore("\n    fun isPrevKey")

        assertTrue(selectorBlock.contains("ReadBook.book?.setPageAnim(items.getOrNull(i)?.second ?: -1)"))
        assertTrue(selectorBlock.contains("ReadBook.saveRead()"))
        assertTrue(selectorBlock.contains("success()"))
    }

    @Test
    fun backgroundPanelShowsImageSamplesAndContinuousControls() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val readActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        val backgroundPanel = layout.elementById("panel_background")
        val colorCard = layout.elementByTag("background_card_color")
        val backgroundCards = listOf(
            "background_card_beach",
            "background_card_night",
            "background_card_green",
            "background_card_parchment",
            "background_card_fresh",
            "background_card_palace",
            "background_card_canvas",
            "background_card_landscape",
            "background_card_bright"
        ).map { id -> layout.elementById(id) }
            .plus(colorCard)

        backgroundCards.forEach { card ->
            assertTrue(card.getAttribute("layout").contains("view_read_background_card"))
            assertTrue(card.hasAncestor(backgroundPanel))
        }
        assertTrue(colorCard.isBefore(layout.elementById("background_card_beach")))
        assertTrue(layout.elementById("seek_background_brightness").hasAncestor(backgroundPanel))
        assertTrue(layout.elementById("seek_background_saturation").hasAncestor(backgroundPanel))
        assertTrue(layout.elementById("seek_background_alpha").hasAncestor(backgroundPanel))
        assertTrue(readMenu.contains("午后沙滩.jpg"))
        assertTrue(readMenu.contains("羊皮纸4.jpg"))
        assertTrue(readMenu.contains("ReadBookConfig.bgAlpha"))
        assertTrue(readMenu.contains("bindBackgroundColorCard("))
        assertTrue(readMenu.contains("binding.llBackgroundImageRow.getChildAt(0)"))
        assertTrue(readMenu.contains("ReadBookConfig.durConfig.setCurBg(0"))
        assertTrue(readMenu.contains("setDialogId(BG_COLOR)"))
        assertTrue(readMenu.contains("context.accentColor"))
        assertTrue(readActivity.contains("BG_COLOR ->"))
        assertTrue(readActivity.contains("binding.readMenu.reset()"))
    }

    @Test
    fun backgroundCardsUseThemeBorderForSelectedState() {
        val cardLayout = parseXml(
            findRepoFile("app/src/main/res/layout/view_read_background_card.xml")
                ?: findRepoFile("src/main/res/layout/view_read_background_card.xml")
                ?: error("view_read_background_card.xml not found")
        )
        val preview = cardLayout.elementById("background_card_preview")
        val border = cardLayout.elementById("background_card_selection_border")
        val check = cardLayout.elementById("iv_background_card_check")
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(border.hasAncestor(preview))
        assertEquals("gone", border.androidAttr("visibility"))
        assertTrue(border.isBefore(check))
        assertTrue(readMenu.contains("backgroundCardSelectionBorder.background = roundedRect"))
        assertTrue(readMenu.contains("backgroundCardSelectionBorder.isVisible = selected"))
        assertTrue(readMenu.contains("if (selected) context.accentColor"))
    }

    @Test
    fun layoutPanelRemovesDuplicatedRegionSpacingAndAddsTipSettings() {
        val layout = readMenuLayout()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val layoutPanel = layout.elementById("panel_layout")

        assertFalse(layoutXml.contains("tv_layout_region_spacing_title"))
        assertFalse(layoutXml.contains("seek_layout_header_spacing"))
        assertFalse(layoutXml.contains("seek_layout_footer_spacing"))
        assertFalse(layoutXml.contains("ll_layout_tip_header_show"))
        assertFalse(layoutXml.contains("ll_layout_tip_footer_show"))
        assertEquals("@string/read_menu_layout_title", layout.elementById("tv_layout_page_margin_title").androidAttr("text"))
        listOf(
            "iv_layout_header_title",
            "iv_layout_footer_title",
            "seek_layout_title_top_spacing",
            "seek_layout_title_bottom_spacing"
        ).forEach { id ->
            assertTrue(layout.elementById(id).hasAncestor(layoutPanel))
        }
        assertTrue(layout.elementById("layout_text_style_entry").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("seek_theme_text_size").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("seek_theme_font_weight").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("seek_layout_letter_spacing").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("seek_layout_line_spacing").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("seek_layout_paragraph_spacing").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("tv_layout_tip_color_value").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("tv_layout_tip_divider_color_value").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("tv_layout_header_show_value").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("tv_layout_footer_show_value").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("tv_layout_header_line_toggle").hasAncestor(layoutPanel))
        assertFalse(layout.elementById("tv_layout_footer_line_toggle").hasAncestor(layoutPanel))
        assertTrue(layout.elementById("tv_layout_header_show_value")
            .hasAncestor(layout.elementById("layout_margin_adjust_preview_host")))
        assertEquals("44dp", layout.elementById("tv_layout_header_title").androidAttr("minWidth"))
        assertEquals("wrap_content", layout.elementById("tv_layout_header_show_value").androidAttr("layout_width"))
        assertTrue(layout.elementById("tv_layout_header_line_toggle")
            .hasAncestor(layout.elementById("layout_margin_adjust_preview_host")))
        assertEquals("86dp", layout.elementById("tv_layout_header_line_toggle").androidAttr("layout_width"))
        assertTrue(layout.elementById("tv_layout_footer_show_value")
            .hasAncestor(layout.elementById("layout_margin_adjust_preview_host")))
        assertEquals("44dp", layout.elementById("tv_layout_footer_title").androidAttr("minWidth"))
        assertEquals("wrap_content", layout.elementById("tv_layout_footer_show_value").androidAttr("layout_width"))
        assertTrue(layout.elementById("tv_layout_footer_line_toggle")
            .hasAncestor(layout.elementById("layout_margin_adjust_preview_host")))
        assertEquals("86dp", layout.elementById("tv_layout_footer_line_toggle").androidAttr("layout_width"))
        assertTrue(readMenu.contains("read_menu_display_auto"))
        assertTrue(readMenu.contains("ReadTipConfig.headerMode"))
        assertTrue(readMenu.contains("ReadBookConfig.titleSize"))
    }

    @Test
    fun readMenuSeekBarsUseRealtimeConfigAndRingThumb() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val seekBar = repoFile("app/src/main/java/io/wanjuan/app/lib/theme/view/ThemeSeekBar.kt").readText()

        assertTrue(seekBar.contains("buildRingThumb"))
        assertTrue(seekBar.contains("Color.WHITE"))
        assertTrue(readMenu.contains("if (fromUser) onStop(progress)"))
        assertFalse(readMenu.contains("override fun onStopTrackingTouch(seekBar: SeekBar) {\n                onStop(seekBar.progress)"))
    }

    @Test
    fun tocPanelMetadataStaysSeparateFromReaderProgressControls() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val tocPanel = layout.elementById("panel_toc")
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()

        listOf(
            "toc_progress_panel",
            "toc_progress_mode_toggle",
            "tv_toc_prev_chapter",
            "seek_toc_progress",
            "tv_toc_next_chapter"
        ).forEach { id ->
            assertFalse(layoutXml.contains("@+id/$id"))
            assertFalse(readMenu.contains(id.camelCaseBindingName()))
        }
        assertFalse(layoutXml.contains("@drawable/bg_read_menu_toc_progress_chip"))
        assertFalse(readMenu.contains("tocProgressWholeBook"))
        assertFalse(readMenu.contains("fun upSeekBar()"))
        assertFalse(readMenu.contains("fun setSeekPage("))
        assertFalse(readMenu.contains("\"${'$'}{chapter.index + 1}. ${'$'}{chapter.title}\""))
        assertTrue(readMenu.contains("chapter.tag"))
        assertTrue(readMenu.contains("chapter.wordCount"))
        assertTrue(readMenu.contains("BookHelp.hasContent"))
        assertTrue(readMenu.contains("ic_lucide_download"))
    }

    @Test
    fun tocPanelCurrentChapterUsesSelectedBackgroundInsteadOfAccentText() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val tocAdapter = readMenu.substringAfter("private class ReadMenuTocAdapter(")
            .substringBefore("private class ReadMenuBookmarkAdapter(")

        assertTrue(tocAdapter.contains("holder.itemView.background = if (selected) tocSelectedBackground(context) else null"))
        assertTrue(tocAdapter.contains("private fun tocSelectedBackground(context: Context): GradientDrawable"))
        assertTrue(tocAdapter.contains("selected -> Color.WHITE"))
        assertFalse(tocAdapter.contains("selected -> context.accentColor"))
        assertFalse(tocAdapter.contains("selected -> ColorUtils.adjustAlpha(context.accentColor"))
        assertFalse(tocAdapter.contains("if (selected) context.accentColor else holder.title.currentTextColor"))
    }

    @Test
    fun chapterProgressMinimapSitsOnReadingSurfaceAndUsesCurrentChapterContentOnlyWhenMenuShows() {
        val layout = readActivityLayout()
        val readActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        val readBook = repoFile("app/src/main/java/io/wanjuan/app/model/ReadBook.kt").readText()
        val readView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/ReadView.kt").readText()
        val pageView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/PageView.kt").readText()
        val contentTextView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/ContentTextView.kt").readText()
        val minimapView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ChapterProgressMinimapView.kt").readText()
        val textChapter = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/entities/TextChapter.kt").readText()
        val chapterProvider = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/provider/ChapterProvider.kt").readText()
        val textChapterLayout = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/provider/TextChapterLayout.kt").readText()
        val minimapPanel = layout.elementById("chapter_progress_minimap_panel")
        val minimap = layout.elementById("chapter_progress_minimap")
        val minimapControls = layout.elementById("chapter_progress_minimap_controls")
        val previousButton = layout.elementById("btn_chapter_minimap_previous")
        val nextButton = layout.elementById("btn_chapter_minimap_next")
        val previousText = layout.elementById("tv_chapter_minimap_previous")
        val nextText = layout.elementById("tv_chapter_minimap_next")
        val readContent = layout.elementById("read_content_container")
        val readMenu = layout.elementById("read_menu")
        val searchMenu = layout.elementById("search_menu")

        assertEquals("LinearLayout", minimapPanel.tagName)
        assertEquals("68dp", minimapPanel.androidAttr("layout_width"))
        assertEquals("wrap_content", minimapPanel.androidAttr("layout_height"))
        assertEquals("end|top", minimapPanel.androidAttr("layout_gravity"))
        assertEquals("8dp", minimapPanel.androidAttr("layout_marginEnd"))
        assertEquals("gone", minimapPanel.androidAttr("visibility"))
        assertEquals("io.wanjuan.app.ui.book.read.ChapterProgressMinimapView", minimap.tagName)
        assertEquals("56dp", minimap.androidAttr("layout_width"))
        assertEquals("300dp", minimap.androidAttr("layout_height"))
        assertEquals("true", minimap.androidAttr("clickable"))
        assertEquals("@string/chapter_progress_minimap", minimap.androidAttr("contentDescription"))
        assertTrue(minimap.hasAncestor(minimapPanel))
        assertTrue(minimap.isBefore(minimapControls))
        assertEquals("vertical", minimapControls.androidAttr("orientation"))
        assertEquals("68dp", minimapControls.androidAttr("layout_width"))
        assertEquals("wrap_content", minimapControls.androidAttr("layout_height"))
        listOf(previousButton, nextButton).forEach { button ->
            assertEquals("FrameLayout", button.tagName)
            assertEquals("68dp", button.androidAttr("layout_width"))
            assertEquals("32dp", button.androidAttr("layout_height"))
            assertEquals("true", button.androidAttr("clickable"))
            assertEquals("true", button.androidAttr("focusable"))
            assertTrue(button.hasAncestor(minimapControls))
        }
        assertEquals("@string/previous_chapter", previousButton.androidAttr("contentDescription"))
        assertEquals("@string/previous_chapter", previousText.androidAttr("text"))
        assertEquals("@color/white", previousText.androidAttr("textColor"))
        assertEquals("12sp", previousText.androidAttr("textSize"))
        assertEquals("center", previousText.androidAttr("gravity"))
        assertEquals("1", previousText.androidAttr("maxLines"))
        assertTrue(previousText.hasAncestor(previousButton))
        assertEquals("@string/next_chapter", nextButton.androidAttr("contentDescription"))
        assertEquals("@string/next_chapter", nextText.androidAttr("text"))
        assertEquals("@color/white", nextText.androidAttr("textColor"))
        assertEquals("12sp", nextText.androidAttr("textSize"))
        assertEquals("center", nextText.androidAttr("gravity"))
        assertEquals("1", nextText.androidAttr("maxLines"))
        assertTrue(nextText.hasAncestor(nextButton))
        assertTrue(layout.elementById("chapter_minimap_previous_glass_view").hasAncestor(previousButton))
        assertTrue(layout.elementById("chapter_minimap_previous_shell_overlay").hasAncestor(previousButton))
        assertTrue(layout.elementById("chapter_minimap_next_glass_view").hasAncestor(nextButton))
        assertTrue(layout.elementById("chapter_minimap_next_shell_overlay").hasAncestor(nextButton))
        assertTrue(previousButton.isBefore(nextButton))
        assertTrue(readContent.isBefore(minimapPanel))
        assertTrue(readMenu.isBefore(minimapPanel))
        assertTrue(minimapPanel.isBefore(searchMenu))
        assertTrue(readActivity.contains("private fun bindChapterProgressMinimap()"))
        assertTrue(readActivity.contains("private fun setupChapterMinimapControlGlass()"))
        assertFalse(
            readActivity.substringAfter("private fun bindChapterProgressMinimap()")
                .substringBefore("private fun setupChapterMinimapControlGlass()")
                .contains("setupChapterMinimapControlGlass()")
        )
        assertTrue(readActivity.contains("if (!ViewCompat.isLaidOut(binding.readContentContainer) || glassViews.any { !ViewCompat.isLaidOut(it) })"))
        assertTrue(readActivity.contains("binding.chapterProgressMinimapPanel.post"))
        assertTrue(readActivity.contains("binding.chapterMinimapPreviousGlassView"))
        assertTrue(readActivity.contains("binding.chapterMinimapNextGlassView"))
        assertTrue(readActivity.contains("ReaderBottomGlassStyle.configureLiquidGlass"))
        assertTrue(readActivity.contains("ReaderBottomGlassStyle.shell"))
        assertTrue(readActivity.contains("ReaderBottomGlassStyle.fallbackShell"))
        assertTrue(readActivity.contains("binding.btnChapterMinimapPrevious.setOnClickListener"))
        assertTrue(readActivity.contains("ReadBook.moveToPrevChapter(upContent = true, toLast = false)"))
        assertTrue(readActivity.contains("binding.btnChapterMinimapNext.setOnClickListener"))
        assertTrue(readActivity.contains("ReadBook.moveToNextChapter(true)"))
        assertTrue(readActivity.contains("private fun updateChapterProgressMinimap()"))
        assertTrue(readActivity.contains("ReadBook.curTextChapter?.pageSize"))
        assertTrue(readActivity.contains("textChapter.getContent()"))
        assertTrue(readActivity.contains("binding.chapterProgressMinimap.updateChapter("))
        assertTrue(readActivity.contains("ReadBook.durPageIndex"))
        assertFalse(readActivity.contains("AppConfig.progressBarBehavior)"))
        assertFalse(readActivity.contains("chapterProgressDragThrottle"))
        assertFalse(readActivity.contains("chapterProgressDragTargetPage"))
        assertFalse(readActivity.contains("ReadBook.skipToPage(targetPage)"))
        assertTrue(readActivity.contains("private fun previewChapterProgressMinimap(ratio: Float)"))
        assertTrue(readActivity.contains("private fun commitChapterProgressMinimap(ratio: Float)"))
        assertTrue(readActivity.contains("binding.readView.previewChapterProgress(target.pageIndex, target.pageOffset)"))
        assertTrue(readActivity.contains("ReadBook.commitChapterProgressPosition(target.chapterPosition)"))
        assertTrue(readActivity.contains("binding.chapterProgressMinimap.pinProgressRatio(ratio)"))
        assertFalse(readActivity.contains("ReadBook.setPageIndex(target.pageIndex)"))
        assertFalse(readActivity.contains("binding.readView.upContent(resetPageOffset = false)"))
        assertTrue(readActivity.contains("private fun chapterPositionForMinimapOffset(page: TextPage, offsetInPage: Float): Int"))
        assertTrue(readActivity.contains("private fun constrainChapterProgressMinimapPanel(): Boolean"))
        assertTrue(readActivity.contains("binding.chapterProgressMinimapPanel.updateLayoutParams<FrameLayout.LayoutParams>"))
        assertTrue(readActivity.contains("binding.chapterProgressMinimap.updateLayoutParams<ViewGroup.LayoutParams>"))
        assertTrue(readActivity.contains("val preservePanelPosition = binding.chapterProgressMinimap.shouldPreservePanelPosition()"))
        assertTrue(readActivity.contains("if (!preservePanelPosition && !constrainChapterProgressMinimapPanel())"))
        assertTrue(readActivity.contains("val minimapHeight = 300.dpToPx().coerceAtMost(maxMinimapHeight)"))
        assertTrue(readActivity.contains("val panelHeight = minimapHeight + controlsTopMargin + controlsHeight"))
        assertTrue(readActivity.contains("topMargin = centeredMinimapPanelTopMargin(topLimit, availableHeight, panelHeight)"))
        assertTrue(readActivity.contains("private fun centeredMinimapPanelTopMargin(topLimit: Int, availableHeight: Int, panelHeight: Int): Int"))
        assertTrue(readActivity.contains("R.id.title_bar_shell"))
        assertTrue(readActivity.contains("R.id.bottom_menu"))
        assertTrue(readActivity.contains("binding.chapterProgressMinimapPanel.gone(!shouldShow || pageCount <= 1)"))
        assertTrue(readActivity.contains("updateChapterProgressMinimap(show = true)"))
        assertTrue(readActivity.contains("private fun scheduleChapterProgressMinimapStableSync()"))
        assertTrue(readActivity.contains("private var pendingChapterProgressMinimapLayoutSync = false"))
        assertTrue(readActivity.contains("pendingChapterProgressMinimapLayoutSync = true"))
        assertTrue(readActivity.contains("binding.chapterProgressMinimapPanel.post {\n            binding.chapterProgressMinimapPanel.post {"))
        assertTrue(readActivity.contains("!binding.chapterProgressMinimap.shouldPreservePanelPosition()"))
        assertTrue(readActivity.contains("constrainChapterProgressMinimapPanel()"))
        assertTrue(readActivity.contains("override fun onMenuShow() {\n        updateChapterProgressMinimap(show = true)\n        scheduleChapterProgressMinimapStableSync()"))
        val updateMinimapBody = readActivity.substringAfter("private fun updateChapterProgressMinimap(")
            .substringBefore("private fun chapterProgressMinimapMenuChromeReady()")
        assertTrue(updateMinimapBody.contains("if (!chapterProgressMinimapMenuChromeReady())"))
        assertTrue(
            updateMinimapBody.indexOf("if (!chapterProgressMinimapMenuChromeReady())") <
                    updateMinimapBody.indexOf("constrainChapterProgressMinimapPanel()")
        )
        assertTrue(readActivity.contains("override fun onMenuHide() {\n        binding.readView.autoPager.resume()\n        binding.chapterProgressMinimap.clearPinnedProgressRatio()\n        binding.chapterProgressMinimapPanel.gone()"))
        assertTrue(minimapView.contains("private var dragRatio: Float? = null"))
        assertTrue(minimapView.contains("private var pinnedProgressRatio: Float? = null"))
        assertTrue(minimapView.contains("fun clearPinnedProgressRatio()"))
        assertTrue(minimapView.contains("fun pinProgressRatio(progressRatio: Float)"))
        assertTrue(minimapView.contains("fun shouldPreservePanelPosition(): Boolean"))
        assertTrue(minimapView.contains("return isDragging || pinnedProgressRatio != null"))
        assertTrue(minimapView.contains("if (!isDragging && pinnedProgressRatio == null"))
        assertTrue(minimapView.contains("private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop"))
        assertTrue(minimapView.contains("private var hasDragged = false"))
        assertTrue(minimapView.contains("var onProgressChanging: ((progressRatio: Float) -> Unit)? = null"))
        assertTrue(minimapView.contains("fun updateChapter("))
        assertTrue(minimapView.contains("private var chapterText: String"))
        assertTrue(minimapView.contains("StaticLayout.Builder.obtain"))
        assertTrue(minimapView.contains("onProgressChanging?.invoke(ratio)"))
        assertTrue(minimapView.contains("onProgressChanged?.invoke(ratio)"))
        assertTrue(minimapView.contains("pinnedProgressRatio = ratio"))
        assertTrue(minimapView.contains("dragRatio ?: pinnedProgressRatio ?: progressRatio()"))
        assertTrue(minimapView.contains("private var dragThumbTouchOffset = 0f"))
        assertTrue(minimapView.contains("private fun beginDrag(y: Float)"))
        assertTrue(minimapView.contains("if (!beginDrag(event.y))"))
        assertTrue(minimapView.contains("if (!hasDragged) {\n                    finishDrag()\n                    return true\n                }"))
        assertTrue(minimapView.contains("if (!hasDragged && kotlin.math.abs(y - dragStartY) < touchSlop)"))
        assertTrue(minimapView.contains("hasDragged = true"))
        assertTrue(minimapView.contains("val initialRatio = dragRatio ?: pinnedProgressRatio ?: progressRatio()"))
        assertTrue(minimapView.contains("private fun isTouchInsideThumb(y: Float, thumbTop: Float, thumbHeight: Float): Boolean"))
        assertTrue(minimapView.contains("ProgressMinimapDragCalculator.dragTouchOffset("))
        assertTrue(minimapView.contains("ProgressMinimapDragCalculator.ratioForY("))
        assertFalse(minimapView.contains("finished: Boolean"))
        assertFalse(minimapView.contains("finished = false"))
        assertFalse(minimapView.contains("drawRoundRect"))
        assertTrue(minimapView.contains("thumbPaint.color = ColorUtils.setAlphaComponent(Color.WHITE, if (isPressed) 96 else 72)"))
        assertTrue(minimapView.contains("thumbStrokePaint.color = ColorUtils.setAlphaComponent(Color.WHITE, if (isPressed) 236 else 200)"))
        assertFalse(minimapView.contains("context.accentColor"))
        assertFalse(minimapView.contains("val rowCount ="))
        assertTrue(readView.contains("fun previewChapterProgress(pageIndex: Int, pageOffset: Int)"))
        assertTrue(readView.contains("curPage.previewChapterProgress(page, pageOffset)"))
        assertTrue(pageView.contains("fun previewChapterProgress(textPage: TextPage, pageOffset: Int)"))
        assertTrue(pageView.contains("binding.contentTextView.previewChapterProgress(textPage, pageOffset)"))
        assertTrue(contentTextView.contains("fun previewChapterProgress(textPage: TextPage, offset: Int)"))
        assertTrue(contentTextView.contains("private var chapterProgressPreview = false"))
        assertTrue(contentTextView.contains("val drawContinuousPages = callBack.isScroll || chapterProgressPreview"))
        assertTrue(contentTextView.contains("private fun hasRelativePage(relativePos: Int): Boolean"))
        assertTrue(contentTextView.contains("fun setPageOffset(offset: Int)"))
        assertTrue(readBook.contains("fun commitChapterProgressPosition(position: Int"))
        assertTrue(readBook.contains("chapterProgressPageBreak = ChapterProgressPageBreak(durChapterIndex, targetPosition)"))
        assertTrue(readBook.contains("fun chapterProgressPageBreakPosition(chapterIndex: Int): Int?"))
        assertTrue(readBook.contains("loadContent(durChapterIndex, upContent = true, resetPageOffset = true)"))
        assertTrue(textChapter.contains("val forcedPageBreakPosition: Int? = null"))
        assertTrue(chapterProvider.contains("ReadBook.chapterProgressPageBreakPosition(bookChapter.index)"))
        assertTrue(textChapterLayout.contains("private val forcedPageBreakPosition = textChapter.forcedPageBreakPosition"))
        assertTrue(textChapterLayout.contains("private fun shouldApplyForcedPageBreak(textLine: TextLine): Boolean"))
    }

    @Test
    fun mangaProgressMinimapSitsOnMangaSurfaceAndReplacesBottomSeek() {
        val layout = mangaActivityLayout()
        val mangaMenuLayout = parseXml(repoFile("app/src/main/res/layout/view_manga_menu.xml"))
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val mangaMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/MangaMenu.kt").readText()
        val minimapView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/MangaProgressMinimapView.kt").readText()
        val strings = repoFile("app/src/main/res/values/strings.xml").readText()
        val zhStrings = repoFile("app/src/main/res/values-zh/strings.xml").readText()
        val webtoonFrame = layout.elementById("webtoon_frame")
        val mangaMenuView = layout.elementById("manga_menu")
        val minimapPanel = layout.elementById("manga_progress_minimap_panel")
        val minimap = layout.elementById("manga_progress_minimap")
        val minimapControls = layout.elementById("manga_progress_minimap_controls")
        val previousButton = layout.elementById("btn_manga_minimap_previous")
        val currentButton = layout.elementById("btn_manga_minimap_current")
        val nextButton = layout.elementById("btn_manga_minimap_next")
        val previousText = layout.elementById("tv_manga_minimap_previous")
        val currentText = layout.elementById("tv_manga_minimap_current")
        val nextText = layout.elementById("tv_manga_minimap_next")
        val loading = layout.elementById("fl_loading")

        assertEquals("LinearLayout", minimapPanel.tagName)
        assertEquals("68dp", minimapPanel.androidAttr("layout_width"))
        assertEquals("wrap_content", minimapPanel.androidAttr("layout_height"))
        assertEquals("end|top", minimapPanel.androidAttr("layout_gravity"))
        assertEquals("8dp", minimapPanel.androidAttr("layout_marginEnd"))
        assertEquals("gone", minimapPanel.androidAttr("visibility"))
        assertEquals("io.wanjuan.app.ui.book.manga.MangaProgressMinimapView", minimap.tagName)
        assertEquals("56dp", minimap.androidAttr("layout_width"))
        assertEquals("wrap_content", minimap.androidAttr("layout_height"))
        assertEquals("true", minimap.androidAttr("clickable"))
        assertEquals("true", minimap.androidAttr("focusable"))
        assertEquals("@string/manga_progress_minimap", minimap.androidAttr("contentDescription"))
        assertTrue(minimap.hasAncestor(minimapPanel))
        assertTrue(minimap.isBefore(minimapControls))
        assertEquals("vertical", minimapControls.androidAttr("orientation"))
        assertEquals("68dp", minimapControls.androidAttr("layout_width"))
        assertEquals("wrap_content", minimapControls.androidAttr("layout_height"))
        listOf(previousButton, currentButton, nextButton).forEach { button ->
            assertEquals("FrameLayout", button.tagName)
            assertEquals("68dp", button.androidAttr("layout_width"))
            assertEquals("32dp", button.androidAttr("layout_height"))
            assertEquals("true", button.androidAttr("clickable"))
            assertEquals("true", button.androidAttr("focusable"))
            assertTrue(button.hasAncestor(minimapControls))
        }
        assertEquals("@string/previous_chapter", previousButton.androidAttr("contentDescription"))
        assertEquals("@string/previous_chapter", previousText.androidAttr("text"))
        assertEquals("@color/white", previousText.androidAttr("textColor"))
        assertEquals("12sp", previousText.androidAttr("textSize"))
        assertEquals("center", previousText.androidAttr("gravity"))
        assertEquals("1", previousText.androidAttr("maxLines"))
        assertTrue(previousText.hasAncestor(previousButton))
        assertEquals("@string/chapter_list", currentButton.androidAttr("contentDescription"))
        assertEquals("@string/chapter", currentText.androidAttr("text"))
        assertEquals("@color/white", currentText.androidAttr("textColor"))
        assertEquals("12sp", currentText.androidAttr("textSize"))
        assertEquals("center", currentText.androidAttr("gravity"))
        assertEquals("1", currentText.androidAttr("maxLines"))
        assertEquals("middle", currentText.androidAttr("ellipsize"))
        assertTrue(currentText.hasAncestor(currentButton))
        assertEquals("@string/next_chapter", nextButton.androidAttr("contentDescription"))
        assertEquals("@string/next_chapter", nextText.androidAttr("text"))
        assertEquals("@color/white", nextText.androidAttr("textColor"))
        assertEquals("12sp", nextText.androidAttr("textSize"))
        assertEquals("center", nextText.androidAttr("gravity"))
        assertEquals("1", nextText.androidAttr("maxLines"))
        assertTrue(nextText.hasAncestor(nextButton))
        assertTrue(layout.elementById("manga_minimap_previous_glass_view").hasAncestor(previousButton))
        assertTrue(layout.elementById("manga_minimap_previous_shell_overlay").hasAncestor(previousButton))
        assertTrue(layout.elementById("manga_minimap_current_glass_view").hasAncestor(currentButton))
        assertTrue(layout.elementById("manga_minimap_current_shell_overlay").hasAncestor(currentButton))
        assertTrue(layout.elementById("manga_minimap_next_glass_view").hasAncestor(nextButton))
        assertTrue(layout.elementById("manga_minimap_next_shell_overlay").hasAncestor(nextButton))
        assertTrue(previousButton.isBefore(currentButton))
        assertTrue(currentButton.isBefore(nextButton))
        assertTrue(webtoonFrame.isBefore(minimapPanel))
        assertTrue(mangaMenuView.isBefore(minimapPanel))
        assertTrue(minimapPanel.isBefore(loading))
        assertTrue(strings.contains("<string name=\"manga_progress_minimap\">Manga progress</string>"))
        assertTrue(zhStrings.contains("<string name=\"manga_progress_minimap\">漫画进度</string>"))

        assertFalse(mangaMenuLayout.elementsByName("io.wanjuan.app.lib.theme.view.ThemeSeekBar")
            .any { it.androidAttr("id") == "@+id/seek_read_page" })
        assertEquals("0dp", mangaMenuLayout.elementById("bottom_menu").androidAttr("layout_height"))
        assertEquals("gone", mangaMenuLayout.elementById("bottom_menu").androidAttr("visibility"))
        assertTrue(runCatching { mangaMenuLayout.elementById("ll_manga_control_pill") }.isFailure)
        assertTrue(runCatching { mangaMenuLayout.elementById("tv_pre") }.isFailure)
        assertTrue(runCatching { mangaMenuLayout.elementById("tv_next") }.isFailure)
        assertFalse(mangaMenu.contains("seekReadPage.setOnSeekBarChangeListener"))
        assertFalse(mangaMenu.contains("fun upSeekBar("))
        assertFalse(mangaMenu.contains("fun skipToPage(index: Int)"))
        assertFalse(mangaMenu.contains("tvPre"))
        assertFalse(mangaMenu.contains("tvNext"))
        assertFalse(mangaMenu.contains("bottomMenu.visible()"))
        assertFalse(mangaMenu.contains("bottomMenu.startAnimation"))
        assertFalse(mangaActivity.contains("binding.mangaMenu.upSeekBar"))

        assertTrue(mangaActivity.contains("private fun bindMangaProgressMinimap()"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimap.onProgressChanging = ::previewMangaProgressMinimap"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimap.onProgressChanged = ::commitMangaProgressMinimap"))
        assertTrue(mangaActivity.contains("private fun setupMangaMinimapControlGlass()"))
        assertFalse(
            mangaActivity.substringAfter("private fun bindMangaProgressMinimap()")
                .substringBefore("private fun setupMangaMinimapControlGlass()")
                .contains("setupMangaMinimapControlGlass()")
        )
        assertTrue(mangaActivity.contains("if (!ViewCompat.isLaidOut(binding.webtoonFrame) || glassViews.any { !ViewCompat.isLaidOut(it) })"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimapPanel.post"))
        assertTrue(mangaActivity.contains("binding.mangaMinimapPreviousGlassView"))
        assertTrue(mangaActivity.contains("binding.mangaMinimapCurrentGlassView"))
        assertTrue(mangaActivity.contains("binding.mangaMinimapNextGlassView"))
        assertTrue(mangaActivity.contains("ReaderBottomGlassStyle.configureLiquidGlass"))
        assertTrue(mangaActivity.contains("ReaderBottomGlassStyle.shell"))
        assertTrue(mangaActivity.contains("ReaderBottomGlassStyle.fallbackShell"))
        assertTrue(mangaActivity.contains("binding.btnMangaMinimapPrevious.setMinimapChapterNavigationClickListener"))
        assertTrue(mangaActivity.contains("ReadManga.moveToPrevChapter(true)"))
        assertTrue(mangaActivity.contains("binding.btnMangaMinimapCurrent.setMinimapChapterNavigationClickListener"))
        assertTrue(mangaActivity.contains("openMangaCatalog()"))
        assertTrue(mangaActivity.contains("binding.btnMangaMinimapNext.setMinimapChapterNavigationClickListener"))
        assertTrue(mangaActivity.contains("ReadManga.moveToNextChapter(true)"))
        assertTrue(mangaActivity.contains("private fun updateMangaProgressMinimap(show: Boolean = binding.mangaMenu.isVisible)"))
        assertTrue(mangaActivity.contains("val imageUrls = currentMangaImageUrls()"))
        assertTrue(mangaActivity.contains("val progressRatio = currentMangaScrollProgressRatio()"))
        assertTrue(mangaActivity.contains("updateMangaMinimapCurrentChapterButton()"))
        assertTrue(mangaActivity.contains("ReadManga.curMangaChapter?.chapter?.title.orEmpty()"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimap.updatePages(imageUrls, ReadManga.book?.origin, ReadManga.durChapterPos, progressRatio)"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimapPanel.gone(!show || pageCount <= 1)"))
        assertTrue(mangaActivity.contains("private fun currentMangaImageUrls(): List<String>"))
        assertTrue(mangaActivity.contains("ReadManga.curMangaChapter?.pages?.filterIsInstance<MangaPage>()"))
        assertTrue(mangaActivity.contains("private fun previewMangaProgressMinimap(ratio: Float)"))
        assertTrue(mangaActivity.contains("private fun commitMangaProgressMinimap(ratio: Float)"))
        assertTrue(mangaActivity.contains("private fun scrollToMangaProgress(ratio: Float, commit: Boolean)"))
        assertTrue(mangaActivity.contains("ReadManga.saveRead(true)"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimap.pinProgressRatio(ratio)"))
        assertTrue(mangaActivity.contains("updateMangaProgressMinimap(menuIsVisible)"))
        assertTrue(mangaActivity.contains("private fun constrainMangaProgressMinimapPanel(): Boolean"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimapPanel.updateLayoutParams<FrameLayout.LayoutParams>"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimap.setMaxAvailableHeight"))
        assertTrue(mangaActivity.contains("val preservePanelPosition = binding.mangaProgressMinimap.shouldPreservePanelPosition()"))
        assertTrue(mangaActivity.contains("if (!preservePanelPosition && !constrainMangaProgressMinimapPanel())"))
        val updateMangaMinimapBody = mangaActivity.substringAfter("private fun updateMangaProgressMinimap(")
            .substringBefore("private fun mangaProgressMinimapMenuChromeReady()")
        assertTrue(updateMangaMinimapBody.contains("if (!mangaProgressMinimapMenuChromeReady())"))
        assertTrue(
            updateMangaMinimapBody.indexOf("if (!mangaProgressMinimapMenuChromeReady())") <
                    updateMangaMinimapBody.indexOf("constrainMangaProgressMinimapPanel()")
        )
        assertTrue(mangaActivity.contains("val minimapHeight = binding.mangaProgressMinimap.desiredHeightWithin(maxMinimapHeight)"))
        assertTrue(mangaActivity.contains("val panelHeight = minimapHeight + controlsTopMargin + controlsHeight"))
        assertTrue(mangaActivity.contains("topMargin = centeredMinimapPanelTopMargin(topLimit, availableHeight, panelHeight)"))
        assertTrue(mangaActivity.contains("private fun centeredMinimapPanelTopMargin(topLimit: Int, availableHeight: Int, panelHeight: Int): Int"))
        assertTrue(mangaActivity.contains("R.id.title_bar_shell"))
        assertTrue(mangaActivity.contains("R.id.bottom_menu"))
        assertTrue(mangaActivity.contains("private fun scheduleMangaProgressMinimapStableSync()"))
        assertTrue(mangaActivity.contains("private var pendingMangaProgressMinimapLayoutSync = false"))
        assertTrue(mangaActivity.contains("pendingMangaProgressMinimapLayoutSync = true"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimapPanel.post {\n            binding.mangaProgressMinimapPanel.post {"))
        assertTrue(mangaActivity.contains("!binding.mangaProgressMinimap.shouldPreservePanelPosition()"))
        assertTrue(mangaActivity.contains("constrainMangaProgressMinimapPanel()"))
        assertTrue(mangaActivity.contains("updateMangaProgressMinimap(menuIsVisible)\n        if (menuIsVisible) {\n            scheduleMangaProgressMinimapStableSync()"))

        assertTrue(minimapView.contains("var onProgressChanging: ((progressRatio: Float) -> Unit)? = null"))
        assertTrue(minimapView.contains("var onProgressChanged: ((progressRatio: Float) -> Unit)? = null"))
        assertTrue(minimapView.contains("fun updatePages(imageUrls: List<String>, sourceOrigin: String?, progress: Int, progressRatio: Float?)"))
        assertTrue(minimapView.contains("fun updateProgress(pageCount: Int, progress: Int, progressRatio: Float?)"))
        assertTrue(minimapView.contains("fun setMaxAvailableHeight(maxHeightPx: Int)"))
        assertTrue(minimapView.contains("fun desiredHeightWithin(maxHeightPx: Int): Int"))
        assertTrue(minimapView.contains("private var maxAvailableHeightPx"))
        assertTrue(minimapView.contains("coerceAtMost(maxAvailableHeightPx)"))
        assertTrue(minimapView.contains("override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)"))
        assertTrue(minimapView.contains("private fun desiredHeightForPageCount(): Int"))
        assertTrue(minimapView.contains("MINIMAP_MIN_HEIGHT_DP"))
        assertTrue(minimapView.contains("MINIMAP_MAX_HEIGHT_DP"))
        assertTrue(minimapView.contains("MINIMAP_PAGE_HEIGHT_DP"))
        assertTrue(minimapView.contains("requestLayout()"))
        assertTrue(minimapView.contains("private val thumbnailDrawables = mutableMapOf<Int, Drawable>()"))
        assertTrue(minimapView.contains("private val thumbnailTargets = mutableMapOf<Int, CustomTarget<Drawable>>()"))
        assertTrue(minimapView.contains("BookCover.loadManga("))
        assertTrue(minimapView.contains("private fun loadThumbnail(index: Int, url: String)"))
        assertTrue(minimapView.contains("private fun drawPageThumbnail(canvas: Canvas, index: Int)"))
        assertTrue(minimapView.contains("private fun clearThumbnailTargets()"))
        assertTrue(minimapView.contains("override fun onDetachedFromWindow()"))
        assertTrue(minimapView.contains("private var pinnedProgressRatio: Float? = null"))
        assertTrue(minimapView.contains("fun pinProgressRatio(progressRatio: Float)"))
        assertTrue(minimapView.contains("fun shouldPreservePanelPosition(): Boolean"))
        assertTrue(minimapView.contains("return isDragging || pinnedProgressRatio != null"))
        assertTrue(minimapView.contains("if (!isDragging && pinnedProgressRatio == null"))
        assertTrue(minimapView.contains("private var dragThumbTouchOffset = 0f"))
        assertTrue(minimapView.contains("private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop"))
        assertTrue(minimapView.contains("private var hasDragged = false"))
        assertTrue(minimapView.contains("private fun beginDrag(y: Float)"))
        assertTrue(minimapView.contains("if (!beginDrag(event.y))"))
        assertTrue(minimapView.contains("if (!hasDragged) {\n                    finishDrag()\n                    return true\n                }"))
        assertTrue(minimapView.contains("if (!hasDragged && kotlin.math.abs(y - dragStartY) < touchSlop)"))
        assertTrue(minimapView.contains("hasDragged = true"))
        assertTrue(minimapView.contains("val initialRatio = dragRatio ?: pinnedProgressRatio ?: progressRatio()"))
        assertTrue(minimapView.contains("private fun isTouchInsideThumb(y: Float, thumbTop: Float, thumbHeight: Float): Boolean"))
        assertTrue(minimapView.contains("pinnedProgressRatio = ratio"))
        assertTrue(minimapView.contains("dragRatio ?: pinnedProgressRatio ?: progressRatio()"))
        assertTrue(minimapView.contains("ProgressMinimapDragCalculator.dragTouchOffset("))
        assertTrue(minimapView.contains("ProgressMinimapDragCalculator.ratioForY("))
        assertTrue(minimapView.contains("onProgressChanging?.invoke(ratio)"))
        assertTrue(minimapView.contains("onProgressChanged?.invoke(ratio)"))
        assertTrue(minimapView.contains("drawPageStrip(canvas)"))
        assertFalse(minimapView.contains("drawRoundRect"))
    }

    @Test
    fun mangaProgressMinimapCommitReloadsTargetPageAfterPreviewScroll() {
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val commitBody = mangaActivity.substringAfter("private fun commitMangaProgressMinimap(ratio: Float)")
            .substringBefore("private fun scrollToMangaProgress")
        val reloadBody = mangaActivity.substringAfter("private fun reloadCommittedMangaProgressPage()")
            .substringBefore("private fun reloadMangaProgressPageIfCurrent")

        assertTrue(commitBody.contains("scrollToMangaProgress(ratio, commit = true)"))
        assertTrue(commitBody.contains("reloadCommittedMangaProgressPage()"))
        assertTrue(mangaActivity.contains("private fun reloadCommittedMangaProgressPage()"))
        assertTrue(mangaActivity.contains("val targetChapterIndex = ReadManga.durChapterIndex"))
        assertTrue(mangaActivity.contains("val targetPage = ReadManga.durChapterPos"))
        assertTrue(
            reloadBody.contains(
                "binding.recyclerView.post {\n" +
                        "            if (ReadManga.durChapterIndex != targetChapterIndex || ReadManga.durChapterPos != targetPage) {\n" +
                        "                return@post\n" +
                        "            }\n" +
                        "            val itemPos = adapterPositionForMangaPage(targetPage)"
            )
        )
        assertTrue(mangaActivity.contains("if (itemPos > -1 && mAdapter.getItem(itemPos) is MangaPage)"))
        assertTrue(mangaActivity.contains("mAdapter.notifyItemChanged(itemPos)"))
    }

    @Test
    fun mangaProgressMinimapPreviewScrollsWithinCurrentChapterPixelsWhileCommitSavesProgress() {
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val minimapView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/MangaProgressMinimapView.kt").readText()
        val progressScrollBody = mangaActivity.substringAfter("private fun scrollToMangaProgress(ratio: Float, commit: Boolean)")
            .substringBefore("private data class MangaChapterScrollTarget")
        val continuousScrollBody = mangaActivity.substringAfter("private fun scrollMangaBodyToProgressRatio(")
            .substringBefore("private fun scrollMangaBodyToChapterBoundaryIfNeeded")
        val progressUpdateBody = mangaActivity.substringAfter("private fun syncMangaProgressAfterScroll(commit: Boolean, targetPage: Int? = null)")
            .substringBefore("private fun adapterPositionForMangaPage")

        assertTrue(minimapView.contains("fun clearPinnedProgressRatio()"))
        assertTrue(mangaActivity.contains("binding.mangaProgressMinimap.clearPinnedProgressRatio()"))
        assertTrue(progressScrollBody.contains("binding.recyclerView.stopScroll()"))
        assertTrue(progressScrollBody.contains("val target = scrollMangaBodyToProgressRatio(ratio)"))
        assertTrue(progressScrollBody.contains("syncMangaProgressAfterScroll(commit, target?.pageIndex)"))
        assertFalse(progressScrollBody.contains("mangaProgressMinimapDragScrollRange"))
        assertFalse(progressScrollBody.contains("targetMangaPageForProgress"))
        assertFalse(progressScrollBody.contains("upMangaProgressAfterScroll(targetPage, commit)"))
        assertFalse(progressScrollBody.contains("scrollToMangaPage(targetPage, commit)"))
        assertTrue(continuousScrollBody.contains("val target = targetMangaChapterScrollOffsetForProgress(ratio) ?: return null"))
        assertTrue(continuousScrollBody.contains("scrollToCurrentMangaChapterOffset(target.pageIndex, target.offsetPx)"))
        assertTrue(continuousScrollBody.contains("return target"))
        assertTrue(mangaActivity.contains("private data class MangaChapterScrollTarget("))
        assertTrue(mangaActivity.contains("private fun targetMangaChapterScrollOffsetForProgress(ratio: Float): MangaChapterScrollTarget?"))
        assertFalse(mangaActivity.contains("private fun targetMangaPageForProgress(ratio: Float): Int?"))
        assertFalse(mangaActivity.contains("targetMangaScrollOffsetForProgress("))
        assertFalse(mangaActivity.contains("computeVerticalScrollRange()"))
        assertFalse(progressScrollBody.contains("smoothScrollToPositionWithOffset"))
        assertTrue(progressUpdateBody.contains("val currentPage = targetPage?.let(::currentMangaPageAt)"))
        assertTrue(progressUpdateBody.indexOf("ReadManga.curPageChanged()") > progressUpdateBody.indexOf("if (commit) {"))
        assertTrue(progressUpdateBody.indexOf("ReadManga.saveRead(true)") > progressUpdateBody.indexOf("if (commit) {"))
    }

    @Test
    fun mangaProgressMinimapUsesCurrentChapterScrollRatioInsteadOfRecyclerScrollRange() {
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val minimapView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/MangaProgressMinimapView.kt").readText()
        val webtoonRecyclerView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/recyclerview/WebtoonRecyclerView.kt").readText()
        val updateBody = mangaActivity.substringAfter("private fun updateMangaProgressMinimap(")
            .substringBefore("private fun scheduleMangaProgressMinimapStableSync()")
        val activityRatioBody = mangaActivity.substringAfter("private fun currentMangaScrollProgressRatio(): Float?")
            .substringBefore("private fun currentMangaChapterScrollProgressRatio")
        val activityPageRatioBody = mangaActivity.substringAfter("private fun pageProgressRatio(pageCount: Int, progress: Int): Float")
            .substringBefore("private fun adapterPositionAfterCurrentMangaChapter")
        val progressBody = minimapView.substringAfter("fun updateProgress(")
            .substringBefore("override fun onAttachedToWindow()")
        val ratioBody = minimapView.substringAfter("private fun progressRatio(): Float")
            .substringBefore("private fun updateTrackRect()")
        val viewPageRatioBody = minimapView.substringAfter("private fun pageProgressRatio(pageCount: Int, progress: Int): Float")
            .substringBefore("private fun updateTrackRect()")
        val onScrolledBody = webtoonRecyclerView.substringAfter("override fun onScrolled(dx: Int, dy: Int)")
            .substringBefore("override fun onScrollStateChanged")

        assertTrue(mangaActivity.contains("private fun currentMangaScrollProgressRatio(): Float?"))
        assertTrue(updateBody.contains("val progressRatio = currentMangaScrollProgressRatio()"))
        assertTrue(updateBody.contains("updatePages(imageUrls, ReadManga.book?.origin, ReadManga.durChapterPos, progressRatio)"))
        assertTrue(updateBody.contains("updateProgress(pageCount, ReadManga.durChapterPos, progressRatio)"))
        assertTrue(activityRatioBody.contains("committedMangaProgressMinimapRatio()?.let { return it }"))
        assertTrue(activityRatioBody.contains("val pageCount = currentMangaPageCount()"))
        assertTrue(activityRatioBody.contains("currentMangaChapterScrollProgressRatio()?.let { return it }"))
        assertTrue(activityRatioBody.contains("return pageProgressRatio(pageCount, ReadManga.durChapterPos)"))
        assertTrue(mangaActivity.contains("private fun currentMangaChapterScrollProgressRatio(): Float?"))
        assertFalse(activityRatioBody.contains("currentMangaProgressMinimapScrollRange()"))
        assertFalse(activityRatioBody.contains("computeVerticalScrollOffset()"))
        assertTrue(minimapView.contains("private var scrollProgressRatio: Float? = null"))
        assertTrue(minimapView.contains("fun updatePages(imageUrls: List<String>, sourceOrigin: String?, progress: Int, progressRatio: Float?)"))
        assertTrue(minimapView.contains("fun updateProgress(pageCount: Int, progress: Int, progressRatio: Float?)"))
        assertTrue(progressBody.contains("val safeProgressRatio = progressRatio?.coerceIn(0f, 1f) ?: pageProgressRatio(safePageCount, safeProgress)"))
        assertTrue(progressBody.contains("this.scrollProgressRatio = safeProgressRatio"))
        assertTrue(ratioBody.contains("return scrollProgressRatio ?: pageProgressRatio(pageCount, progress)"))
        assertFalse(ratioBody.contains("progress / (pageCount - 1).toFloat()"))
        assertTrue(activityPageRatioBody.contains("/ pageCount.toFloat()"))
        assertFalse(activityPageRatioBody.contains("(pageCount - 1).toFloat()"))
        assertTrue(viewPageRatioBody.contains("/ pageCount.toFloat()"))
        assertFalse(viewPageRatioBody.contains("(pageCount - 1).toFloat()"))
        assertTrue(onScrolledBody.contains("if (position != NO_POSITION) {"))
        assertTrue(onScrolledBody.contains("if (position != mLastCenterViewPosition)"))
        assertTrue(onScrolledBody.contains("mPreScrollListener?.onPreScrollListener(this, dx, dy, position)"))
        assertFalse(onScrolledBody.contains("position != NO_POSITION && position != mLastCenterViewPosition"))
    }

    @Test
    fun mangaProgressMinimapDragTargetsCurrentChapterOffsetsInsteadOfGlobalScrollRange() {
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val bodyScrollBody = mangaActivity.substringAfter("private fun scrollMangaBodyToProgressRatio(")
            .substringBefore("private fun scrollMangaBodyToChapterBoundaryIfNeeded")
        val progressRatioBody = mangaActivity.substringAfter("private fun currentMangaScrollProgressRatio(): Float?")
            .substringBefore("private fun currentMangaChapterScrollProgressRatio")

        assertTrue(mangaActivity.contains("private fun targetMangaChapterScrollOffsetForProgress(ratio: Float): MangaChapterScrollTarget?"))
        assertTrue(bodyScrollBody.contains("val target = targetMangaChapterScrollOffsetForProgress(ratio) ?: return null"))
        assertTrue(bodyScrollBody.contains("scrollToCurrentMangaChapterOffset(target.pageIndex, target.offsetPx)"))
        assertTrue(bodyScrollBody.contains("return target"))
        assertFalse(mangaActivity.contains("private fun targetMangaPageForProgress(ratio: Float): Int?"))
        assertFalse(bodyScrollBody.contains("targetMangaScrollOffsetForProgress"))
        assertTrue(progressRatioBody.contains("currentMangaChapterScrollProgressRatio()?.let { return it }"))
        assertTrue(progressRatioBody.contains("return pageProgressRatio(pageCount, ReadManga.durChapterPos)"))
        assertFalse(progressRatioBody.contains("currentMangaProgressMinimapScrollRange()"))
    }

    @Test
    fun mangaProgressMinimapDragUsesThumbCenterAsProgressBaseline() {
        val minimapView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/MangaProgressMinimapView.kt").readText()
        val ratioBody = minimapView.substringAfter("private fun ratioForY(y: Float): Float")
            .substringBefore("private fun ratioForThumbCenterY")
        val beginDragBody = minimapView.substringAfter("private fun beginDrag(y: Float): Boolean")
            .substringBefore("private fun isTouchInsideThumb")
        val drawThumbBody = minimapView.substringAfter("private fun drawThumb(canvas: Canvas)")
            .substringBefore("private fun thumbHeight")

        assertTrue(minimapView.contains("private var dragThumbTouchOffset = 0f"))
        assertTrue(beginDragBody.contains("dragThumbTouchOffset = ProgressMinimapDragCalculator.dragTouchOffset("))
        assertTrue(ratioBody.contains("val thumbHeight = thumbHeight(trackRect.height())"))
        assertTrue(ratioBody.contains("val centerY = y - dragThumbTouchOffset + thumbHeight / 2f"))
        assertTrue(ratioBody.contains("return ratioForThumbCenterY(centerY)"))
        assertTrue(minimapView.contains("private fun ratioForThumbCenterY(centerY: Float): Float"))
        assertTrue(minimapView.contains("ProgressMinimapDragCalculator.ratioForTrackY("))
        assertTrue(ratioBody.contains("dragThumbTouchOffset"))
        assertTrue(drawThumbBody.contains("val centerY = thumbCenterYForRatio(ratio)"))
        assertTrue(drawThumbBody.contains("top = centerY - thumbHeight / 2f"))
    }

    @Test
    fun mangaProgressMinimapStartsDraggingBeforeTrackPreviewScroll() {
        val minimapView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/MangaProgressMinimapView.kt").readText()
        val actionDownBody = minimapView.substringAfter("MotionEvent.ACTION_DOWN -> {")
            .substringBefore("MotionEvent.ACTION_MOVE -> {")

        assertTrue(actionDownBody.contains("isDragging = true"))
        assertTrue(
            actionDownBody.indexOf("isDragging = true") <
                    actionDownBody.indexOf("beginDrag(event.y)")
        )
    }

    @Test
    fun mangaProgressMinimapPreviewStaysWithinCurrentChapterPageRange() {
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val preScrollBody = mangaActivity.substringAfter("setPreScrollListener { _, _, _, position ->")
            .substringBefore("binding.webtoonFrame.run")
        val bindBody = mangaActivity.substringAfter("private fun bindMangaProgressMinimap()")
            .substringBefore("private fun setupMangaMinimapControlGlass()")
        val previewBody = mangaActivity.substringAfter("private fun previewMangaProgressMinimap(ratio: Float)")
            .substringBefore("private fun commitMangaProgressMinimap")
        val commitBody = mangaActivity.substringAfter("private fun commitMangaProgressMinimap(ratio: Float)")
            .substringBefore("private fun scrollToMangaProgress")
        val scrollBody = mangaActivity.substringAfter("private fun scrollToMangaProgress(ratio: Float, commit: Boolean)")
            .substringBefore("private data class MangaChapterScrollTarget")
        val bodyScrollBody = mangaActivity.substringAfter("private fun scrollMangaBodyToProgressRatio(")
            .substringBefore("private fun scrollMangaBodyToChapterBoundaryIfNeeded")
        val boundaryBody = mangaActivity.substringAfter("private fun scrollMangaBodyToChapterBoundaryIfNeeded(ratio: Float)")
            .substringBefore("private fun scrollToMangaChapterEnd")
        val chapterEndBody = mangaActivity.substringAfter("private fun scrollToMangaChapterEnd(): Boolean")
            .substringBefore("private fun currentMangaScrollProgressRatio")
        val progressRatioBody = mangaActivity.substringAfter("private fun currentMangaScrollProgressRatio(): Float?")
            .substringBefore("private fun currentMangaChapterScrollProgressRatio")
        val clampBody = mangaActivity.substringAfter("private fun clampMangaProgressMinimapDragWithinCurrentChapter(")
            .substringBefore("private fun syncMangaProgressAfterScroll")
        val touchBody = mangaActivity.substringAfter("setOnTouchListener { _, event ->")
            .substringBefore("false")
        val previousChapterClickBody = bindBody.substringAfter("binding.btnMangaMinimapPrevious.setMinimapChapterNavigationClickListener")
            .substringBefore("binding.btnMangaMinimapNext.setMinimapChapterNavigationClickListener")
        val nextChapterClickBody = bindBody.substringAfter("binding.btnMangaMinimapNext.setMinimapChapterNavigationClickListener")
            .substringBefore("private fun")

        assertFalse(mangaActivity.contains("private data class MangaProgressMinimapDragScrollRange"))
        assertFalse(mangaActivity.contains("mangaProgressMinimapDragScrollRange"))
        assertTrue(mangaActivity.contains("private var committedMangaProgressMinimapRatio: Float? = null"))
        assertTrue(mangaActivity.contains("private var committedMangaProgressMinimapChapterIndex: Int? = null"))
        assertTrue(mangaActivity.contains("private fun rememberCommittedMangaProgressMinimapRatio(ratio: Float)"))
        assertTrue(mangaActivity.contains("private fun clearCommittedMangaProgressMinimapRatio()"))
        assertTrue(previewBody.contains("scrollToMangaProgress(ratio, commit = false)"))
        assertFalse(previewBody.contains("ensureMangaProgressMinimapDragScrollRange()"))
        assertTrue(commitBody.contains("rememberCommittedMangaProgressMinimapRatio(ratio)"))
        assertTrue(scrollBody.contains("val target = scrollMangaBodyToProgressRatio(ratio)"))
        assertTrue(scrollBody.contains("syncMangaProgressAfterScroll(commit, target?.pageIndex)"))
        assertTrue(bodyScrollBody.contains("ratio: Float"))
        assertTrue(bodyScrollBody.contains("scrollMangaBodyToChapterBoundaryIfNeeded(ratio)?.let { return it }"))
        assertTrue(bodyScrollBody.contains("val target = targetMangaChapterScrollOffsetForProgress(ratio) ?: return null"))
        assertTrue(bodyScrollBody.contains("scrollToCurrentMangaChapterOffset(target.pageIndex, target.offsetPx)"))
        assertTrue(bodyScrollBody.contains("return target"))
        assertTrue(mangaActivity.contains("private fun scrollMangaBodyToChapterBoundaryIfNeeded(ratio: Float): MangaChapterScrollTarget?"))
        assertTrue(boundaryBody.contains("progress >= 1f -> {"))
        assertTrue(boundaryBody.contains("if (scrollToMangaChapterEnd())"))
        assertTrue(mangaActivity.contains("private fun scrollToMangaChapterEnd(): Boolean"))
        assertTrue(chapterEndBody.contains("val endBoundaryPosition = adapterPositionAfterCurrentMangaChapter(ReadManga.durChapterIndex)"))
        assertTrue(chapterEndBody.contains("mLayoutManager.scrollToPositionWithOffset(endBoundaryPosition, currentMangaScrollExtent())"))
        assertTrue(mangaActivity.contains("private data class MangaChapterScrollTarget("))
        assertTrue(mangaActivity.contains("private fun targetMangaChapterScrollOffsetForProgress(ratio: Float): MangaChapterScrollTarget?"))
        assertFalse(mangaActivity.contains("private fun targetMangaPageForProgress(ratio: Float): Int?"))
        assertTrue(progressRatioBody.contains("committedMangaProgressMinimapRatio()?.let { return it }"))
        assertTrue(progressRatioBody.contains("currentMangaChapterScrollProgressRatio()?.let { return it }"))
        assertTrue(progressRatioBody.contains("return pageProgressRatio(pageCount, ReadManga.durChapterPos)"))
        assertFalse(mangaActivity.contains("private fun currentMangaProgressMinimapScrollRange()"))
        assertFalse(mangaActivity.contains("private fun ensureMangaProgressMinimapDragScrollRange()"))
        assertTrue(mangaActivity.contains("private fun adapterPositionAfterCurrentMangaChapter(chapterIndex: Int): Int?"))
        assertTrue(mangaActivity.contains("private fun currentMangaScrollExtent(): Int"))
        assertFalse(commitBody.contains("try {"))
        assertFalse(commitBody.contains("finally {"))
        assertTrue(preScrollBody.contains("binding.mangaProgressMinimap.isDraggingProgress()"))
        assertTrue(preScrollBody.contains("committedMangaProgressMinimapRatio() != null"))
        assertTrue(preScrollBody.contains("clampMangaProgressMinimapDragWithinCurrentChapter(item)"))
        assertTrue(preScrollBody.contains("return@setPreScrollListener"))
        assertTrue(mangaActivity.contains("private fun clampMangaProgressMinimapDragWithinCurrentChapter("))
        assertTrue(clampBody.contains("scrollToMangaChapterEnd()"))
        assertTrue(clampBody.contains("scrollToCurrentMangaChapterOffset(0, 0)"))
        assertTrue(clampBody.contains("committedMangaProgressMinimapRatio() != null"))
        assertTrue(touchBody.contains("MotionEvent.ACTION_DOWN"))
        assertTrue(touchBody.contains("clearCommittedMangaProgressMinimapRatio()"))
        assertTrue(previousChapterClickBody.contains("clearCommittedMangaProgressMinimapRatio()"))
        assertTrue(nextChapterClickBody.contains("clearCommittedMangaProgressMinimapRatio()"))
        assertFalse(bindBody.contains("binding.mangaProgressMinimap.onProgressDragFinished"))
        assertFalse(mangaActivity.contains("private fun finishMangaProgressMinimapDrag()"))
    }

    @Test
    fun mangaProgressMinimapReloadsCurrentPageWhenThumbnailFinishesLoading() {
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()
        val minimapView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/MangaProgressMinimapView.kt").readText()
        val bindBody = mangaActivity.substringAfter("private fun bindMangaProgressMinimap()")
            .substringBefore("private fun setupMangaMinimapControlGlass()")

        assertTrue(minimapView.contains("var onThumbnailReady: ((pageIndex: Int, imageUrl: String) -> Unit)? = null"))
        assertTrue(minimapView.contains("onThumbnailReady?.invoke(index, url)"))
        assertTrue(bindBody.contains("binding.mangaProgressMinimap.onThumbnailReady = ::reloadMangaProgressPageIfCurrent"))
        assertTrue(mangaActivity.contains("private fun reloadMangaProgressPageIfCurrent(pageIndex: Int, imageUrl: String)"))
        assertTrue(mangaActivity.contains("val targetChapterIndex = ReadManga.durChapterIndex"))
        assertTrue(
            mangaActivity.contains(
                "binding.recyclerView.post {\n" +
                        "            if (ReadManga.durChapterIndex != targetChapterIndex || ReadManga.durChapterPos != pageIndex) {\n" +
                        "                return@post\n" +
                        "            }\n" +
                        "            if (currentMangaImageUrlAt(pageIndex) != imageUrl) {\n" +
                        "                return@post\n" +
                        "            }\n" +
                        "            reloadMangaProgressPage(pageIndex)"
            )
        )
        assertTrue(mangaActivity.contains("val holder = binding.recyclerView.findViewHolderForAdapterPosition(itemPos) as? MangaAdapter.PageViewHolder"))
        assertTrue(mangaActivity.contains("if (holder?.binding?.flProgress?.isVisible == false)"))
    }

    @Test
    fun mangaProgressMinimapThumbnailsDoNotQueueWholeChapterBeforeBodyImage() {
        val minimapView = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/MangaProgressMinimapView.kt").readText()
        val mangaViewHolder = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/recyclerview/MangaVH.kt").readText()
        val mangaAdapter = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/recyclerview/MangaAdapter.kt").readText()

        assertTrue(minimapView.contains("private val thumbnailLoadingIndexes = mutableSetOf<Int>()"))
        assertTrue(minimapView.contains("private const val MAX_THUMBNAIL_REQUESTS"))
        assertTrue(minimapView.contains("val remainingSlots = MAX_THUMBNAIL_REQUESTS - thumbnailLoadingIndexes.size"))
        assertTrue(minimapView.contains("sortedWith(compareBy<Int> { kotlin.math.abs(it - progress) }"))
        assertTrue(minimapView.contains(".take(remainingSlots)"))
        assertTrue(minimapView.contains("thumbnailLoadingIndexes.remove(index)"))
        assertTrue(minimapView.contains("maybeLoadThumbnails()"))
        assertTrue(minimapView.contains("prioritizeCurrentThumbnail()"))
        assertTrue(minimapView.contains("cancelThumbnailTarget(index)"))
        assertTrue(minimapView.contains("!isShown"))
        assertTrue(minimapView.contains(".priority(Priority.LOW)"))
        assertFalse(minimapView.contains("imageUrls.forEachIndexed { index, url ->"))
        assertTrue(mangaViewHolder.contains(".priority(Priority.IMMEDIATE)"))
        assertTrue(mangaAdapter.contains(".priority(Priority.LOW)"))
    }

    @Test
    fun expandedReaderPanelHidesChapterProgressMinimapUntilDismissed() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val readActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()

        assertTrue(readMenu.contains("val isExpandedPanelVisible: Boolean"))
        assertTrue(readMenu.contains("callBack.onReadMenuExpandedPanelVisibilityChanged(true)"))
        assertTrue(readMenu.contains("callBack.onReadMenuExpandedPanelVisibilityChanged(false)"))
        assertTrue(readMenu.contains("fun onReadMenuExpandedPanelVisibilityChanged(isVisible: Boolean)"))
        assertTrue(readActivity.contains("override fun onReadMenuExpandedPanelVisibilityChanged(isVisible: Boolean)"))
        assertTrue(readActivity.contains("val shouldShow = show && !binding.readMenu.isExpandedPanelVisible"))
        assertTrue(readActivity.contains("binding.chapterProgressMinimapPanel.gone(!shouldShow || pageCount <= 1)"))
        assertTrue(
            readActivity.contains(
                "if (isVisible) {\n" +
                        "            binding.chapterProgressMinimapPanel.gone()\n" +
                        "        } else {\n" +
                        "            updateChapterProgressMinimap(show = binding.readMenu.isVisible)\n" +
                        "        }"
            )
        )
    }

    @Test
    fun minimapControlGlassWaitsForLaidOutViewsBeforeConfiguringLiquidGlass() {
        val glassStyle = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReaderBottomGlassStyle.kt").readText()

        assertTrue(glassStyle.contains("import androidx.core.view.ViewCompat"))
        assertTrue(glassStyle.contains("): Boolean {"))
        assertTrue(glassStyle.contains("if (!ViewCompat.isLaidOut(target) || !ViewCompat.isLaidOut(liquidGlassView)) {\n            return false\n        }"))
        assertTrue(glassStyle.contains("return true"))
    }

    @Test
    fun readerGlassChromeUsesMoreTransparentAlphaRecipe() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val mangaMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/MangaMenu.kt").readText()
        val minimapButtons = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReaderBottomGlassStyle.kt").readText()

        listOf(readMenu, mangaMenu, minimapButtons).forEach(::assertMoreTransparentGlassRecipe)
    }

    @Test
    fun minimapChapterButtonsUsePressedBackgroundFeedbackWithoutClippingScale() {
        val readLayout = readActivityLayout()
        val mangaLayout = mangaActivityLayout()
        val feedback = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/MinimapChapterButtonFeedback.kt").readText()
        val readActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        val mangaActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/manga/ReadMangaActivity.kt").readText()

        listOf(
            readLayout.elementById("chapter_progress_minimap_panel"),
            readLayout.elementById("chapter_progress_minimap_controls"),
            mangaLayout.elementById("manga_progress_minimap_panel"),
            mangaLayout.elementById("manga_progress_minimap_controls")
        ).forEach { container ->
            assertEquals("false", container.androidAttr("clipChildren"))
            assertEquals("false", container.androidAttr("clipToPadding"))
        }

        listOf(
            readLayout.elementById("btn_chapter_minimap_previous"),
            readLayout.elementById("btn_chapter_minimap_next"),
            mangaLayout.elementById("btn_manga_minimap_previous"),
            mangaLayout.elementById("btn_manga_minimap_current"),
            mangaLayout.elementById("btn_manga_minimap_next")
        ).forEach { button ->
            assertEquals("false", button.androidAttr("clipChildren"))
            assertEquals("false", button.androidAttr("clipToPadding"))
            assertEquals("false", button.androidAttr("clipToOutline"))
        }

        assertFalse(readActivity.contains("button.clipToOutline = true"))
        assertFalse(mangaActivity.contains("button.clipToOutline = true"))
        assertTrue(readActivity.contains("button.clipToOutline = false"))
        assertTrue(mangaActivity.contains("button.clipToOutline = false"))
        assertTrue(feedback.contains("const val MINIMAP_CHAPTER_BUTTON_PRESSED_SCALE = 1.08f"))
        assertTrue(feedback.contains("fun ViewGroup.setMinimapChapterNavigationClickListener"))
        assertTrue(feedback.contains("setOnTouchListener"))
        assertTrue(feedback.contains("MotionEvent.ACTION_DOWN"))
        assertTrue(feedback.contains("MotionEvent.ACTION_UP"))
        assertTrue(feedback.contains("MotionEvent.ACTION_CANCEL"))
        assertTrue(feedback.contains("applyMinimapChapterButtonPressedFeedback(label)"))
        assertTrue(feedback.contains("clearMinimapChapterButtonPressedFeedback(label)"))
        assertTrue(feedback.contains("context.accentColor"))
        assertTrue(feedback.contains("scaleX = MINIMAP_CHAPTER_BUTTON_PRESSED_SCALE"))
        assertTrue(feedback.contains("scaleY = MINIMAP_CHAPTER_BUTTON_PRESSED_SCALE"))
        assertTrue(feedback.contains("overlay.alpha = MINIMAP_CHAPTER_BUTTON_OVERLAY_MAX_ALPHA"))
        assertTrue(feedback.contains("addView(this, insertIndex, params)"))
        assertFalse(feedback.contains("label.setTextColor(context.accentColor)"))
        assertFalse(feedback.contains("androidx.core.graphics.ColorUtils.blendARGB"))
        assertTrue(
            feedback.indexOf("val insertIndex = indexOfChild(label).takeIf { it >= 0 } ?: childCount") <
                feedback.indexOf("addView(this, insertIndex, params)")
        )

        assertTrue(readActivity.contains("binding.btnChapterMinimapPrevious.setMinimapChapterNavigationClickListener(binding.tvChapterMinimapPrevious)"))
        assertTrue(readActivity.contains("binding.btnChapterMinimapNext.setMinimapChapterNavigationClickListener(binding.tvChapterMinimapNext)"))
        assertTrue(mangaActivity.contains("binding.btnMangaMinimapPrevious.setMinimapChapterNavigationClickListener(binding.tvMangaMinimapPrevious)"))
        assertTrue(mangaActivity.contains("binding.btnMangaMinimapNext.setMinimapChapterNavigationClickListener(binding.tvMangaMinimapNext)"))
    }

    @Test
    fun tocDragHandleKeepsDraggedHeightBelowFullscreenThreshold() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val tocSetup = readMenu.substringAfter("private fun setupTocPanel()")
            .substringBefore("private fun setTocPanelPage")

        assertTrue(tocSetup.contains("tocDragHandle.setOnTouchListener"))
        assertFalse(tocSetup.contains("rvPanelToc.setOnTouchListener"))
        assertFalse(tocSetup.contains("taggedRecycler(\"rv_panel_bookmarks\").setOnTouchListener"))
        assertTrue(readMenu.contains("private fun settleTocPanelDrag()"))
        assertTrue(readMenu.contains("val currentHeight = binding.flExpandedPanel.height"))
        assertTrue(readMenu.contains("val thresholdHeight = tocFullscreenThresholdHeight()"))
        assertTrue(readMenu.contains("animateTocPanelTo(currentHeight)"))
        assertTrue(readMenu.contains("return fullReadMenuHeight().coerceAtLeast(tocDefaultMinHeight())"))
        assertTrue(readMenu.contains("private fun fullReadMenuHeight(): Int"))
        assertTrue(readMenu.contains("private fun bottomTabBarHeightForPanel(panelHeight: Int): Int"))
        assertTrue(readMenu.contains("private fun syncTocFullscreenChrome()"))
        assertTrue(readMenu.contains("hideBottomTabIndicatorImmediately()"))
        assertTrue(readMenu.contains("bottomTabNavViewport.gone()"))
        assertTrue(readMenu.contains("bottomTabIndicatorContainer.invisible()"))
        assertTrue(readMenu.contains("flExpandedPanel.bringToFront()"))
        assertTrue(readMenu.contains("private fun syncTocFullscreenPanelInsets(fullscreen: Boolean)"))
        assertTrue(readMenu.contains("readMenuNormalPaddingBottom = this@ReadMenu.paddingBottom"))
        assertTrue(readMenu.contains("readMenuNormalPaddingBottom"))
        assertTrue(readMenu.contains("bottomMenu.setPaddingRelative(0, bottomMenu.paddingTop, 0, 0)"))
        assertTrue(readMenu.contains("private fun syncTocFullscreenGlassCorners(fullscreen: Boolean)"))
        assertTrue(readMenu.contains("updateBottomTabGlassLayerHeight(tocFullPanelHeight())"))
        assertTrue(readMenu.contains("private fun bottomTabGlassStableLayerHeight(panelHeight: Int): Int"))
        assertTrue(readMenu.contains("val stablePanelHeight = panelHeight"))
        assertTrue(readMenu.contains(".coerceAtLeast(rootHeight)"))
        assertTrue(readMenu.contains("return (bottomTabCollapsedHeight() + stablePanelHeight)"))
        assertTrue(readMenu.contains("private fun isBottomTabGlassLayerLaidOutAtStableHeight(): Boolean"))
        assertTrue(readMenu.contains("!isBottomTabGlassLayerLaidOutAtStableHeight()"))
        assertTrue(readMenu.contains("binding.bottomTabGlassView.height == bottomTabGlassLayerHeight"))
        assertFalse(readMenu.contains("val middleHeight = (tocDefaultPanelHeight() + tocFullPanelHeight()) / 2"))
    }

    @Test
    fun tocFullscreenGlassCornerSyncWaitsForBoundLiquidGlass() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val safeGlassView = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/SafeLiquidGlassView.kt").readText()
        val cornerSync = readMenu.substringAfter("private fun syncTocFullscreenGlassCorners")
            .substringBefore("private fun measureBottomPanelHeight")
        val bottomGlassSetup = readMenu.substringAfter("private fun setupBottomTabFrostedGlassViews")
            .substringBefore("private fun configureLayoutAdjustFrostedGlass")
        val singleGlassSetup = readMenu.substringAfter("private fun setupBottomTabFrostedGlassView(")
            .substringBefore("private fun bottomTabGlassShell")

        assertTrue(readMenu.contains("private fun bottomTabGlassCornerRadius"))
        assertTrue(cornerSync.contains("val radius = bottomTabGlassCornerRadius(fullscreen)"))
        assertTrue(cornerSync.contains("boundBottomTabGlassViewIds.contains(bottomTabGlassView.id)"))
        assertTrue(cornerSync.contains("bottomTabGlassView.isReadyForLiquidGlassConfig()"))
        assertTrue(cornerSync.contains("bottomTabGlassView.setCornerRadius(radius)"))
        assertTrue(bottomGlassSetup.contains("cornerRadius = bottomTabGlassCornerRadius()"))
        assertTrue(readMenu.contains("private fun View.isReadyForLiquidGlassConfig(): Boolean"))
        assertTrue(readMenu.contains("return isLaidOut && width > 0 && height > 0"))
        assertTrue(bottomGlassSetup.contains("!bottomTabGlassView.isReadyForLiquidGlassConfig()"))
        assertTrue(layoutXml.contains("io.wanjuan.app.ui.widget.SafeLiquidGlassView"))
        assertTrue(safeGlassView.contains("class SafeLiquidGlassView"))
        assertTrue(safeGlassView.contains("override fun onSizeChanged"))
        assertFalse(layoutXml.contains("com.qmdeve.liquidglass.widget.LiquidGlassView"))
        assertTrue(singleGlassSetup.contains("): Boolean"))
        assertTrue(singleGlassSetup.contains("if (!target.isReadyForLiquidGlassConfig() || !liquidGlassView.isReadyForLiquidGlassConfig())"))
        assertTrue(singleGlassSetup.contains("val shouldBind = !boundBottomTabGlassViewIds.contains(liquidGlassView.id)"))
        assertTrue(singleGlassSetup.contains("boundBottomTabGlassViewIds.add(liquidGlassView.id)"))
        assertTrue(
            singleGlassSetup.indexOf("liquidGlassView.setCornerRadius(cornerRadius)") <
                singleGlassSetup.indexOf("boundBottomTabGlassViewIds.add(liquidGlassView.id)")
        )
        assertFalse(singleGlassSetup.contains("if (boundBottomTabGlassViewIds.add(liquidGlassView.id))"))
        assertFalse(cornerSync.contains("if (!AppConfig.isEInkMode) {\n            bottomTabGlassView.setCornerRadius(radius)\n        }"))
    }

    @Test
    fun liquidGlassRetriesDoNotStarveReaderIdleInitialization() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val readActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()
        val retryHelper = readMenu.substringAfter("private fun scheduleLiquidGlassSetup")
            .substringBefore("private fun bottomTabGlassShell")

        assertTrue(readActivity.contains("Looper.myQueue().addIdleHandler"))
        assertTrue(readMenu.contains("private fun scheduleLiquidGlassSetup(anchor: View, block: () -> Unit)"))
        assertTrue(readMenu.contains("private fun canScheduleLiquidGlassSetup(anchor: View): Boolean"))
        assertTrue(retryHelper.contains("anchor.postOnAnimation"))
        assertTrue(retryHelper.contains("this@ReadMenu.isShown"))
        assertTrue(retryHelper.contains("anchor.isShown"))
        assertFalse(readMenu.contains("if (!AppConfig.isEInkMode && titleBarGlassView.isVisible)"))
        assertFalse(readMenu.contains("if (!AppConfig.isEInkMode && bottomTabGlassView.isVisible)"))
    }

    @Test
    fun oldPageAndMoreMenuIdsAreNotSecondaryTabs() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertFalse(readMenu.contains("BottomTab.Page,"))
        assertFalse(readMenu.contains("BottomTab.More"))
        assertFalse(readMenu.contains("R.id.menu_read_page ->"))
        assertFalse(readMenu.contains("R.id.menu_read_more"))
    }

    @Test
    fun darkBottomTabGlassUsesNearBlackSurfaceAndTint() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("private fun bottomTabDarkGlassSurfaceColor(): Int"))
        assertTrue(readMenu.contains("Color.rgb(8, 10, 14)"))
        assertTrue(readMenu.contains("private fun bottomTabGlassTintColor(): FloatArray"))
        assertTrue(readMenu.contains("floatArrayOf(0.08f, 0.10f, 0.14f)"))
        assertTrue(readMenu.contains("0.52f + glassLevel * 0.18f"))
    }

    @Test
    fun bottomTabShellDoesNotDrawOuterStroke() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val glassShell = readMenu.substringAfter("private fun bottomTabGlassShell")
            .substringBefore("private fun bottomTabGlassFallbackShell")
        val eInkShell = readMenu.substringAfter("if (AppConfig.isEInkMode)")
            .substringBefore("return@run")

        assertFalse(glassShell.contains("setStroke("))
        assertFalse(eInkShell.contains("1.dpToPx()"))
    }

    @Test
    fun bottomTabSelectedLabelStaysWhiteOnAccentIndicator() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val colorBlock = readMenu.substringAfter("private fun applyBottomNavigationColors()")
            .substringBefore("private fun syncBottomNavigationSelection()")

        assertTrue(readMenu.contains("private fun bottomTabSelectedLabelColor(): Int = Color.WHITE"))
        assertTrue(colorBlock.contains("val iconColors = Selector.colorBuild()"))
        assertTrue(colorBlock.contains("val textColors = Selector.colorBuild()"))
        assertTrue(colorBlock.contains(".setPressedColor(Color.WHITE)"))
        assertTrue(colorBlock.contains(".setSelectedColor(bottomTabSelectedLabelColor())"))
        assertTrue(colorBlock.contains(".setCheckedColor(bottomTabSelectedLabelColor())"))
        assertTrue(colorBlock.contains("nav.itemIconTintList = iconColors"))
        assertTrue(colorBlock.contains("nav.itemTextColor = textColors"))
        assertFalse(colorBlock.contains("nav.itemTextColor = colors"))
    }

    @Test
    fun bottomTabSelectedIconStaysWhiteOnAccentIndicator() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("private fun bottomTabSelectedContentColor(): Int = Color.WHITE"))
        assertTrue(readMenu.substringAfter("val iconColors = Selector.colorBuild()")
            .substringBefore("val textColors = Selector.colorBuild()")
            .contains(".setCheckedColor(bottomTabSelectedContentColor())"))
    }

    @Test
    fun expandedPanelIsDockedToBottomTabBar() {
        val layout = readMenuLayout()
        val expandedPanel = layout.elementById("fl_expanded_panel")
        val bottomTabBar = layout.elementById("bottom_tab_bar")

        assertEquals(
            bottomTabBar.androidAttr("layout_height"),
            expandedPanel.androidAttr("layout_marginBottom")
        )
    }

    @Test
    fun themePanelKeepsVerticalScrollAvailable() {
        val layout = readMenuLayout()
        val themePanel = layout.elementById("panel_theme")
        val layoutPanel = layout.elementById("panel_layout")
        val layoutScroll = layout.elementById("panel_layout_scroll")

        assertEquals("vertical", themePanel.androidAttr("scrollbars"))
        assertEquals("false", themePanel.androidAttr("fadeScrollbars"))
        assertEquals("outsideOverlay", themePanel.androidAttr("scrollbarStyle"))
        assertEquals("right", themePanel.androidAttr("verticalScrollbarPosition"))
        assertEquals("LinearLayout", layoutPanel.tagName)
        assertEquals("vertical", layoutScroll.androidAttr("scrollbars"))
        assertEquals("false", layoutScroll.androidAttr("fadeScrollbars"))
        assertEquals("outsideOverlay", layoutScroll.androidAttr("scrollbarStyle"))
        assertEquals("right", layoutScroll.androidAttr("verticalScrollbarPosition"))
    }

    @Test
    fun themePresetsAreInSingleHorizontalRow() {
        val layout = readMenuLayout()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val presetScroller = layout.elementById("hsv_theme_presets")
        val presetRow = layout.elementById("ll_theme_preset_row")
        val presetCard = layout.elementById("theme_card_follow_system")

        assertEquals("horizontal", presetRow.androidAttr("orientation"))
        assertTrue(presetCard.hasAncestor(presetRow))
        assertTrue(presetCard.hasAncestor(presetScroller))
        assertFalse(layoutXml.contains("theme_card_dark"))
        assertFalse(layoutXml.contains("theme_card_paper"))
        assertFalse(layoutXml.contains("theme_card_eye_green"))
        assertFalse(layoutXml.contains("theme_card_quiet_blue"))
        assertFalse(layoutXml.contains("theme_card_night"))
        assertTrue(layoutXml.contains("theme_card_add"))
    }

    @Test
    fun themePanelUsesCardRailForThemeSelectionAndAddAction() {
        val layout = readMenuLayout()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val row = layout.elementById("ll_theme_preset_row")
        val defaultCard = layout.elementById("theme_card_follow_system")
        val addCard = layout.elementById("theme_card_add")

        assertFalse(layoutXml.contains("ll_theme_tabs"))
        assertFalse(layoutXml.contains("ll_theme_tab_preset"))
        assertFalse(layoutXml.contains("ll_theme_tab_custom"))
        assertFalse(layoutXml.contains("ll_theme_tab_eye"))
        assertFalse(layoutXml.contains("@string/read_menu_theme_mine"))
        assertFalse(layoutXml.contains("@string/read_menu_theme_save_current"))
        assertFalse(layoutXml.contains("@string/read_menu_theme_eye_mode"))
        assertTrue(defaultCard.hasAncestor(row))
        assertTrue(addCard.hasAncestor(row))
        assertTrue(defaultCard.isBefore(addCard))
        assertEquals("96dp", defaultCard.androidAttr("layout_width"))
        assertEquals("96dp", addCard.androidAttr("layout_width"))
        assertEquals("8dp", defaultCard.androidAttr("layout_marginEnd"))
        assertEquals("", addCard.androidAttr("layout_marginEnd"))
        assertEquals(2, row.childElements("include").size)
    }

    @Test
    fun themePresetAppliesLayoutPageTurnAndBackgroundSuite() {
        val presetModel = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemePreset.kt").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(presetModel.contains("layoutLabelRes"))
        assertTrue(presetModel.contains("pageTurnLabelRes"))
        assertTrue(presetModel.contains("backgroundLabelRes"))
        assertTrue(presetModel.contains("fun summaryText"))
        assertTrue(presetModel.contains("pageAnimationSpeed"))
        assertTrue(readMenu.contains("bindThemeCardPreviewText(card, preset.textColor)"))
        assertTrue(readMenu.contains("ReadBookConfig.textSize = preset.textSize"))
        assertTrue(readMenu.contains("ReadBookConfig.lineSpacingExtra = preset.lineSpacingExtra"))
        assertTrue(readMenu.contains("ReadBookConfig.paragraphSpacing = preset.paragraphSpacing"))
        assertTrue(readMenu.contains("ReadBookConfig.paddingTop = preset.paddingTop"))
        assertTrue(readMenu.contains("ReadBookConfig.titleSize = preset.titleSize"))
        assertTrue(readMenu.contains("ReadTipConfig.headerMode = preset.headerMode"))
        assertTrue(readMenu.contains("ReadTipConfig.footerMode = preset.footerMode"))
        assertTrue(readMenu.contains("ReadBookConfig.pageAnim = preset.pageAnim"))
        assertTrue(readMenu.contains("AppConfig.pageAnimationSpeed = preset.pageAnimationSpeed"))
        assertTrue(readMenu.contains("ReadBookConfig.bgBrightness = preset.bgBrightness"))
    }

    @Test
    fun themeSuiteSummaryStaysCompactInsideNarrowPresetCards() {
        val defaultStrings = repoFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(defaultStrings.contains("""<string name="read_menu_theme_suite_summary">%1${'$'}s / %2${'$'}s\n%3${'$'}s</string>"""))
        assertFalse(defaultStrings.contains("Layout: %1${'$'}s / Turn: %2${'$'}s"))
    }

    @Test
    fun themeSaveCurrentAndMyThemesHaveReaderSuiteEntryPoints() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val suiteStore = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemeSuite.kt")

        assertTrue(suiteStore.exists())
        val suiteStoreText = suiteStore.readText()
        assertTrue(suiteStoreText.contains("object ReadMenuThemeSuiteStore"))
        assertTrue(suiteStoreText.contains("fun captureCurrent"))
        assertTrue(suiteStoreText.contains("fun applyToReader"))
        assertTrue(layoutXml.contains("theme_card_add"))
        assertTrue(readMenu.contains("binding.themeCardAdd.root.setOnClickListener { addCurrentThemeSuite() }"))
        assertTrue(readMenu.contains("bindThemeAddCard(binding.themeCardAdd)"))
        assertFalse(readMenu.contains("private fun saveCurrentThemeSuite()"))
        assertFalse(readMenu.contains("ThemeTab"))
        assertFalse(readMenu.contains("showSavedThemeSuites"))
        assertFalse(readMenu.contains("llThemeTab"))
    }

    @Test
    fun currentThemeAlwaysHasASelectedNonAddCard() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val suiteStore = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemeSuite.kt").readText()

        assertTrue(suiteStore.contains("fun matchesCurrentTheme"))
        assertTrue(readMenu.contains("val savedSuites = ReadMenuThemeSuiteStore.load(context)"))
        assertTrue(readMenu.contains("syncThemeSuiteCards(savedThemeCards.size"))
        assertTrue(readMenu.contains("val selectedPresetIndex = themePresets.indexOfFirst"))
        assertTrue(readMenu.contains("presetIndex == selectedPresetIndex"))
        assertFalse(readMenu.contains("val needsCurrentCard"))
        assertTrue(readMenu.contains("bindThemeAddCard(binding.themeCardAdd)"))
    }

    @Test
    fun themeRailChoosesOnlyOneSelectedThemeCard() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("val selectedPresetIndex = themePresets.indexOfFirst"))
        assertTrue(readMenu.contains("presetIndex == selectedPresetIndex"))
        assertTrue(readMenu.contains("savedIndex == selectedSavedIndex"))
        assertTrue(readMenu.contains("if (explicitSavedIndex == -1)"))
    }

    @Test
    fun themeRailRemembersExplicitSavedCardSelectionForDuplicateSuites() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val suiteStore = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemeSuite.kt").readText()

        assertTrue(suiteStore.contains("ACTIVE_PREF_KEY"))
        assertTrue(suiteStore.contains("fun explicitSavedIndex"))
        assertTrue(suiteStore.contains("fun selectedSavedIndex"))
        assertTrue(suiteStore.contains("it.createdAt == activeCreatedAt"))
        assertTrue(readMenu.contains("ReadMenuThemeSuiteStore.selectedSavedIndex(context, savedSuites)"))
        assertTrue(readMenu.contains("ReadMenuThemeSuiteStore.select(context, suite)"))
    }

    @Test
    fun selectedSavedThemeIsUpdatedWhenReaderThemeControlsChange() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val suiteStore = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemeSuite.kt").readText()
        val activity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()

        assertTrue(suiteStore.contains("fun updateActiveFromCurrent"))
        assertTrue(suiteStore.contains("createdAt = active.createdAt"))
        assertTrue(readMenu.contains("fun persistActiveThemeSuiteChange()"))
        assertTrue(readMenu.contains("ReadMenuThemeSuiteStore.updateActiveFromCurrent(context)"))
        assertTrue(readMenu.contains("persistActiveThemeSuiteChange()"))
        assertTrue(activity.contains("binding.readMenu.persistActiveThemeSuiteChange()"))
    }

    @Test
    fun themeRailLetsExplicitSavedThemeWinOverMatchingPreset() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("val explicitSavedIndex = ReadMenuThemeSuiteStore.explicitSavedIndex(context, savedSuites)"))
        assertTrue(readMenu.contains("val selectedPresetIndex = if (explicitSavedIndex == -1)"))
        assertTrue(readMenu.contains("explicitSavedIndex != -1 -> explicitSavedIndex"))
    }

    @Test
    fun savingThemeAlwaysCreatesNamedCard() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val suiteStore = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemeSuite.kt").readText()

        assertTrue(suiteStore.contains("fun save(context: Context, suite: ReadMenuThemeSuite)"))
        assertTrue(suiteStore.contains("suites + suite"))
        assertTrue(suiteStore.contains("takeLast(MAX_SUITES)"))
        assertTrue(readMenu.contains("ReadMenuThemeSuiteStore.save(context, suite)"))
        assertTrue(readMenu.contains("ReadMenuThemeSuiteStore.select(context, suite)"))
        assertFalse(readMenu.contains("ReadMenuThemeSuiteStore.saveOrSelectExisting(context, suite)"))
    }

    @Test
    fun savingThemeThatMatchesPresetStillCreatesCustomTheme() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertFalse(readMenu.contains("themePresets.any { preset ->"))
        assertFalse(readMenu.contains("ReadMenuThemeSuiteStore.clearSelection(context)\n                    updateThemePresetCards()\n                    return@okButton"))
        assertTrue(readMenu.contains("ReadMenuThemeSuiteStore.save(context, suite)"))
        assertTrue(readMenu.contains("context.toastOnUi(context.getString(R.string.read_menu_theme_saved, suite.name))"))
        assertFalse(readMenu.contains("context.alert(R.string.read_menu_theme_save_current)"))
    }

    @Test
    fun themePreviewCardsUseConsistentSampleTextForPresetAndSavedThemes() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val presetModel = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemePreset.kt").readText()

        assertTrue(presetModel.contains("DEFAULT_PREVIEW_TITLE"))
        assertTrue(presetModel.contains("DEFAULT_PREVIEW_BODY"))
        assertTrue(readMenu.contains("bindThemeCardPreviewText(card, preset.textColor)"))
        assertTrue(readMenu.contains("bindThemeCardPreviewText(card, suite.textColor)"))
        assertTrue(readMenu.contains("card.tvThemeCardTitle.text = ReadMenuThemePreset.DEFAULT_PREVIEW_TITLE"))
        assertTrue(readMenu.contains("card.tvThemeCardBody.text = ReadMenuThemePreset.DEFAULT_PREVIEW_BODY"))
        assertFalse(readMenu.contains("card.tvThemeCardTitle.text = suite.name"))
        assertFalse(readMenu.contains("card.tvThemeCardBody.text = suite.summaryText(context)"))
    }

    @Test
    fun savedThemeLongPressShowsTextOnlyRenameAndDeleteActions() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val suiteStore = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemeSuite.kt").readText()

        assertTrue(readMenu.contains("card.root.setOnLongClickListener"))
        assertTrue(readMenu.contains("showThemeSuiteActions(suite)"))
        assertTrue(readMenu.contains("private fun showThemeSuiteActions(suite: ReadMenuThemeSuite)"))
        assertTrue(readMenu.contains("context.selector(suite.name, actions)"))
        assertTrue(readMenu.contains("context.getString(R.string.read_menu_theme_rename)"))
        assertTrue(readMenu.contains("context.getString(R.string.delete)"))
        assertTrue(readMenu.contains("renameThemeSuite(suite)"))
        assertTrue(readMenu.contains("deleteThemeSuite(suite)"))
        assertFalse(readMenu.contains("selectedThemeActionPanel"))
        assertFalse(readMenu.contains("showSelectedThemeActionPanel("))
        assertFalse(readMenu.contains("themeActionButton("))
        assertFalse(readMenu.contains("themeActionPanelBackground("))
        assertFalse(readMenu.contains("R.drawable.ic_lucide_pencil"))
        assertFalse(readMenu.contains("R.drawable.ic_lucide_trash_2"))
        assertFalse(readMenu.contains("width = 96.dpToPx() + actionWidth"))
        assertTrue(suiteStore.contains("fun delete"))
        assertTrue(suiteStore.contains("filterNot { it.createdAt == suite.createdAt }"))
    }

    @Test
    fun savedThemeRevealKeepsStandardCardWidthWithoutActionExtension() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("val targetWidth = 96.dpToPx()"))
        assertTrue(readMenu.contains("ValueAnimator.ofInt(0, targetWidth)"))
        assertFalse(readMenu.contains("actionPanelWidth"))
        assertFalse(readMenu.contains("ValueAnimator.ofInt(0, 42.dpToPx())"))
    }

    @Test
    fun savedThemeCardsShowCustomBadgeButPresetAndCurrentCardsDoNot() {
        val cardLayout = repoFile("app/src/main/res/layout/view_read_theme_card.xml").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val zhStrings = repoFile("app/src/main/res/values-zh/strings.xml").readText()

        assertTrue(cardLayout.contains("@+id/tv_theme_card_badge"))
        assertTrue(cardLayout.contains("@string/read_menu_theme_custom_badge"))
        assertTrue(zhStrings.contains("<string name=\"read_menu_theme_custom_badge\">自定义</string>"))
        assertTrue(readMenu.contains("private data class ThemeSuiteCard"))
        assertTrue(readMenu.contains("val custom: Boolean"))
        assertTrue(readMenu.contains("savedSuites.mapIndexed { savedIndex, suite ->"))
        assertTrue(readMenu.contains("ThemeSuiteCard(suite, savedIndex == selectedSavedIndex, true)"))
        assertFalse(readMenu.contains("ThemeSuiteCard(it, true, false)"))
        assertTrue(readMenu.contains("card.tvThemeCardBadge.isVisible = false"))
        assertTrue(readMenu.contains("card.tvThemeCardBadge.isVisible = custom"))
    }

    @Test
    fun savingThemeRevealsCardBeforeAddCardAndScrollsRight() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val suiteStore = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemeSuite.kt").readText()

        assertTrue(readMenu.contains("llThemePresetRow.indexOfChild(themeCardAdd.root)"))
        assertTrue(readMenu.contains("llThemePresetRow.addView(card.root, insertIndex)"))
        assertTrue(readMenu.contains("ReadMenuThemeSuiteStore.captureDefaultPreset"))
        assertTrue(readMenu.contains("finishThemeSuiteApplied(revealSuite = suite)"))
        assertTrue(readMenu.contains("suite.applyToReader()"))
        assertTrue(readMenu.contains("animateThemeSuiteCardReveal"))
        assertTrue(readMenu.contains("val targetWidth = 96.dpToPx()"))
        assertTrue(readMenu.contains("ValueAnimator.ofInt(0, targetWidth)"))
        assertTrue(readMenu.contains("hsvThemePresets.smoothScrollTo(llThemePresetRow.width"))
        assertTrue(suiteStore.contains("fun captureDefaultPreset(name: String, preset: ReadMenuThemePreset)"))
        assertTrue(suiteStore.contains("ReadMenuThemeSuite.fromPreset(name, preset)"))
        assertTrue(suiteStore.contains("fun fromPreset(name: String, preset: ReadMenuThemePreset): ReadMenuThemeSuite"))
        assertFalse(readMenu.contains("ReadMenuThemeSuiteStore.captureCurrent(name)"))
    }

    @Test
    fun savedThemeCardsCanBeRenamedByLongPress() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val suiteStore = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemeSuite.kt").readText()
        val defaultStrings = repoFile("app/src/main/res/values/strings.xml").readText()
        val zhStrings = repoFile("app/src/main/res/values-zh/strings.xml").readText()

        assertTrue(readMenu.contains("card.root.setOnLongClickListener"))
        assertTrue(readMenu.contains("renameThemeSuite(suite)"))
        assertTrue(readMenu.contains("ReadMenuThemeSuiteStore.rename(context, suite, name)"))
        assertTrue(suiteStore.contains("fun rename(context: Context, suite: ReadMenuThemeSuite, name: String)"))
        assertTrue(defaultStrings.contains("read_menu_theme_rename"))
        assertTrue(zhStrings.contains("read_menu_theme_rename"))
    }

    @Test
    fun savedThemeSuiteCapturesFullReaderLayoutFontAndTipSettings() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val suiteStore = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenuThemeSuite.kt").readText()

        listOf(
            "textBold",
            "textFont",
            "systemTypeface",
            "paddingTop",
            "paddingBottom",
            "paddingLeft",
            "paddingRight",
            "titleTopSpacing",
            "titleBottomSpacing",
            "titleSize",
            "titleMode",
            "headerMode",
            "headerPaddingTop",
            "headerPaddingBottom",
            "headerPaddingLeft",
            "headerPaddingRight",
            "showHeaderLine",
            "footerMode",
            "footerPaddingTop",
            "footerPaddingBottom",
            "footerPaddingLeft",
            "footerPaddingRight",
            "showFooterLine"
        ).forEach { field ->
            assertTrue("suite should contain $field", suiteStore.contains("val $field"))
        }

        assertTrue(suiteStore.contains("ReadBookConfig.textFont = textFont.orEmpty()"))
        assertTrue(suiteStore.contains("AppConfig.systemTypefaces = systemTypeface"))
        assertTrue(suiteStore.contains("ReadTipConfig.headerMode = headerMode"))
        assertTrue(suiteStore.contains("ReadTipConfig.footerMode = footerMode"))
        assertTrue(readMenu.contains("private fun setSystemFont(systemTypeface: Int)"))
        assertTrue(readMenu.substringAfter("private fun setSystemFont").substringBefore("private fun setBuiltInFont")
            .contains("persistActiveThemeSuiteChange()"))
        assertTrue(readMenu.substringAfter("private fun setLayoutBodyPadding").substringBefore("private fun setLayoutTitleSize")
            .contains("persistActiveThemeSuiteChange()"))
        assertTrue(readMenu.substringAfter("private fun setLayoutTitleMode").substringBefore("private fun setLayoutTipPadding")
            .contains("persistActiveThemeSuiteChange()"))
        assertTrue(readMenu.substringAfter("private fun setHeaderDisplayMode").substringBefore("private fun setFooterDisplayMode")
            .contains("persistActiveThemeSuiteChange()"))
        assertTrue(readMenu.substringAfter("private fun setFooterDividerVisible").substringBefore("private fun bindBackgroundSeek")
            .contains("persistActiveThemeSuiteChange()"))
    }

    @Test
    fun themeAddCardUsesCenteredLargePlusWithoutPreviewSummary() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("card.tvThemeCardBody.isGone = true"))
        assertTrue(readMenu.contains("card.tvThemeCardTitle.gravity = Gravity.CENTER"))
        assertTrue(readMenu.contains("card.tvThemeCardTitle.textSize = 28f"))
        assertFalse(readMenu.contains("card.tvThemeCardBody.setText(R.string.read_menu_theme_add_summary)"))
    }

    @Test
    fun customFontAddCardUsesSameCenteredPlusStyleAsThemeAddCard() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("bindFontAddCard(card)"))
        assertFalse(readMenu.contains("card.tvThemeCardBody.setText(R.string.read_style_font_custom)"))
    }

    @Test
    fun builtInFontSamplesAreHiddenAndLargeFontsAreNotPackagedAssets() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val builtInFonts = repoFile("app/src/main/java/io/wanjuan/app/help/config/BuiltInReadFonts.kt").readText()
        val defaultStrings = repoFile("app/src/main/res/values/strings.xml").readText()
        val zhStrings = repoFile("app/src/main/res/values-zh/strings.xml").readText()
        val movedFontNames = listOf(
            "source_han_sans_cn_regular.otf",
            "source_han_sans_sc_vf.otf",
            "harmonyos_sans_sc_regular.ttf",
            "harmonyos_sans_sc_thin.ttf",
            "harmonyos_sans_sc_light.ttf",
            "harmonyos_sans_sc_medium.ttf",
            "harmonyos_sans_sc_bold.ttf",
            "harmonyos_sans_sc_black.ttf",
            "wenyuan_sans_sc_vf.otf",
            "mi_sans_vf.ttf",
            "alimama_fang_yuan_ti_vf.ttf",
            "lxgw_wenkai_screen.ttf",
            "lxgw_neo_xihei.ttf",
            "lxgw_fasmart_gothic.ttf",
            "lxgw_zhenkai_gb_regular.ttf"
        )

        listOf(
            "read_style_font_harmonyos_sans",
            "read_style_font_sans",
            "read_style_font_wenyuan_sans_vf",
            "read_style_font_mi_sans_vf",
            "read_style_font_alimama_fang_yuan_ti_vf",
            "read_style_font_lxgw_wenkai_screen",
            "read_style_font_lxgw_neo_xihei",
            "read_style_font_lxgw_fasmart_gothic",
            "read_style_font_lxgw_zhenkai"
        ).forEach { fontString ->
            assertFalse(readMenu.contains(fontString))
        }
        assertFalse(readMenu.contains("setBuiltInFont("))
        assertFalse(readMenu.contains("isBuiltInFontSelected("))
        assertFalse(readMenu.contains("builtInTypeface("))
        assertFalse(readMenu.contains("BuiltInReadFonts."))
        movedFontNames.forEach { fontName ->
            assertFalse(repoFile("app/src/main/assets/font/$fontName").exists())
            assertTrue(repoFile("app/src/main/nonPackagedAssets/font/$fontName").exists())
        }
        assertTrue(repoFile("app/src/main/assets/font/number.ttf").exists())
        assertFalse(readMenu.contains("read_style_font_source"))
        assertFalse(readMenu.contains("SOURCE_HAN_SERIF"))
        assertFalse(builtInFonts.contains("SOURCE_HAN_SERIF"))
        assertFalse(defaultStrings.contains("read_style_font_source"))
        assertFalse(zhStrings.contains("read_style_font_source"))
        assertFalse(repoFile("app/src/main/assets/font/source_han_serif_cn_regular.otf").exists())
        assertFalse(repoFile("app/src/main/assets/font/source_han_serif_sc_vf.otf").exists())
    }

    @Test
    fun chapterLayoutKeyIncludesTypefaceInputs() {
        val readBook = repoFile("app/src/main/java/io/wanjuan/app/model/ReadBook.kt").readText()

        assertTrue(readBook.contains("append(ReadBookConfig.textFont)"))
        assertTrue(readBook.contains("append(AppConfig.systemTypefaces)"))
        assertTrue(readBook.contains("append(ReadBookConfig.textWeight)"))
        assertTrue(readBook.contains("append(paint.letterSpacing)"))
        assertTrue(readBook.contains("append(titlePaint.letterSpacing)"))
    }

    @Test
    fun fontCardsUseTallerPreviewSizingThanThemeCards() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("height = 82.dpToPx()"))
    }

    @Test
    fun fontControlsBelongToLayoutPanelOnly() {
        val layout = readMenuLayout()
        val layoutPanel = layout.elementById("panel_layout")
        val themePanel = layout.elementById("panel_theme")
        val previewHost = layout.elementById("layout_margin_adjust_preview_host")
        val fontRow = layout.elementById("ll_font_sample_row")
        val textStyleControls = listOf(
            "seek_theme_font_weight",
            "seek_theme_text_size"
        ).map { id -> layout.elementById(id) }

        assertTrue(fontRow.hasAncestor(layoutPanel))
        assertFalse(fontRow.hasAncestor(themePanel))
        textStyleControls.forEach { control ->
            assertTrue(control.hasAncestor(previewHost))
            assertFalse(control.hasAncestor(themePanel))
        }
    }

    @Test
    fun layoutPanelKeepsSpacingAndStyleControlsInSingleLayoutView() {
        val layout = readMenuLayout()
        val layoutPanel = layout.elementById("panel_layout")
        val layoutScroll = layout.elementById("panel_layout_scroll")
        val spacingPanel = layout.elementById("panel_layout_spacing")
        val fontPanel = layout.elementById("panel_layout_font")
        val stylePanel = layout.elementById("panel_layout_style")
        val marginEntries = listOf(
            "layout_text_style_entry",
            "layout_margin_entry_body",
            "layout_margin_entry_title",
            "layout_margin_entry_header",
            "layout_margin_entry_footer"
        ).map { id -> layout.elementById(id) }

        assertTrue(spacingPanel.hasAncestor(layoutPanel))
        assertTrue(spacingPanel.hasAncestor(layoutScroll))
        assertEquals("", spacingPanel.androidAttr("visibility"))
        assertEquals("", fontPanel.androidAttr("visibility"))
        assertTrue(stylePanel.hasAncestor(layoutPanel))
        assertTrue(stylePanel.hasAncestor(layoutScroll))
        assertEquals("", stylePanel.androidAttr("visibility"))
        marginEntries.forEach { entry ->
            assertTrue(entry.hasAncestor(spacingPanel))
            assertEquals("44dp", entry.androidAttr("layout_height"))
        }
        assertEquals("14sp", layout.elementById("tv_layout_text_style_entry").androidAttr("textSize"))
        assertEquals("13sp", layout.elementById("tv_layout_text_style_entry_value").androidAttr("textSize"))
    }

    @Test
    fun layoutPanelUsesCompactMarginEntriesAndImmersiveMarginOverlay() {
        val layout = readMenuLayout()
        val spacingPanel = layout.elementById("panel_layout_spacing")
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val overlay = layout.elementById("layout_margin_adjust_overlay")
        val overlayPanel = layout.elementById("layout_margin_adjust_panel")
        val previewHost = layout.elementById("layout_margin_adjust_preview_host")
        val previewFrame = layout.elementById("layout_margin_adjust_preview")

        assertFalse(previewHost.hasAncestor(spacingPanel))
        assertEquals("gone", overlay.androidAttr("visibility"))
        assertEquals("match_parent", overlay.androidAttr("layout_width"))
        assertEquals("match_parent", overlay.androidAttr("layout_height"))
        assertEquals("androidx.constraintlayout.widget.ConstraintLayout", previewHost.tagName)
        assertEquals("220dp", previewHost.androidAttr("layout_height"))
        assertTrue(overlayPanel.hasAncestor(overlay))
        assertTrue(previewHost.hasAncestor(overlayPanel))
        assertEquals("io.wanjuan.app.ui.book.read.ReadMarginPreviewView", previewFrame.tagName)
        assertEquals("142dp", previewFrame.androidAttr("layout_width"))
        assertEquals("142dp", previewFrame.androidAttr("layout_height"))
        assertEquals("0dp", layout.elementById("layout_margin_adjust_glass_view").androidAttr("layout_height"))
        assertEquals("0dp", layout.elementById("layout_margin_adjust_shell_overlay").androidAttr("layout_height"))

        listOf(
            "layout_margin_entry_body",
            "layout_margin_entry_title",
            "layout_margin_entry_header",
            "layout_margin_entry_footer"
        ).forEach { id ->
            assertTrue(layout.elementById(id).hasAncestor(spacingPanel))
        }

        listOf(
            "seek_layout_padding_top",
            "seek_layout_padding_bottom",
            "seek_layout_padding_left",
            "seek_layout_padding_right",
            "seek_layout_title_top_spacing",
            "seek_layout_title_bottom_spacing"
        ).forEach { id ->
            assertEquals("gone", layout.elementById(id).androidAttr("visibility"))
        }

        listOf(
            "seek_theme_text_size",
            "seek_theme_font_weight",
            "seek_layout_letter_spacing",
            "seek_layout_line_spacing",
            "seek_layout_paragraph_spacing",
            "tv_layout_tip_color_value",
            "tv_layout_tip_divider_color_value",
            "tv_layout_header_show_value",
            "tv_layout_footer_show_value",
            "tv_layout_header_line_toggle",
            "tv_layout_footer_line_toggle"
        ).forEach { id ->
            assertTrue(layout.elementById(id).hasAncestor(previewHost))
        }

        mapOf(
            "layout_margin_spinbox_top" to "End_toStartOf",
            "layout_margin_spinbox_left" to "End_toStartOf",
            "layout_margin_spinbox_bottom" to "Start_toEndOf",
            "layout_margin_spinbox_right" to "Start_toEndOf"
        ).forEach { (id, constraint) ->
            val spinbox = layout.elementById(id)
            assertTrue(spinbox.hasAncestor(previewHost))
            assertEquals("@id/layout_margin_adjust_preview", spinbox.appAttr("layout_constraint$constraint"))
            assertEquals("@id/layout_margin_adjust_preview", spinbox.appAttr("layout_constraintTop_toTopOf"))
            assertEquals("@id/layout_margin_adjust_preview", spinbox.appAttr("layout_constraintBottom_toBottomOf"))
        }
        assertEquals(
            layout.elementById("tv_layout_padding_top_label").androidAttr("layout_marginTop"),
            layout.elementById("tv_layout_padding_bottom_label").androidAttr("layout_marginTop")
        )
        assertEquals(
            layout.elementById("layout_margin_spinbox_top_field").androidAttr("layout_marginTop"),
            layout.elementById("layout_margin_spinbox_bottom_field").androidAttr("layout_marginTop")
        )
        assertEquals(
            layout.elementById("tv_layout_padding_left_label").androidAttr("layout_marginTop"),
            layout.elementById("tv_layout_padding_right_label").androidAttr("layout_marginTop")
        )
        assertEquals(
            layout.elementById("layout_margin_spinbox_left_field").androidAttr("layout_marginTop"),
            layout.elementById("layout_margin_spinbox_right_field").androidAttr("layout_marginTop")
        )

        assertTrue(layout.elementById("ll_layout_margin_title_mode").hasAncestor(previewHost))
        assertTrue(layout.elementById("layout_margin_title_size").hasAncestor(previewHost))
        assertTrue(layout.elementById("seek_layout_title_size").hasAncestor(previewHost))
        assertEquals("76dp", layout.elementById("ll_layout_margin_title_mode").androidAttr("layout_height"))
        assertEquals("vertical", layout.elementById("ll_layout_margin_title_mode").androidAttr("orientation"))
        assertTrue(layout.elementById("tv_layout_margin_title_mode_left")
            .hasAncestor(layout.elementById("layout_margin_title_mode_row_1")))
        assertTrue(layout.elementById("tv_layout_margin_title_mode_advanced")
            .hasAncestor(layout.elementById("layout_margin_title_mode_row_2")))
        assertTrue(layout.elementById("layout_text_style_controls").hasAncestor(previewHost))
        assertEquals("@id/layout_margin_adjust_preview_host", layout.elementById("layout_text_style_controls")
            .appAttr("layout_constraintTop_toTopOf"))
        assertTrue(layout.elementById("layout_text_typography_section").hasAncestor(layout.elementById("layout_text_style_controls")))
        assertTrue(layout.elementById("layout_text_spacing_section").hasAncestor(layout.elementById("layout_text_style_controls")))
        assertTrue(layout.elementById("layout_text_color_section").hasAncestor(layout.elementById("layout_text_style_controls")))
        assertEquals("58dp", layout.elementById("layout_text_size_row").androidAttr("layout_height"))
        assertEquals("58dp", layout.elementById("layout_text_weight_row").androidAttr("layout_height"))
        assertTrue(layout.elementById("vw_layout_tip_color_swatch").hasAncestor(layout.elementById("ll_layout_tip_color")))
        assertTrue(layout.elementById("layout_tip_header_controls").hasAncestor(previewHost))
        assertTrue(layout.elementById("layout_tip_footer_controls").hasAncestor(previewHost))
        assertTrue(layout.elementById("ll_layout_tip_divider_color").hasAncestor(previewHost))
        assertEquals("72dp", layout.elementById("layout_margin_adjust_overlay").androidAttr("paddingTop"))
        assertEquals("16dp", layout.elementById("layout_margin_adjust_overlay").androidAttr("paddingBottom"))
        assertEquals("wrap_content", layout.elementById("layout_tip_divider_section").androidAttr("layout_height"))
        assertEquals("36dp", layout.elementById("layout_tip_divider_toggle_row").androidAttr("layout_height"))
        assertEquals("32dp", layout.elementById("tv_layout_header_line_toggle").androidAttr("layout_height"))
        assertEquals("66dp", layout.elementById("tv_layout_header_line_toggle").androidAttr("layout_width"))
        assertEquals("40dp", layout.elementById("ll_layout_tip_divider_color").androidAttr("layout_height"))
        assertEquals("LinearLayout", layout.elementById("layout_tip_controls").tagName)
        assertEquals("wrap_content", layout.elementById("layout_tip_controls").androidAttr("layout_height"))
        assertEquals("", layout.elementById("layout_tip_controls").androidAttr("scrollbars"))
        assertEquals("", layout.elementById("layout_tip_controls")
            .appAttr("layout_constraintTop_toTopOf"))
        assertEquals("@id/layout_margin_adjust_preview", layout.elementById("layout_tip_controls")
            .appAttr("layout_constraintTop_toBottomOf"))
        assertEquals("", layout.elementById("layout_tip_controls")
            .appAttr("layout_constraintBottom_toBottomOf"))
        assertTrue(layout.elementById("layout_header_display_auto_card").hasAncestor(layout.elementById("layout_tip_header_controls")))
        assertTrue(layout.elementById("layout_header_display_show_card").hasAncestor(layout.elementById("layout_tip_header_controls")))
        assertTrue(layout.elementByTag("layout_header_display_hide_card").hasAncestor(layout.elementById("layout_tip_header_controls")))
        assertTrue(layout.elementByTag("layout_footer_display_auto_card").hasAncestor(layout.elementById("layout_tip_footer_controls")))
        assertTrue(layout.elementById("layout_footer_display_show_card").hasAncestor(layout.elementById("layout_tip_footer_controls")))
        assertTrue(layout.elementById("layout_footer_display_hide_card").hasAncestor(layout.elementById("layout_tip_footer_controls")))
        listOf(
            layout.elementById("layout_header_display_auto_card"),
            layout.elementById("layout_header_display_show_card"),
            layout.elementByTag("layout_header_display_hide_card"),
            layout.elementByTag("layout_footer_display_auto_card"),
            layout.elementById("layout_footer_display_show_card"),
            layout.elementById("layout_footer_display_hide_card")
        ).forEach { card ->
            assertEquals("wrap_content", card.androidAttr("layout_height"))
            assertEquals("56dp", card.androidAttr("minHeight"))
            assertEquals("8dp", card.androidAttr("paddingTop"))
            assertEquals("8dp", card.androidAttr("paddingBottom"))
        }
        listOf(
            "@string/read_menu_header_auto_summary",
            "@string/read_menu_header_show_summary",
            "@string/read_menu_header_hide_summary",
            "@string/read_menu_footer_auto_summary",
            "@string/read_menu_footer_show_summary",
            "@string/read_menu_footer_hide_summary"
        ).forEach { text ->
            val summary = layout.elementsByAndroidText(text).single()
            assertEquals("2", summary.androidAttr("maxLines"))
            assertEquals("", summary.androidAttr("singleLine"))
            assertEquals("end", summary.androidAttr("ellipsize"))
        }
        assertTrue(layout.elementById("vw_header_display_auto_radio").hasAncestor(layout.elementById("layout_header_display_auto_card")))
        assertTrue(layout.elementByTag("vw_header_display_hide_radio").hasAncestor(layout.elementByTag("layout_header_display_hide_card")))
        assertTrue(layout.elementByTag("vw_footer_display_auto_radio").hasAncestor(layout.elementByTag("layout_footer_display_auto_card")))
        assertTrue(layout.elementById("vw_footer_display_hide_radio").hasAncestor(layout.elementById("layout_footer_display_hide_card")))
        assertEquals("gone", layout.elementById("vw_header_display_auto_radio").androidAttr("visibility"))
        assertEquals("gone", layout.elementByTag("vw_footer_display_auto_radio").androidAttr("visibility"))
        assertTrue(repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
            .contains("android:tag=\"read_menu_tip_preview\""))
        assertFalse(repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
            .contains("android:text=\"@string/read_menu_footer_show_summary\"\n                                    android:textSize=\"12sp\"\n                                    android:visibility=\"gone\""))
        assertTrue(layout.elementById("vw_layout_tip_divider_color_swatch").hasAncestor(layout.elementById("ll_layout_tip_divider_color")))
        assertEquals("@drawable/ic_arrow_right", layout.elementById("iv_layout_tip_divider_color_arrow").androidAttr("src"))
        assertEquals("gone", layout.elementByTag("layout_tip_padding_section").androidAttr("visibility"))
        assertTrue(layout.elementById("layout_tip_header_padding_controls").hasAncestor(previewHost))
        assertTrue(layout.elementById("layout_tip_footer_padding_controls").hasAncestor(previewHost))
        assertTrue(layout.elementById("seek_layout_header_padding_top").hasAncestor(layout.elementById("layout_tip_header_padding_controls")))
        assertTrue(layout.elementById("seek_layout_footer_padding_bottom").hasAncestor(layout.elementById("layout_tip_footer_padding_controls")))
        assertEquals(
            "@id/layout_margin_spinbox_bottom",
            layout.elementById("layout_margin_title_size")
                .appAttr("layout_constraintTop_toBottomOf")
        )
        assertTrue(readMenu.contains("layoutMarginAdjustPreview.setMode"))
        assertTrue(readMenu.contains("layoutMarginAdjustPreview.setTitleSize"))
        assertTrue(readMenu.contains("layoutMarginAdjustPreview.setTitleMode"))
        assertTrue(readMenu.contains("LayoutMarginAdjustMode.Text"))
        assertTrue(readMenu.contains("ReadMarginPreviewView.Mode.Text"))
        assertTrue(readMenu.contains("layoutMarginAdjustPreview.visible(!showTextStyleMode)"))
        assertTrue(readMenu.contains("val showVerticalSpinboxes = !showTextStyleMode"))
        assertTrue(readMenu.contains("layoutMarginSpinboxTop.visible(showVerticalSpinboxes)"))
        assertTrue(readMenu.contains("ReadMarginPreviewView.Mode.Title"))
        assertTrue(readMenu.contains("ReadMarginPreviewView.Mode.Header"))
        assertTrue(readMenu.contains("ReadMarginPreviewView.Mode.Footer"))
        assertTrue(readMenu.contains("updateLayoutMarginPreview"))
        assertTrue(readMenu.contains("syncLayoutAdjustPopupMetrics"))
        assertTrue(readMenu.contains("setLayoutAdjustGlassLayerHeight"))
        assertTrue(readMenu.contains("layoutTipHeaderControls.gone(!showHeaderMode)"))
        assertTrue(readMenu.contains("layoutTipFooterControls.gone(!showFooterMode)"))
        assertTrue(readMenu.contains("showLayoutMarginAdjustOverlay"))
        assertTrue(readMenu.contains("hideLayoutMarginAdjustOverlay"))
        assertTrue(readMenu.contains("setHeaderDisplayMode(2)"))
        assertTrue(readMenu.contains("setFooterDisplayMode(2)"))
        assertTrue(readMenu.contains("tintDescendantText(card, if (selected) Color.WHITE else textColor)"))
        assertTrue(readMenu.contains("context.accentColor"))
    }

    @Test
    fun readTipDefaultsPutChapterTitleAndPercentInFooter() {
        val readBookConfig = repoFile("app/src/main/java/io/wanjuan/app/help/config/ReadBookConfig.kt").readText()
        val bundledConfig = repoFile("app/src/main/assets/defaultData/readConfig.json").readText()

        assertTrue(readBookConfig.contains("var tipHeaderLeft: Int = ReadTipConfig.time"))
        assertTrue(readBookConfig.contains("var tipHeaderRight: Int = ReadTipConfig.battery"))
        assertTrue(readBookConfig.contains("var tipFooterLeft: Int = ReadTipConfig.chapterTitle"))
        assertTrue(readBookConfig.contains("var tipFooterRight: Int = ReadTipConfig.totalProgress"))
        assertTrue(readBookConfig.contains("private fun normalizeDefaultTipSlots(config: Config)"))
        assertTrue(readBookConfig.contains("config.tipFooterLeft == ReadTipConfig.bookName"))
        assertTrue(readBookConfig.contains("config.tipFooterRight == ReadTipConfig.pageAndTotal"))
        assertTrue(readBookConfig.contains("config.tipFooterRight = ReadTipConfig.totalProgress"))
        assertTrue(bundledConfig.contains("\"tipHeaderLeft\": 2"))
        assertTrue(bundledConfig.contains("\"tipHeaderRight\": 3"))
        assertTrue(bundledConfig.contains("\"tipFooterLeft\": 1"))
        assertTrue(bundledConfig.contains("\"tipFooterRight\": 5"))
        assertFalse(bundledConfig.contains("\"tipFooterRight\": 6"))
    }

    @Test
    fun headerTipItemsCanBeCustomizedFromCurrentReadMenu() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val headerControls = layout.elementById("layout_tip_header_controls")

        assertTrue(layout.elementById("layout_header_tip_items").hasAncestor(headerControls))
        assertEquals(
            "@string/read_menu_header_items",
            layout.elementById("tv_layout_header_tip_items_title").androidAttr("text")
        )
        listOf(
            "ll_layout_header_tip_left",
            "ll_layout_header_tip_middle",
            "ll_layout_header_tip_right",
            "tv_layout_header_tip_left_value",
            "tv_layout_header_tip_middle_value",
            "tv_layout_header_tip_right_value"
        ).forEach { id ->
            assertTrue(layout.elementById(id).hasAncestor(layout.elementById("layout_header_tip_items")))
        }
        assertTrue(readMenu.contains("showHeaderTipItemSelector"))
        assertTrue(readMenu.contains("ReadTipConfig.tipHeaderLeft = value"))
        assertTrue(readMenu.contains("ReadTipConfig.tipHeaderMiddle = value"))
        assertTrue(readMenu.contains("ReadTipConfig.tipHeaderRight = value"))
        assertTrue(readMenu.contains("postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))"))
    }

    @Test
    fun footerTipItemsCanBeCustomizedFromCurrentReadMenu() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val footerControls = layout.elementById("layout_tip_footer_controls")

        assertTrue(layout.elementById("layout_footer_tip_items").hasAncestor(footerControls))
        assertEquals(
            "@string/read_menu_footer_items",
            layout.elementById("tv_layout_footer_tip_items_title").androidAttr("text")
        )
        listOf(
            "ll_layout_footer_tip_left",
            "ll_layout_footer_tip_middle",
            "ll_layout_footer_tip_right",
            "tv_layout_footer_tip_left_value",
            "tv_layout_footer_tip_middle_value",
            "tv_layout_footer_tip_right_value"
        ).forEach { id ->
            assertTrue(layout.elementById(id).hasAncestor(layout.elementById("layout_footer_tip_items")))
        }
        assertTrue(readMenu.contains("ReadTipConfig.tipFooterLeft = value"))
        assertTrue(readMenu.contains("ReadTipConfig.tipFooterMiddle = value"))
        assertTrue(readMenu.contains("ReadTipConfig.tipFooterRight = value"))
        assertTrue(readMenu.contains("postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))"))
    }

    @Test
    fun layoutPanelUsesCompactReferenceSizing() {
        val layout = readMenuLayout()

        assertEquals("18sp", layout.elementById("tv_panel_layout_title").androidAttr("textSize"))
        assertEquals("14sp", layout.elementById("tv_layout_letter_spacing_label").androidAttr("textSize"))
        assertEquals("14sp", layout.elementById("tv_layout_padding_top_label").androidAttr("textSize"))
    }

    @Test
    fun titleMarginAdjustPopupStacksControlsAwayFromPreview() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("val useTitleStackLayout = showTitleMode"))
        assertTrue(readMenu.contains("syncLayoutMarginSpinboxLayout(showHorizontal, useTitleStackLayout)"))
        assertTrue(readMenu.contains("private fun syncLayoutMarginSpinboxLayout(\n        useBodyCrossLayout: Boolean,\n        useTitleStackLayout: Boolean\n    )"))
        assertTrue(readMenu.contains("LayoutMarginAdjustMode.Title -> 352.dpToPx()"))
        assertTrue(readMenu.contains("topToBottom = R.id.layout_margin_spinbox_top"))
        assertTrue(readMenu.contains("layoutMarginTitleSize.updateLayoutParams<ConstraintLayout.LayoutParams>"))
        assertTrue(readMenu.contains("topToBottom = R.id.layout_margin_adjust_preview"))
        assertTrue(readMenu.contains("llLayoutMarginTitleMode.updateLayoutParams<ConstraintLayout.LayoutParams>"))
    }

    @Test
    fun bodyMarginAdjustPopupReflowsFourSpinboxesAroundPreview() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("syncLayoutMarginSpinboxLayout(showHorizontal, useTitleStackLayout)"))
        assertTrue(readMenu.contains("useBodyCrossLayout: Boolean"))
        assertTrue(readMenu.contains("useTitleStackLayout: Boolean"))
        assertTrue(readMenu.contains("LayoutMarginAdjustMode.Body -> 300.dpToPx()"))
        assertTrue(readMenu.contains("layoutMarginSpinboxTop.updateLayoutParams<ConstraintLayout.LayoutParams>"))
        assertTrue(readMenu.contains("bottomToTop = R.id.layout_margin_adjust_preview"))
        assertTrue(readMenu.contains("layoutMarginSpinboxBottom.updateLayoutParams<ConstraintLayout.LayoutParams>"))
        assertTrue(readMenu.contains("topToBottom = R.id.layout_margin_adjust_preview"))
        assertTrue(readMenu.contains("layoutMarginSpinboxLeft.updateLayoutParams<ConstraintLayout.LayoutParams>"))
        assertTrue(readMenu.contains("endToStart = R.id.layout_margin_adjust_preview"))
        assertTrue(readMenu.contains("layoutMarginSpinboxRight.updateLayoutParams<ConstraintLayout.LayoutParams>"))
        assertTrue(readMenu.contains("startToEnd = R.id.layout_margin_adjust_preview"))
    }

    @Test
    fun layoutAdjustOverlayUsesBottomTabFrostedGlassRecipe() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("layoutAdjustGlassShell(glassLevel, cornerRadius)"))
        assertTrue(readMenu.contains("layoutAdjustGlassFallbackShell(glassLevel, cornerRadius)"))
        assertTrue(readMenu.contains("return readBottomTabGlassShell(glassLevel, cornerRadius)"))
        assertTrue(readMenu.contains("return readBottomTabGlassFallbackShell(glassLevel, cornerRadius)"))
        assertTrue(readMenu.contains("refractionHeight = (12f + glassLevel * 8f).dpToPx()"))
        assertTrue(readMenu.contains("refractionOffset = (34f + glassLevel * 18f).dpToPx()"))
        assertTrue(readMenu.contains("blurRadius = (22f + glassLevel * 30f).dpToPx()"))
        assertTrue(readMenu.contains("tintAlpha = bottomTabGlassTintAlpha(glassLevel)"))
        assertFalse(
            Regex("""private fun bottomTabGlassShell\(glassLevel: Float\): GradientDrawable \{\s*return layoutAdjustGlassShell""")
                .containsMatchIn(readMenu)
        )
    }

    @Test
    fun layoutAdjustOverlayCapturesBlurredBackdropBeforeShowingPanel() {
        val layout = readMenuLayout()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val panel = layout.elementById("layout_margin_adjust_panel")
        val blurBackdrop = layout.elementById("layout_margin_adjust_blur_backdrop")
        val glassView = layout.elementById("layout_margin_adjust_glass_view")

        assertTrue(blurBackdrop.hasAncestor(panel))
        assertEquals("ImageView", blurBackdrop.tagName)
        assertEquals("0dp", blurBackdrop.androidAttr("layout_height"))
        assertTrue(blurBackdrop.isBefore(glassView))
        assertTrue(readMenu.contains("captureLayoutAdjustBackdrop()"))
        assertTrue(readMenu.contains("updateLayoutAdjustBlurBackdrop()"))
        assertTrue(readMenu.contains("clearLayoutAdjustBackdrop()"))
        assertTrue(readMenu.contains("layoutMarginAdjustBlurBackdrop.setImageBitmap"))
    }

    @Test
    fun fontWeightChangesApplyOnceOnSeekStopAndBuiltInFontsAreCached() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val chapterProvider = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/page/provider/ChapterProvider.kt").readText()
        val weightListener = readMenu.substringAfter("seekThemeFontWeight.setOnSeekBarChangeListener")
            .substringBefore("seekThemeTextSize.setOnSeekBarChangeListener")

        assertFalse(weightListener.contains("if (fromUser) setFontWeight(progress)"))
        assertTrue(weightListener.contains("override fun onStopTrackingTouch(seekBar: SeekBar)"))
        assertTrue(weightListener.contains("setFontWeight(seekBar.progress)"))
        assertTrue(chapterProvider.contains("builtInTypefaceCache"))
        assertTrue(chapterProvider.contains("private fun builtInTypeface(assetPath: String): Typeface"))
        assertTrue(chapterProvider.contains("BuiltInReadFonts.targetWeight"))
    }

    @Test
    fun advancedTitleDialogUsesRoomierSectionsAndActionSpacing() {
        val dialog = repoFile(
            "app/src/main/java/io/wanjuan/app/ui/book/read/config/AdvancedTitleConfigDialog.kt"
        ).readText()

        assertTrue(dialog.contains("(resources.displayMetrics.widthPixels * 0.94f).toInt()"))
        assertTrue(dialog.contains("(resources.displayMetrics.heightPixels * 0.84f).toInt()"))
        assertTrue(dialog.contains("setPadding(20.dpToPx(), 16.dpToPx(), 20.dpToPx(), 10.dpToPx())"))
        assertTrue(dialog.contains("fun sectionGap(heightDp: Int = 8)"))
        assertTrue(dialog.contains("val actionRow = LinearLayout(context).apply"))
        assertTrue(dialog.contains("setMargins(0, 0, 8.dpToPx(), 0)"))
        assertTrue(dialog.contains("setMargins(8.dpToPx(), 0, 0, 0)"))
    }

    @Test
    fun fontAndLayoutPanelsDoNotExposeTertiaryLayoutTabs() {
        val layoutXml = repoFile("app/src/main/res/layout/view_read_menu.xml").readText()
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertFalse(layoutXml.contains("ll_layout_tabs"))
        assertFalse(layoutXml.contains("layout_tab_font"))
        assertFalse(layoutXml.contains("layout_tab_spacing"))
        assertFalse(layoutXml.contains("layout_tab_style"))
        assertFalse(readMenu.contains("layoutTabFont.setOnClickListener"))
        assertFalse(readMenu.contains("layoutTabSpacing.setOnClickListener"))
        assertFalse(readMenu.contains("layoutTabStyle.setOnClickListener"))
        assertFalse(readMenu.contains("LayoutTab"))
        assertFalse(readMenu.contains("BottomTab.Font"))
    }

    @Test
    fun expandedPanelHeightAdaptsToContentBeforeCapping() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()

        assertTrue(readMenu.contains("applyAdaptivePanelHeight"))
        assertFalse(readMenu.contains("val targetHeight = if (tab == BottomTab.Layout || tab == BottomTab.Theme)"))
    }

    @Test
    fun expandedPanelDoesNotInsetScrollbarsWithBackgroundPadding() {
        val panelBackground = drawableRoot("bg_read_menu_panel")

        assertEquals("", panelBackground.elementByName("padding")?.androidAttr("right").orEmpty())
    }

    @Test
    fun themeCardsUseCompactPreviewSizing() {
        val cardLayout = parseXml(
            findRepoFile("app/src/main/res/layout/view_read_theme_card.xml")
                ?: findRepoFile("src/main/res/layout/view_read_theme_card.xml")
                ?: error("view_read_theme_card.xml not found")
        )

        assertEquals("70dp", cardLayout.elementById("theme_card_preview").androidAttr("layout_height"))
    }

    @Test
    fun themePresetCardsFitThreeFullItemsOnPhoneWidth() {
        val layout = readMenuLayout()
        val card = layout.elementById("theme_card_follow_system")

        assertEquals("96dp", card.androidAttr("layout_width"))
    }

    @Test
    fun themePanelUsesCompactReferenceSizing() {
        val layout = readMenuLayout()

        assertEquals("18sp", layout.elementById("tv_panel_theme_title").androidAttr("textSize"))
        assertEquals("14sp", layout.elementById("tv_theme_font_weight_label").androidAttr("textSize"))
        assertEquals("40dp", layout.elementById("seek_theme_font_weight").androidAttr("layout_height"))
        assertEquals("40dp", layout.elementById("seek_theme_text_size").androidAttr("layout_height"))
    }

    @Test
    fun legacyReadStyleSheetIsNotReachableFromReaderMenu() {
        val readMenu = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadMenu.kt").readText()
        val readActivity = repoFile("app/src/main/java/io/wanjuan/app/ui/book/read/ReadBookActivity.kt").readText()

        assertFalse(readMenu.contains("showReadStyle"))
        assertFalse(readMenu.contains("openReadStylePanel"))
        assertFalse(readActivity.contains("ReadStyleDialog"))
        assertFalse(repoFile("app/src/main/res/layout/dialog_read_book_style.xml").exists())
    }

    private fun readMenuLayout(): Element {
        val file = findRepoFile("app/src/main/res/layout/view_read_menu.xml")
            ?: findRepoFile("src/main/res/layout/view_read_menu.xml")
            ?: error("view_read_menu.xml not found")

        return parseXml(file)
    }

    private fun readActivityLayout(): Element {
        val file = findRepoFile("app/src/main/res/layout/activity_book_read.xml")
            ?: findRepoFile("src/main/res/layout/activity_book_read.xml")
            ?: error("activity_book_read.xml not found")

        return parseXml(file)
    }

    private fun mangaActivityLayout(): Element {
        val file = findRepoFile("app/src/main/res/layout/activity_manga.xml")
            ?: findRepoFile("src/main/res/layout/activity_manga.xml")
            ?: error("activity_manga.xml not found")

        return parseXml(file)
    }

    private fun drawableRoot(name: String): Element {
        val file = findRepoFile("app/src/main/res/drawable/$name.xml")
            ?: findRepoFile("src/main/res/drawable/$name.xml")
            ?: error("$name.xml not found")

        return parseXml(file)
    }

    private fun parseXml(file: File): Element {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(file).documentElement
    }

    private fun findRepoFile(relativePath: String): File? {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.isFile }
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }

    private fun Element.elementById(id: String): Element {
        if (androidAttr("id") == "@+id/$id" || androidAttr("id") == "@id/$id") {
            return this
        }
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                runCatching { return child.elementById(id) }
            }
        }
        error("Element with id $id not found")
    }

    private fun Element.elementByTag(tag: String): Element {
        if (androidAttr("tag") == tag) {
            return this
        }
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                runCatching { return child.elementByTag(tag) }
            }
        }
        error("Element with tag $tag not found")
    }

    private fun Element.elementByName(name: String): Element? {
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element && child.tagName == name) {
                return child
            }
        }
        return null
    }

    private fun Element.childElements(name: String): List<Element> {
        val children = childNodes
        return buildList {
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is Element && child.tagName == name) {
                    add(child)
                }
            }
        }
    }

    private fun Element.elementsByAndroidText(text: String): List<Element> {
        val children = childNodes
        return buildList {
            if (androidAttr("text") == text) {
                add(this@elementsByAndroidText)
            }
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is Element) {
                    addAll(child.elementsByAndroidText(text))
                }
            }
        }
    }

    private fun Element.elementsByName(name: String): List<Element> {
        val children = childNodes
        return buildList {
            if (tagName == name) {
                add(this@elementsByName)
            }
            for (index in 0 until children.length) {
                val child = children.item(index)
                if (child is Element) {
                    addAll(child.elementsByName(name))
                }
            }
        }
    }

    private fun Element.hasAncestor(ancestor: Element): Boolean {
        return generateSequence(parentNode) { it.parentNode }
            .filterIsInstance<Element>()
            .any { it === ancestor }
    }

    private fun Element.isBefore(other: Element): Boolean {
        val parent = parentNode
        require(parent === other.parentNode) { "Elements do not share a parent" }
        val children = parent.childNodes
        for (index in 0 until children.length) {
            when (children.item(index)) {
                this -> return true
                other -> return false
            }
        }
        error("Elements not found under parent")
    }

    private fun Element.androidAttr(name: String): String =
        getAttributeNS(ANDROID_NAMESPACE, name)

    private fun Element.appAttr(name: String): String =
        getAttributeNS(APP_NAMESPACE, name)

    private fun assertMoreTransparentGlassRecipe(source: String) {
        listOf(
            "0.34f + glassLevel * 0.12f",
            "0.16f + glassLevel * 0.09f",
            "0.28f + glassLevel * 0.10f",
            "0.11f + glassLevel * 0.07f",
            "0.20f + glassLevel * 0.08f",
            "0.07f + glassLevel * 0.05f",
            "0.36f + glassLevel * 0.10f",
            "0.14f + glassLevel * 0.10f",
            "(0.12f + glassLevel * 0.14f).coerceAtMost(0.26f)",
            "(0.06f + glassLevel * 0.10f).coerceAtMost(0.16f)"
        ).forEach { expected ->
            assertTrue("Missing transparent glass recipe value: $expected", source.contains(expected))
        }
        listOf(
            "0.42f + glassLevel * 0.14f",
            "0.22f + glassLevel * 0.12f",
            "0.35f + glassLevel * 0.13f",
            "0.26f + glassLevel * 0.11f",
            "0.10f + glassLevel * 0.07f",
            "0.46f + glassLevel * 0.14f",
            "0.20f + glassLevel * 0.14f",
            "(0.16f + glassLevel * 0.18f).coerceAtMost(0.34f)",
            "(0.08f + glassLevel * 0.14f).coerceAtMost(0.22f)",
            "0.52f + glassLevel * 0.18f",
            "0.28f + glassLevel * 0.16f",
            "0.44f + glassLevel * 0.16f",
            "0.20f + glassLevel * 0.12f",
            "0.34f + glassLevel * 0.14f",
            "0.14f + glassLevel * 0.10f",
            "0.58f + glassLevel * 0.16f",
            "0.26f + glassLevel * 0.18f",
            "(0.22f + glassLevel * 0.22f).coerceAtMost(0.44f)",
            "(0.12f + glassLevel * 0.18f).coerceAtMost(0.30f)"
        ).forEach { oldValue ->
            assertFalse("Old opaque glass recipe value is still present: $oldValue", source.contains(oldValue))
        }
    }

    private fun String.camelCaseBindingName(): String {
        return split('_').mapIndexed { index, segment ->
            if (index == 0) segment else segment.replaceFirstChar { it.uppercase() }
        }.joinToString("")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
    }
}
