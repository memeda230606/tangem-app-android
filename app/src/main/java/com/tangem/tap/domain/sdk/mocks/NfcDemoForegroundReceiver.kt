package com.tangem.tap.domain.sdk.mocks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives MagicOS' proprietary NFC discovery broadcast while the demo activity is in the foreground. */
internal class NfcDemoForegroundReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HONOR_NDEF_DISCOVERED) return

        val isDemoTag = NfcDemoTag.isDemoTag(intent.dataString)
        val scenarioId = NfcDemoTag.scenarioId(intent.dataString) ?: PHYSICAL_TAG_SCENARIO
        val consumed = NfcDemoTapGate.consumeTag(scenarioId, isDemoTag)
        NfcDemoLogger.event(
            name = "foreground_nfc_broadcast_received",
            details = "scenario=$scenarioId consumed=$consumed",
        )
    }

    companion object {
        const val HONOR_NDEF_DISCOVERED = "com.hihonor.nfc.action.NDEF_DISCOVERED"
        private const val PHYSICAL_TAG_SCENARIO = "physical"
    }
}
