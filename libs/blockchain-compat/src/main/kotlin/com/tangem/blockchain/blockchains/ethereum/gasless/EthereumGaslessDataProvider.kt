package com.tangem.blockchain.blockchains.ethereum.gasless

import com.tangem.blockchain.blockchains.ethereum.models.EIP7702AuthorizationData
import com.tangem.blockchain.common.BlockchainSdkError
import com.tangem.blockchain.extensions.Result
import java.math.BigInteger

interface EthereumGaslessDataProvider {
    suspend fun getGaslessContractNonce(userAddress: String): Result<BigInteger> =
        Result.Failure(BlockchainSdkError.CustomError("Gasless nonce is not implemented"))

    suspend fun prepareEIP7702AuthorizationData(): Result<EIP7702AuthorizationData> =
        Result.Failure(BlockchainSdkError.CustomError("EIP-7702 authorization is not implemented"))
}
