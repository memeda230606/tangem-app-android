package com.tangem.tap.di

import android.content.Context
import com.tangem.core.analytics.api.AnalyticsErrorHandler
import com.tangem.domain.card.BuildConfig
import com.tangem.domain.card.repository.CardRepository
import com.tangem.domain.card.repository.CardSdkConfigRepository
import com.tangem.domain.dynamicaddresses.DynamicAddressesFeatureToggles
import com.tangem.features.onboarding.v2.OnboardingV2FeatureToggles
import com.tangem.sdk.api.TangemSdkManager
import com.tangem.tap.domain.sdk.impl.DefaultTangemSdkManager
import com.tangem.tap.domain.sdk.impl.MockTangemSdkManager
import com.tangem.tap.domain.sdk.mocks.MockProvider
import com.tangem.tap.domain.sdk.mocks.content.ExternalNdefWalletMockContent
import com.tangem.tap.domain.tasks.product.BlockchainToDeriveFinder
import com.tangem.tap.domain.tasks.visa.TangemPayGenerateAddressAndSignChallengeTask
import com.tangem.tap.domain.tasks.visa.VisaCardActivationTask
import com.tangem.tap.domain.visa.VisaCardScanHandler
import com.tangem.tap.features.intentHandler.handlers.ExternalNdefScanController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal class TangemSdkManagerModule {

    @Provides
    @Singleton
    fun provideTangemSdkManager(
        @ApplicationContext context: Context,
        cardSdkConfigRepository: CardSdkConfigRepository,
        visaCardScanHandler: VisaCardScanHandler,
        visaCardActivationTaskFactory: VisaCardActivationTask.Factory,
        tangemPayChallengeTaskFactory: TangemPayGenerateAddressAndSignChallengeTask.Factory,
        onboardingV2FeatureToggles: OnboardingV2FeatureToggles,
        dynamicAddressesFeatureToggles: DynamicAddressesFeatureToggles,
        blockchainToDeriveFinder: BlockchainToDeriveFinder,
        analyticsErrorHandler: AnalyticsErrorHandler,
        cardRepository: CardRepository,
        externalNdefScanController: ExternalNdefScanController,
    ): TangemSdkManager {
        val isExternalBuild = com.tangem.wallet.BuildConfig.BUILD_TYPE == EXTERNAL_BUILD_TYPE

        return if (BuildConfig.MOCK_DATA_SOURCE || isExternalBuild) {
            if (isExternalBuild) {
                // An NDEF test tag has no secure element. Keep all later card commands inside the existing mock SDK.
                ExternalNdefWalletMockContent.initialize(context)
                MockProvider.setMocks(ExternalNdefWalletMockContent)
            }
            MockTangemSdkManager(
                resources = context.resources,
                returnOnlyRequestedDerivations = isExternalBuild,
                externalNdefScanController = externalNdefScanController.takeIf { isExternalBuild },
            )
        } else {
            DefaultTangemSdkManager(
                cardSdkConfigRepository = cardSdkConfigRepository,
                resources = context.resources,
                visaCardScanHandler = visaCardScanHandler,
                visaCardActivationTaskFactory = visaCardActivationTaskFactory,
                tangemPayChallengeTaskFactory = tangemPayChallengeTaskFactory,
                onboardingV2FeatureToggles = onboardingV2FeatureToggles,
                dynamicAddressesFeatureToggles = dynamicAddressesFeatureToggles,
                blockchainToDeriveFinder = blockchainToDeriveFinder,
                analyticsErrorHandler = analyticsErrorHandler,
                cardRepository = cardRepository,
            )
        }
    }

    private companion object {
        const val EXTERNAL_BUILD_TYPE = "external"
    }
}
