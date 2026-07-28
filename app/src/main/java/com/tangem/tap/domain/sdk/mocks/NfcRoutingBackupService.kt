package com.tangem.tap.domain.sdk.mocks

import com.tangem.common.CompletionResult
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.operations.backup.PrimaryCard
import com.tangem.sdk.api.CardBackupService

/** Keeps the real and DemoCard backup engines alive and delegates according to the selected card origin. */
internal class NfcRoutingBackupService(
    private val real: CardBackupService,
    private val demo: NfcDemoBackupService,
) : CardBackupService {
    private val active: CardBackupService
        get() = if (NfcCardOriginRegistry.current == NfcCardOriginRegistry.Origin.Demo) demo else real

    override val currentState get() = active.currentState
    override val addedBackupCardsCount get() = active.addedBackupCardsCount
    override val primaryCardId get() = active.primaryCardId
    override val backupCardIds get() = active.backupCardIds
    override val primaryCardBatchId get() = active.primaryCardBatchId
    override val backupCardsBatchIds get() = active.backupCardsBatchIds
    override var skipCompatibilityChecks: Boolean
        get() = active.skipCompatibilityChecks
        set(value) { active.skipCompatibilityChecks = value }

    override fun discardSavedBackup() = active.discardSavedBackup()
    override fun addBackupCard(callback: (CompletionResult<CardDTO>) -> Unit) = active.addBackupCard(callback)
    override fun setAccessCode(accessCode: String) = active.setAccessCode(accessCode)
    override fun setPasscode(passcode: String) = active.setPasscode(passcode)
    override fun proceedBackup(iconScanRes: Int?, callback: (CompletionResult<CardDTO>) -> Unit) =
        active.proceedBackup(iconScanRes, callback)
    override fun setPrimaryCard(primaryCard: PrimaryCard) = active.setPrimaryCard(primaryCard)
    override fun readPrimaryCard(
        iconScanRes: Int?,
        cardId: String?,
        callback: (CompletionResult<PrimaryCard>) -> Unit,
    ) = active.readPrimaryCard(iconScanRes, cardId, callback)

    fun prepareInterruptedBackup(scanResponse: ScanResponse): ScanResponse {
        NfcCardOriginRegistry.current = NfcCardOriginRegistry.Origin.Demo
        return demo.prepareInterruptedBackup(scanResponse)
    }
}
