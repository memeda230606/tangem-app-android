package com.tangem.tap.domain.sdk.impl

import androidx.annotation.DrawableRes
import arrow.core.Either
import com.tangem.Message
import com.tangem.common.CompletionResult
import com.tangem.common.KeyPair
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.TangemSdkError
import com.tangem.common.extensions.ByteArrayKey
import com.tangem.core.analytics.models.AnalyticsParam
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.domain.demo.models.DemoConfig
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.pay.WithdrawalSignatureResult
import com.tangem.domain.visa.model.TangemPayInitialCredentials
import com.tangem.domain.visa.model.VisaActivationInput
import com.tangem.domain.visa.model.VisaDataForApprove
import com.tangem.domain.visa.model.VisaSignedDataByCustomerWallet
import com.tangem.operations.derivation.DerivationTaskResponse
import com.tangem.operations.preflightread.PreflightReadFilter
import com.tangem.operations.wallet.CreateWalletResponse
import com.tangem.sdk.api.TangemSdkManager
import com.tangem.sdk.api.visa.VisaCardActivationResponse
import com.tangem.sdk.api.visa.VisaCardActivationTaskMode
import com.tangem.tap.domain.sdk.mocks.NfcCardOriginRegistry
import com.tangem.tap.domain.sdk.mocks.NfcDemoHotWalletBridge
import com.tangem.tap.domain.sdk.mocks.NfcDemoTapGate

/** Routes a single scan entry to either the ordinary-NFC demo engine or the real Tangem SDK. */
@Suppress("TooManyFunctions", "LongParameterList")
internal class AutoRoutingTangemSdkManager(
    private val real: TangemSdkManager,
    private val demo: MockTangemSdkManager,
    private val hotWalletBridge: NfcDemoHotWalletBridge,
) : TangemSdkManager by real {

    override suspend fun scanProduct(
        cardId: String?,
        messageRes: Int?,
        allowsRequestAccessCodeFromRepository: Boolean,
        shouldCheckIsAlreadyActivated: Boolean,
        source: AnalyticsParam.ScreensSources,
    ): CompletionResult<ScanResponse> {
        if (cardId != null) {
            return if (isDemoCard(cardId)) {
                NfcCardOriginRegistry.current = NfcCardOriginRegistry.Origin.Demo
                demo.scanProduct(
                    cardId,
                    messageRes,
                    allowsRequestAccessCodeFromRepository,
                    shouldCheckIsAlreadyActivated,
                    source,
                )
            } else {
                NfcCardOriginRegistry.current = NfcCardOriginRegistry.Origin.Real
                real.scanProduct(
                    cardId,
                    messageRes,
                    allowsRequestAccessCodeFromRepository,
                    shouldCheckIsAlreadyActivated,
                    source,
                )
            }
        }

        return when (NfcDemoTapGate.awaitTap(operation = "identify_card_type", feedbackMillis = 100L)) {
            NfcDemoTapGate.Result.Success -> {
                NfcCardOriginRegistry.current = NfcCardOriginRegistry.Origin.Demo
                demo.scanProductAfterPhysicalGate()
            }
            NfcDemoTapGate.Result.RealCard -> {
                NfcCardOriginRegistry.current = NfcCardOriginRegistry.Origin.Real
                real.scanProduct(
                    null,
                    messageRes,
                    allowsRequestAccessCodeFromRepository,
                    shouldCheckIsAlreadyActivated,
                    source,
                )
            }
            NfcDemoTapGate.Result.Cancelled -> CompletionResult.Failure(TangemSdkError.UserCancelled())
            NfcDemoTapGate.Result.TagLost,
            NfcDemoTapGate.Result.Timeout,
            -> CompletionResult.Failure(TangemSdkError.TagLost())
        }
    }

    override suspend fun createProductWallet(scanResponse: ScanResponse, shouldReset: Boolean) =
        manager(scanResponse.card.cardId).createProductWallet(scanResponse, shouldReset)

    override suspend fun importWallet(
        scanResponse: ScanResponse,
        mnemonic: String,
        passphrase: String?,
        shouldReset: Boolean,
    ) = manager(scanResponse.card.cardId).importWallet(scanResponse, mnemonic, passphrase, shouldReset)

    override suspend fun derivePublicKeys(
        cardId: String?,
        derivations: Map<ByteArrayKey, List<DerivationPath>>,
        preflightReadFilter: PreflightReadFilter?,
    ): CompletionResult<DerivationTaskResponse> = manager(
        cardId,
    ).derivePublicKeys(cardId, derivations, preflightReadFilter)

    override suspend fun deriveExtendedPublicKey(
        cardId: String?,
        walletPublicKey: ByteArray,
        derivation: DerivationPath,
    ): CompletionResult<ExtendedPublicKey> =
        manager(cardId).deriveExtendedPublicKey(cardId, walletPublicKey, derivation)

    override suspend fun resetToFactorySettings(cardId: String, allowsRequestAccessCodeFromRepository: Boolean) =
        manager(cardId).resetToFactorySettings(cardId, allowsRequestAccessCodeFromRepository)

    override suspend fun resetBackupCard(cardNumber: Int, userWalletId: UserWalletId) =
        manager(userWalletId).resetBackupCard(cardNumber, userWalletId)

    override suspend fun saveAccessCode(accessCode: String, cardsIds: Set<String>) =
        manager(cardsIds.firstOrNull()).saveAccessCode(accessCode, cardsIds)

    override suspend fun deleteSavedUserCodes(cardsIds: Set<String>) =
        manager(cardsIds.firstOrNull()).deleteSavedUserCodes(cardsIds)

    override suspend fun clearSavedUserCodes() = currentManager().clearSavedUserCodes()
    override suspend fun setPasscode(cardId: String?) = manager(cardId).setPasscode(cardId)
    override suspend fun setAccessCode(cardId: String?) = manager(cardId).setAccessCode(cardId)
    override suspend fun setLongTap(cardId: String?) = manager(cardId).setLongTap(cardId)
    override suspend fun setAccessCodeRecoveryEnabled(cardId: String?, enabled: Boolean) =
        manager(cardId).setAccessCodeRecoveryEnabled(cardId, enabled)

    override suspend fun scanCard(
        cardId: String?,
        allowRequestAccessCodeFromRepository: Boolean,
    ): CompletionResult<CardDTO> = manager(cardId).scanCard(cardId, allowRequestAccessCodeFromRepository)

    override suspend fun <T> runTaskAsync(
        runnable: CardSessionRunnable<T>,
        preflightReadFilter: PreflightReadFilter?,
        cardId: String?,
        initialMessage: Message?,
        accessCode: String?,
        @DrawableRes iconScanRes: Int?,
    ): CompletionResult<T> = if (manager(cardId) === demo) {
        CompletionResult.Failure(TangemSdkError.UserCancelled())
    } else {
        real.runTaskAsync(runnable, preflightReadFilter, cardId, initialMessage, accessCode, iconScanRes)
    }

    override suspend fun createFirstTwinWallet(
        cardId: String,
        initialMessage: Message,
    ): CompletionResult<CreateWalletResponse> = manager(cardId).createFirstTwinWallet(cardId, initialMessage)

    override suspend fun createSecondTwinWallet(
        firstPublicKey: String,
        firstCardId: String,
        issuerKeys: KeyPair,
        preparingMessage: Message,
        creatingWalletMessage: Message,
        initialMessage: Message,
    ): CompletionResult<CreateWalletResponse> = manager(firstCardId).createSecondTwinWallet(
        firstPublicKey,
        firstCardId,
        issuerKeys,
        preparingMessage,
        creatingWalletMessage,
        initialMessage,
    )

    override suspend fun finalizeTwin(
        secondCardPublicKey: ByteArray,
        issuerKeyPair: KeyPair,
        cardId: String,
        initialMessage: Message,
    ): CompletionResult<ScanResponse> = manager(
        cardId,
    ).finalizeTwin(secondCardPublicKey, issuerKeyPair, cardId, initialMessage)

    override fun changeProductType(isRing: Boolean) = currentManager().changeProductType(isRing)
    override fun clearProductType() = currentManager().clearProductType()

    override suspend fun activateVisaCard(
        mode: VisaCardActivationTaskMode,
        activationInput: VisaActivationInput,
    ): CompletionResult<VisaCardActivationResponse> = currentManager().activateVisaCard(mode, activationInput)

    override suspend fun visaCustomerWalletApprove(
        visaDataForApprove: VisaDataForApprove,
    ): CompletionResult<VisaSignedDataByCustomerWallet> = currentManager().visaCustomerWalletApprove(visaDataForApprove)

    override suspend fun tangemPayProduceInitialCredentials(
        preflightReadFilter: PreflightReadFilter,
    ): Either<Throwable, TangemPayInitialCredentials> =
        currentManager().tangemPayProduceInitialCredentials(preflightReadFilter)

    override suspend fun getWithdrawalSignature(
        hash: String,
        preflightReadFilter: PreflightReadFilter,
    ): Either<Throwable, WithdrawalSignatureResult> = currentManager().getWithdrawalSignature(hash, preflightReadFilter)

    private fun manager(cardId: String?): TangemSdkManager = when {
        cardId != null && isDemoCard(cardId) -> demo
        cardId != null -> real
        else -> currentManager()
    }

    private fun manager(userWalletId: UserWalletId): TangemSdkManager =
        if (hotWalletBridge.isDemoWallet(userWalletId)) demo else real

    private fun currentManager(): TangemSdkManager =
        if (NfcCardOriginRegistry.current == NfcCardOriginRegistry.Origin.Demo) demo else real

    private fun isDemoCard(cardId: String): Boolean = DemoConfig.isNfcDemoCardId(cardId)
}
