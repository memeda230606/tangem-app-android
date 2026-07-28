package com.tangem.tap.domain.sdk.mocks

import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError
import com.tangem.domain.models.scan.ProductType
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.operations.derivation.DerivationTaskResponse
import com.tangem.sdk.api.CreateProductWalletTaskResponse
import com.tangem.tap.domain.sdk.mocks.content.*

object MockProvider {

    private var content: MockContent = getMockContent(ProductType.Wallet)
    private var activeScanResponse: ScanResponse? = null

    var isPreset: Boolean = false
        private set

    var currentNfcDemoScenarioId: String? = null
        private set

    private var isEmulatingError: Boolean = false

    private var emulatedError: TangemError = TangemSdkError.TagLost()

    val availableMocks: List<MockOption> = listOf(
        MockOption("Wallet") { WalletMockContent },
        MockOption("Note") { NoteMockContent },
        MockOption("Twins") { TwinsMockContent },
        MockOption("Ring") { RingMockContent },
        MockOption("Wallet 2") { Wallet2MockContent },
        MockOption("Visa") { VisaMockContent },
        MockOption("Wallet 2 (No Backup)") { Wallet2NoBackupMockContent },
        MockOption("Wallet 2 (No Backup, No Wallets)") { Wallet2NoBackupNoWalletsMockContent },
        MockOption("Wallet 2 (Seed Phrase)") { Wallet2WithSeedPhraseMockContent },
        MockOption("Wallet 2 (With derivations)") { Wallet2WithDerivationsMockContent },
        MockOption("Shiba") { ShibaMockContent },
        MockOption("Shiba (No Backup)") { ShibaNoBackupMockContent },
        MockOption("Shiba (No Backup, No Wallets)") { ShibaNoBackupNoWalletsMockContent },
        MockOption("Ed25519 Curve") { EdCurveMockContent },
        MockOption("Secp256k1 Curve") { Secpk1CurveMockContent },
        MockOption("Backup Wallet") { BackupWalletMockContent },
        MockOption("Dev Wallet") { DevWalletMockContent },
        MockOption("Firmware 4.12") { Firmware412MockContent },
        MockOption("Cobrand") { showCobrandConfigDialog(it) },
    )

    fun setEmulateError(error: TangemError? = null) {
        isEmulatingError = true
        error?.let {
            emulatedError = it
        }
        NfcDemoLogger.warning("mock_error_enabled", "type=${emulatedError::class.simpleName}")
    }

    fun resetEmulateError() {
        isEmulatingError = false
        NfcDemoLogger.event("mock_error_disabled")
    }

    fun setMocks(productType: ProductType) {
        content = getMockContent(productType)
        isPreset = true
    }

    fun setMocks(mockContent: MockContent) {
        content = mockContent
        isPreset = true
    }

    /**
     * Applies a demo scenario encoded as an NDEF URI record.
     *
     * This is only called from NFC-demo-enabled build types. The NFC tag is not an authentication factor;
     * it only chooses one of the local card profiles.
     */
    fun applyNfcDemoTag(tagUri: String?): Boolean {
        val scenarioId = NfcDemoTag.scenarioId(tagUri)
        if (scenarioId == null) {
            NfcDemoLogger.warning("scenario_rejected", "reason=invalid_uri")
            return false
        }

        NfcDemoLogger.event("scenario_received", "scenario=$scenarioId")

        val mockContent = getNfcDemoMockContent(scenarioId) ?: run {
            NfcDemoLogger.warning("scenario_rejected", "scenario=$scenarioId reason=unsupported")
            return false
        }

        resetEmulateError()
        setMocks(mockContent)
        activeScanResponse = null
        currentNfcDemoScenarioId = scenarioId

        if (scenarioId == "tag-lost") {
            setEmulateError()
        }

        NfcDemoLogger.event("scenario_applied", "scenario=$scenarioId result=success")
        return true
    }

    /** Restores the last local profile after Android recreates the app process. */
    fun restoreNfcDemoScenario(scenarioId: String?): Boolean {
        val restoredContent = scenarioId?.let(::getNfcDemoMockContent) ?: return false
        content = restoredContent
        activeScanResponse = null
        isPreset = true
        currentNfcDemoScenarioId = scenarioId
        isEmulatingError = scenarioId == "tag-lost"
        NfcDemoLogger.event("scenario_restored", "scenario=$scenarioId")
        return true
    }

    private fun getNfcDemoMockContent(scenarioId: String): MockContent? {
        return when (scenarioId) {
            "all", "wallet-2" -> Wallet2MockContent
            "wallet" -> WalletMockContent
            "twins" -> TwinsMockContent
            "ring" -> RingMockContent
            "note" -> NoteMockContent
            "wallet-2-no-backup" -> Wallet2NoBackupMockContent
            "wallet-2-empty" -> Wallet2NoBackupNoWalletsMockContent
            "wallet-2-seed" -> Wallet2WithSeedPhraseMockContent
            "wallet-2-derivations" -> Wallet2WithDerivationsMockContent
            "shiba" -> ShibaMockContent
            "shiba-no-backup" -> ShibaNoBackupMockContent
            "shiba-empty" -> ShibaNoBackupNoWalletsMockContent
            "backup-wallet" -> BackupWalletMockContent
            "dev-wallet" -> DevWalletMockContent
            "firmware-4-12" -> Firmware412MockContent
            "ed25519" -> EdCurveMockContent
            "secp256k1" -> Secpk1CurveMockContent
            "visa" -> VisaMockContent
            "tag-lost" -> Wallet2MockContent
            else -> null
        }
    }

    fun setMocksWithoutPresetFlag(mockContent: MockContent) {
        content = mockContent
    }

    fun getSuccessResponse() = CompletionResult.Success(content.successResponse).orFailure()

    fun <T> getDemoResponse(data: T) = CompletionResult.Success(data).orFailure()

    fun getScanResponse() = CompletionResult.Success(activeScanResponse ?: content.scanResponse).orFailure()

    fun getDerivationTaskResponse() = CompletionResult.Success(
        activeScanResponse?.let { DerivationTaskResponse(entries = it.derivedKeys) } ?: content.derivationTaskResponse,
    ).orFailure()

    fun getCardDto() = CompletionResult.Success(activeScanResponse?.card ?: content.cardDto).orFailure()

    fun getCurrentCardDto() = activeScanResponse?.card ?: content.cardDto

    fun setCreatedWalletResponse(response: CreateProductWalletTaskResponse) {
        activeScanResponse = content.scanResponse.copy(
            card = response.card,
            derivedKeys = response.derivedKeys,
            primaryCard = response.primaryCard,
        )
    }

    fun getCurrentPrimaryCard() = content.createProductWalletTaskResponse.primaryCard

    fun getCurrentCreateProductWalletResponse() = content.createProductWalletTaskResponse

    fun getExtendedPublicKey() = CompletionResult.Success(content.extendedPublicKey).orFailure()

    fun getCreateProductWalletResponse(): CompletionResult<CreateProductWalletTaskResponse> {
        return CompletionResult.Success(content.createProductWalletTaskResponse).orFailure()
    }

    fun getImportWalletResponse(): CompletionResult<CreateProductWalletTaskResponse> {
        return CompletionResult.Success(content.importWalletResponse).orFailure()
    }

    // region Twin-specific

    fun finalizeTwin() = CompletionResult.Success(content.finalizeTwinResponse).orFailure()

    fun createFirstTwinWallet() = CompletionResult.Success(content.createFirstTwinResponse).orFailure()

    fun createSecondTwinWallet() = CompletionResult.Success(content.createSecondTwinResponse).orFailure()

    // endregion

    private fun getMockContent(productType: ProductType): MockContent {
        return when (productType) {
            ProductType.Wallet -> WalletMockContent
            ProductType.Wallet2 -> Wallet2WithSeedPhraseMockContent
            ProductType.Note -> NoteMockContent
            ProductType.Ring -> RingMockContent
            ProductType.Twins -> TwinsMockContent
            ProductType.Visa -> VisaMockContent
            ProductType.Start2Coin -> NoteMockContent
        }
    }

    internal fun resetForTest() {
        content = getMockContent(ProductType.Wallet)
        activeScanResponse = null
        isPreset = false
        currentNfcDemoScenarioId = null
        isEmulatingError = false
        emulatedError = TangemSdkError.TagLost()
    }

    private fun <T> CompletionResult.Success<T>.orFailure(): CompletionResult<T> {
        return if (isEmulatingError) {
            CompletionResult.Failure(emulatedError)
        } else {
            this
        }
    }
}
