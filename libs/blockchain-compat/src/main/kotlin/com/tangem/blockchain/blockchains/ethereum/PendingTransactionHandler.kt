package com.tangem.blockchain.blockchains.ethereum

import com.tangem.blockchain.common.TransactionData

interface PendingTransactionHandler {
    fun addPendingGaslessTransaction(
        transactionData: TransactionData.Uncompiled,
        txHash: String,
        contractAddress: String?,
    ) = Unit
}
