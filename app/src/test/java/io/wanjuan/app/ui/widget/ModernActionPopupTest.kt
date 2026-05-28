package io.wanjuan.app.ui.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModernActionPopupTest {

    @Test
    fun checkedMenuActionsUseThemeHoverBackground() {
        val source = repoFile("app/src/main/java/io/wanjuan/app/ui/widget/ModernActionPopup.kt").readText()
        val actionBlock = source.substringAfter("data class Action(")
            .substringBefore("fun show(")
        val menuBlock = source.substringAfter("fun showFromMenu(")
            .substringBefore("private fun createContent(")
        val itemBlock = source.substringAfter("private fun LinearLayout.createItem(")
            .substringBefore("private fun measurePopupSize(")

        assertTrue(actionBlock.contains("val checked: Boolean = false"))
        assertTrue(menuBlock.contains("checked = item.isChecked"))
        assertTrue(itemBlock.contains("val selectedHoverColor = ColorUtils.adjustAlpha(context.accentColor"))
        assertTrue(itemBlock.contains("isSelected = action.checked"))
        assertTrue(itemBlock.contains("if (action.checked) selectedHoverColor else Color.TRANSPARENT"))
        assertTrue(itemBlock.contains("if (action.checked) selectedHoverColor else ContextCompat.getColor(context, R.color.background_menu)"))
        assertFalse(menuBlock.contains("Action(item.title.toString()) { onClick(item) }"))
    }

    private fun repoFile(relativePath: String): File {
        return generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: File(relativePath)
    }
}
