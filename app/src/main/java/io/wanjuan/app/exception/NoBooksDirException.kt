package io.wanjuan.app.exception

import io.wanjuan.app.R
import splitties.init.appCtx

class NoBooksDirException: NoStackTraceException(appCtx.getString(R.string.no_books_dir))