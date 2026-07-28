package com.tangem.blockchain.blockchains.ethereum.tokenmethods

import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.common.smartcontract.SmartContractCallData

data class TransferERC20TokenCallData(
    val destination: String,
    val amount: Amount,
) : SmartContractCallData {
    override val data: ByteArray
        get() = "${destination}:${amount.value}".encodeToByteArray()
}
