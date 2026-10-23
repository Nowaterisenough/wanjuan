package io.wanjuan.app.lib.cronet

import java.util.concurrent.CancellationException

internal object CronetSafety {
    fun <T> initialize(onFailure: (Throwable) -> Unit, create: () -> T): T? {
        return try {
            create()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
            null
        } catch (e: LinkageError) {
            onFailure(e)
            null
        }
    }

    fun <T : Any> request(
        cronet: () -> T?,
        fallback: () -> T,
        onFailure: (Throwable) -> Unit
    ): T {
        var failure: Throwable? = null
        val response = initialize({ failure = it; onFailure(it) }, cronet)
        if (response != null) return response
        // Keep fallback outside the Cronet try/catch: never execute the same HTTP request twice.
        return try {
            fallback()
        } catch (e: Exception) {
            failure?.takeIf { it !== e }?.let(e::addSuppressed)
            throw e
        }
    }
}
