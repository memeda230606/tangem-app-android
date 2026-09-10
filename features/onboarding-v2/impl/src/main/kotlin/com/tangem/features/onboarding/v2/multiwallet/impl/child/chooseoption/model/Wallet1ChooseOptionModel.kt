package com.tangem.features.onboarding.v2.multiwallet.impl.child.chooseoption.model

import androidx.compose.runtime.Stable
import arrow.core.getOrElse
import com.tangem.common.routing.AppRoute
import com.tangem.core.analytics.api.AnalyticsEventHandler
import com.tangem.core.analytics.models.AnalyticsParam
import com.tangem.core.decompose.di.GlobalUiMessageSender
import com.tangem.core.decompose.di.ModelScoped
import com.tangem.core.decompose.model.Model
import com.tangem.core.decompose.model.ParamsContainer
import com.tangem.core.decompose.navigation.Router
import com.tangem.core.decompose.ui.UiMessageSender
import com.tangem.core.ui.R as CoreUiR
import com.tangem.core.ui.extensions.resourceReference
import com.tangem.core.ui.extensions.toWrappedList
import com.tangem.core.ui.message.DialogMessage
import com.tangem.core.ui.message.EventMessageAction
import com.tangem.domain.card.common.TapWorkarounds.canSkipBackup
import com.tangem.domain.card.repository.CardRepository
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.wallets.builder.ColdUserWalletBuilder
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.wallets.hot.HotWalletNfcRecoveryException
import com.tangem.domain.wallets.hot.HotWalletNfcSecurity
import com.tangem.domain.wallets.usecase.SaveWalletUseCase
import com.tangem.domain.wallets.usecase.UpdateWalletUseCase
import com.tangem.features.onboarding.v2.common.analytics.OnboardingEvent
import com.tangem.features.onboarding.v2.multiwallet.impl.child.MultiWalletChildParams
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import com.tangem.utils.logging.TangemLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
@ModelScoped
internal class Wallet1ChooseOptionModel @Inject constructor(
    override val dispatchers: CoroutineDispatcherProvider,
    paramsContainer: ParamsContainer,
    private val coldUserWalletBuilderFactory: ColdUserWalletBuilder.Factory,
    private val cardRepository: CardRepository,
    private val saveWalletUseCase: SaveWalletUseCase,
    private val updateWalletUseCase: UpdateWalletUseCase,
    private val hotWalletNfcSecurity: HotWalletNfcSecurity,
    private val router: Router,
    @GlobalUiMessageSender private val uiMessageSender: UiMessageSender,
    private val analyticsHandler: AnalyticsEventHandler,
) : Model() {

    private val params = paramsContainer.require<MultiWalletChildParams>()
    private var skipClicked = false
    private var backupClicked = false
    val returnToParentFlow = MutableSharedFlow<Unit>()

    init {
        analyticsHandler.send(OnboardingEvent.Backup.ScreenOpened())
    }

    val isExternalFlow: Boolean
        get() = params.multiWalletState.value.externalUserWallet != null

    val canSkipBackup = isExternalFlow || params.multiWalletState.value.currentScanResponse.card.canSkipBackup

    fun onBackupClick(onStandardBackup: () -> Unit) {
        val userWallet = params.multiWalletState.value.externalUserWallet
        if (userWallet == null) {
            onStandardBackup()
            return
        }
        if (backupClicked) return
        backupClicked = true

        modelScope.launch {
            runCatching {
                hotWalletNfcSecurity.createRecoveryPackage(userWallet)
            }.onSuccess { recoveryCode ->
                showRecoveryCode(userWallet, recoveryCode)
            }.onFailure { throwable ->
                backupClicked = false
                TangemLogger.e("Unable to create external-card recovery package", throwable)
                val supportCode = (throwable as? HotWalletNfcRecoveryException)?.supportCode ?: "RCV-200"
                uiMessageSender.send(
                    DialogMessage(
                        title = resourceReference(CoreUiR.string.nfc_wallet_recovery_setup_failed_title),
                        message = resourceReference(
                            CoreUiR.string.nfc_wallet_recovery_setup_failed_message,
                            listOf(supportCode).toWrappedList(),
                        ),
                    ),
                )
            }
        }
    }

    fun onSkipClick() {
        if (skipClicked) return
        skipClicked = true

        analyticsHandler.send(OnboardingEvent.Backup.Skipped())

        modelScope.launch {
            val externalUserWallet = params.multiWalletState.value.externalUserWallet
            if (externalUserWallet != null) {
                finishExternalOnboarding(externalUserWallet)
                return@launch
            }

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

                returnToParentFlow.emit(Unit)
            }
        }
    }

    private fun showRecoveryCode(userWallet: UserWallet.Hot, recoveryCode: String) {
        uiMessageSender.send(
            DialogMessage(
                title = resourceReference(CoreUiR.string.nfc_wallet_save_recovery_code_title),
                message = resourceReference(
                    CoreUiR.string.nfc_wallet_save_recovery_code_message,
                    listOf(recoveryCode).toWrappedList(),
                ),
                firstAction = EventMessageAction(
                    title = resourceReference(CoreUiR.string.common_done),
                    onClick = {
                        modelScope.launch {
                            updateWalletUseCase(userWallet.walletId) { current ->
                                require(current is UserWallet.Hot)
                                current.copy(backedUp = true)
                            }.getOrElse { error("Unable to mark external recovery as complete: $it") }
                            finishExternalOnboarding(userWallet)
                        }
                    },
                ),
                isDismissable = false,
                shouldDismissOnFirstAction = true,
            ),
        )
    }

    private suspend fun finishExternalOnboarding(userWallet: UserWallet.Hot) {
        cardRepository.finishCardActivation(params.multiWalletState.value.currentScanResponse.card.cardId)
        router.replaceAll(
            AppRoute.Wallet,
            AppRoute.WalletActivation(userWalletId = userWallet.walletId, isBackupExists = true),
        )
    }

    private fun createUserWallet(scanResponse: ScanResponse): UserWallet.Cold {
        return requireNotNull(
            value = coldUserWalletBuilderFactory.create(scanResponse = scanResponse).build(),
            lazyMessage = { "User wallet not created" },
        )
    }
}
