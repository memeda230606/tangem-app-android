package com.tangem.blockchain.blockchains.ethereum

import com.tangem.blockchain.blockchains.ethereum.models.EIP7702AuthorizationData
import com.tangem.blockchain.blockchains.ethereum.network.EthereumFeeHistory
import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.common.BlockchainSdkError
import com.tangem.blockchain.common.TransactionExtras
import com.tangem.blockchain.common.WalletManager
import com.tangem.blockchain.common.smartcontract.SmartContractCallData
import com.tangem.blockchain.extensions.Result
import java.math.BigInteger

data class EthereumTransactionExtras(
    val callData: SmartContractCallData? = null,
    val gasLimit: BigInteger? = null,
    val nonce: BigInteger? = null,
) : TransactionExtras

interface EthereumWalletManager : WalletManager {
    suspend fun getGasPrice(): Result<BigInteger> {
        return Result.Failure(BlockchainSdkError.CustomError("Gas price is not implemented"))
    }

    suspend fun getGasHistory(): Result<EthereumFeeHistory> {
        return Result.Failure(BlockchainSdkError.CustomError("Gas history is not implemented"))
    }

    suspend fun getGasLimit(
        amount: Amount,
        destination: String,
        callData: SmartContractCallData? = null,
    ): Result<BigInteger> {
        return Result.Failure(BlockchainSdkError.CustomError("Gas limit is not implemented"))
    }
}

data class EthereumGaslessAuthorizationStub(
    val data: EIP7702AuthorizationData = EIP7702AuthorizationData(),
)
