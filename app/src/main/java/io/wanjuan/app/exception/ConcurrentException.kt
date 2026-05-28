@file:Suppress("unused")

package io.wanjuan.app.exception

/**
 * 并发限制
 */
class ConcurrentException(msg: String, val waitTime: Long) : NoStackTraceException(msg)