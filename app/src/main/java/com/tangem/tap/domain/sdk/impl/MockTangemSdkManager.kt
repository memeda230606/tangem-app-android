package com.tangem.tap.domain.sdk.impl

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import arrow.core.Either
import arrow.core.left
import arrow.core.right
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
import com.tangem.domain.visa.model.TangemPayAuthTokens
import com.tangem.domain.visa.model.TangemPayInitialCredentials
import com.tangem.domain.visa.model.VisaActivationInput
import com.tangem.domain.visa.model.VisaActivationOrderInfo
import com.tangem.domain.visa.model.VisaCardWalletDataToSignRequest
import com.tangem.domain.visa.model.VisaDataForApprove
import com.tangem.domain.visa.model.VisaDataToSignByCardWallet
import com.tangem.domain.visa.model.VisaSignedDataByCustomerWallet
import com.tangem.domain.visa.model.sign
import com.tangem.operations.derivation.DerivationTaskResponse
import com.tangem.operations.preflightread.PreflightReadFilter
import com.tangem.operations.wallet.CreateWalletResponse
import com.tangem.sdk.api.CreateProductWalletTaskResponse
import com.tangem.sdk.api.TangemSdkManager
import com.tangem.sdk.api.visa.VisaCardActivationResponse
import com.tangem.sdk.api.visa.VisaCardActivationTaskMode
import com.tangem.tap.domain.sdk.mocks.NfcDemoTapGate
import com.tangem.tap.domain.sdk.mocks.NfcDemoLogger
import com.tangem.tap.domain.sdk.mocks.NfcDemoHotWalletBridge
import com.tangem.tap.domain.sdk.mocks.MockProvider
import com.tangem.tap.domain.sdk.mocks.showMockCardPicker
import com.tangem.tap.foregroundActivityObserver
import com.tangem.wallet.BuildConfig

@Suppress("TooManyFunctions")
internal class MockTangemSdkManager(
    private val resources: Resources,
    private val hotWalletBridge: NfcDemoHotWalletBridge,
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
        NfcDemoLogger.event("sdk_scan_started", "preset=${MockProvider.isPreset}")
        if (BuildConfig.NFC_DEMO_ENABLED) {
            when (val tapResult = NfcDemoTapGate.awaitTap(operation = "scan_product")) {
                NfcDemoTapGate.Result.Success -> Unit
                NfcDemoTapGate.Result.RealCard,
                NfcDemoTapGate.Result.TagLost,
                NfcDemoTapGate.Result.Timeout,
                -> {
                    NfcDemoLogger.warning("sdk_scan_finished", "result=$tapResult physicalTap=false")
                    return CompletionResult.Failure(TangemSdkError.TagLost())
                }
                NfcDemoTapGate.Result.Cancelled -> {
                    NfcDemoLogger.warning("sdk_scan_finished", "result=cancelled physicalTap=false")
                    return CompletionResult.Failure(TangemSdkError.UserCancelled())
                }
            }
            NfcDemoLogger.event("sdk_scan_physical_gate_finished", "result=success physicalTap=true")
        }
        return scanProductAfterPhysicalGate()
    }

    internal suspend fun scanProductAfterPhysicalGate(): CompletionResult<ScanResponse> {
        if (!MockProvider.isPreset) {
            val activity = foregroundActivityObserver.foregroundActivity
            if (activity != null) {
                val selectedMock = showMockCardPicker(activity)
                if (selectedMock != null) {
                    MockProvider.setMocksWithoutPresetFlag(selectedMock)
                } else {
                    NfcDemoLogger.warning("sdk_scan_finished", "result=cancelled")
                    return CompletionResult.Failure(TangemSdkError.UserCancelled())
                }
            }
        }
        val response = MockProvider.getScanResponse()
        val restored = if (response is CompletionResult.Success && BuildConfig.NFC_DEMO_ENABLED) {
            CompletionResult.Success(
                hotWalletBridge.restoreSavedScanResponse(response.data.card.cardId, response.data),
            )
        } else {
            response
        }
        return restored.also {
            NfcDemoLogger.event("sdk_scan_finished", "result=${it::class.simpleName}")
        }
    }

    override suspend fun createProductWallet(
        scanResponse: ScanResponse,
        shouldReset: Boolean,
    ): CompletionResult<CreateProductWalletTaskResponse> {
        return mockOperation("create_product_wallet") {
            val fixture = MockProvider.getCreateProductWalletResponse()
            if (!BuildConfig.NFC_DEMO_ENABLED || fixture !is CompletionResult.Success) return@mockOperation fixture
            runCatching { hotWalletBridge.createWalletResponse(scanResponse.card.cardId, fixture.data) }
                .fold(
                    onSuccess = { response ->
                        MockProvider.setCreatedWalletResponse(response)
                        hotWalletBridge.saveCreatedScanResponse(
                            cardId = scanResponse.card.cardId,
                            scanResponse = scanResponse.copy(
                                card = response.card,
                                derivedKeys = response.derivedKeys,
                                primaryCard = response.primaryCard,
                            ),
                        )
                        CompletionResult.Success(response)
                    },
                    onFailure = { CompletionResult.Failure(TangemSdkError.ExceptionError(it)) },
                )
        }
    }

    override suspend fun importWallet(
        scanResponse: ScanResponse,
        mnemonic: String,
        passphrase: String?,
        shouldReset: Boolean,
    ): CompletionResult<CreateProductWalletTaskResponse> {
        return mockOperation("import_wallet") {
            val fixture = MockProvider.getImportWalletResponse()
            if (!BuildConfig.NFC_DEMO_ENABLED || fixture !is CompletionResult.Success) return@mockOperation fixture
            runCatching { hotWalletBridge.createWalletResponse(scanResponse.card.cardId, fixture.data) }
                .fold(
                    onSuccess = { response ->
                        MockProvider.setCreatedWalletResponse(response)
                        hotWalletBridge.saveCreatedScanResponse(
                            cardId = scanResponse.card.cardId,
                            scanResponse = scanResponse.copy(
                                card = response.card,
                                derivedKeys = response.derivedKeys,
                                primaryCard = response.primaryCard,
                            ),
                        )
                        CompletionResult.Success(response)
                    },
                    onFailure = { CompletionResult.Failure(TangemSdkError.ExceptionError(it)) },
                )
        }
    }

    override suspend fun derivePublicKeys(
        cardId: String?,
        derivations: Map<ByteArrayKey, List<DerivationPath>>,
        preflightReadFilter: PreflightReadFilter?,
    ): CompletionResult<DerivationTaskResponse> {
        return mockOperation("derive_public_keys") { MockProvider.getDerivationTaskResponse() }
    }

    override suspend fun deriveExtendedPublicKey(
        cardId: String?,
        walletPublicKey: ByteArray,
        derivation: DerivationPath,
    ): CompletionResult<ExtendedPublicKey> {
        return mockOperation("derive_extended_public_key") { MockProvider.getExtendedPublicKey() }
    }

    override suspend fun resetToFactorySettings(
        cardId: String,
        allowsRequestAccessCodeFromRepository: Boolean,
    ): CompletionResult<Boolean> {
        return mockOperation("reset_factory") { CompletionResult.Success(true) }
    }

    override suspend fun resetBackupCard(cardNumber: Int, userWalletId: UserWalletId): CompletionResult<Boolean> {
        return mockOperation("reset_backup_card") { CompletionResult.Success(true) }
    }

    override suspend fun saveAccessCode(accessCode: String, cardsIds: Set<String>): CompletionResult<Unit> {
        if (BuildConfig.NFC_DEMO_ENABLED) hotWalletBridge.protectCurrentWallet(accessCode)
        return localOperation("save_access_code") { CompletionResult.Success(Unit) }
    }

    override suspend fun deleteSavedUserCodes(cardsIds: Set<String>): CompletionResult<Unit> {
        return localOperation("delete_saved_user_codes") { CompletionResult.Success(Unit) }
    }

    override suspend fun clearSavedUserCodes(): CompletionResult<Unit> {
        return localOperation("clear_saved_user_codes") { CompletionResult.Success(Unit) }
    }

    override suspend fun setPasscode(cardId: String?): CompletionResult<SuccessResponse> {
        return mockOperation("set_passcode") { MockProvider.getSuccessResponse() }
    }

    override suspend fun setAccessCode(cardId: String?): CompletionResult<SuccessResponse> {
        return mockOperation("set_access_code") { MockProvider.getSuccessResponse() }
    }

    override suspend fun setLongTap(cardId: String?): CompletionResult<SuccessResponse> {
        return mockOperation("set_long_tap") { MockProvider.getSuccessResponse() }
    }

    override suspend fun setAccessCodeRecoveryEnabled(
        cardId: String?,
        enabled: Boolean,
    ): CompletionResult<SuccessResponse> {
        return mockOperation("set_access_code_recovery") { MockProvider.getSuccessResponse() }
    }

    override suspend fun scanCard(
        cardId: String?,
        allowRequestAccessCodeFromRepository: Boolean,
    ): CompletionResult<CardDTO> {
        return mockOperation("scan_card") { MockProvider.getCardDto() }
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
        return mockOperation("create_first_twin_wallet") { MockProvider.createFirstTwinWallet() }
    }

    override suspend fun createSecondTwinWallet(
        firstPublicKey: String,
        firstCardId: String,
        issuerKeys: KeyPair,
        preparingMessage: Message,
        creatingWalletMessage: Message,
        initialMessage: Message,
    ): CompletionResult<CreateWalletResponse> {
        return mockOperation("create_second_twin_wallet") { MockProvider.createSecondTwinWallet() }
    }

    override fun changeProductType(isRing: Boolean) = Unit

    override fun clearProductType() = Unit

    override suspend fun finalizeTwin(
        secondCardPublicKey: ByteArray,
        issuerKeyPair: KeyPair,
        cardId: String,
        initialMessage: Message,
    ): CompletionResult<ScanResponse> {
        return mockOperation("finalize_twin") { MockProvider.finalizeTwin() }
    }

    // endregion

    // region Visa-specific

    override suspend fun activateVisaCard(
        mode: VisaCardActivationTaskMode,
        activationInput: VisaActivationInput,
    ): CompletionResult<VisaCardActivationResponse> {
        NfcDemoLogger.event("visa_card_activation", "phase=card_wallet_sign mode=${mode::class.simpleName}")
        return mockOperation("activate_visa_card") {
            MockProvider.getDemoResponse(
                VisaCardActivationResponse(
                    signedActivationData = MOCK_ACTIVATION_DATA.sign(
                        rootOTP = MOCK_ROOT_OTP,
                        otpCounter = MOCK_OTP_COUNTER,
                        signature = MOCK_SIGNATURE,
                    ),
                    newCardDTO = MockProvider.getCurrentCardDto(),
                ),
            )
        }
    }

    override suspend fun visaCustomerWalletApprove(
        visaDataForApprove: VisaDataForApprove,
    ): CompletionResult<VisaSignedDataByCustomerWallet> {
        NfcDemoLogger.event("visa_card_activation", "phase=customer_wallet_sign")
        return mockOperation("visa_customer_wallet_approve") {
            MockProvider.getDemoResponse(
                visaDataForApprove.dataToSign.sign(
                    signature = MOCK_SIGNATURE,
                    customerWalletAddress = visaDataForApprove.targetAddress,
                ),
            )
        }
    }

    override suspend fun tangemPayProduceInitialCredentials(
        preflightReadFilter: PreflightReadFilter,
    ): Either<Throwable, TangemPayInitialCredentials> {
        NfcDemoLogger.event("tangem_pay_credentials", "phase=produce")
        return when (val gateError = awaitPhysicalGate("tangem_pay_initial_credentials")) {
            null -> when (
                val result = MockProvider.getDemoResponse(
                    TangemPayInitialCredentials(
                        customerWalletAddress = MOCK_CUSTOMER_WALLET_ADDRESS,
                        authTokens = TangemPayAuthTokens(
                            accessToken = MOCK_ACCESS_TOKEN,
                            expiresAt = MOCK_TOKEN_EXPIRES_AT,
                            refreshToken = MOCK_REFRESH_TOKEN,
                            refreshExpiresAt = MOCK_TOKEN_EXPIRES_AT,
                            idempotencyKey = MOCK_IDEMPOTENCY_KEY,
                        ),
                    ),
                )
            ) {
                is CompletionResult.Failure -> result.error.left()
                is CompletionResult.Success -> result.data.right()
            }
            else -> gateError.left()
        }
    }

    override suspend fun getWithdrawalSignature(
        hash: String,
        preflightReadFilter: PreflightReadFilter,
    ): Either<Throwable, WithdrawalSignatureResult> {
        NfcDemoLogger.event("tangem_pay_withdrawal_signature", "phase=produce")
        return when (val gateError = awaitPhysicalGate("tangem_pay_withdrawal_signature")) {
            null -> when (
                val result = MockProvider.getDemoResponse(
                    WithdrawalSignatureResult.Success(MOCK_SIGNATURE),
                )
            ) {
                is CompletionResult.Failure -> result.error.left()
                is CompletionResult.Success -> result.data.right()
            }
            else -> gateError.left()
        }
    }

    // endregion

    private suspend inline fun <T> mockOperation(
        operation: String,
        crossinline block: suspend () -> CompletionResult<T>,
    ): CompletionResult<T> {
        NfcDemoLogger.event("sdk_operation_started", "operation=$operation")
        val gateError = awaitPhysicalGate(operation)
        if (gateError != null) {
            NfcDemoLogger.warning(
                "sdk_operation_finished",
                "operation=$operation result=${gateError::class.simpleName} physicalTap=false",
            )
            return CompletionResult.Failure(gateError)
        }

        return block().also { result ->
            NfcDemoLogger.event(
                "sdk_operation_finished",
                "operation=$operation result=${result::class.simpleName} physicalTap=${BuildConfig.NFC_DEMO_ENABLED}",
            )
        }
    }

    private inline fun <T> localOperation(operation: String, block: () -> CompletionResult<T>): CompletionResult<T> {
        NfcDemoLogger.event("sdk_local_operation_started", "operation=$operation")
        return block().also { result ->
            NfcDemoLogger.event(
                "sdk_local_operation_finished",
                "operation=$operation result=${result::class.simpleName}",
            )
        }
    }

    private suspend fun awaitPhysicalGate(operation: String): TangemSdkError? {
        if (!BuildConfig.NFC_DEMO_ENABLED) return null

        return when (val tapResult = NfcDemoTapGate.awaitTap(operation)) {
            NfcDemoTapGate.Result.Success -> {
                NfcDemoLogger.event(
                    "sdk_operation_physical_gate_finished",
                    "operation=$operation result=success physicalTap=true",
                )
                null
            }
            NfcDemoTapGate.Result.RealCard,
            NfcDemoTapGate.Result.Cancelled,
            -> TangemSdkError.UserCancelled()
            NfcDemoTapGate.Result.TagLost,
            NfcDemoTapGate.Result.Timeout,
            -> TangemSdkError.TagLost()
        }.also { error ->
            if (error != null) {
                NfcDemoLogger.warning(
                    "sdk_operation_physical_gate_finished",
                    "operation=$operation result=${error::class.simpleName} physicalTap=false",
                )
            }
        }
    }

    private companion object {
        const val MOCK_ROOT_OTP = "00000000000000000000000000000000"
        const val MOCK_OTP_COUNTER = 1
        const val MOCK_SIGNATURE =
            "0000000000000000000000000000000000000000000000000000000000000000" +
                "000000000000000000000000000000000000000000000000000000000000001b"
        const val MOCK_CUSTOMER_WALLET_ADDRESS = "0x0000000000000000000000000000000000000002"
        const val MOCK_ACCESS_TOKEN = "mock-access-token"
        const val MOCK_REFRESH_TOKEN = "mock-refresh-token"
        const val MOCK_IDEMPOTENCY_KEY = "mock-idempotency-key"
        const val MOCK_TOKEN_EXPIRES_AT = 9_999_999_999L

        val MOCK_ACTIVATION_DATA = VisaDataToSignByCardWallet(
            request = VisaCardWalletDataToSignRequest(
                activationOrderInfo = VisaActivationOrderInfo(
                    orderId = "mock-activation-order",
                    customerId = "mock-customer",
                    customerWalletAddress = MOCK_CUSTOMER_WALLET_ADDRESS,
                    cardWalletAddress = MOCK_CUSTOMER_WALLET_ADDRESS,
                ),
                cardWalletAddress = MOCK_CUSTOMER_WALLET_ADDRESS,
            ),
            hashToSign = "0000000000000000000000000000000000000000000000000000000000000001",
        )
    }
}
