package com.tangem.blockchain.transactionhistory

sealed class TransactionHistoryState {
    sealed class Success : TransactionHistoryState() {
        data object Empty : Success()
        data class HasTransactions(val txCount: Int) : Success()
    }

    sealed class Failed : TransactionHistoryState() {
        data class FetchError(val exception: Exception) : Failed()
    }

    data object NotImplemented : TransactionHistoryState()
}
