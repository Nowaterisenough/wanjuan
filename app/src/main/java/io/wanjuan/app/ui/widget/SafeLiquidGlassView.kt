package io.wanjuan.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import com.qmdeve.liquidglass.widget.LiquidGlassView

class SafeLiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LiquidGlassView(context, attrs, defStyleAttr) {

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // LiquidGlassView 1.0.3 rebuilds asynchronously here. If a posted
        // parameter update runs after that rebuild removes the inner glass
        // view, it crashes in the library. Callers re-apply config after
        // layout has settled, so avoid the unsafe rebuild path.
    }
}
