package com.tangem.blockchain.blockchains.stellar

import com.tangem.blockchain.common.TransactionExtras
import java.math.BigInteger

sealed class StellarMemo {
    data class Id(val value: BigInteger) : StellarMemo()
    data class Text(val value: String) : StellarMemo()
}

data class StellarTransactionExtras(val memo: StellarMemo) : TransactionExtras
