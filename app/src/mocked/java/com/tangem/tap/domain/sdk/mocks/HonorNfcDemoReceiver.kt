package com.tangem.tap.domain.sdk.mocks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import com.tangem.tap.MainActivity

/**
 * Bridges MagicOS' OEM NDEF broadcast to the regular mocked NFC intent flow.
 *
 * The receiver deliberately lives in the mocked source set. The OEM action is not protected,
 * so it must never become an entry point in a production wallet build.
 */
class HonorNfcDemoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HONOR_NDEF_DISCOVERED) return

        val tag = intent.getNfcTag()
        val uri = intent.findDemoTagUri(tag)
        val resolvedUri = uri ?: NfcDemoTag.WEB_URI_PREFIX + DEFAULT_SCENARIO

        NfcDemoLogger.event(
            name = "honor_nfc_broadcast_received",
            details = buildString {
                append("uri=")
                append(uri ?: "missing")
                append(" fallback=")
                append(uri == null)
                append(" extras=")
                append(intent.extras?.keySet()?.sorted()?.joinToString().orEmpty())
            },
        )

        if (!NfcDemoTag.isDemoTag(resolvedUri)) return
        val scenarioId = NfcDemoTag.scenarioId(resolvedUri) ?: return
        if (NfcDemoTapGate.consumeTag(scenarioId)) {
            NfcDemoLogger.event(
                name = "honor_nfc_gate_dispatch",
                details = "scenario=$scenarioId consumed=true",
            )
            return
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = NfcAdapter.ACTION_NDEF_DISCOVERED
            data = Uri.parse(resolvedUri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        runCatching { context.startActivity(launchIntent) }
            .onSuccess {
                NfcDemoLogger.event(
                    name = "honor_nfc_demo_launch",
                    details = "scenario=${NfcDemoTag.scenarioId(resolvedUri)}",
                )
            }
            .onFailure { error ->
                NfcDemoLogger.event(
                    name = "honor_nfc_demo_launch_failed",
                    details = "error=${error.javaClass.simpleName}",
                )
            }
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
            ?.flatMap { message -> message.records.asSequence() }
            ?.mapNotNull { record -> record.toUri()?.toString() }
            ?.firstOrNull(NfcDemoTag::isDemoTag)
        if (intentUri != null) return intentUri

        return runCatching { Ndef.get(tag)?.cachedNdefMessage }
            .getOrNull()
            ?.records
            ?.asSequence()
            ?.mapNotNull { record -> record.toUri()?.toString() }
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

    private companion object {
        const val HONOR_NDEF_DISCOVERED = "com.hihonor.nfc.action.NDEF_DISCOVERED"
        const val DEFAULT_SCENARIO = "visa"
    }
}
