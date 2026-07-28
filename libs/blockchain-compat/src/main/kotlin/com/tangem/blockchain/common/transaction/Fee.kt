package com.tangem.blockchain.common.transaction

import com.tangem.blockchain.common.Amount
import java.math.BigDecimal
import java.math.BigInteger

sealed class Fee {
    abstract val amount: Amount

    data class Common(override val amount: Amount) : Fee()
    data class Bitcoin(
        override val amount: Amount,
        val satoshiPerByte: BigDecimal? = null,
        val txSize: BigDecimal = BigDecimal.ZERO,
    ) : Fee()
    data class CardanoToken(
        override val amount: Amount,
        val minAdaValue: BigDecimal? = null,
    ) : Fee()
    data class Kaspa(
        override val amount: Amount,
        val mass: Long = 1,
        val feeRate: BigInteger = BigInteger.ZERO,
        val revealTransactionFee: Fee? = null,
    ) : Fee()
    data class Tron(
        override val amount: Amount,
        val remainingEnergy: Long = 0,
        val feeEnergy: Long = 0,
    ) : Fee()
    data class Hedera(override val amount: Amount) : Fee()
    data class Aptos(
        override val amount: Amount,
        val gasLimit: BigInteger = BigInteger.ZERO,
    ) : Fee()
    data class Sui(override val amount: Amount) : Fee()
    data class Filecoin(
        override val amount: Amount,
        val gasLimit: BigInteger = BigInteger.ZERO,
    ) : Fee()
    data class VeChain(
        override val amount: Amount,
        val gasLimit: BigInteger = BigInteger.ZERO,
    ) : Fee()
    data class Alephium(override val amount: Amount) : Fee()

    sealed class Ethereum : Fee() {
        abstract val gasLimit: BigInteger

        data class Legacy(
            override val amount: Amount,
            override val gasLimit: BigInteger,
            val gasPrice: BigInteger,
        ) : Ethereum()

        data class EIP1559(
            override val amount: Amount,
            override val gasLimit: BigInteger,
            val maxFeePerGas: BigInteger,
            val priorityFee: BigInteger = BigInteger.ZERO,
        ) : Ethereum()

        data class TokenCurrency(
            override val amount: Amount,
            override val gasLimit: BigInteger,
            val coinPriceInToken: BigInteger = BigInteger.ZERO,
            val feeTransferGasLimit: BigInteger = BigInteger.ZERO,
            val baseGas: BigInteger = BigInteger.ZERO,
        ) : Ethereum()
    }
}

sealed class TransactionFee {
    abstract val normal: Fee

    data class Single(override val normal: Fee) : TransactionFee()
    data class Choosable(val minimum: Fee, override val normal: Fee, val priority: Fee) : TransactionFee()
}

data class TransactionSendResult(
    val hash: String,
)

data class TransactionsSendResult(
    val hashes: List<String>,
) {
    val results: List<TransactionSendResult>
        get() = hashes.map(::TransactionSendResult)
}
