package com.tangem.blockchain.transactionhistory.models

import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.common.Token
import com.tangem.blockchain.common.pagination.Page

data class TransactionHistoryRequest(
    val address: String,
    val decimals: Int,
    val page: Page,
    val pageSize: Int,
    val filterType: FilterType,
) {
    sealed class FilterType {
        data object Coin : FilterType()
        data class Contract(val token: Token) : FilterType()
    }
}

data class TransactionHistoryResponse(
    val items: List<TransactionHistoryItem> = emptyList(),
    val nextPage: Page = Page.LastPage,
)

data class TransactionHistoryItem(
    val txHash: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val destinationType: DestinationType,
    val sourceType: SourceType,
    val status: TransactionStatus,
    val type: TransactionType,
    val amount: Amount,
) {
    sealed class SourceType {
        data class Single(val address: String) : SourceType()
        data class Multiple(val addresses: List<String>) : SourceType()
    }

    sealed class DestinationType {
        data class Single(val addressType: AddressType) : DestinationType()
        data class Multiple(val addressTypes: List<AddressType>) : DestinationType()
    }

    sealed class AddressType {
        abstract val address: String

        data class Contract(override val address: String) : AddressType()
        data class User(override val address: String) : AddressType()
        data class Validator(override val address: String) : AddressType()
    }

    enum class TransactionStatus {
        Confirmed,
        Failed,
        Unconfirmed,
    }

    sealed class TransactionType {
        data object Transfer : TransactionType()
        data class ContractMethod(val id: String, val callData: String? = null) : TransactionType()
        data class ContractMethodName(val name: String, val callData: String? = null) : TransactionType()

        sealed class SolanaStakingTransactionType : TransactionType() {
            data class Stake(val validatorAddress: String? = null) : SolanaStakingTransactionType()
            data object Unstake : SolanaStakingTransactionType()
            data object Withdraw : SolanaStakingTransactionType()
        }

        sealed class TronStakingTransactionType : TransactionType() {
            data object FreezeBalanceV2Contract : TronStakingTransactionType()
            data object UnfreezeBalanceV2Contract : TronStakingTransactionType()
            data class VoteWitnessContract(val validatorAddress: String) : TronStakingTransactionType()
            data object WithdrawBalanceContract : TronStakingTransactionType()
            data object WithdrawExpireUnfreezeContract : TronStakingTransactionType()
        }
    }
}
