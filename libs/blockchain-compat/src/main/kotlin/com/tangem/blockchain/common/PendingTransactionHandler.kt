package com.tangem.blockchain.common

interface PendingTransactionHandler {
    fun addPendingGaslessTransaction(
        transactionData: TransactionData.Uncompiled,
        txHash: String,
        contractAddress: String?,
    ) = Unit
}
