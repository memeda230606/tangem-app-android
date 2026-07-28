package com.tangem.data.visa

import android.util.Log
import com.tangem.utils.logging.TangemLogger
import java.util.concurrent.atomic.AtomicLong

/** Request/response-style diagnostics for local Visa and Tangem Pay fixtures. */
internal object MockServerLogger {

    private val logger = TangemLogger.withTag(TAG)
    private val requestCounter = AtomicLong()

    fun <T> respond(operation: String, details: String = "", block: () -> T): T {
        val requestId = requestCounter.incrementAndGet()
        info(message(requestId, "request", operation, details))

        return try {
            block().also {
                info(message(requestId, "response", operation, "result=success"))
            }
        } catch (throwable: Throwable) {
            error(
                message = message(
                    requestId,
                    "response",
                    operation,
                    "result=failure type=${throwable::class.simpleName}",
                ),
                throwable = throwable,
            )
            throw throwable
        }
    }

    suspend fun <T> respondSuspend(operation: String, details: String = "", block: suspend () -> T): T {
        val requestId = requestCounter.incrementAndGet()
        info(message(requestId, "request", operation, details))

        return try {
            block().also {
                info(message(requestId, "response", operation, "result=success"))
            }
        } catch (throwable: Throwable) {
            error(
                message = message(
                    requestId,
                    "response",
                    operation,
                    "result=failure type=${throwable::class.simpleName}",
                ),
                throwable = throwable,
            )
            throw throwable
        }
    }

    fun event(name: String, details: String = "") {
        info(
            buildString {
                append("phase=event event=")
                append(name)
                if (details.isNotBlank()) {
                    append(' ')
                    append(details)
                }
            },
        )
    }

    private fun info(message: String) {
        Log.i(TAG, message)
        logger.i(message)
    }

    private fun error(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
        logger.e(message, throwable)
    }

    private fun message(requestId: Long, phase: String, operation: String, details: String): String {
        return buildString {
            append("requestId=")
            append(requestId)
            append(" phase=")
            append(phase)
            append(" operation=")
            append(operation)
            if (details.isNotBlank()) {
                append(' ')
                append(details)
            }
        }
    }

    const val TAG = "TangemMockServer"
}
