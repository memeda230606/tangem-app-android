package com.tangem.operations.sign

import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.operations.CommandResponse

data class SignData(
    val hash: ByteArray,
    val publicKey: ByteArray,
    val derivationPath: DerivationPath? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignData) return false
        return hash.contentEquals(other.hash) &&
            publicKey.contentEquals(other.publicKey) &&
            derivationPath == other.derivationPath
    }

    override fun hashCode(): Int {
        var result = hash.contentHashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + (derivationPath?.hashCode() ?: 0)
        return result
    }
}

data class SignHashResponse(
    val signature: ByteArray = byteArrayOf(),
    val totalSignedHashes: Int? = null,
) : CommandResponse

class SignHashCommand(
    private val hash: ByteArray,
    private val walletPublicKey: ByteArray,
    private val derivationPath: DerivationPath? = null,
) : CardSessionRunnable<SignHashResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<SignHashResponse>) -> Unit) {
        callback(CompletionResult.Success(SignHashResponse()))
    }
}

data class SignHashesResponse(
    val signatures: List<ByteArray> = emptyList(),
    val totalSignedHashes: Int? = null,
) : CommandResponse

class SignHashesCommand(
    private val hashes: Array<ByteArray>,
    private val walletPublicKey: ByteArray,
    private val derivationPath: DerivationPath? = null,
) : CardSessionRunnable<SignHashesResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<SignHashesResponse>) -> Unit) {
        callback(
            CompletionResult.Success(
                SignHashesResponse(
                    signatures = hashes.map { byteArrayOf() },
                    totalSignedHashes = null,
                ),
            ),
        )
    }
}

data class SignResponse(
    val walletPublicKey: ByteArray,
    val signature: ByteArray = byteArrayOf(),
    val totalSignedHashes: Int? = null,
) : CommandResponse

class MultipleSignCommand(
    private val dataToSign: List<SignData>,
    private val walletPublicKey: ByteArray,
) : CardSessionRunnable<List<SignResponse>> {

    override fun run(session: CardSession, callback: (result: CompletionResult<List<SignResponse>>) -> Unit) {
        val responses = dataToSign.map { data ->
            SignResponse(
                walletPublicKey = data.publicKey.takeIf { it.isNotEmpty() } ?: walletPublicKey,
                signature = byteArrayOf(),
                totalSignedHashes = null,
            )
        }
        callback(CompletionResult.Success(responses))
    }
}
