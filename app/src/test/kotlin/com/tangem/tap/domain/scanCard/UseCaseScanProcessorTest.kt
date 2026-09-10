package com.tangem.tap.domain.scanCard

import arrow.core.left
import arrow.core.right
import com.tangem.common.routing.AppRoute
import com.tangem.common.core.TangemError
import com.tangem.common.routing.AppRouter
import com.tangem.core.analytics.models.AnalyticsParam
import com.tangem.core.analytics.utils.TrackingContextProxy
import com.tangem.domain.card.ScanCardException
import com.tangem.domain.card.ScanCardUseCase
import com.tangem.domain.card.ScanFailsRequester
import com.tangem.domain.common.wallets.UserWalletsListRepository
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.onboarding.WasTwinsOnboardingShownUseCase
import com.tangem.domain.wallets.hot.HotWalletNfcSecurity
import com.tangem.tap.domain.TapSdkError
import com.tangem.tap.features.intentHandler.handlers.ExternalNdefScanController
import com.tangem.tap.features.onboarding.OnboardingHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private val externalNdefScanController = mockk<ExternalNdefScanController>()
    private val hotWalletNfcSecurity = mockk<HotWalletNfcSecurity>()
    private val userWalletsListRepository = mockk<UserWalletsListRepository>()
    private val processor = UseCaseScanProcessor(
        scanCardUseCase = scanCardUseCase,
        scanFailsRequester = mockk(relaxed = true),
        appRouter = appRouter,
        trackingContextProxy = trackingContextProxy,
        wasTwinsOnboardingShownUseCase = mockk<WasTwinsOnboardingShownUseCase>(relaxed = true),
        onboardingHelper = onboardingHelper,
        externalNdefScanController = externalNdefScanController,
        hotWalletNfcSecurity = hotWalletNfcSecurity,
        userWalletsListRepository = userWalletsListRepository,
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
    fun `accepted external unbound card opens official card onboarding`() = runTest {
        val scanResponse = mockk<ScanResponse>()
        every { externalNdefScanController.requireAcceptedIdentity() } returns cardIdentity(boundWalletId = null)
        var walletNotCreated = false
        var successCalled = false

        processor.proceedWithExternalScan(
            scanResponse = scanResponse,
            onWalletNotCreated = { walletNotCreated = true },
            onFailure = {},
            onSuccess = { successCalled = true },
        )

        assertTrue(walletNotCreated)
        assertFalse(successCalled)
        coVerify { trackingContextProxy.addContext(scanResponse) }
        verify {
            appRouter.push(
                match { it is AppRoute.Onboarding },
                any(),
            )
        }
    }

    @Test
    fun `accepted external bound card selects matching local mobile wallet`() = runTest {
        val scanResponse = mockk<ScanResponse>()
        val hotWallet = mockk<UserWallet.Hot>(relaxed = true)
        every { externalNdefScanController.requireAcceptedIdentity() } returns cardIdentity(BOUND_WALLET_ID)
        coEvery { userWalletsListRepository.userWalletsSync() } returns listOf(hotWallet)
        every { hotWalletNfcSecurity.matchesBinding(hotWallet, BOUND_WALLET_ID) } returns true
        coEvery { userWalletsListRepository.select(any()) } returns hotWallet.right()
        var walletNotCreated = false
        var successCalled = false

        processor.proceedWithExternalScan(
            scanResponse = scanResponse,
            onWalletNotCreated = { walletNotCreated = true },
            onFailure = {},
            onSuccess = { successCalled = true },
        )

        assertFalse(walletNotCreated)
        assertFalse(successCalled)
        coVerify { userWalletsListRepository.select(hotWallet.walletId) }
        verify { appRouter.replaceAll(AppRoute.Wallet, onComplete = any()) }
    }

    @Test
    fun `accepted external bound card without local wallet opens official create import choice`() = runTest {
        val scanResponse = mockk<ScanResponse>()
        every { externalNdefScanController.requireAcceptedIdentity() } returns cardIdentity(BOUND_WALLET_ID)
        coEvery { userWalletsListRepository.userWalletsSync() } returns emptyList()
        var walletNotCreated = false
        var successCalled = false

        processor.proceedWithExternalScan(
            scanResponse = scanResponse,
            onWalletNotCreated = { walletNotCreated = true },
            onFailure = {},
            onSuccess = { successCalled = true },
        )

        assertTrue(walletNotCreated)
        assertFalse(successCalled)
        verify {
            appRouter.push(
                AppRoute.CreateMobileWallet(
                    source = AnalyticsParam.ScreensSources.Onboarding,
                    isNfcRecovery = true,
                ),
                any(),
            )
        }
    }

    @Test
    fun `external card bound to another wallet is rejected when a local mobile wallet exists`() = runTest {
        val scanResponse = mockk<ScanResponse>()
        val hotWallet = mockk<UserWallet.Hot>(relaxed = true)
        every { externalNdefScanController.requireAcceptedIdentity() } returns cardIdentity(BOUND_WALLET_ID)
        coEvery { userWalletsListRepository.userWalletsSync() } returns listOf(hotWallet)
        every { hotWalletNfcSecurity.matchesBinding(hotWallet, BOUND_WALLET_ID) } returns false
        var failure: TangemError? = null
        var walletNotCreated = false
        var successCalled = false

        assertTrue(processor.isExternalCardBoundToDifferentLocalWallet())

        processor.proceedWithExternalScan(
            scanResponse = scanResponse,
            onWalletNotCreated = { walletNotCreated = true },
            onFailure = { failure = it },
            onSuccess = { successCalled = true },
        )

        assertTrue(failure is TapSdkError.ExternalCardBoundToAnotherWallet)
        assertFalse(walletNotCreated)
        assertFalse(successCalled)
        verify(exactly = 0) { appRouter.push(any(), any()) }
    }

    @Test
    fun `external card matching a local mobile wallet is accepted`() = runTest {
        val hotWallet = mockk<UserWallet.Hot>(relaxed = true)
        every { externalNdefScanController.requireAcceptedIdentity() } returns cardIdentity(BOUND_WALLET_ID)
        coEvery { userWalletsListRepository.userWalletsSync() } returns listOf(hotWallet)
        every { hotWalletNfcSecurity.matchesBinding(hotWallet, BOUND_WALLET_ID) } returns true

        assertFalse(processor.isExternalCardBoundToDifferentLocalWallet())
    }

    @Test
    fun `external bound card remains recoverable when device has no local mobile wallet`() = runTest {
        every { externalNdefScanController.requireAcceptedIdentity() } returns cardIdentity(BOUND_WALLET_ID)
        coEvery { userWalletsListRepository.userWalletsSync() } returns emptyList()

        assertFalse(processor.isExternalCardBoundToDifferentLocalWallet())
    }

    @Test
    fun `external unbound card is not treated as a wallet mismatch`() = runTest {
        every { externalNdefScanController.requireAcceptedIdentity() } returns cardIdentity(boundWalletId = null)

        assertFalse(processor.isExternalCardBoundToDifferentLocalWallet())
        coVerify(exactly = 0) { userWalletsListRepository.userWalletsSync() }
    }

    private fun cardIdentity(boundWalletId: String?) = ExternalNdefScanController.CardIdentity(
        cardInstanceId = "00000000-0000-0000-0000-000000000001",
        keyVersion = 1,
        targetUrl = "https://hm.niubtmd.com/tangem/testnet",
        verificationToken = "token",
        boundWalletId = boundWalletId,
    )

    private companion object {
        const val BOUND_WALLET_ID = "c66d8c68-6982-367d-94c6-b1ab705f19f2"
    }
}
