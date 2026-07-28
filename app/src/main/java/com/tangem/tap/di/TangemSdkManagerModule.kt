package com.tangem.tap.di

import android.content.Context
import com.tangem.core.analytics.api.AnalyticsErrorHandler
import com.tangem.domain.card.BuildConfig
import com.tangem.domain.card.repository.CardRepository
import com.tangem.domain.card.repository.CardSdkConfigRepository
import com.tangem.domain.dynamicaddresses.DynamicAddressesFeatureToggles
import com.tangem.domain.visa.repository.VisaActivationStatusRepository
import com.tangem.features.onboarding.v2.OnboardingV2FeatureToggles
import com.tangem.sdk.api.TangemSdkManager
import com.tangem.tap.domain.sdk.impl.DefaultTangemSdkManager
import com.tangem.tap.domain.sdk.impl.MockTangemSdkManager
import com.tangem.tap.domain.sdk.impl.AutoRoutingTangemSdkManager
import com.tangem.tap.domain.sdk.mocks.NfcDemoHotWalletBridge
import com.tangem.tap.domain.tasks.product.BlockchainToDeriveFinder
import com.tangem.tap.domain.tasks.visa.TangemPayGenerateAddressAndSignChallengeTask
import com.tangem.tap.domain.tasks.visa.VisaCardActivationTask
import com.tangem.tap.domain.visa.VisaCardScanHandler
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
        visaActivationStatusRepository: VisaActivationStatusRepository,
        nfcDemoHotWalletBridge: NfcDemoHotWalletBridge,
    ): TangemSdkManager {
        val realManager = DefaultTangemSdkManager(
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
            visaActivationStatusRepository = visaActivationStatusRepository,
        )
        return if (BuildConfig.NFC_DEMO_ENABLED) {
            AutoRoutingTangemSdkManager(
                real = realManager,
                demo = MockTangemSdkManager(
                    resources = context.resources,
                    hotWalletBridge = nfcDemoHotWalletBridge,
                ),
                hotWalletBridge = nfcDemoHotWalletBridge,
            )
        } else {
            realManager
        }
    }
}
