package io.wanjuan.app.ui.association

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.wanjuan.app.R
import io.wanjuan.app.base.BaseViewModel
import io.wanjuan.app.constant.AppLog

sealed interface ImportPreviewState {
    data object Loading : ImportPreviewState
    data object Ready : ImportPreviewState
    data object Saving : ImportPreviewState
    data object Finished : ImportPreviewState
    data class Error(val message: String) : ImportPreviewState
}

abstract class ImportPreviewViewModel<T>(app: Application) : BaseViewModel(app) {
    val allSources = arrayListOf<T>()
    val selectStatus = arrayListOf<Boolean>()
    private val previewState = MutableLiveData<ImportPreviewState>()
    val state: LiveData<ImportPreviewState> get() = previewState

    fun load(text: String) {
        if (state.value != null) return
        previewState.value = ImportPreviewState.Loading
        execute {
            preparePreview(text)
        }.onSuccess {
            previewState.value = if (allSources.isEmpty()) {
                ImportPreviewState.Error(context.getString(R.string.wrong_format))
            } else {
                ImportPreviewState.Ready
            }
        }.onError { reportError(it) }
    }

    fun confirm() {
        if (state.value != ImportPreviewState.Ready || selectStatus.none { it }) return
        previewState.value = ImportPreviewState.Saving
        execute { saveSelection() }
            .onSuccess { previewState.value = ImportPreviewState.Finished }
            .onError { reportError(it) }
    }

    private fun reportError(error: Throwable) {
        val message = "ImportError:${error.localizedMessage ?: error.javaClass.simpleName}"
        AppLog.put(message, error)
        previewState.value = ImportPreviewState.Error(message)
    }

    protected abstract suspend fun preparePreview(text: String)
    protected abstract suspend fun saveSelection()
}
