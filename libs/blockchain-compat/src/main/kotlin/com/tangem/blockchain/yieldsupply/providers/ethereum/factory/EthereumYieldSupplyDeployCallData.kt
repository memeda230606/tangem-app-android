package com.tangem.blockchain.yieldsupply.providers.ethereum.factory

import com.tangem.blockchain.common.smartcontract.SmartContractCallData

data class EthereumYieldSupplyDeployCallData(
    override val data: ByteArray = byteArrayOf(),
) : SmartContractCallData
