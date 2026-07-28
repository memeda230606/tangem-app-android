package com.tangem.blockchain.blockchains.bitcoin

import com.tangem.blockchain.common.TransactionExtras

data class BitcoinTransactionExtras(
    val memo: String? = null,
    val changeAddress: String? = null,
) : TransactionExtras
