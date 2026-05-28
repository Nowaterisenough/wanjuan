package io.wanjuan.app.help.config

object BuiltInReadFonts {

    private const val ASSET_PREFIX = "asset://"

    const val HARMONYOS_SANS_SC = "font/harmonyos_sans_sc_regular.ttf"
    const val HARMONYOS_SANS_SC_THIN = "font/harmonyos_sans_sc_thin.ttf"
    const val HARMONYOS_SANS_SC_LIGHT = "font/harmonyos_sans_sc_light.ttf"
    const val HARMONYOS_SANS_SC_MEDIUM = "font/harmonyos_sans_sc_medium.ttf"
    const val HARMONYOS_SANS_SC_BOLD = "font/harmonyos_sans_sc_bold.ttf"
    const val HARMONYOS_SANS_SC_BLACK = "font/harmonyos_sans_sc_black.ttf"
    const val SOURCE_HAN_SANS_CN = "font/source_han_sans_cn_regular.otf"
    const val SOURCE_HAN_SANS_SC_VF = "font/source_han_sans_sc_vf.otf"
    const val WEN_YUAN_SANS_SC_VF = "font/wenyuan_sans_sc_vf.otf"
    const val MI_SANS_VF = "font/mi_sans_vf.ttf"
    const val ALIMAMA_FANG_YUAN_TI_VF = "font/alimama_fang_yuan_ti_vf.ttf"
    const val LXGW_WENKAI_SCREEN = "font/lxgw_wenkai_screen.ttf"
    const val LXGW_NEO_XIHEI = "font/lxgw_neo_xihei.ttf"
    const val LXGW_FASMART_GOTHIC = "font/lxgw_fasmart_gothic.ttf"
    const val LXGW_ZHENKAI_GB = "font/lxgw_zhenkai_gb_regular.ttf"

    data class WeightPlan(
        val assetPath: String,
        val variable: Boolean,
        val baseWeight: Int,
        val syntheticStrokeEm: Float,
    )

    fun uri(assetPath: String): String {
        return "$ASSET_PREFIX$assetPath"
    }

    fun assetPath(fontPath: String): String? {
        return null
    }

    fun targetWeight(progress: Int): Int {
        val value = progress.coerceIn(0, 100)
        return if (value <= 50) {
            300 + (value / 50f * 100f).toInt()
        } else {
            400 + ((value - 50) / 50f * 500f).toInt()
        }.coerceIn(300, 900)
    }

    fun weightPlan(fontPath: String, targetWeight: Int): WeightPlan? {
        return null
    }
}
