package com.tangem.data.pay.repository

import com.tangem.data.visa.BuildConfig
import com.tangem.datasource.api.common.config.ApiConfig
import com.tangem.datasource.api.common.config.ApiEnvironment
import com.tangem.datasource.api.common.config.managers.ApiConfigsManager
import com.tangem.domain.card.common.util.cardTypesResolver
import com.tangem.domain.common.wallets.UserWalletsListRepository
import com.tangem.domain.common.wallets.getSyncOrNull
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.models.wallet.UserWalletId
import javax.inject.Inject
import javax.inject.Singleton

/** Restricts local Tangem Pay fixtures to the dedicated NFC demo wallet in Hybrid builds. */
@Singleton
internal class TangemPayFixturePolicy @Inject constructor(
    private val apiConfigsManager: ApiConfigsManager,
    private val userWalletsListRepository: UserWalletsListRepository,
) {

    fun useFixture(userWalletId: UserWalletId): Boolean {
        val wallet = userWalletsListRepository.getSyncOrNull(userWalletId)

        return shouldUseWalletFixture(
            isFullMockMode = isFullMockMode,
            isNfcDemoEnabled = BuildConfig.NFC_DEMO_ENABLED,
            cardId = (wallet as? UserWallet.Cold)?.cardId,
            isVisaWallet = wallet.isVisaWallet(),
        )
    }

    fun useFixtureForGlobalRequest(): Boolean {
        val hasNfcDemoVisaWallet = userWalletsListRepository.userWallets.value
            .orEmpty()
            .any { wallet ->
                shouldUseWalletFixture(
                    isFullMockMode = false,
                    isNfcDemoEnabled = BuildConfig.NFC_DEMO_ENABLED,
                    cardId = (wallet as? UserWallet.Cold)?.cardId,
                    isVisaWallet = wallet.isVisaWallet(),
                )
            }

        return shouldUseGlobalFixture(
            isFullMockMode = isFullMockMode,
            isNfcDemoEnabled = BuildConfig.NFC_DEMO_ENABLED,
            hasNfcDemoVisaWallet = hasNfcDemoVisaWallet,
        )
    }

    /** Hybrid exposes Tangem Pay only for its dedicated NFC demo Visa wallet. */
    fun shouldExposeTangemPay(userWalletId: UserWalletId): Boolean {
        return !shouldSuppressWallet(
            isFullMockMode = isFullMockMode,
            isNfcDemoEnabled = BuildConfig.NFC_DEMO_ENABLED,
            usesFixture = useFixture(userWalletId),
        )
    }

    fun shouldSuppressGlobalRequest(): Boolean {
        return shouldSuppressGlobal(
            isFullMockMode = isFullMockMode,
            isNfcDemoEnabled = BuildConfig.NFC_DEMO_ENABLED,
            usesFixture = useFixtureForGlobalRequest(),
        )
    }

    private val isFullMockMode: Boolean
        get() = BuildConfig.MOCK_DATA_SOURCE || apiConfigsManager
            .getEnvironmentConfig(ApiConfig.ID.TangemPay)
            .environment == ApiEnvironment.MOCK

    private fun UserWallet?.isVisaWallet(): Boolean {
        return this is UserWallet.Cold && scanResponse.cardTypesResolver.isVisaWallet()
    }

    internal companion object {
        // Keep in sync with VisaMockContent.CARD_ID in the app module.
        const val NFC_DEMO_VISA_CARD_ID = "AE05888888880018"

        fun shouldUseWalletFixture(
            isFullMockMode: Boolean,
            isNfcDemoEnabled: Boolean,
            cardId: String?,
            isVisaWallet: Boolean,
        ): Boolean {
            return isFullMockMode ||
                (isNfcDemoEnabled && isVisaWallet && cardId == NFC_DEMO_VISA_CARD_ID)
        }

        fun shouldUseGlobalFixture(
            isFullMockMode: Boolean,
            isNfcDemoEnabled: Boolean,
            hasNfcDemoVisaWallet: Boolean,
        ): Boolean {
            return isFullMockMode || (isNfcDemoEnabled && hasNfcDemoVisaWallet)
        }

        fun shouldSuppressWallet(isFullMockMode: Boolean, isNfcDemoEnabled: Boolean, usesFixture: Boolean): Boolean {
            return isNfcDemoEnabled && !isFullMockMode && !usesFixture
        }

        fun shouldSuppressGlobal(isFullMockMode: Boolean, isNfcDemoEnabled: Boolean, usesFixture: Boolean): Boolean {
            return shouldSuppressWallet(isFullMockMode, isNfcDemoEnabled, usesFixture)
        }
    }
}
