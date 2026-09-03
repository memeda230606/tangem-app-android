package com.tangem.tap.features.intentHandler.handlers

import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import com.tangem.common.routing.entity.InitScreenLaunchMode
import com.tangem.tap.features.intentHandler.handlers.BackgroundScanIntentHandler.NdefOnlyIntentResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

internal class BackgroundScanIntentHandlerTest {

    private var elapsedRealtime = 0L
    private val handler = BackgroundScanIntentHandler { elapsedRealtime }

    @Test
    fun `Tangem NDEF deeplink is consumed while Card SDK is visible`() {
        val intent = tangemNfcIntent()
        handler.onCardSdkVisibilityChanged(true)

        val isConsumed = handler.consumeIfDuplicateNfcDeeplink(intent)

        assertTrue(isConsumed)
        verify {
            intent.action = null
            intent.data = null
        }
    }

    @Test
    fun `Tangem NDEF deeplink is consumed immediately after Card SDK is hidden`() {
        handler.onCardSdkVisibilityChanged(true)
        elapsedRealtime = 1_000L
        handler.onCardSdkVisibilityChanged(false)
        elapsedRealtime = 2_999L

        assertTrue(handler.consumeIfDuplicateNfcDeeplink(tangemNfcIntent()))
    }

    @Test
    fun `Tangem NDEF deeplink is allowed after cooldown`() {
        handler.onCardSdkVisibilityChanged(true)
        elapsedRealtime = 1_000L
        handler.onCardSdkVisibilityChanged(false)
        elapsedRealtime = 3_001L

        assertFalse(handler.consumeIfDuplicateNfcDeeplink(tangemNfcIntent()))
    }

    @Test
    fun `ordinary Tangem deeplink is not consumed`() {
        val intent = intentWithUri("https", "www.tangem.com", "/news/article")
        handler.onCardSdkVisibilityChanged(true)

        assertFalse(handler.consumeIfDuplicateNfcDeeplink(intent))
    }

    @Test
    fun `supported NDEF tag opens app without card scan in NDEF-only mode`() {
        val ndefOnlyHandler = BackgroundScanIntentHandler(isNdefOnlyMode = true)
        val intent = nfcIntent(
            action = NfcAdapter.ACTION_NDEF_DISCOVERED,
            host = "你的域名.com",
            path = "/download",
        )

        val launchMode = ndefOnlyHandler.getInitScreenLaunchMode(intent)

        assertEquals(InitScreenLaunchMode.Standard, launchMode)
        verify {
            intent.action = null
            intent.data = null
        }
    }

    @Test
    fun `legacy NDEF tag is rejected because secure cards require authenticated reader mode`() {
        val ndefOnlyHandler = BackgroundScanIntentHandler(isNdefOnlyMode = true)
        val intent = nfcIntent(
            action = NfcAdapter.ACTION_NDEF_DISCOVERED,
            host = "你的域名.com",
            path = "/download",
        )

        assertEquals(NdefOnlyIntentResult.Rejected, ndefOnlyHandler.consumeNfcIntentInNdefOnlyMode(intent))
    }

    @Test
    fun `legacy writer marker is rejected because it is not cryptographically authenticated`() {
        val ndefOnlyHandler = BackgroundScanIntentHandler(isNdefOnlyMode = true)
        val supportedUri = uri("https", "wallet.example.com", "/new-download")
        val uriRecord = mockk<NdefRecord>(relaxed = true) {
            every { toUri() } returns supportedUri
        }
        val writerMarker = mockk<NdefRecord> {
            every { tnf } returns NdefRecord.TNF_EXTERNAL_TYPE
            every { type } returns "com.niubtmd:nfc-card".toByteArray(StandardCharsets.US_ASCII)
            every { payload } returns "1".toByteArray(StandardCharsets.US_ASCII)
        }
        val ndefMessage = mockk<NdefMessage> {
            every { records } returns arrayOf(uriRecord, writerMarker)
        }
        val intent = mockk<Intent>(relaxed = true) {
            every { action } returns NfcAdapter.ACTION_NDEF_DISCOVERED
            every { data } returns null
            @Suppress("DEPRECATION")
            every { getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) } returns arrayOf(ndefMessage)
        }

        assertEquals(NdefOnlyIntentResult.Rejected, ndefOnlyHandler.consumeNfcIntentInNdefOnlyMode(intent))
    }

    @Test
    fun `other NDEF tag is rejected without card scan in NDEF-only mode`() {
        val ndefOnlyHandler = BackgroundScanIntentHandler(isNdefOnlyMode = true)
        val intent = nfcIntent(
            action = NfcAdapter.ACTION_NDEF_DISCOVERED,
            path = "/ndef/demo/v1/another-card",
        )

        assertEquals(NdefOnlyIntentResult.Rejected, ndefOnlyHandler.consumeNfcIntentInNdefOnlyMode(intent))
    }

    @Test
    fun `official Tangem card link is rejected and cannot continue to web routing in NDEF-only mode`() {
        val ndefOnlyHandler = BackgroundScanIntentHandler(isNdefOnlyMode = true)
        val intent = nfcIntent(
            action = NfcAdapter.ACTION_NDEF_DISCOVERED,
            path = "/ndef/card",
        )

        assertEquals(NdefOnlyIntentResult.Rejected, ndefOnlyHandler.consumeNfcIntentInNdefOnlyMode(intent))
        verify {
            intent.action = null
            intent.data = null
        }
    }

    @Test
    fun `technology NFC intent is rejected without card scan in NDEF-only mode`() {
        val ndefOnlyHandler = BackgroundScanIntentHandler(isNdefOnlyMode = true)
        val intent = nfcIntent(
            action = NfcAdapter.ACTION_TECH_DISCOVERED,
            host = "你的域名.com",
            path = "/download",
        )

        assertEquals(NdefOnlyIntentResult.Rejected, ndefOnlyHandler.consumeNfcIntentInNdefOnlyMode(intent))
    }

    @Test
    fun `NDEF-only handling is disabled for other builds`() {
        val intent = nfcIntent(
            action = NfcAdapter.ACTION_NDEF_DISCOVERED,
            host = "你的域名.com",
            path = "/download",
        )

        assertEquals(NdefOnlyIntentResult.NotHandled, handler.consumeNfcIntentInNdefOnlyMode(intent))
    }

    private fun tangemNfcIntent(): Intent = intentWithUri("https", "www.tangem.com", "/ndef/demo/v1/wallet")

    private fun nfcIntent(action: String, path: String, host: String = "www.tangem.com"): Intent {
        val intent = intentWithUri("https", host, path)
        every { intent.action } returns action

        return intent
    }

    private fun intentWithUri(scheme: String, host: String, path: String): Intent {
        val uri = uri(scheme, host, path)

        return mockk(relaxed = true) {
            every { data } returns uri
        }
    }

    private fun uri(scheme: String, host: String, path: String): Uri {
        return mockk {
            every { this@mockk.scheme } returns scheme
            every { this@mockk.host } returns host
            every { this@mockk.path } returns path
            every { this@mockk.query } returns null
            every { this@mockk.fragment } returns null
        }
    }
}
