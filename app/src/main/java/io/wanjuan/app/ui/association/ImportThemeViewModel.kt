package io.wanjuan.app.ui.association

import android.app.Application
import androidx.core.net.toUri
import io.wanjuan.app.R
import io.wanjuan.app.constant.AppConst
import io.wanjuan.app.exception.NoStackTraceException
import io.wanjuan.app.help.config.ThemeConfig
import io.wanjuan.app.help.http.decompressed
import io.wanjuan.app.help.http.newCallResponseBody
import io.wanjuan.app.help.http.okHttpClient
import io.wanjuan.app.help.http.text
import io.wanjuan.app.utils.GSON
import io.wanjuan.app.utils.fromJsonArray
import io.wanjuan.app.utils.fromJsonObject
import io.wanjuan.app.utils.isAbsUrl
import io.wanjuan.app.utils.isJsonArray
import io.wanjuan.app.utils.isJsonObject
import io.wanjuan.app.utils.isUri
import io.wanjuan.app.utils.readText
import splitties.init.appCtx

class ImportThemeViewModel(app: Application) : ImportPreviewViewModel<ThemeConfig.Config>(app) {

    val checkSources = arrayListOf<ThemeConfig.Config?>()

    override suspend fun saveSelection() {
        selectStatus.forEachIndexed { index, b ->
            if (b) {
                ThemeConfig.addConfig(allSources[index])
            }
        }
    }

    override suspend fun preparePreview(text: String) {
        importSourceAwait(text.trim())
        allSources.forEach { config ->
            val source = ThemeConfig.configList.find {
                it.themeName == config.themeName
            }
            checkSources.add(source)
            selectStatus.add(source == null || source != config)
        }
    }

    private suspend fun importSourceAwait(text: String) {
        when {
            text.isJsonObject() -> {
                GSON.fromJsonObject<ThemeConfig.Config>(text).getOrThrow().let {
                    allSources.add(it)
                }
            }

            text.isJsonArray() -> GSON.fromJsonArray<ThemeConfig.Config>(text).getOrThrow()
                .let { items ->
                    allSources.addAll(items)
                }

            text.isAbsUrl() -> {
                importSourceUrl(text)
            }

            text.isUri() -> {
                importSourceAwait(text.toUri().readText(appCtx))
            }

            else -> throw NoStackTraceException(context.getString(R.string.wrong_format))
        }
    }

    private suspend fun importSourceUrl(url: String) {
        okHttpClient.newCallResponseBody {
            if (url.endsWith("#requestWithoutUA")) {
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.decompressed().text().let {
            importSourceAwait(it)
        }
    }
}
