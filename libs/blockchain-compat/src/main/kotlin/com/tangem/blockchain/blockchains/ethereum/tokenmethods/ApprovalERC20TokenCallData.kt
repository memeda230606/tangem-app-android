package com.tangem.blockchain.blockchains.ethereum.tokenmethods

import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.common.smartcontract.SmartContractCallData

data class ApprovalERC20TokenCallData(
    val spenderAddress: String,
    val amount: Amount? = null,
) : SmartContractCallData {
    constructor(data: ByteArray) : this(spenderAddress = "", amount = null)

    val methodId: String = "0x095ea7b3"

    override val data: ByteArray
        get() = "approve:$spenderAddress:${amount?.value ?: ""}".encodeToByteArray()
}
