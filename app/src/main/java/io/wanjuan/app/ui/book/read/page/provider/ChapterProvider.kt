package io.wanjuan.app.ui.book.read.page.provider

import android.graphics.Paint
import android.graphics.Paint.FontMetrics
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.os.postDelayed
import io.wanjuan.app.constant.AppLog
import io.wanjuan.app.constant.EventBus
import io.wanjuan.app.data.entities.Book
import io.wanjuan.app.data.entities.BookChapter
import io.wanjuan.app.help.book.BookContent
import io.wanjuan.app.help.book.isEpub
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.help.config.BuiltInReadFonts
import io.wanjuan.app.help.config.ReadBookConfig
import io.wanjuan.app.model.ReadBook
import io.wanjuan.app.ui.book.read.page.entities.TextChapter
import io.wanjuan.app.utils.RealPathUtil
import io.wanjuan.app.utils.buildMainHandler
import io.wanjuan.app.utils.dpToPx
import io.wanjuan.app.utils.isContentScheme
import io.wanjuan.app.utils.isPad
import io.wanjuan.app.utils.postEvent
import io.wanjuan.app.utils.spToPx
import io.wanjuan.app.utils.textHeight
import kotlinx.coroutines.CoroutineScope
import splitties.init.appCtx
import androidx.core.net.toUri

/**
 * 解析内容生成章节和页面
 */
@Suppress("DEPRECATION", "ConstPropertyName")
object ChapterProvider {
    //用于图片字的替换
    const val srcReplaceStr = "袮" //▩▣ //这是不应该存在的汉字,会替换为祢，这个字符用来标记
    const val srcReplaceChar = '袮'
    const val srcReplacementChar = '祢'
    //用于评论按钮的替换
    const val reviewStr = "꧁"
    const val reviewChar = '꧁'
    const val indentChar = "　"

    @JvmStatic
    var viewWidth = 0
        private set

    @JvmStatic
    var viewHeight = 0
        private set

    @JvmStatic
    var paddingLeft = 0
        private set

    @JvmStatic
    var paddingTop = 0
        private set

    @JvmStatic
    var paddingRight = 0
        private set

    @JvmStatic
    var paddingBottom = 0
        private set

    @JvmStatic
    var visibleWidth = 0
        private set

    @JvmStatic
    var visibleHeight = 0
        private set

    @JvmStatic
    var visibleRight = 0
        private set

    @JvmStatic
    var visibleBottom = 0
        private set

    @JvmStatic
    var lineSpacingExtra = 0f
        private set

    @JvmStatic
    var paragraphSpacing = 0
        private set

    @JvmStatic
    var titleTopSpacing = 0
        private set

    @JvmStatic
    var titleBottomSpacing = 0
        private set

    @JvmStatic
    var indentCharWidth = 0f
        private set

    @JvmStatic
    var titlePaintTextHeight = 0f
        private set

    @JvmStatic
    var contentPaintTextHeight = 0f
        private set

    @JvmStatic
    var titlePaintFontMetrics = FontMetrics()

    @JvmStatic
    var contentPaintFontMetrics = FontMetrics()

    @JvmStatic
    var typeface: Typeface? = Typeface.DEFAULT
        private set

    @JvmStatic
    var titlePaint: TextPaint = TextPaint()

    @JvmStatic
    var contentPaint: TextPaint = TextPaint()

    @JvmStatic
    var reviewPaint: TextPaint = TextPaint()

    @JvmStatic
    var doublePage = false
        private set

    @JvmStatic
    var visibleRect = RectF()

    private val handler by lazy {
        buildMainHandler()
    }

    private var upViewSizeRunnable: Runnable? = null
    private val builtInTypefaceCache = mutableMapOf<String, Typeface>()

    private data class TypefacePlan(
        val typeface: Typeface?,
        val syntheticStrokeEm: Float = 0f,
    )

    init {
        upStyle()
    }

    fun getTextChapterAsync(
        scope: CoroutineScope,
        book: Book,
        bookChapter: BookChapter,
        displayTitle: String,
        bookContent: BookContent,
        chapterSize: Int,
    ): TextChapter {

        val textChapter = TextChapter(
            bookChapter,
            bookChapter.index, displayTitle,
            chapterSize,
            bookContent.sameTitleRemoved,
            bookChapter.isVip,
            bookChapter.isPay,
            bookContent.effectiveReplaceRules,
            ReadBook.chapterProgressPageBreakPosition(bookChapter.index)
        ).apply {
            createLayout(scope, book, bookContent)
        }

        return textChapter
    }

    /**
     * 更新样式
     */
    fun upStyle() {
        val textWeight = readTextWeight()
        val titleTypefacePlan = getTypefacePlan(
            ReadBookConfig.textFont,
            textWeight.coerceAtLeast(700)
        )
        val contentTypefacePlan = getTypefacePlan(ReadBookConfig.textFont, textWeight)
        typeface = contentTypefacePlan.typeface
        getPaints(titleTypefacePlan, contentTypefacePlan).let {
            titlePaint = it.first
            contentPaint = it.second
//            reviewPaint.color = contentPaint.color
//            reviewPaint.textSize = contentPaint.textSize * 0.45f
//            reviewPaint.textAlign = Paint.Align.CENTER
        }
        //间距
        lineSpacingExtra = ReadBookConfig.lineSpacingExtra / 10f
        paragraphSpacing = ReadBookConfig.paragraphSpacing
        titleTopSpacing = ReadBookConfig.titleTopSpacing.dpToPx()
        titleBottomSpacing = ReadBookConfig.titleBottomSpacing.dpToPx()
        val bodyIndent = ReadBookConfig.paragraphIndent
        indentCharWidth = if (bodyIndent.isNotEmpty()) {
            var indentWidth = StaticLayout.getDesiredWidth(bodyIndent, contentPaint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                indentWidth += contentPaint.letterSpacing * contentPaint.textSize
            }
            indentWidth / bodyIndent.length
        } else {
            0f
        }
        titlePaintTextHeight = titlePaint.textHeight
        contentPaintTextHeight = contentPaint.textHeight
        titlePaintFontMetrics = titlePaint.fontMetrics
        contentPaintFontMetrics = contentPaint.fontMetrics
        upLayout()
    }

    private fun getTypefacePlan(fontPath: String, targetWeight: Int): TypefacePlan {
        val builtInWeightPlan = BuiltInReadFonts.weightPlan(fontPath, targetWeight)
        if (builtInWeightPlan != null) {
            return kotlin.runCatching {
                val assetTypeface = builtInTypeface(builtInWeightPlan.assetPath)
                val weightedTypeface = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                    builtInWeightPlan.variable
                ) {
                    Typeface.create(assetTypeface, targetWeight.coerceIn(100, 900), false)
                } else {
                    assetTypeface
                }
                TypefacePlan(
                    typeface = weightedTypeface,
                    syntheticStrokeEm = builtInWeightPlan.syntheticStrokeEm,
                )
            }.getOrElse {
                ReadBookConfig.textFont = ""
                ReadBookConfig.save()
                TypefacePlan(Typeface.SANS_SERIF)
            }
        }
        val fallbackTypeface = getTypeface(fontPath)
        return TypefacePlan(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Typeface.create(fallbackTypeface, targetWeight.coerceIn(100, 900), false)
            } else {
                fallbackTypeface
            }
        )
    }

    private fun getTypeface(fontPath: String): Typeface? {
        return kotlin.runCatching {
            val builtInAssetPath = BuiltInReadFonts.assetPath(fontPath)
            when {
                builtInAssetPath != null -> builtInTypeface(builtInAssetPath)

                fontPath.isContentScheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    appCtx.contentResolver
                        .openFileDescriptor(fontPath.toUri(), "r")!!
                        .use {
                            Typeface.Builder(it.fileDescriptor).build()
                        }
                }

                fontPath.isContentScheme() -> {
                    Typeface.createFromFile(RealPathUtil.getPath(appCtx, fontPath.toUri()))
                }

                fontPath.isNotEmpty() -> Typeface.createFromFile(fontPath)
                else -> when (AppConfig.systemTypefaces) {
                    1 -> Typeface.SERIF
                    2 -> Typeface.MONOSPACE
                    else -> Typeface.DEFAULT
                }
            }
        }.getOrElse {
            ReadBookConfig.textFont = ""
            ReadBookConfig.save()
            Typeface.SANS_SERIF
        } ?: Typeface.DEFAULT
    }

    private fun builtInTypeface(assetPath: String): Typeface {
        return synchronized(builtInTypefaceCache) {
            builtInTypefaceCache.getOrPut(assetPath) {
                Typeface.createFromAsset(appCtx.assets, assetPath)
            }
        }
    }

    private fun getPaints(
        titleTypefacePlan: TypefacePlan,
        contentTypefacePlan: TypefacePlan,
    ): Pair<TextPaint, TextPaint> {
        // 字体统一处理
        val baseTypeface = contentTypefacePlan.typeface
        val bold = Typeface.create(baseTypeface, Typeface.BOLD)
        val normal = Typeface.create(baseTypeface, Typeface.NORMAL)
        val (titleFont, textFont) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Pair(
                titleTypefacePlan.typeface,
                contentTypefacePlan.typeface
            )
        } else {
            when (ReadBookConfig.textBold) {
                1 -> Pair(bold, bold)
                2 -> Pair(normal, normal)
                else -> Pair(bold, normal)
            }
        }

        //标题
        val tPaint = TextPaint()
        tPaint.color = ReadBookConfig.textColor
        tPaint.letterSpacing = ReadBookConfig.letterSpacing
        tPaint.typeface = titleFont
        tPaint.textSize = with(ReadBookConfig) { textSize + titleSize }.toFloat().spToPx()
        tPaint.isAntiAlias = true
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && AppConfig.optimizeRender) {
            tPaint.isLinearText = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            applySyntheticStroke(tPaint, titleTypefacePlan.syntheticStrokeEm)
        }
        //正文
        val cPaint = TextPaint()
        cPaint.color = ReadBookConfig.textColor
        cPaint.letterSpacing = ReadBookConfig.letterSpacing
        cPaint.typeface = textFont
        cPaint.textSize = ReadBookConfig.textSize.toFloat().spToPx()
        cPaint.isAntiAlias = true
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && AppConfig.optimizeRender) {
            cPaint.isLinearText = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            applySyntheticStroke(cPaint, contentTypefacePlan.syntheticStrokeEm)
        }
        return Pair(tPaint, cPaint)
    }

    private fun applySyntheticStroke(paint: TextPaint, strokeEm: Float) {
        if (strokeEm <= 0f) {
            return
        }
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeWidth = paint.textSize * strokeEm
    }

    private fun readTextWeight(): Int {
        return BuiltInReadFonts.targetWeight(ReadBookConfig.textWeight)
    }

    /**
     * 更新View尺寸
     */
    fun upViewSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        if (width != viewWidth || height != viewHeight) {
            if (ReadBook.book?.isEpub == true) {
                upViewSizeRunnable?.let {
                    handler.removeCallbacks(it)
                    upViewSizeRunnable = null
                }
                notifyViewSizeChange(width, height)
            } else if (width == viewWidth) {
                upViewSizeRunnable = handler.postDelayed(300) {
                    upViewSizeRunnable = null
                    notifyViewSizeChange(width, height)
                }
            } else {
                notifyViewSizeChange(width, height)
            }
        } else if (upViewSizeRunnable != null) {
            handler.removeCallbacks(upViewSizeRunnable!!)
            upViewSizeRunnable = null
        }
    }

    private fun notifyViewSizeChange(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        upLayout()
        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
    }

    /**
     * 更新绘制尺寸
     */
    fun upLayout() {
        when (AppConfig.doublePageHorizontal) {
            "0" -> doublePage = false
            "1" -> doublePage = true
            "2" -> {
                doublePage = (viewWidth > viewHeight)
                        && ReadBook.pageAnim() != 3
            }

            "3" -> {
                doublePage = (viewWidth > viewHeight || appCtx.isPad)
                        && ReadBook.pageAnim() != 3
            }
        }

        if (viewWidth <= 0 || viewHeight <= 0) {
            return
        }

        paddingLeft = ReadBookConfig.paddingLeft.dpToPx()
        paddingTop = ReadBookConfig.paddingTop.dpToPx()
        paddingRight = ReadBookConfig.paddingRight.dpToPx()
        paddingBottom = ReadBookConfig.paddingBottom.dpToPx()
        visibleWidth = if (doublePage) {
            viewWidth / 2 - paddingLeft - paddingRight
        } else {
            viewWidth - paddingLeft - paddingRight
        }
        //留1dp画最后一行下划线
        visibleHeight = viewHeight - paddingTop - paddingBottom
        visibleRight = viewWidth - paddingRight
        visibleBottom = paddingTop + visibleHeight

        if (paddingLeft >= visibleRight || paddingTop >= visibleBottom) {
            AppLog.put("边距设置过大，请重新设置", toast = true)
            setFallbackLayout()
        }

        visibleRect.set( //留余，让溢出时也显示
            paddingLeft.toFloat() - 10,
            paddingTop.toFloat() - 10,
            visibleRight.toFloat() + 10,
            visibleBottom.toFloat() + 10f.dpToPx() //下划线最远10dp
        )

    }

    private fun setFallbackLayout() {
        paddingLeft = 20.dpToPx()
        paddingTop = 5.dpToPx()
        paddingRight = 20.dpToPx()
        paddingBottom = 5.dpToPx()
        visibleWidth = if (doublePage) {
            viewWidth / 2 - paddingLeft - paddingRight
        } else {
            viewWidth - paddingLeft - paddingRight
        }
        //留1dp画最后一行下划线
        visibleHeight = viewHeight - paddingTop - paddingBottom
        visibleRight = viewWidth - paddingRight
        visibleBottom = paddingTop + visibleHeight
    }

}
