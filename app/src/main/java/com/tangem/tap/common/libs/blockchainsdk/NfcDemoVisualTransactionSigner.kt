package com.tangem.tap.common.libs.blockchainsdk

import com.tangem.blockchain.common.TransactionSigner
import com.tangem.blockchain.common.Wallet
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import com.tangem.operations.sign.SignData
import com.tangem.tap.domain.sdk.mocks.NfcDemoLogger
import com.tangem.tap.domain.sdk.mocks.NfcDemoTapGate

/**
 * Produces deterministic, non-cryptographic signatures for the fully mocked visual flow.
 * The result is accepted only by the local demo transaction sender and must never be broadcast.
 */
internal object NfcDemoVisualTransactionSigner : TransactionSigner {

    override suspend fun sign(
        hashes: List<ByteArray>,
        publicKey: Wallet.PublicKey,
    ): CompletionResult<List<ByteArray>> = simulated(hashes.size) { List(hashes.size, ::signature) }

    override suspend fun sign(hash: ByteArray, publicKey: Wallet.PublicKey): CompletionResult<ByteArray> =
        simulated(count = 1) { signature(index = 0) }

    override suspend fun multiSign(
        dataToSign: List<SignData>,
        publicKey: Wallet.PublicKey,
    ): CompletionResult<Map<ByteArray, ByteArray>> = simulated(dataToSign.size) {
        dataToSign.mapIndexed { index, signData -> signData.publicKey to signature(index) }.toMap()
    }

    private suspend fun <T> simulated(count: Int, result: () -> T): CompletionResult<T> {
        NfcDemoLogger.event("visual_signing_started", "items=$count cryptographic=false")

        return when (val tapResult = NfcDemoTapGate.awaitTap(operation = "transaction_sign")) {
            NfcDemoTapGate.Result.Success -> {
                NfcDemoLogger.event(
                    name = "visual_signing_completed",
                    details = "result=success items=$count cryptographic=false physicalTap=true",
                )
                CompletionResult.Success(result())
            }
            NfcDemoTapGate.Result.RealCard,
            NfcDemoTapGate.Result.TagLost,
            NfcDemoTapGate.Result.Timeout,
            -> {
                NfcDemoLogger.warning(
                    name = "visual_signing_completed",
                    details = "result=$tapResult items=$count cryptographic=false physicalTap=false",
                )
                CompletionResult.Failure(TangemSdkError.TagLost())
            }
            NfcDemoTapGate.Result.Cancelled -> {
                NfcDemoLogger.warning(
                    name = "visual_signing_completed",
                    details = "result=cancelled items=$count cryptographic=false physicalTap=false",
                )
                CompletionResult.Failure(TangemSdkError.UserCancelled())
            }
        }
    }

    private fun signature(index: Int): ByteArray = ByteArray(SIGNATURE_SIZE).also { signature ->
        signature[signature.lastIndex] = (index + 1).toByte()
    }

    private const val SIGNATURE_SIZE = 64
}
