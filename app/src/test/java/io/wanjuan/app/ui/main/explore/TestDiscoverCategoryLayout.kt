package io.wanjuan.app.ui.main.explore

import io.wanjuan.app.data.entities.rule.ExploreKind
import io.wanjuan.app.data.entities.rule.FlexChildStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDiscoverCategoryLayout {
    @Test
    fun flatSourcesDoNotGainAnArtificialGroup() {
        val items = build(link("Recent"), link("Popular"))
        assertTrue(DiscoverCategoryLayout.groups(items).isEmpty())
        assertEquals(listOf("Recent", "Popular"), DiscoverCategoryLayout.tags(items, null, null).map { it.text })
    }

    @Test
    fun adjacentParentAndSectionHeadingsPreserveBothLevels() {
        val items = build(
            heading("Books"), heading("Genres"), link("Fiction"),
            heading("Rankings"), link("Weekly"),
            heading("Photos"), heading("Nature"), link("Forests"),
            heading("Cities"), link("Streets")
        )
        assertEquals(listOf("Books", "Photos"), DiscoverCategoryLayout.groups(items))
        assertEquals(listOf("Genres", "Rankings"), DiscoverCategoryLayout.subgroups(items, "Books"))
        assertEquals(listOf("Nature", "Cities"), DiscoverCategoryLayout.subgroups(items, "Photos"))
        assertEquals(listOf("Forests"), DiscoverCategoryLayout.tags(items, "Photos", "Nature").map { it.text })
    }

    @Test
    fun independentHeadingsRemainFlatGroups() {
        val items = build(heading("Books"), link("Fiction"), heading("Photos"), link("Nature"))
        assertEquals(listOf("Books", "Photos"), DiscoverCategoryLayout.groups(items))
        assertTrue(DiscoverCategoryLayout.subgroups(items, "Books").isEmpty())
    }

    @Test
    fun ungroupedEntrancesStayInNavigationAndControlsStayInSettings() {
        val items = build(link("Home"), select("Global"), heading("Books"), link("Fiction"), select("Sort"),
            heading("Photos"), link("Nature"), select("Format"))
        assertEquals(listOf("Other", "Books", "Photos"), DiscoverCategoryLayout.groups(items))
        assertEquals(listOf("Home"), DiscoverCategoryLayout.tags(items, "Other", null).map { it.text })
        assertEquals(listOf("Global", "Sort"), DiscoverCategoryLayout.settings(items, "Books", null).map { it.text })
    }

    @Test
    fun sectionSettingsDoNotLeakBetweenSections() {
        val items = build(heading("Photos"), heading("Nature"), link("Forests"), select("Season"),
            heading("Cities"), link("Streets"), select("Country"))
        assertEquals(listOf("Country"), DiscoverCategoryLayout.settings(items, "Photos", "Cities").map { it.text })
    }

    @Test
    fun settingsOnlySectionsRemainAvailableWithinTheirParent() {
        val items = build(heading("Books"), heading("Genres"), link("Fiction"),
            heading("Settings"), select("Sort"))
        assertEquals(listOf("Sort"), DiscoverCategoryLayout.settings(items, "Books", "Genres").map { it.text })
    }

    @Test
    fun headingsWithOnlyControlsDoNotCreateEmptyNavigationGroups() {
        val items = build(heading("Books"), link("Fiction"), heading("Settings"), select("Account"))
        assertEquals(listOf("Books"), DiscoverCategoryLayout.groups(items))
        assertEquals(listOf("Account"), DiscoverCategoryLayout.settings(items, "Books", null).map { it.text })
    }

    private fun build(vararg kinds: ExploreKind) = DiscoverCategoryLayout.build(kinds.toList(), "Other")
    private fun link(title: String) = ExploreKind(title, "/$title")
    private fun select(title: String) = ExploreKind(title, type = ExploreKind.Type.select)
    private fun heading(title: String) = ExploreKind(title, style = FlexChildStyle(layout_flexBasisPercent = 1f))
}
