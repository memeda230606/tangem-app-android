package com.tangem.blockchain.yieldsupply

import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.common.smartcontract.SmartContractCallData
import com.tangem.blockchain.yieldsupply.providers.ethereum.factory.EthereumYieldSupplyDeployCallData
import com.tangem.blockchain.yieldsupply.providers.ethereum.yield.EthereumYieldSupplyEnterCallData
import com.tangem.blockchain.yieldsupply.providers.ethereum.yield.EthereumYieldSupplyExitCallData
import com.tangem.blockchain.yieldsupply.providers.ethereum.yield.EthereumYieldSupplyInitTokenCallData
import com.tangem.blockchain.yieldsupply.providers.ethereum.yield.EthereumYieldSupplyReactivateTokenCallData
import com.tangem.blockchain.yieldsupply.providers.ethereum.yield.EthereumYieldSupplySendCallData

object YieldSupplyContractCallDataProviderFactory {
    fun getDeployCallData(
        tokenContractAddress: String,
        walletAddress: String,
        maxNetworkFee: Amount,
    ): SmartContractCallData {
        return EthereumYieldSupplyDeployCallData(
            data = "deploy:$tokenContractAddress:$walletAddress:${maxNetworkFee.value}".encodeToByteArray(),
        )
    }

    fun getInitTokenCallData(tokenContractAddress: String, maxNetworkFee: Amount): SmartContractCallData {
        return EthereumYieldSupplyInitTokenCallData(
            tokenContractAddress = tokenContractAddress,
            data = "init:$tokenContractAddress:${maxNetworkFee.value}".encodeToByteArray(),
        )
    }

    fun getReactivateTokenCallData(tokenContractAddress: String, maxNetworkFee: Amount): SmartContractCallData {
        return EthereumYieldSupplyReactivateTokenCallData(
            tokenContractAddress = tokenContractAddress,
            data = "reactivate:$tokenContractAddress:${maxNetworkFee.value}".encodeToByteArray(),
        )
    }

    fun getEnterCallData(tokenContractAddress: String): SmartContractCallData {
        return EthereumYieldSupplyEnterCallData(
            tokenContractAddress = tokenContractAddress,
            data = "enter:$tokenContractAddress".encodeToByteArray(),
        )
    }

    fun getExitCallData(tokenContractAddress: String): SmartContractCallData {
        return EthereumYieldSupplyExitCallData(
            tokenContractAddress = tokenContractAddress,
            data = "exit:$tokenContractAddress".encodeToByteArray(),
        )
    }

    fun getSendCallData(
        tokenContractAddress: String,
        destinationAddress: String,
        amount: Amount,
    ): SmartContractCallData {
        return EthereumYieldSupplySendCallData(
            destinationAddress = destinationAddress,
            data = "send:$tokenContractAddress:$destinationAddress:${amount.value}".encodeToByteArray(),
        )
    }
}
