package io.wanjuan.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuiltInReadFontsTest {

    @Test
    fun weightProgressFiftyMapsToRegularWeight() {
        assertEquals(300, BuiltInReadFonts.targetWeight(0))
        assertEquals(400, BuiltInReadFonts.targetWeight(50))
        assertEquals(900, BuiltInReadFonts.targetWeight(100))
    }

    @Test
    fun movedReadFontAssetsAreNotLoadableBuiltIns() {
        val movedFonts = listOf(
            BuiltInReadFonts.HARMONYOS_SANS_SC,
            BuiltInReadFonts.HARMONYOS_SANS_SC_THIN,
            BuiltInReadFonts.HARMONYOS_SANS_SC_LIGHT,
            BuiltInReadFonts.HARMONYOS_SANS_SC_MEDIUM,
            BuiltInReadFonts.HARMONYOS_SANS_SC_BOLD,
            BuiltInReadFonts.HARMONYOS_SANS_SC_BLACK,
            BuiltInReadFonts.SOURCE_HAN_SANS_CN,
            BuiltInReadFonts.SOURCE_HAN_SANS_SC_VF,
            BuiltInReadFonts.WEN_YUAN_SANS_SC_VF,
            BuiltInReadFonts.MI_SANS_VF,
            BuiltInReadFonts.ALIMAMA_FANG_YUAN_TI_VF,
            BuiltInReadFonts.LXGW_WENKAI_SCREEN,
            BuiltInReadFonts.LXGW_NEO_XIHEI,
            BuiltInReadFonts.LXGW_FASMART_GOTHIC,
            BuiltInReadFonts.LXGW_ZHENKAI_GB
        )

        movedFonts.forEach { assetPath ->
            assertNull(BuiltInReadFonts.assetPath(BuiltInReadFonts.uri(assetPath)))
            assertNull(BuiltInReadFonts.weightPlan(BuiltInReadFonts.uri(assetPath), 500))
            assertNull(BuiltInReadFonts.weightPlan(assetPath, 500))
        }
    }
}
