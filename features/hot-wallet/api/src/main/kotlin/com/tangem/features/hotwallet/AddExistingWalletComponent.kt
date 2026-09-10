package com.tangem.features.hotwallet

import com.tangem.core.decompose.factory.ComponentFactory
import com.tangem.core.ui.decompose.ComposableContentComponent

interface AddExistingWalletComponent : ComposableContentComponent {
    interface Factory : ComponentFactory<Params, AddExistingWalletComponent>

    data class Params(
        val isNfcRecovery: Boolean = false,
    )
}
