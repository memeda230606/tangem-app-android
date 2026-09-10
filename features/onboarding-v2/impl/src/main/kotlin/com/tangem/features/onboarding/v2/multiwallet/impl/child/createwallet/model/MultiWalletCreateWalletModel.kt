package com.tangem.features.onboarding.v2.multiwallet.impl.child.createwallet.model

import androidx.compose.runtime.Stable
import arrow.core.getOrElse
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import com.tangem.common.routing.AppRoute
import com.tangem.core.analytics.api.AnalyticsEventHandler
import com.tangem.core.analytics.models.AnalyticsParam
import com.tangem.core.analytics.models.Basic
import com.tangem.core.analytics.models.event.OnboardingAnalyticsEvent
import com.tangem.core.decompose.di.ModelScoped
import com.tangem.core.decompose.di.GlobalUiMessageSender
import com.tangem.core.decompose.model.Model
import com.tangem.core.decompose.model.ParamsContainer
import com.tangem.core.decompose.navigation.Router
import com.tangem.core.decompose.ui.UiMessageSender
import com.tangem.core.ui.message.DialogMessage
import com.tangem.core.ui.extensions.resourceReference
import com.tangem.datasource.local.appsflyer.AppsFlyerStore
import com.tangem.domain.card.repository.CardRepository
import com.tangem.domain.feedback.GetWalletMetaInfoUseCase
import com.tangem.domain.feedback.SendFeedbackEmailUseCase
import com.tangem.domain.feedback.models.FeedbackEmailType
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.wallets.builder.ColdUserWalletBuilder
import com.tangem.domain.wallets.builder.HotUserWalletBuilder
import com.tangem.domain.wallets.hot.HotWalletNfcSecurity
import com.tangem.domain.wallets.usecase.SaveWalletUseCase
import com.tangem.domain.wallets.usecase.SyncWalletWithRemoteUseCase
import com.tangem.features.onboarding.v2.impl.R
import com.tangem.features.onboarding.v2.multiwallet.impl.child.MultiWalletChildParams
import com.tangem.features.onboarding.v2.multiwallet.impl.child.createwallet.ui.state.MultiWalletCreateWalletUM
import com.tangem.features.onboarding.v2.multiwallet.impl.common.ui.resetCardDialog
import com.tangem.features.onboarding.v2.multiwallet.impl.model.OnboardingMultiWalletState.Step
import com.tangem.sdk.api.TangemSdkManager
import com.tangem.hot.sdk.TangemHotSdk
import com.tangem.hot.sdk.model.HotAuth
import com.tangem.hot.sdk.model.MnemonicType
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import com.tangem.utils.coroutines.runSuspendCatching
import com.tangem.utils.logging.TangemLogger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("LongParameterList")
@Stable
@ModelScoped
internal class MultiWalletCreateWalletModel @Inject constructor(
    paramsContainer: ParamsContainer,
    private val router: Router,
    override val dispatchers: CoroutineDispatcherProvider,
    private val tangemSdkManager: TangemSdkManager,
    private val sendFeedbackEmailUseCase: SendFeedbackEmailUseCase,
    private val getWalletMetaInfoUseCase: GetWalletMetaInfoUseCase,
    private val cardRepository: CardRepository,
    private val analyticsHandler: AnalyticsEventHandler,
    private val coldUserWalletBuilderFactory: ColdUserWalletBuilder.Factory,
    private val hotUserWalletBuilderFactory: HotUserWalletBuilder.Factory,
    private val saveWalletUseCase: SaveWalletUseCase,
    private val syncWalletWithRemoteUseCase: SyncWalletWithRemoteUseCase,
    private val tangemHotSdk: TangemHotSdk,
    private val hotWalletNfcSecurity: HotWalletNfcSecurity,
    @GlobalUiMessageSender private val uiMessageSender: UiMessageSender,
    private val appsFlyerStore: AppsFlyerStore,
) : Model() {

    private val params = paramsContainer.require<MultiWalletChildParams>()
    private val multiWalletState
        get() = params.multiWalletState

    private val _uiState = MutableStateFlow(
        MultiWalletCreateWalletUM(
            title = if (params.parentParams.withSeedPhraseFlow) {
                resourceReference(R.string.onboarding_create_wallet_options_title)
            } else {
                resourceReference(R.string.onboarding_create_wallet_header)
            },
            bodyText = if (params.parentParams.withSeedPhraseFlow) {
                resourceReference(R.string.onboarding_create_wallet_options_message)
            } else {
                resourceReference(R.string.onboarding_create_wallet_body)
            },
            onCreateWalletClick = {
                analyticsHandler.send(OnboardingAnalyticsEvent.CreateWallet.ButtonCreateWallet())
                createWallet(false)
            },
            showOtherOptionsButton = params.parentParams.withSeedPhraseFlow,
            onOtherOptionsClick = {
                analyticsHandler.send(OnboardingAnalyticsEvent.CreateWallet.ButtonOtherOptions())
                modelScope.launch {
                    onDone.emit(Step.SeedPhrase)
                }
            },
            onTermsOfUseClick = { router.push(AppRoute.Disclaimer(isTosAccepted = true)) },
            dialog = null,
        ),
    )

    val uiState: StateFlow<MultiWalletCreateWalletUM> = _uiState
    val onDone = MutableSharedFlow<Step>()
    private var createClicked = false

    init {
        analyticsHandler.send(OnboardingAnalyticsEvent.CreateWallet.ScreenOpened())
    }

    private fun createWallet(shouldReset: Boolean) {
        if (createClicked) return
        createClicked = true

        modelScope.launch {
            if (hotWalletNfcSecurity.supportsCard(multiWalletState.value.currentScanResponse)) {
                createExternalWallet()
                return@launch
            }

            val result = tangemSdkManager.createProductWallet(
                scanResponse = multiWalletState.value.currentScanResponse,
                shouldReset = shouldReset,
            )

            when (result) {
                is CompletionResult.Success -> {
                    multiWalletState.update {
                        it.copy(
                            currentScanResponse = it.currentScanResponse.copy(
                                card = result.data.card,
                                derivedKeys = result.data.derivedKeys,
                                primaryCard = result.data.primaryCard,
                            ),
                        )
                    }

                    cardRepository.startCardActivation(cardId = result.data.card.cardId)

                    analyticsHandler.send(
                        event = OnboardingAnalyticsEvent.CreateWallet.WalletCreatedSuccessfully(
                            passPhraseState = AnalyticsParam.EmptyFull.Empty,
                            referralId = appsFlyerStore.get()?.refcode,
                        ),
                    )

                    val cardDoesNotSupportBackup = result.data.card.settings.isBackupAllowed.not()
                    when {
                        cardDoesNotSupportBackup -> createWalletAndNavigateBackWithDone()
                        params.parentParams.withSeedPhraseFlow -> onDone.emit(Step.AddBackupDevice)
                        else -> onDone.emit(Step.ChooseBackupOption)
                    }
                }

                is CompletionResult.Failure -> {
                    createClicked = false
                    if (result.error is TangemSdkError.WalletAlreadyCreated) {
                        // show should reset dialog
                        handleActivationError()
                    }
                }
            }
        }
    }

    /** Uses the real mobile signer while keeping the official Tangem card-onboarding presentation. */
    private suspend fun createExternalWallet() {
        var generatedWalletId: com.tangem.hot.sdk.model.HotWalletId? = null

        runSuspendCatching {
            val hotWalletId = tangemHotSdk.generateWallet(HotAuth.NoAuth, mnemonicType = MnemonicType.Words12)
            generatedWalletId = hotWalletId
            val userWallet = hotUserWalletBuilderFactory.create(hotWalletId).build().copy(isTestnetOnly = true)

            hotWalletNfcSecurity.bindWallet(userWallet)
            saveWalletUseCase(
                userWallet = userWallet,
                canOverride = true,
                analyticsSource = AnalyticsParam.ScreensSources.Onboarding,
            ).getOrElse { error("Unable to save external TESTNET wallet: $it") }

            cardRepository.startCardActivation(multiWalletState.value.currentScanResponse.card.cardId)
            multiWalletState.update { it.copy(externalUserWallet = userWallet) }

            analyticsHandler.send(
                event = OnboardingAnalyticsEvent.CreateWallet.WalletCreatedSuccessfully(
                    passPhraseState = AnalyticsParam.EmptyFull.Empty,
                    referralId = appsFlyerStore.get()?.refcode,
                ),
            )

            modelScope.launch(dispatchers.main + NonCancellable) {
                syncWalletWithRemoteUseCase(userWalletId = userWallet.walletId)
            }

            onDone.emit(Step.ChooseBackupOption)
        }.onFailure { throwable ->
            generatedWalletId?.let { walletId -> runCatching { tangemHotSdk.delete(walletId) } }
            createClicked = false
            TangemLogger.e("Unable to create external TESTNET wallet", throwable)
            uiMessageSender.send(
                DialogMessage(
                    message = resourceReference(com.tangem.core.ui.R.string.nfc_wallet_creation_failed_message),
                    title = resourceReference(com.tangem.core.ui.R.string.nfc_wallet_creation_failed_title),
                ),
            )
        }
    }

    private fun createWalletAndNavigateBackWithDone() {
        modelScope.launch {
            val scanResponse = params.multiWalletState.value.currentScanResponse

            val userWallet = createUserWallet(scanResponse)
            saveWalletUseCase(
                userWallet = userWallet,
                canOverride = true,
                analyticsSource = AnalyticsParam.ScreensSources.Onboarding,
            ).onRight {
                cardRepository.finishCardActivation(scanResponse.card.cardId)

                // save user wallet for manage tokens screen
                params.multiWalletState.update {
                    it.copy(resultUserWallet = userWallet)
                }

                onDone.emit(Step.Done)
            }
        }
    }

    private suspend fun createUserWallet(scanResponse: ScanResponse): UserWallet.Cold {
        return requireNotNull(
            value = coldUserWalletBuilderFactory.create(scanResponse = scanResponse).build(),
            lazyMessage = { "User wallet not created" },
        )
    }

    private fun handleActivationError() {
        _uiState.update { state ->
            state.copy(
                dialog = resetCardDialog(
                    onConfirm = ::navigateToSupportScreen,
                    dismiss = { _uiState.update { it.copy(dialog = null) } },
                    onDismissButtonClick = ::resetCard,
                ),
            )
        }
    }

    private fun resetCard() {
        createWallet(true)
    }

    fun navigateToSupportScreen() {
        modelScope.launch {
            val cardInfo =
                getWalletMetaInfoUseCase(multiWalletState.value.currentScanResponse).getOrNull() ?: return@launch
            analyticsHandler.send(Basic.ButtonSupport(source = AnalyticsParam.ScreensSources.Onboarding))
            sendFeedbackEmailUseCase(FeedbackEmailType.DirectUserRequest(cardInfo))
        }
    }
}
