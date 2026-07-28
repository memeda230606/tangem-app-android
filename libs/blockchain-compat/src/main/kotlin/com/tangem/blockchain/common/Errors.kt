package com.tangem.blockchain.common

import com.tangem.common.core.TangemError
import java.math.BigDecimal

open class BlockchainSdkError(
    code: Int,
    message: String? = null,
    cause: Throwable? = null,
) : TangemError(code = code, message = message, cause = cause) {
    data class WrappedTangemError(val tangemError: TangemError) :
        BlockchainSdkError(code = tangemError.code, message = tangemError.customMessage, cause = tangemError)

    class WrappedThrowable(val throwable: Throwable) :
        BlockchainSdkError(code = 1, message = throwable.message, cause = throwable)

    class CustomError(message: String? = null) : BlockchainSdkError(code = 2, message = message)
    object AccountNotFound : BlockchainSdkError(code = 3) {
        val amountToCreateAccount: BigDecimal? = null
    }
    object DestinationTagRequired : BlockchainSdkError(code = 4)
    object TransactionDustChangeError : BlockchainSdkError(code = 5)
    data class CreateAccountUnderfunded(val amount: BigDecimal? = null) : BlockchainSdkError(code = 6) {
        val minReserve: Amount = Amount(value = amount)
    }
    object FailedToCreateAccount : BlockchainSdkError(code = 7)
    class NPError(message: String? = null) : BlockchainSdkError(code = 8, message = message)

    sealed class Tron(code: Int, message: String? = null) : BlockchainSdkError(code, message) {
        class AccountActivationError(code: Int = 0) : Tron(100 + code)
    }

    sealed class Kaspa(code: Int, message: String? = null) : BlockchainSdkError(code, message) {
        object ZeroUtxoError : Kaspa(200)
        data class DustChangeError(val minimumAmount: BigDecimal) : Kaspa(201)
    }

    sealed class Sui(code: Int, message: String? = null) : BlockchainSdkError(code, message) {
        object OneSuiRequired : Sui(300)
    }

    sealed class Ethereum(code: Int, message: String? = null, cause: Throwable? = null) :
        BlockchainSdkError(code, message, cause) {
        data class Api(val apiCode: Int, val apiMessage: String) : Ethereum(apiCode, apiMessage)
        object InsufficientFundsForOperation : Ethereum(401)
    }

    sealed class Stellar(code: Int, message: String? = null) : BlockchainSdkError(code, message) {
        data class MinReserveRequired(val reserveAmount: BigDecimal? = null) : Stellar(500) {
            val amount: BigDecimal = reserveAmount ?: BigDecimal.ZERO
            val symbol: String = "XLM"
        }
    }

    sealed class Xrp(code: Int, message: String? = null) : BlockchainSdkError(code, message) {
        data class MinReserveRequired(val reserveAmount: BigDecimal? = null) : Xrp(600) {
            val amount: BigDecimal = reserveAmount ?: BigDecimal.ZERO
            val symbol: String = "XRP"
        }
    }

    sealed class Solana(code: Int, message: String? = null) : BlockchainSdkError(code, message) {
        data class DestinationRentExemption(val rentAmount: BigDecimal) : Solana(700)
    }

    sealed class Cardano(code: Int, message: String? = null) : BlockchainSdkError(code, message) {
        object InsufficientMinAdaBalanceToSendToken : Cardano(800)
        object InsufficientRemainingBalanceToWithdrawTokens : Cardano(801)
        object InsufficientRemainingBalance : Cardano(802)
        object InsufficientSendingAdaAmount : Cardano(803)
    }

    sealed class Koinos(code: Int, message: String? = null) : BlockchainSdkError(code, message) {
        class InsufficientBalance : Koinos(900)
        data class InsufficientMana(val manaBalance: BigDecimal? = null, val maxMana: BigDecimal? = null) :
            Koinos(901)

        data class ManaFeeExceedsBalance(val availableKoinForTransfer: BigDecimal) : Koinos(902)
    }
}
