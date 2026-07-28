package com.tangem.blockchain.blockchains.ethereum.network

import java.math.BigDecimal
import java.math.BigInteger

sealed class EthereumFeeHistory {
    abstract val baseFee: BigDecimal

    data class Common(
        override val baseFee: BigDecimal = BigDecimal.ZERO,
        val marketPriorityFee: BigDecimal = BigDecimal.ZERO,
    ) : EthereumFeeHistory()

    data class Fallback(
        override val baseFee: BigDecimal = BigDecimal.ZERO,
        val gasPrice: BigInteger = BigInteger.ZERO,
    ) : EthereumFeeHistory()
}
