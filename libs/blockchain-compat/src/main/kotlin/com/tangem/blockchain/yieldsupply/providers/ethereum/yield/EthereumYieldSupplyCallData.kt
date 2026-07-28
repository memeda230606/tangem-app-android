package com.tangem.blockchain.yieldsupply.providers.ethereum.yield

import com.tangem.blockchain.common.smartcontract.SmartContractCallData

open class EthereumYieldSupplySendCallData(
    val destinationAddress: String = "",
    override val data: ByteArray = byteArrayOf(),
) : SmartContractCallData

open class EthereumYieldSupplyEnterCallData(
    val tokenContractAddress: String = "",
    override val data: ByteArray = byteArrayOf(),
) : SmartContractCallData {
    companion object {
        fun decode(data: String): EthereumYieldSupplyEnterCallData? {
            return EthereumYieldSupplyEnterCallData(tokenContractAddress = data.extractAddressLikeValue())
        }
    }
}

open class EthereumYieldSupplyExitCallData(
    val tokenContractAddress: String = "",
    override val data: ByteArray = byteArrayOf(),
) : SmartContractCallData {
    companion object {
        fun decode(data: String): EthereumYieldSupplyExitCallData? {
            return EthereumYieldSupplyExitCallData(tokenContractAddress = data.extractAddressLikeValue())
        }
    }
}

open class EthereumYieldSupplyInitTokenCallData(
    val tokenContractAddress: String = "",
    override val data: ByteArray = byteArrayOf(),
) : SmartContractCallData {
    companion object {
        fun decode(data: String): EthereumYieldSupplyInitTokenCallData? {
            return EthereumYieldSupplyInitTokenCallData(tokenContractAddress = data.extractAddressLikeValue())
        }
    }
}

open class EthereumYieldSupplyReactivateTokenCallData(
    val tokenContractAddress: String = "",
    override val data: ByteArray = byteArrayOf(),
) : SmartContractCallData {
    companion object {
        fun decode(data: String): EthereumYieldSupplyReactivateTokenCallData? {
            return EthereumYieldSupplyReactivateTokenCallData(tokenContractAddress = data.extractAddressLikeValue())
        }
    }
}

private fun String.extractAddressLikeValue(): String {
    return Regex("0x[a-fA-F0-9]{40}").find(this)?.value.orEmpty()
}
