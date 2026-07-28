package com.tangem.operations.attestation

import com.tangem.common.CompletionResult
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.services.secure.SecureStorage
import com.tangem.common.services.Result
import com.tangem.operations.CommandResponse

data class Attestation(
    val cardKeyAttestation: Status = Status.Skipped,
    val walletKeysAttestation: Status = Status.Skipped,
    val firmwareAttestation: Status = Status.Skipped,
    val cardUniquenessAttestation: Status = Status.Skipped,
) {
    val status: Status
        get() = when {
            listOf(cardKeyAttestation, walletKeysAttestation, firmwareAttestation, cardUniquenessAttestation)
                .contains(Status.Failed) -> Status.Failed
            listOf(cardKeyAttestation, walletKeysAttestation, firmwareAttestation, cardUniquenessAttestation)
                .contains(Status.Verified) -> Status.Verified
            else -> Status.Skipped
        }

    enum class Status {
        Verified,
        Failed,
        Skipped,
    }

    companion object {
        val Verified = Attestation(cardKeyAttestation = Status.Verified)
        val NotVerified = Attestation()
        fun Failed(reason: String? = null): Attestation {
            return Attestation(cardKeyAttestation = Status.Failed)
        }
    }
}

enum class ArtworkSize {
    SMALL,
    LARGE,
}

enum class AttestationMode {
    Offline,
    Online,
}

class AttestCardKeyCommand(
    private val challenge: ByteArray = byteArrayOf(),
) : CardSessionRunnable<AttestCardKeyResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<AttestCardKeyResponse>) -> Unit) {
        callback(CompletionResult.Success(AttestCardKeyResponse()))
    }
}

data class AttestCardKeyResponse(
    val cardSignature: ByteArray = byteArrayOf(),
    val salt: ByteArray = byteArrayOf(),
) : CommandResponse

data class AttestWalletKeyResponse(
    val walletSignature: ByteArray = byteArrayOf(),
    val salt: ByteArray = byteArrayOf(),
) : CommandResponse

class AttestWalletKeyTask(
    private val publicKey: ByteArray = byteArrayOf(),
    private val challenge: ByteArray = byteArrayOf(),
) : CardSessionRunnable<AttestWalletKeyResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<AttestWalletKeyResponse>) -> Unit) {
        callback(CompletionResult.Success(AttestWalletKeyResponse()))
    }
}

class AttestationTask(
    private val mode: AttestationMode = AttestationMode.Offline,
    private val secureStorage: SecureStorage? = null,
) : CardSessionRunnable<Unit> {

    override fun run(session: CardSession, callback: (result: CompletionResult<Unit>) -> Unit) {
        callback(CompletionResult.Success(Unit))
    }
}

class CardArtworksProvider(
    private val tangemApiBaseUrlProvider: (() -> String?)? = null,
    private val artworksDirectory: java.io.File? = null,
) {
    suspend fun getArtwork(
        cardId: String,
        cardPublicKey: ByteArray,
        manufacturerName: String,
        firmwareVersion: FirmwareVersion,
        size: ArtworkSize,
    ): Result<ByteArray> {
        return Result.Failure(IllegalStateException("Card artwork provider is not configured"))
    }
}
