package io.wanjuan.app.ui.main.bookshelf

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookshelfGridLayoutTest {

    @Test
    fun mainBottomBarDecorationAppliesBottomSpaceToEveryItemInLastGridRow() {
        val viewExtensions = repoFile("app/src/main/java/io/wanjuan/app/utils/ViewExtensions.kt")
            .readText()

        assertTrue(
            "RecyclerView bottom bar spacing must detect GridLayoutManager so an incomplete " +
                    "last row does not stretch sibling item views.",
            viewExtensions.contains("parent.layoutManager as? GridLayoutManager")
        )
        assertTrue(
            "Grid layouts should apply bottom spacing to every item in the final row, not just " +
                    "the final adapter item.",
            viewExtensions.contains(
                "val lastRowIndex = layoutManager.spanSizeLookup.getSpanGroupIndex("
            ) &&
                    viewExtensions.contains("itemCount - 1") &&
                    viewExtensions.contains(
                        "val rowIndex = layoutManager.spanSizeLookup.getSpanGroupIndex(position, spanCount)"
                    ) &&
                    viewExtensions.contains("rowIndex == lastRowIndex -> bottomSpace")
        )
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
