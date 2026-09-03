package com.tangem.tap.domain.scanCard

import arrow.core.left
import com.tangem.common.core.TangemError
import com.tangem.common.routing.AppRouter
import com.tangem.core.analytics.models.AnalyticsParam
import com.tangem.core.analytics.utils.TrackingContextProxy
import com.tangem.domain.card.ScanCardException
import com.tangem.domain.card.ScanCardUseCase
import com.tangem.domain.card.ScanFailsRequester
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.onboarding.WasTwinsOnboardingShownUseCase
import com.tangem.tap.features.onboarding.OnboardingHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class UseCaseScanProcessorTest {

    private val scanCardUseCase = mockk<ScanCardUseCase>()
    private val appRouter = mockk<AppRouter>(relaxed = true)
    private val trackingContextProxy = mockk<TrackingContextProxy>(relaxed = true)
    private val onboardingHelper = mockk<OnboardingHelper>(relaxed = true)
    private val processor = UseCaseScanProcessor(
        scanCardUseCase = scanCardUseCase,
        scanFailsRequester = mockk(relaxed = true),
        appRouter = appRouter,
        trackingContextProxy = trackingContextProxy,
        wasTwinsOnboardingShownUseCase = mockk<WasTwinsOnboardingShownUseCase>(relaxed = true),
        onboardingHelper = onboardingHelper,
    )

    @Test
    fun `user cancellation invokes cancel callback without reporting failure`() = runTest {
        coEvery { scanCardUseCase.invoke(any(), any(), any()) } returns ScanCardException.UserCancelled().left()
        val progressChanges = mutableListOf<Boolean>()
        var isCancelled = false
        var failure: TangemError? = null

        processor.scan(
            analyticsSource = AnalyticsParam.ScreensSources.Main,
            cardId = null,
            onProgressStateChange = { progressChanges += it },
            onWalletNotCreated = {},
            onCancel = { isCancelled = true },
            onFailure = { failure = it },
            onSuccess = {},
        )

        assertTrue(isCancelled)
        assertNull(failure)
        assertEquals(listOf(true, false), progressChanges)
    }

    @Test
    fun `accepted external empty card opens onboarding`() = runTest {
        val scanResponse = mockk<ScanResponse>()
        coEvery { onboardingHelper.isOnboardingCase(scanResponse) } returns true
        var walletNotCreated = false
        var successCalled = false

        processor.proceedWithExternalScan(
            scanResponse = scanResponse,
            onWalletNotCreated = { walletNotCreated = true },
            onSuccess = { successCalled = true },
        )

        assertTrue(walletNotCreated)
        assertFalse(successCalled)
        coVerify { trackingContextProxy.addContext(scanResponse) }
        coVerify {
            appRouter.push(
                match { it is com.tangem.common.routing.AppRoute.Onboarding },
                any(),
            )
        }
    }

    @Test
    fun `accepted external initialized card continues as successful scan`() = runTest {
        val scanResponse = mockk<ScanResponse>()
        coEvery { onboardingHelper.isOnboardingCase(scanResponse) } returns false
        var walletNotCreated = false
        var successfulResponse: ScanResponse? = null

        processor.proceedWithExternalScan(
            scanResponse = scanResponse,
            onWalletNotCreated = { walletNotCreated = true },
            onSuccess = { successfulResponse = it },
        )

        assertFalse(walletNotCreated)
        assertEquals(scanResponse, successfulResponse)
        coVerify { trackingContextProxy.setContext(scanResponse) }
    }
}
