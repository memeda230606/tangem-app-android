package com.tangem.tap.features.intentHandler.handlers

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import com.tangem.common.routing.entity.InitScreenLaunchMode
import com.tangem.tap.domain.sdk.mocks.MockProvider
import com.tangem.tap.domain.sdk.mocks.NfcDemoLogger
import com.tangem.tap.domain.sdk.mocks.NfcDemoTag
import com.tangem.tap.domain.sdk.mocks.NfcDemoTapGate
import com.tangem.wallet.BuildConfig

/**
[REDACTED_AUTHOR]
 */
class BackgroundScanIntentHandler {

    private val nfcActions = arrayOf(
        NfcAdapter.ACTION_NDEF_DISCOVERED,
        NfcAdapter.ACTION_TECH_DISCOVERED,
        NfcAdapter.ACTION_TAG_DISCOVERED,
    )

    fun getInitScreenLaunchMode(intent: Intent?): InitScreenLaunchMode {
        return if (shouldOpenScanCard(intent)) {
            InitScreenLaunchMode.WithCardScan
        } else {
            InitScreenLaunchMode.Standard
        }
    }

    private fun shouldOpenScanCard(intent: Intent?): Boolean {
        if (intent == null || intent.action !in nfcActions) return false

        val tag = intent.getNfcTag()
        val demoTagUri = intent.findDemoTagUri(tag)
        val scenarioId = NfcDemoTag.scenarioId(demoTagUri)
        NfcDemoLogger.event(
            name = "nfc_intent_received",
            details = "action=${intent.action} kind=${if (scenarioId != null) "demo" else "physical"}",
        )

        if (scenarioId != null) {
            if (BuildConfig.NFC_DEMO_ENABLED && NfcDemoTapGate.consumeTag(scenarioId, isDemoTag = true)) {
                NfcDemoLogger.event(
                    name = "nfc_demo_gate_dispatch",
                    details = "scenario=$scenarioId consumed=true",
                )
                intent.action = null
                intent.data = null
                return false
            }

            val isDemoScenarioApplied =
                BuildConfig.NFC_DEMO_ENABLED && MockProvider.applyNfcDemoTag(demoTagUri)
            NfcDemoLogger.event(
                name = "nfc_demo_dispatch",
                details = "scenario=$scenarioId applied=$isDemoScenarioApplied",
            )
            intent.action = null
            intent.data = null
            return isDemoScenarioApplied
        }

        intent.action = null

        NfcDemoLogger.event("physical_tag_dispatch", "tagPresent=${tag != null}")
        return tag != null
    }

    private fun Intent.findDemoTagUri(tag: Tag?): String? {
        dataString?.takeIf(NfcDemoTag::isDemoTag)?.let { return it }

        val messages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                ?.filterIsInstance<NdefMessage>()
                ?.toTypedArray()
        }

        val intentUri = messages
            ?.asSequence()
            ?.flatMap { it.records.asSequence() }
            ?.mapNotNull { it.toUri()?.toString() }
            ?.firstOrNull(NfcDemoTag::isDemoTag)
        if (intentUri != null) return intentUri

        return runCatching { Ndef.get(tag)?.cachedNdefMessage }
            .getOrNull()
            ?.records
            ?.asSequence()
            ?.mapNotNull { it.toUri()?.toString() }
            ?.firstOrNull(NfcDemoTag::isDemoTag)
    }

    private fun Intent.getNfcTag(): Tag? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }
    }
}
