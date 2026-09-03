package com.tangem.tap.domain.sdk.impl

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import arrow.core.Either
import com.tangem.Message
import com.tangem.common.CompletionResult
import com.tangem.common.KeyPair
import com.tangem.common.SuccessResponse
import com.tangem.common.authentication.keystore.DummyKeystoreManager
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.TangemSdkError
import com.tangem.common.core.UserCodeRequestPolicy
import com.tangem.common.extensions.ByteArrayKey
import com.tangem.common.services.InMemoryStorage
import com.tangem.core.analytics.models.AnalyticsParam
import com.tangem.core.res.getStringSafe
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.pay.WithdrawalSignatureResult
import com.tangem.domain.visa.model.TangemPayInitialCredentials
import com.tangem.domain.visa.model.VisaActivationInput
import com.tangem.domain.visa.model.VisaDataForApprove
import com.tangem.domain.visa.model.VisaSignedDataByCustomerWallet
import com.tangem.operations.derivation.DerivationTaskResponse
import com.tangem.operations.derivation.ExtendedPublicKeysMap
import com.tangem.operations.preflightread.PreflightReadFilter
import com.tangem.operations.wallet.CreateWalletResponse
import com.tangem.sdk.api.CreateProductWalletTaskResponse
import com.tangem.sdk.api.TangemSdkManager
import com.tangem.sdk.api.visa.VisaCardActivationResponse
import com.tangem.sdk.api.visa.VisaCardActivationTaskMode
import com.tangem.tap.ForegroundActivityObserver
import com.tangem.tap.domain.sdk.mocks.MockContent
import com.tangem.tap.domain.sdk.mocks.MockProvider
import com.tangem.tap.domain.sdk.mocks.content.ExternalNdefWalletMockContent
import com.tangem.tap.features.intentHandler.handlers.ExternalNdefScanController
import com.tangem.wallet.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@Suppress("TooManyFunctions")
class MockTangemSdkManager(
    private val resources: Resources,
    private val returnOnlyRequestedDerivations: Boolean = false,
    private val externalNdefScanController: ExternalNdefScanController? = null,
) : TangemSdkManager {

    private var userCodeRequestPolicyInternal: UserCodeRequestPolicy = UserCodeRequestPolicy.Default

    override val canUseBiometry: Boolean = false

    override val needEnrollBiometrics: Boolean = false

    override val keystoreManager = DummyKeystoreManager()

    override val secureStorage = InMemoryStorage()

    override val userCodeRequestPolicy: UserCodeRequestPolicy
        get() = userCodeRequestPolicyInternal

    override suspend fun checkCanUseBiometry(awaitInitialization: Boolean): Boolean = canUseBiometry

    override suspend fun checkNeedEnrollBiometrics(awaitInitialization: Boolean): Boolean = needEnrollBiometrics

    override suspend fun scanProduct(
        cardId: String?,
        messageRes: Int?,
        allowsRequestAccessCodeFromRepository: Boolean,
        shouldCheckIsAlreadyActivated: Boolean,
        source: AnalyticsParam.ScreensSources,
    ): CompletionResult<ScanResponse> {
        if (!MockProvider.isPreset) {
            val activity = ForegroundActivityObserver.foregroundActivity
            if (activity != null) {
                val selectedMock = showMockCardPickerDialog(activity)
                if (selectedMock != null) {
                    MockProvider.setMocksWithoutPresetFlag(selectedMock)
                } else {
                    return CompletionResult.Failure(TangemSdkError.UserCancelled())
                }
            }
        }
        return MockProvider.getScanResponse()
    }

    override suspend fun createProductWallet(
        scanResponse: ScanResponse,
        shouldReset: Boolean,
    ): CompletionResult<CreateProductWalletTaskResponse> {
        val response = MockProvider.getCreateProductWalletResponse()
        if (externalNdefScanController == null || response !is CompletionResult.Success) return response

        val claimed = withContext(Dispatchers.IO) { ExternalNdefWalletMockContent.claimCurrentCard() }
        return if (claimed) response else CompletionResult.Failure(TangemSdkError.UserCancelled())
    }

    override suspend fun importWallet(
        scanResponse: ScanResponse,
        mnemonic: String,
        passphrase: String?,
        shouldReset: Boolean,
    ): CompletionResult<CreateProductWalletTaskResponse> {
        return MockProvider.getImportWalletResponse()
    }

    override suspend fun derivePublicKeys(
        cardId: String?,
        derivations: Map<ByteArrayKey, List<DerivationPath>>,
        preflightReadFilter: PreflightReadFilter?,
    ): CompletionResult<DerivationTaskResponse> {
        if (externalNdefScanController != null) {
            when (externalNdefScanController.awaitScan()) {
                ExternalNdefScanController.Result.Accepted -> Unit
                ExternalNdefScanController.Result.Rejected -> {
                    return CompletionResult.Failure(TangemSdkError.UserCancelled())
                }
            }
        }

        val result = MockProvider.getDerivationTaskResponse()
        if (!returnOnlyRequestedDerivations || result !is CompletionResult.Success) return result

        val requestedEntries = result.data.entries.mapNotNull { (walletPublicKey, availableKeys) ->
            val requestedPaths = derivations[walletPublicKey].orEmpty().toSet()
            val fallbackKey = availableKeys.values.firstOrNull()
            val matchingKeys = requestedPaths.mapNotNull { path ->
                val extendedPublicKey = availableKeys[path] ?: fallbackKey ?: return@mapNotNull null

                path to extendedPublicKey
            }.toMap()

            if (matchingKeys.isEmpty()) {
                null
            } else {
                walletPublicKey to ExtendedPublicKeysMap(matchingKeys)
            }
        }.toMap()

        ExternalNdefWalletMockContent.rememberDerivations(derivations)

        return CompletionResult.Success(DerivationTaskResponse(entries = requestedEntries))
    }

    override suspend fun deriveExtendedPublicKey(
        cardId: String?,
        walletPublicKey: ByteArray,
        derivation: DerivationPath,
    ): CompletionResult<ExtendedPublicKey> {
        return MockProvider.getExtendedPublicKey()
    }

    override suspend fun resetToFactorySettings(
        cardId: String,
        allowsRequestAccessCodeFromRepository: Boolean,
    ): CompletionResult<Boolean> {
        return CompletionResult.Success(true)
    }

    override suspend fun resetBackupCard(cardNumber: Int, userWalletId: UserWalletId): CompletionResult<Boolean> {
        return CompletionResult.Success(true)
    }

    override suspend fun saveAccessCode(accessCode: String, cardsIds: Set<String>): CompletionResult<Unit> {
        return CompletionResult.Success(Unit)
    }

    override suspend fun deleteSavedUserCodes(cardsIds: Set<String>): CompletionResult<Unit> {
        return CompletionResult.Success(Unit)
    }

    override suspend fun clearSavedUserCodes(): CompletionResult<Unit> {
        return CompletionResult.Success(Unit)
    }

    override suspend fun setPasscode(cardId: String?): CompletionResult<SuccessResponse> {
        return MockProvider.getSuccessResponse()
    }

    override suspend fun setAccessCode(cardId: String?): CompletionResult<SuccessResponse> {
        return MockProvider.getSuccessResponse()
    }

    override suspend fun setLongTap(cardId: String?): CompletionResult<SuccessResponse> {
        return MockProvider.getSuccessResponse()
    }

    override suspend fun setAccessCodeRecoveryEnabled(
        cardId: String?,
        enabled: Boolean,
    ): CompletionResult<SuccessResponse> {
        return MockProvider.getSuccessResponse()
    }

    override suspend fun scanCard(
        cardId: String?,
        allowRequestAccessCodeFromRepository: Boolean,
    ): CompletionResult<CardDTO> {
        return MockProvider.getCardDto()
    }

    override suspend fun <T> runTaskAsync(
        runnable: CardSessionRunnable<T>,
        preflightReadFilter: PreflightReadFilter?,
        cardId: String?,
        initialMessage: Message?,
        accessCode: String?,
        @DrawableRes iconScanRes: Int?,
    ): CompletionResult<T> = error("This method is deprecated")

    override fun changeDisplayedCardIdNumbersCount(scanResponse: ScanResponse?) {
        // intentionally do nothing
    }

    @Deprecated("TangemSdkManager shouldn't returns a string from resources")
    override fun getString(@StringRes stringResId: Int, vararg formatArgs: Any?): String {
        val args = formatArgs.toSet().filterNotNull().toTypedArray()

        return resources.getStringSafe(stringResId, *args)
    }

    override fun setUserCodeRequestPolicy(policy: UserCodeRequestPolicy) {
        userCodeRequestPolicyInternal = policy
    }

    // region Twin-specific

    override suspend fun createFirstTwinWallet(
        cardId: String,
        initialMessage: Message,
    ): CompletionResult<CreateWalletResponse> {
        return MockProvider.createFirstTwinWallet()
    }

    override suspend fun createSecondTwinWallet(
        firstPublicKey: String,
        firstCardId: String,
        issuerKeys: KeyPair,
        preparingMessage: Message,
        creatingWalletMessage: Message,
        initialMessage: Message,
    ): CompletionResult<CreateWalletResponse> {
        return MockProvider.createSecondTwinWallet()
    }

    override fun changeProductType(isRing: Boolean) = Unit

    override fun clearProductType() = Unit

    override suspend fun finalizeTwin(
        secondCardPublicKey: ByteArray,
        issuerKeyPair: KeyPair,
        cardId: String,
        initialMessage: Message,
    ): CompletionResult<ScanResponse> {
        return MockProvider.finalizeTwin()
    }

    // endregion

    // region Visa-specific

    override suspend fun activateVisaCard(
        mode: VisaCardActivationTaskMode,
        activationInput: VisaActivationInput,
    ): CompletionResult<VisaCardActivationResponse> {
        error("Not implemented")
    }

    override suspend fun visaCustomerWalletApprove(
        visaDataForApprove: VisaDataForApprove,
    ): CompletionResult<VisaSignedDataByCustomerWallet> {
        error("Not implemented")
    }

    override suspend fun tangemPayProduceInitialCredentials(
        preflightReadFilter: PreflightReadFilter,
    ): Either<Throwable, TangemPayInitialCredentials> {
        error("Not implemented")
    }

    override suspend fun getWithdrawalSignature(
        hash: String,
        preflightReadFilter: PreflightReadFilter,
    ): Either<Throwable, WithdrawalSignatureResult> {
        error("Not implemented")
    }

    // endregion
}

private suspend fun showMockCardPickerDialog(activity: AppCompatActivity): MockContent? =
    withContext(Dispatchers.Main) {
        val selected = suspendCancellableCoroutine { continuation ->
            val mocks = MockProvider.availableMocks
            val names = mocks.map { it.title }.toTypedArray()

            val dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.mock_card_picker_title)
                .setItems(names) { _, which ->
                    if (continuation.isActive) {
                        continuation.resume(mocks[which])
                    }
                }
                .setOnCancelListener {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
                .create()

            continuation.invokeOnCancellation { activity.runOnUiThread(dialog::dismiss) }
            dialog.show()
        }

        selected?.resolve?.invoke(activity)
    }
