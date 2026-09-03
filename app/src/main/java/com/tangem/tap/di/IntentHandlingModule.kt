package com.tangem.tap.di

import com.tangem.tap.features.intentHandler.handlers.BackgroundScanIntentHandler
import com.tangem.tap.features.intentHandler.handlers.ExternalNdefScanController
import com.tangem.wallet.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object IntentHandlingModule {

    @Provides
    @Singleton
    fun provideExternalNdefScanController(): ExternalNdefScanController {
        return ExternalNdefScanController(isEnabled = BuildConfig.BUILD_TYPE == EXTERNAL_BUILD_TYPE)
    }

    @Provides
    @Singleton
    fun provideBackgroundScanIntentHandler(
        externalNdefScanController: ExternalNdefScanController,
    ): BackgroundScanIntentHandler {
        return BackgroundScanIntentHandler(
            isNdefOnlyMode = externalNdefScanController.isEnabled,
            externalNdefScanController = externalNdefScanController,
        )
    }

    private const val EXTERNAL_BUILD_TYPE = "external"
}
