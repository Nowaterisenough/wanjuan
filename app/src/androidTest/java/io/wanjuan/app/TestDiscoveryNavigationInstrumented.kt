package io.wanjuan.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.wanjuan.app.constant.PreferKey
import io.wanjuan.app.data.appDb
import io.wanjuan.app.data.entities.BookSource
import io.wanjuan.app.data.entities.rule.ExploreKind
import io.wanjuan.app.data.entities.rule.ExploreRule
import io.wanjuan.app.data.entities.rule.FlexChildStyle
import io.wanjuan.app.help.config.AppConfig
import io.wanjuan.app.ui.main.MainActivity
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.defaultSharedPreferences
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class TestDiscoveryNavigationInstrumented {
    @Test
    fun nestedCategoriesAndScriptUrlsLoadTheSelectedList() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.defaultSharedPreferences
        val previousMode = preferences.all[PreferKey.modernDiscoveryPage] as? Boolean
        val previousSource = AppConfig.modernDiscoverySourceUrl
        val requests = CopyOnWriteArrayList<String>()
        val server = ServerSocket(0)
        val origin = "http://127.0.0.1:${server.localPort}"
        val worker = thread(isDaemon = true, name = "discovery-fixture") {
            while (!server.isClosed) {
                runCatching {
                    server.accept().use { socket ->
                        val path = socket.getInputStream().bufferedReader().readLine().split(' ')[1]
                        requests.add(path)
                        val body = "<div class='book'><a href='/book$path'>分类测试样本</a></div>".toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
        }
        fun heading(title: String) = ExploreKind(title, style = FlexChildStyle(layout_flexBasisPercent = 1f))
        val kinds = listOf(
            heading("图书"), heading("文学"), ExploreKind("散文", "/essays"), ExploreKind("诗歌", "/poems"),
            heading("知识"), ExploreKind("科学", "/science"), ExploreKind("历史", "/history"),
            heading("图集"), heading("自然"), ExploreKind("山川", "{{ '/mountains?page=' + page }}"), ExploreKind("海洋", "/ocean"),
            heading("城市"), ExploreKind("街道", "/streets"), ExploreKind("建筑", "/buildings")
        )
        val source = BookSource(bookSourceUrl = origin, bookSourceName = "分类兼容测试",
            exploreUrl = "@js:" + GSON.toJson(kinds),
            ruleExplore = ExploreRule(bookList = ".book", name = "a@text", bookUrl = "a@href"))
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            appDb.bookSourceDao.insert(source)
            preferences.edit().putBoolean(PreferKey.modernDiscoveryPage, true).commit()
            AppConfig.modernDiscoverySourceUrl = origin
            scenario = ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity {
                it.findViewById<BottomNavigationView>(R.id.bottom_navigation_view).selectedItemId = R.id.menu_discovery
            }
            await(scenario) { root -> texts(root.findViewById(R.id.rv_discover_selects)) == listOf("图书", "图集") }
            click(scenario, R.id.rv_discover_selects, "图集")
            await(scenario) { root -> texts(root.findViewById(R.id.rv_discover_subgroups)) == listOf("自然", "城市") }
            await(scenario) { requests.any { it == "/mountains?page=1" } }
            click(scenario, R.id.rv_discover_subgroups, "城市")
            await(scenario) { root -> texts(root.findViewById(R.id.rv_discover_tags)) == listOf("街道", "建筑") }
            await(scenario) { requests.any { it == "/streets" } }
            scenario.recreate()
            await(scenario) { root -> texts(root.findViewById(R.id.rv_discover_tags)) == listOf("街道", "建筑") }
            scenario.onActivity { activity ->
                val root = activity.window.decorView
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val file = File(context.getExternalFilesDir(null), "source-compat/navigation.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            assertTrue(requests.isNotEmpty())
        } finally {
            scenario?.close()
            AppConfig.modernDiscoverySourceUrl = previousSource
            preferences.edit().apply {
                if (previousMode == null) remove(PreferKey.modernDiscoveryPage)
                else putBoolean(PreferKey.modernDiscoveryPage, previousMode)
            }.commit()
            appDb.bookSourceDao.delete(origin)
            val books = appDb.searchBookDao.getByBookUrls(requests.map { "$origin/book$it" })
            appDb.searchBookDao.delete(*books.toTypedArray())
            server.close()
            worker.join(1000)
        }
    }

    private fun click(scenario: ActivityScenario<MainActivity>, id: Int, text: String) {
        scenario.onActivity { activity ->
            val label = views(activity.findViewById(id)).filterIsInstance<TextView>().first { it.text.toString() == text }
            assertTrue(label.performClick())
        }
    }

    private fun await(scenario: ActivityScenario<MainActivity>, condition: (View) -> Boolean) {
        repeat(160) {
            var ready = false
            scenario.onActivity { ready = condition(it.window.decorView) }
            if (ready) return
            Thread.sleep(50)
        }
        throw AssertionError("Discovery UI did not reach the expected state")
    }

    private fun texts(view: View?): List<String> = views(view).filterIsInstance<TextView>()
        .filter { it.isShown }.map { it.text.toString() }.toList()

    private fun views(view: View?): Sequence<View> = sequence {
        if (view != null) {
            yield(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
        }
    }
}
