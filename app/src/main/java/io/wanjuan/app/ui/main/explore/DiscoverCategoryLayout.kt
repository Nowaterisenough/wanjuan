package io.wanjuan.app.ui.main.explore

import io.wanjuan.app.data.entities.rule.ExploreKind

internal object DiscoverCategoryLayout {
    fun build(
        kinds: List<ExploreKind>,
        otherTitle: String,
        blockedActions: Set<String> = emptySet()
    ): List<DiscoverTagItem> {
        var group: String? = null
        var subgroup: String? = null
        var nested = false
        val headings = mutableListOf<String>()
        val items = mutableListOf<DiscoverTagItem>()
        for (kind in kinds) {
            if (isHeading(kind)) {
                val title = label(kind).trim()
                    .replace(Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$"), "")
                    .replace(Regex("\\s{2,}"), " ")
                    .trim()
                if (title.isNotBlank()) headings.add(title)
                continue
            }
            val action = kind.action?.takeIf { it.isNotBlank() }
            val url = kind.url?.takeIf { it.isNotBlank() }
            val control = kind.type == ExploreKind.Type.select || kind.type == ExploreKind.Type.text
            val button = !control && action != null && (kind.type == ExploreKind.Type.button || url == null)
            if (!control && !button && url == null) continue
            if (headings.isNotEmpty()) {
                // Legacy flex layouts express a parent followed immediately by its first section.
                if (headings.size > 1) {
                    group = headings.first()
                    subgroup = headings.drop(1).distinct().joinToString(" / ")
                    nested = true
                } else if (nested) {
                    subgroup = headings.single()
                } else {
                    group = headings.single()
                    subgroup = null
                }
                headings.clear()
            }
            if (button && action in blockedActions) continue
            items.add(DiscoverTagItem(
                kind = if (button) kind.copy(type = ExploreKind.Type.button) else kind.copy(url = url),
                text = label(kind), isButton = button, group = group, subgroup = subgroup
            ))
        }
        val hasGroups = items.any { it.isNavigation && it.group != null }
        val grouped = items.map {
            if (hasGroups && it.isNavigation && it.group == null) it.copy(group = otherTitle) else it
        }
        val nestedGroups = grouped.filter { it.isNavigation && it.subgroup != null }.map { it.group }.toSet()
        val navigationGroups = groups(grouped)
        return grouped.map {
            if (!it.isNavigation && it.group !in navigationGroups) {
                it.copy(group = null, subgroup = null)
            } else if (!it.isNavigation && it.subgroup !in subgroups(grouped, it.group)) {
                it.copy(subgroup = null)
            } else if (it.isNavigation && it.group in nestedGroups && it.subgroup == null) {
                it.copy(subgroup = otherTitle)
            } else it
        }.distinctBy {
            listOf(it.group, it.subgroup, it.kind.type, it.kind.title, it.kind.url, it.kind.action, it.kind.loadMoreAction)
        }
    }

    fun groups(items: List<DiscoverTagItem>): List<String> =
        items.filter { it.isNavigation }.mapNotNull { it.group }.distinct()

    fun subgroups(items: List<DiscoverTagItem>, group: String?): List<String> =
        items.filter { it.isNavigation && it.group == group }.mapNotNull { it.subgroup }.distinct()

    fun tags(items: List<DiscoverTagItem>, group: String?, subgroup: String?): List<DiscoverTagItem> =
        items.filter { it.isNavigation && it.group == group && it.subgroup == subgroup }

    fun settings(items: List<DiscoverTagItem>, group: String?, subgroup: String?): List<DiscoverTagItem> =
        items.filter {
            !it.isNavigation && (it.group == null || it.group == group) &&
                (it.subgroup == null || it.subgroup == subgroup)
        }

    private val DiscoverTagItem.isNavigation: Boolean
        get() = !isButton && kind.type != ExploreKind.Type.select && kind.type != ExploreKind.Type.text &&
            !kind.url.isNullOrBlank()

    private fun isHeading(kind: ExploreKind): Boolean {
        if (!kind.url.isNullOrBlank() || !kind.action.isNullOrBlank() || kind.type != ExploreKind.Type.url) return false
        val style = kind.style()
        return style.layout_flexBasisPercent >= 0.95f ||
            (style.layout_flexGrow >= 1f && style.layout_flexBasisPercent < 0f)
    }

    private fun label(kind: ExploreKind): String {
        val name = kind.viewName
        return if (name != null && name.length in 3..28 && name.first() == '\'' && name.last() == '\'') {
            name.substring(1, name.length - 1)
        } else kind.title.ifBlank { kind.type }
    }
}
