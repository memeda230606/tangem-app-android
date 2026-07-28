package com.tangem.sdk

import com.tangem.common.core.Config
import com.tangem.sdk.nfc.NfcManager

class DefaultSessionViewDelegate(
    val nfcManager: NfcManager,
    val activity: Any?,
) {
    var sdkConfig: Config? = null
}
