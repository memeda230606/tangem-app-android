package com.tangem.tap.domain.sdk.mocks

import android.content.Context
import com.tangem.common.CompletionResult
import com.tangem.common.card.Card
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.core.TangemSdkError
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.operations.backup.PrimaryCard
import com.tangem.sdk.api.CardBackupService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ordinary-NFC implementation of the backup ceremony used by NFC-enabled builds.
 *
 * The NDEF tag is only a physical presence trigger. Card identifiers and backup state are local fixtures, and no
 * key material is read from or written to the tag.
 */
internal class NfcDemoBackupService(
    private val stateStorage: NfcDemoBackupStateStorage,
    private val scope: CoroutineScope,
    private val hotWalletBridge: NfcDemoHotWalletBridge? = null,
) : CardBackupService {

    constructor(context: Context, hotWalletBridge: NfcDemoHotWalletBridge) : this(
        stateStorage = SharedPreferencesNfcDemoBackupStateStorage(context.applicationContext),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        hotWalletBridge = hotWalletBridge,
    )

    private val backupCards = mutableListOf<CardDTO>()
    private var primaryCard: PrimaryCard? = null
    private var accessCodeIsSet = false
    private var passcodeIsSet = false
    private var skipCompatibilityChecksInternal = false

    override var currentState: CardBackupService.State = CardBackupService.State.Preparing
        private set

    override val addedBackupCardsCount: Int get() = backupCards.size
    override val primaryCardId: String? get() = primaryCard?.cardId
    override val backupCardIds: List<String> get() = backupCards.map(CardDTO::cardId)
    override val primaryCardBatchId: String? get() = primaryCard?.batchId
    override val backupCardsBatchIds: List<String> get() = backupCards.map(CardDTO::batchId)
    override var skipCompatibilityChecks: Boolean
        get() = skipCompatibilityChecksInternal
        set(value) {
            skipCompatibilityChecksInternal = value
            persistState()
        }

    init {
        stateStorage.load()?.let(::restoreState)
    }

    override fun discardSavedBackup() {
        primaryCard = null
        backupCards.clear()
        accessCodeIsSet = false
        passcodeIsSet = false
        skipCompatibilityChecksInternal = false
        currentState = CardBackupService.State.Preparing
        stateStorage.clear()
        NfcDemoLogger.event("backup_state_reset")
    }

    /**
     * Restores the minimum non-sensitive state required by the finalize UI before that UI is created.
     *
     * Older NFC-demo builds only persisted the onboarding [ScanResponse], while the demo backup service lived in
     * memory. After process recreation the finalize model therefore received an empty service and its scan button
     * had no card batch id to act on. Restore the ceremony from the persisted response before routing resumes it.
     */
    fun prepareInterruptedBackup(scanResponse: ScanResponse): ScanResponse {
        val scenarioId = MockProvider.currentNfcDemoScenarioId ?: LEGACY_RECOVERY_SCENARIO
        MockProvider.restoreNfcDemoScenario(scenarioId)
        val createWalletResponse = MockProvider.getCurrentCreateProductWalletResponse()
        val restoredScanResponse = if (
            scanResponse.card.wallets.isEmpty() && createWalletResponse.card.wallets.isNotEmpty()
        ) {
            NfcDemoLogger.warning(
                "backup_scan_response_normalized",
                "scenario=$scenarioId wallets=${createWalletResponse.card.wallets.size}",
            )
            scanResponse.copy(
                card = createWalletResponse.card,
                derivedKeys = createWalletResponse.derivedKeys,
                primaryCard = scanResponse.primaryCard ?: createWalletResponse.primaryCard,
            )
        } else {
            scanResponse
        }

        when (currentState) {
            CardBackupService.State.Preparing -> prepareInterruptedBackup(
                card = restoredScanResponse.card,
                savedPrimaryCard = restoredScanResponse.primaryCard,
            )
            CardBackupService.State.Finished -> {
                // NFC completed but the app process died before the wallet was saved. Repeat only the last tap.
                currentState = CardBackupService.State.FinalizingBackupCard(
                    index = backupCards.size.coerceAtLeast(1),
                )
                persistState()
                NfcDemoLogger.warning(
                    "backup_state_rewound_after_incomplete_save",
                    "cardIndex=${backupCards.size.coerceAtLeast(1)}",
                )
            }
            CardBackupService.State.FinalizingPrimaryCard,
            is CardBackupService.State.FinalizingBackupCard,
            -> Unit
        }

        return restoredScanResponse
    }

    private fun prepareInterruptedBackup(card: CardDTO, savedPrimaryCard: PrimaryCard?) {
        if (currentState != CardBackupService.State.Preparing) return

        val scenarioId = MockProvider.currentNfcDemoScenarioId ?: LEGACY_RECOVERY_SCENARIO
        MockProvider.restoreNfcDemoScenario(scenarioId)
        primaryCard = savedPrimaryCard ?: createPrimaryCard(card)
        backupCards.clear()
        repeat(LEGACY_RECOVERY_BACKUP_CARDS_COUNT) { index -> backupCards += createBackupCard(index + 1) }
        accessCodeIsSet = true
        currentState = CardBackupService.State.FinalizingPrimaryCard
        persistState()
        NfcDemoLogger.warning(
            "backup_state_prepared_before_resume",
            "scenario=$scenarioId cards=$LEGACY_RECOVERY_BACKUP_CARDS_COUNT",
        )
    }

    override fun addBackupCard(callback: (CompletionResult<CardDTO>) -> Unit) {
        val cardNumber = backupCards.size + 1
        runPhysicalOperation("backup_add_card_$cardNumber", callback) {
            val card = createBackupCard(cardNumber)
            backupCards += card
            updatePreparingState()
            persistState()
            card
        }
    }

    override fun setAccessCode(accessCode: String): CompletionResult<Unit> {
        accessCodeIsSet = accessCode.isNotBlank()
        updatePreparingState()
        persistState()
        NfcDemoLogger.event("backup_access_code_set", "isSet=$accessCodeIsSet")
        scope.launch { hotWalletBridge?.protectCurrentWallet(accessCode) }
        return CompletionResult.Success(Unit)
    }

    override fun setPasscode(passcode: String): CompletionResult<Unit> {
        passcodeIsSet = passcode.isNotBlank()
        updatePreparingState()
        persistState()
        NfcDemoLogger.event("backup_passcode_set", "isSet=$passcodeIsSet")
        return CompletionResult.Success(Unit)
    }

    override fun proceedBackup(iconScanRes: Int?, callback: (CompletionResult<CardDTO>) -> Unit) {
        reconstructInterruptedBackupIfNeeded()

        val operation = when (val state = currentState) {
            CardBackupService.State.FinalizingPrimaryCard -> "backup_finalize_primary"
            is CardBackupService.State.FinalizingBackupCard -> "backup_finalize_card_${state.index}"
            CardBackupService.State.Preparing,
            CardBackupService.State.Finished,
            -> {
                callback(CompletionResult.Failure(TangemSdkError.UnknownError()))
                return
            }
        }

        runPhysicalOperation(operation, callback) {
            when (val state = currentState) {
                CardBackupService.State.FinalizingPrimaryCard -> {
                    currentState = CardBackupService.State.FinalizingBackupCard(index = 1)
                    persistState()
                    currentPrimaryCardDto()
                }
                is CardBackupService.State.FinalizingBackupCard -> {
                    val card = backupCards[state.index - 1]
                    currentState = if (state.index >= backupCards.size) {
                        CardBackupService.State.Finished
                    } else {
                        CardBackupService.State.FinalizingBackupCard(index = state.index + 1)
                    }
                    persistState()
                    card
                }
                CardBackupService.State.Preparing,
                CardBackupService.State.Finished,
                -> error("Unexpected backup state: $state")
            }
        }
    }

    override fun setPrimaryCard(primaryCard: PrimaryCard) {
        this.primaryCard = primaryCard
        updatePreparingState()
        persistState()
        NfcDemoLogger.event("backup_primary_card_set", "cardId=${primaryCard.cardId.takeLast(4)}")
    }

    override fun readPrimaryCard(
        iconScanRes: Int?,
        cardId: String?,
        callback: (CompletionResult<PrimaryCard>) -> Unit,
    ) {
        runPhysicalOperation("backup_read_primary", callback) {
            MockProvider.getCurrentPrimaryCard() ?: createPrimaryCard(currentPrimaryCardDto())
        }
    }

    private fun updatePreparingState() {
        if (currentState == CardBackupService.State.Preparing &&
            primaryCard != null &&
            backupCards.isNotEmpty() &&
            (accessCodeIsSet || passcodeIsSet)
        ) {
            currentState = CardBackupService.State.FinalizingPrimaryCard
        }
    }

    private fun restoreState(snapshot: NfcDemoBackupSnapshot) {
        if (!MockProvider.restoreNfcDemoScenario(snapshot.scenarioId)) {
            NfcDemoLogger.warning("backup_state_restore_failed", "reason=scenario_missing")
            stateStorage.clear()
            return
        }

        val primaryCardDto = MockProvider.getCurrentCardDto().copy(
            cardId = snapshot.primaryCardId,
            batchId = snapshot.primaryCardBatchId,
        )
        primaryCard = createPrimaryCard(primaryCardDto)
        backupCards.clear()
        repeat(snapshot.backupCardsCount) { index -> backupCards += createBackupCard(index + 1) }
        accessCodeIsSet = snapshot.accessCodeIsSet
        passcodeIsSet = snapshot.passcodeIsSet
        skipCompatibilityChecksInternal = snapshot.skipCompatibilityChecks
        currentState = snapshot.state
        NfcDemoLogger.event(
            "backup_state_restored",
            "state=${snapshot.state::class.simpleName} cards=${snapshot.backupCardsCount}",
        )
    }

    /**
     * Older NFC-demo builds did not persist their in-memory backup service. The onboarding component itself is
     * restorable, so reconstruct the matching three-device ceremony when upgrading from one of those builds.
     */
    private fun reconstructInterruptedBackupIfNeeded() {
        if (currentState != CardBackupService.State.Preparing) return

        val card = MockProvider.getCurrentCardDto()
        prepareInterruptedBackup(card = card, savedPrimaryCard = MockProvider.getCurrentPrimaryCard())
        NfcDemoLogger.warning(
            "backup_state_reconstructed",
            "cards=$LEGACY_RECOVERY_BACKUP_CARDS_COUNT",
        )
    }

    private fun persistState() {
        val primary = primaryCard ?: return
        stateStorage.save(
            NfcDemoBackupSnapshot(
                scenarioId = MockProvider.currentNfcDemoScenarioId,
                primaryCardId = primary.cardId,
                primaryCardBatchId = primary.batchId ?: MockProvider.getCurrentCardDto().batchId,
                backupCardsCount = backupCards.size,
                accessCodeIsSet = accessCodeIsSet,
                passcodeIsSet = passcodeIsSet,
                state = currentState,
                skipCompatibilityChecks = skipCompatibilityChecksInternal,
            ),
        )
    }

    private fun currentPrimaryCardDto(): CardDTO = MockProvider.getCurrentCardDto().copy(
        cardId = primaryCard?.cardId ?: MockProvider.getCurrentCardDto().cardId,
        batchId = primaryCard?.batchId ?: MockProvider.getCurrentCardDto().batchId,
    )

    private fun createBackupCard(number: Int): CardDTO {
        val source = MockProvider.getCurrentCardDto()
        val suffix = number.toString().padStart(length = 2, padChar = '0')
        val baseId = source.cardId.dropLast(n = 2)
        val publicKey = source.cardPublicKey.copyOf().also { key ->
            if (key.isNotEmpty()) key[key.lastIndex] = (key.last() + number).toByte()
        }

        return source.copy(
            cardId = "$baseId$suffix",
            cardPublicKey = publicKey,
        )
    }

    private fun createPrimaryCard(card: CardDTO): PrimaryCard = PrimaryCard(
        cardId = card.cardId,
        batchId = card.batchId,
        cardPublicKey = card.cardPublicKey,
        linkingKey = ByteArray(size = 33) { index -> (index + 1).toByte() },
        existingWalletsCount = card.wallets.size,
        isHDWalletAllowed = card.settings.isHDWalletAllowed,
        issuer = Card.Issuer(card.issuer.name, card.issuer.publicKey),
        manufacturer = Card.Manufacturer(
            name = card.manufacturer.name,
            manufactureDate = card.manufacturer.manufactureDate,
            signature = card.manufacturer.signature,
        ),
        walletCurves = card.supportedCurves,
        firmwareVersion = FirmwareVersion(
            major = card.firmwareVersion.major,
            minor = card.firmwareVersion.minor,
            patch = card.firmwareVersion.patch,
            type = card.firmwareVersion.type,
        ),
        isKeysImportAllowed = card.settings.isKeysImportAllowed,
        certificate = null,
    )

    private fun <T> runPhysicalOperation(
        operation: String,
        callback: (CompletionResult<T>) -> Unit,
        success: () -> T,
    ) {
        scope.launch {
            NfcDemoLogger.event("backup_operation_started", "operation=$operation")
            val result = when (val tapResult = NfcDemoTapGate.awaitTap(operation)) {
                NfcDemoTapGate.Result.Success -> CompletionResult.Success(success())
                NfcDemoTapGate.Result.RealCard,
                NfcDemoTapGate.Result.Cancelled,
                -> CompletionResult.Failure(TangemSdkError.UserCancelled())
                NfcDemoTapGate.Result.TagLost,
                NfcDemoTapGate.Result.Timeout,
                -> CompletionResult.Failure(TangemSdkError.TagLost())
            }
            NfcDemoLogger.event(
                "backup_operation_finished",
                "operation=$operation result=${result::class.simpleName}",
            )
            callback(result)
        }
    }

    private companion object {
        const val LEGACY_RECOVERY_SCENARIO = "wallet-2-empty"
        const val LEGACY_RECOVERY_BACKUP_CARDS_COUNT = 2
    }
}
