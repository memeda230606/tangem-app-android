package com.tangem.tap.domain.sdk.mocks

import android.util.Log
import com.tangem.utils.logging.TangemLogger

/** Structured, non-sensitive diagnostics for the ordinary-NFC mocked flow. */
internal object NfcDemoLogger {

    private val logger = TangemLogger.withTag(TAG)

    fun event(name: String, details: String = "") {
        val message = buildMessage(name, details)
        Log.i(TAG, message)
        logger.i(message)
    }

    fun warning(name: String, details: String = "") {
        val message = buildMessage(name, details)
        Log.w(TAG, message)
        logger.w(message)
    }

    private fun buildMessage(name: String, details: String): String {
        return buildString {
            append("event=")
            append(name)
            if (details.isNotBlank()) {
                append(' ')
                append(details)
            }
        }
    }

    const val TAG = "TangemNfcDemo"
}
