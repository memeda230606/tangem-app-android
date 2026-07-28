package com.tangem.blockchain.blockchains.tron

import com.tangem.blockchain.common.TransactionExtras
import com.tangem.blockchain.common.smartcontract.SmartContractCallData

data class TronTransactionExtras(
    val callData: SmartContractCallData? = null,
) : TransactionExtras
