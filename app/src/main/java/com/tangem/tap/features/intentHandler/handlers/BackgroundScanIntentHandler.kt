package com.tangem.tap.features.intentHandler.handlers

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.SystemClock
import com.tangem.common.routing.entity.InitScreenLaunchMode

/**
[REDACTED_AUTHOR]
 */
class BackgroundScanIntentHandler(
    private val isNdefOnlyMode: Boolean = false,
    private val externalNdefScanController: ExternalNdefScanController? = null,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {

    private var isCardSdkVisible = false
    private var lastCardSdkHiddenAt: Long? = null

    private val nfcActions = arrayOf(
        NfcAdapter.ACTION_NDEF_DISCOVERED,
        NfcAdapter.ACTION_TECH_DISCOVERED,
        NfcAdapter.ACTION_TAG_DISCOVERED,
    )

    fun getInitScreenLaunchMode(intent: Intent?): InitScreenLaunchMode {
        if (consumeNfcIntentInNdefOnlyMode(intent) != NdefOnlyIntentResult.NotHandled) {
            return InitScreenLaunchMode.Standard
        }

        return if (shouldOpenScanCard(intent)) {
            InitScreenLaunchMode.WithCardScan
        } else {
            InitScreenLaunchMode.Standard
        }
    }

    /**
     * In the External build an NFC tag is only an app-entry marker. It must never start the Tangem Card SDK,
     * because a regular NDEF tag cannot answer Tangem APDU commands.
     */
    fun consumeNfcIntentInNdefOnlyMode(intent: Intent?): NdefOnlyIntentResult {
        if (!isNdefOnlyMode || intent == null || intent.action !in nfcActions) {
            return NdefOnlyIntentResult.NotHandled
        }

        // Secure cards expose no readable NDEF payload. They are accepted only by the authenticated
        // reader mode shown after the user taps the scan button.
        val result = NdefOnlyIntentResult.Rejected

        externalNdefScanController?.onNfcIntentResult(
            when (result) {
                NdefOnlyIntentResult.Accepted -> ExternalNdefScanController.Result.Accepted
                NdefOnlyIntentResult.Rejected -> ExternalNdefScanController.Result.Rejected
                NdefOnlyIntentResult.NotHandled -> error("NDEF-only intent unexpectedly not handled")
            },
        )

        // The NFC intent has been fully handled. Do not route its URL or start a real card scan afterwards.
        intent.action = null
        intent.data = null

        return result
    }

    /**
     * Remembers the Card SDK dialog lifecycle so an NDEF intent produced by the card that has just been scanned
     * is not handled as a new scan request.
     */
    fun onCardSdkVisibilityChanged(isVisible: Boolean) {
        if (isCardSdkVisible && !isVisible) {
            lastCardSdkHiddenAt = elapsedRealtime()
        }

        isCardSdkVisible = isVisible
    }

    /**
     * Consumes a Tangem NDEF link received while a scan is active or immediately after it has completed.
     */
    fun consumeIfDuplicateNfcDeeplink(intent: Intent): Boolean {
        if (!intent.data.isTangemNfcDeeplink()) return false

        val hiddenAt = lastCardSdkHiddenAt
        val receivedRightAfterScan = hiddenAt != null &&
            elapsedRealtime() - hiddenAt in 0..NFC_DEEPLINK_COOLDOWN_MILLIS
        val shouldConsume = isCardSdkVisible || receivedRightAfterScan

        if (shouldConsume) {
            intent.action = null
            intent.data = null
        }

        return shouldConsume
    }

    private fun shouldOpenScanCard(intent: Intent?): Boolean {
        if (intent == null || intent.action !in nfcActions) return false

        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        intent.action = null

        if (tag != null) {
            // The NFC intent has already been converted into WithCardScan. Its NDEF URL must not be routed again.
            intent.data = null
        }

        return tag != null
    }

    private fun android.net.Uri?.isTangemNfcDeeplink(): Boolean {
        if (this == null || scheme !in WEB_SCHEMES) return false

        val normalizedPath = path.orEmpty()

        return host?.lowercase() in TANGEM_HOSTS &&
            (normalizedPath == NFC_DEEPLINK_PATH || normalizedPath.startsWith("$NFC_DEEPLINK_PATH/"))
    }

    enum class NdefOnlyIntentResult {
        NotHandled,
        Accepted,
        Rejected,
    }

    private companion object {
        const val NFC_DEEPLINK_COOLDOWN_MILLIS = 2_000L
        const val NFC_DEEPLINK_PATH = "/ndef"
        val WEB_SCHEMES = setOf("http", "https")
        val TANGEM_HOSTS = setOf("tangem.com", "www.tangem.com")
    }
}
