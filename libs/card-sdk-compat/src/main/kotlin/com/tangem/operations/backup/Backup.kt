package com.tangem.operations.backup

import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.card.Card
import com.tangem.common.card.EllipticCurve
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable

class BackupService {
    companion object {}

    sealed interface State {
        data object Idle : State
        data object FinalizingPrimaryCard : State
        data class FinalizingBackupCard(val index: Int) : State
        data object Finished : State
    }

    var skipCompatibilityChecks: Boolean = false
    var currentState: State = State.Idle
        private set

    private var primaryCard: PrimaryCard? = null
    private val backupCards = mutableListOf<PrimaryCard>()

    val primaryCardId: String?
        get() = primaryCard?.cardId

    val primaryCardBatchId: String?
        get() = primaryCard?.batchId

    val backupCardIds: List<String>
        get() = backupCards.map { it.cardId }

    val backupCardsBatchIds: List<String>
        get() = backupCards.map { it.batchId }

    val addedBackupCardsCount: Int
        get() = backupCards.size

    fun setAccessCode(accessCode: String) = Unit

    fun discardSavedBackup() {
        primaryCard = null
        backupCards.clear()
        currentState = State.Idle
        skipCompatibilityChecks = false
    }

    fun setPrimaryCard(card: PrimaryCard) {
        primaryCard = card
        currentState = State.FinalizingPrimaryCard
    }

    fun readPrimaryCard(iconScanRes: Int? = null, cardId: String, callback: (CompletionResult<PrimaryCard>) -> Unit) {
        val card = primaryCard ?: PrimaryCard(cardId = cardId)
        primaryCard = card
        currentState = State.FinalizingPrimaryCard
        callback(CompletionResult.Success(card))
    }

    fun addBackupCard(callback: (CompletionResult<PrimaryCard>) -> Unit) {
        val index = backupCards.size + 2
        val card = PrimaryCard(
            cardId = "backup-card-$index",
            batchId = primaryCard?.batchId.orEmpty(),
        )
        backupCards += card
        callback(CompletionResult.Success(card))
    }

    fun proceedBackup(iconScanRes: Int? = null, callback: (CompletionResult<Card>) -> Unit) {
        val card = when (val state = currentState) {
            State.FinalizingPrimaryCard -> {
                currentState = if (backupCards.isEmpty()) State.Finished else State.FinalizingBackupCard(index = 1)
                primaryCard?.toCard() ?: Card(cardId = "")
            }
            is State.FinalizingBackupCard -> {
                val backupCard = backupCards.getOrNull(state.index - 1)
                currentState = if (state.index >= backupCards.size) {
                    State.Finished
                } else {
                    State.FinalizingBackupCard(index = state.index + 1)
                }
                backupCard?.toCard() ?: Card(cardId = "")
            }
            State.Finished,
            State.Idle,
            -> primaryCard?.toCard() ?: Card(cardId = "")
        }

        callback(CompletionResult.Success(card))
    }

    fun finalizeBackup(callback: (CompletionResult<Card>) -> Unit) {
        currentState = State.Finished
        callback(CompletionResult.Success(primaryCard?.toCard() ?: Card(cardId = "")))
    }
}

data class PrimaryCard(
    val cardId: String = "",
    val batchId: String = "",
    val cardPublicKey: ByteArray = byteArrayOf(),
    val linkingKey: ByteArray? = null,
    val existingWalletsCount: Int = 0,
    val isHDWalletAllowed: Boolean = true,
    val issuer: Card.Issuer = Card.Issuer(),
    val manufacturer: Card.Manufacturer = Card.Manufacturer(),
    val walletCurves: List<EllipticCurve> = emptyList(),
    val firmwareVersion: FirmwareVersion = FirmwareVersion(),
    val isKeysImportAllowed: Boolean = false,
    val certificate: Any? = null,
)

private fun PrimaryCard.toCard(): Card = Card(
    cardId = cardId,
    batchId = batchId,
    cardPublicKey = cardPublicKey,
    issuer = issuer,
    manufacturer = manufacturer,
    supportedCurves = walletCurves,
    firmwareVersion = firmwareVersion,
    settings = Card.Settings(
        maxWalletsCount = existingWalletsCount,
        isHDWalletAllowed = isHDWalletAllowed,
        isKeysImportAllowed = isKeysImportAllowed,
    ),
)

class ResetBackupCommand : CardSessionRunnable<SuccessResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<SuccessResponse>) -> Unit) {
        session.environment.card = session.environment.card?.copy(backupStatus = null)
        callback(CompletionResult.Success(SuccessResponse()))
    }
}

class StartPrimaryCardLinkingCommand : CardSessionRunnable<PrimaryCard> {

    override fun run(session: CardSession, callback: (result: CompletionResult<PrimaryCard>) -> Unit) {
        callback(CompletionResult.Success(session.environment.card.toPrimaryCard()))
    }
}

class StartPrimaryCardLinkingTask : CardSessionRunnable<PrimaryCard> {

    override fun run(session: CardSession, callback: (result: CompletionResult<PrimaryCard>) -> Unit) {
        callback(CompletionResult.Success(session.environment.card.toPrimaryCard()))
    }
}

private fun Card?.toPrimaryCard(): PrimaryCard {
    return PrimaryCard(
        cardId = this?.cardId.orEmpty(),
        batchId = this?.batchId.orEmpty(),
        cardPublicKey = this?.cardPublicKey ?: byteArrayOf(),
        issuer = this?.issuer ?: Card.Issuer(),
        manufacturer = this?.manufacturer ?: Card.Manufacturer(),
        walletCurves = this?.supportedCurves.orEmpty(),
        firmwareVersion = this?.firmwareVersion ?: FirmwareVersion(),
        isHDWalletAllowed = this?.settings?.isHDWalletAllowed ?: true,
        isKeysImportAllowed = this?.settings?.isKeysImportAllowed ?: false,
    )
}

fun BackupService.Companion.create(tangemSdk: TangemSdk, activity: Any): BackupService = BackupService()
