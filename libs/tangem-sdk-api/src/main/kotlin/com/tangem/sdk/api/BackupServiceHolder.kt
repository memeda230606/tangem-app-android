package com.tangem.sdk.api

import androidx.activity.ComponentActivity
import com.tangem.TangemSdk
import com.tangem.common.CompletionResult
import com.tangem.domain.models.scan.CardDTO
import com.tangem.operations.backup.BackupService
import com.tangem.operations.backup.PrimaryCard
import com.tangem.sdk.extensions.init
import java.lang.ref.WeakReference

class BackupServiceHolder {

    lateinit var backupService: WeakReference<CardBackupService>
        private set

    fun createAndSetService(tangemSdk: TangemSdk, activity: ComponentActivity) {
        backupService = WeakReference(RealCardBackupService(BackupService.init(tangemSdk, activity)))
    }

    fun setService(service: CardBackupService) {
        backupService = WeakReference(service)
    }
}

/**
 * App-facing backup API. The production implementation delegates to Tangem SDK without changing its behavior.
 */
interface CardBackupService {

    sealed interface State {
        data object Preparing : State
        data object FinalizingPrimaryCard : State
        data class FinalizingBackupCard(val index: Int) : State
        data object Finished : State
    }

    val currentState: State
    val addedBackupCardsCount: Int
    val primaryCardId: String?
    val backupCardIds: List<String>
    val primaryCardBatchId: String?
    val backupCardsBatchIds: List<String>
    var skipCompatibilityChecks: Boolean

    fun discardSavedBackup()

    fun addBackupCard(callback: (CompletionResult<CardDTO>) -> Unit)

    fun setAccessCode(accessCode: String): CompletionResult<Unit>

    fun setPasscode(passcode: String): CompletionResult<Unit>

    fun proceedBackup(iconScanRes: Int? = null, callback: (CompletionResult<CardDTO>) -> Unit)

    fun setPrimaryCard(primaryCard: PrimaryCard)

    fun readPrimaryCard(
        iconScanRes: Int? = null,
        cardId: String? = null,
        callback: (CompletionResult<PrimaryCard>) -> Unit,
    )
}

private class RealCardBackupService(
    private val delegate: BackupService,
) : CardBackupService {

    override val currentState: CardBackupService.State
        get() = when (val state = delegate.currentState) {
            BackupService.State.Preparing -> CardBackupService.State.Preparing
            BackupService.State.FinalizingPrimaryCard -> CardBackupService.State.FinalizingPrimaryCard
            is BackupService.State.FinalizingBackupCard -> CardBackupService.State.FinalizingBackupCard(state.index)
            BackupService.State.Finished -> CardBackupService.State.Finished
        }

    override val addedBackupCardsCount: Int get() = delegate.addedBackupCardsCount
    override val primaryCardId: String? get() = delegate.primaryCardId
    override val backupCardIds: List<String> get() = delegate.backupCardIds
    override val primaryCardBatchId: String? get() = delegate.primaryCardBatchId
    override val backupCardsBatchIds: List<String> get() = delegate.backupCardsBatchIds

    override var skipCompatibilityChecks: Boolean
        get() = delegate.skipCompatibilityChecks
        set(value) {
            delegate.skipCompatibilityChecks = value
        }

    override fun discardSavedBackup() = delegate.discardSavedBackup()

    override fun addBackupCard(callback: (CompletionResult<CardDTO>) -> Unit) {
        delegate.addBackupCard { callback(it.mapSuccess(::CardDTO)) }
    }

    override fun setAccessCode(accessCode: String): CompletionResult<Unit> = delegate.setAccessCode(accessCode)

    override fun setPasscode(passcode: String): CompletionResult<Unit> = delegate.setPasscode(passcode)

    override fun proceedBackup(iconScanRes: Int?, callback: (CompletionResult<CardDTO>) -> Unit) {
        delegate.proceedBackup(iconScanRes) { callback(it.mapSuccess(::CardDTO)) }
    }

    override fun setPrimaryCard(primaryCard: PrimaryCard) = delegate.setPrimaryCard(primaryCard)

    override fun readPrimaryCard(
        iconScanRes: Int?,
        cardId: String?,
        callback: (CompletionResult<PrimaryCard>) -> Unit,
    ) = delegate.readPrimaryCard(iconScanRes, cardId, callback)

    private inline fun <T, R> CompletionResult<T>.mapSuccess(transform: (T) -> R): CompletionResult<R> = when (this) {
        is CompletionResult.Success -> CompletionResult.Success(transform(data))
        is CompletionResult.Failure -> CompletionResult.Failure(error)
    }
}
