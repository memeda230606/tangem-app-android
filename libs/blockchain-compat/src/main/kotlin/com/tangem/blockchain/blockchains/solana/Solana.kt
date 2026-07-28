package com.tangem.blockchain.blockchains.solana

import com.tangem.blockchain.common.WalletManager
import com.tangem.blockchain.common.BlockchainSdkError
import com.tangem.blockchain.common.TransactionSigner
import com.tangem.blockchain.extensions.Result
import java.math.BigDecimal

interface RentProvider {
    suspend fun minimalBalanceForRentExemption(): Result<BigDecimal>
    fun rentAmount(): BigDecimal
}

interface SolanaWalletManager : WalletManager {
    suspend fun send(transaction: ByteArray): Result<String> =
        Result.Failure(BlockchainSdkError.CustomError("Solana transaction sending is not implemented"))

    suspend fun handleLargeLegacyTransaction(signer: TransactionSigner, txHash: ByteArray): Result<Unit> =
        Result.Failure(BlockchainSdkError.CustomError("Large Solana transaction sending is not implemented"))
}

object SolanaTransactionHelper {
    fun removeSignaturesPlaceholders(transaction: ByteArray): ByteArray = transaction
}
