package com.tangem.tap.features.intentHandler.handlers

import androidx.activity.ComponentActivity
import com.tangem.Message
import com.tangem.common.core.ProductType
import com.tangem.sdk.DefaultSessionViewDelegate
import com.tangem.sdk.nfc.NfcManager
import kotlinx.coroutines.flow.StateFlow

/** Shows the SDK's own card-scanning UI while the External build reads an NDEF tag. */
class ExternalNdefScanUi(
    activity: ComponentActivity,
) {

    private val viewDelegate = DefaultSessionViewDelegate(
        nfcManager = NfcManager().apply { setCurrentActivity(activity) },
        activity = activity,
    )

    val isVisible: StateFlow<Boolean> = viewDelegate.viewVisibility

    suspend fun show(message: String) {
        if (isVisible.value) return

        viewDelegate.onSessionStarted(
            cardId = null,
            message = Message(message),
            enableHowTo = true,
            iconScanRes = null,
            productType = ProductType.ANY,
        )
    }

    fun dismiss() {
        viewDelegate.dismiss()
    }
}
