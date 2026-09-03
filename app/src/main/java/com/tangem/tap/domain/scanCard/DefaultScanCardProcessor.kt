package com.tangem.tap.domain.scanCard

import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError
import com.tangem.core.analytics.models.AnalyticsParam
import com.tangem.domain.card.ScanCardProcessor
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.tap.domain.sdk.mocks.content.ExternalNdefWalletMockContent
import com.tangem.tap.features.intentHandler.handlers.ExternalNdefScanController

// TODO: Remove this object after feature toggle was removed and use ScanCardUseCase instead
internal class DefaultScanCardProcessor(
    private val legacyScanProcessor: LegacyScanProcessor,
    private val useCaseScanProcessor: UseCaseScanProcessor,
    private val cardScanningFeatureToggles: CardScanningFeatureToggles,
    private val externalNdefScanController: ExternalNdefScanController,
) : ScanCardProcessor {
    private val isNewCardScanningEnabled: Boolean
        get() = cardScanningFeatureToggles.isNewCardScanningEnabled

    override suspend fun scan(
        cardId: String?,
        allowsRequestAccessCodeFromRepository: Boolean,
        analyticsSource: AnalyticsParam.ScreensSources,
        shouldCheckIsAlreadyActivated: Boolean,
    ): CompletionResult<ScanResponse> {
        if (externalNdefScanController.isEnabled) {
            return when (externalNdefScanController.awaitScan()) {
                ExternalNdefScanController.Result.Accepted -> {
                    ExternalNdefWalletMockContent.selectCard(externalNdefScanController.requireAcceptedIdentity())
                    CompletionResult.Success(ExternalNdefWalletMockContent.scanResponse)
                }
                ExternalNdefScanController.Result.Rejected -> CompletionResult.Failure(TangemSdkError.UserCancelled())
            }
        }

        return if (isNewCardScanningEnabled) {
            useCaseScanProcessor.scan(cardId, allowsRequestAccessCodeFromRepository)
        } else {
            legacyScanProcessor.scan(
                analyticsSource = analyticsSource,
                cardId = cardId,
                allowsRequestAccessCodeFromRepository = allowsRequestAccessCodeFromRepository,
                shouldCheckIsAlreadyActivated = shouldCheckIsAlreadyActivated,
            )
        }
    }

    @Suppress("LongParameterList")
    override suspend fun scan(
        analyticsSource: AnalyticsParam.ScreensSources,
        shouldCheckIsAlreadyActivated: Boolean,
        cardId: String?,
        onProgressStateChange: suspend (showProgress: Boolean) -> Unit,
        onWalletNotCreated: suspend () -> Unit,
        onCancel: suspend () -> Unit,
        onFailure: suspend (error: TangemError) -> Unit,
        onSuccess: suspend (scanResponse: ScanResponse) -> Unit,
    ) {
        if (externalNdefScanController.isEnabled) {
            try {
                onProgressStateChange(true)
                when (externalNdefScanController.awaitScan()) {
                    ExternalNdefScanController.Result.Accepted -> {
                        ExternalNdefWalletMockContent.selectCard(externalNdefScanController.requireAcceptedIdentity())
                        useCaseScanProcessor.proceedWithExternalScan(
                            scanResponse = ExternalNdefWalletMockContent.scanResponse,
                            onWalletNotCreated = onWalletNotCreated,
                            onSuccess = onSuccess,
                        )
                    }
                    ExternalNdefScanController.Result.Rejected -> onCancel()
                }
            } finally {
                onProgressStateChange(false)
            }
            return
        }

        if (isNewCardScanningEnabled) {
            useCaseScanProcessor.scan(
                analyticsSource = analyticsSource,
                cardId = cardId,
                onProgressStateChange = onProgressStateChange,
                onWalletNotCreated = onWalletNotCreated,
                onCancel = onCancel,
                onFailure = onFailure,
                onSuccess = onSuccess,
            )
        } else {
            legacyScanProcessor.scan(
                analyticsSource = analyticsSource,
                shouldCheckIsAlreadyActivated = shouldCheckIsAlreadyActivated,
                cardId = cardId,
                onProgressStateChange = onProgressStateChange,
                onWalletNotCreated = onWalletNotCreated,
                onCancel = onCancel,
                onFailure = onFailure,
                onSuccess = onSuccess,
            )
        }
    }
}
