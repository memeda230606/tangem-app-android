package com.tangem.features.hotwallet

import com.tangem.core.analytics.models.AnalyticsParam
import com.tangem.core.decompose.factory.ComponentFactory
import com.tangem.core.ui.decompose.ComposableContentComponent

interface CreateMobileWalletComponent : ComposableContentComponent {
    data class Params(
        val source: AnalyticsParam.ScreensSources,
        val isNfcRecovery: Boolean = false,
    )

    interface Factory : ComponentFactory<Params, CreateMobileWalletComponent>
}
