package com.tangem.blockchain.assetsdiscovery.models

import java.math.BigDecimal

sealed class DiscoveredAsset {
    abstract val amount: BigDecimal

    data class Token(
        val contractAddress: String,
        override val amount: BigDecimal,
    ) : DiscoveredAsset()
}
