package com.tangem.features.onboarding.v2.visa.impl.child.inprogress.model

import arrow.core.right
import com.google.common.truth.Truth.assertThat
import com.tangem.core.analytics.api.AnalyticsEventHandler
import com.tangem.core.decompose.model.ParamsContainer
import com.tangem.core.decompose.ui.UiMessageSender
import com.tangem.datasource.local.visa.VisaAuthTokenStorage
import com.tangem.datasource.local.visa.VisaOTPStorage
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.visa.datasource.VisaAuthRemoteDataSource
import com.tangem.domain.visa.model.VisaActivationRemoteState
import com.tangem.domain.visa.repository.VisaActivationRepository
import com.tangem.domain.visa.repository.VisaActivationStatusRepository
import com.tangem.domain.wallets.builder.ColdUserWalletBuilder
import com.tangem.domain.wallets.usecase.SaveWalletUseCase
import com.tangem.features.onboarding.v2.visa.impl.child.inprogress.OnboardingVisaInProgressComponent
import com.tangem.features.onboarding.v2.visa.impl.child.inprogress.OnboardingVisaInProgressComponent.Params
import com.tangem.features.onboarding.v2.visa.impl.route.OnboardingVisaRoute
import com.tangem.utils.coroutines.TestingCoroutineDispatcherProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class OnboardingVisaInProgressModelTest {

    @Test
    fun `GIVEN immediate awaiting pin response WHEN collector subscribes later THEN navigation event is delivered`() =
        runTest {
            val activationRepository = mockk<VisaActivationRepository>()
            coEvery { activationRepository.getActivationRemoteState() } returns
                VisaActivationRemoteState.AwaitingPinCode(
                    activationOrderInfo = mockk(),
                    status = VisaActivationRemoteState.AwaitingPinCode.Status.WaitingForPinCode,
                ).right()

            val model = createModel(
                testScope = this,
                activationRepository = activationRepository,
            )

            advanceUntilIdle()

            val event = withTimeout(1_000) { model.onDone.first() }
            val route = (event as Params.DoneEvent.NavigateTo).route

            assertThat(route).isInstanceOf(OnboardingVisaRoute.PinCode::class.java)
        }

    private fun createModel(
        testScope: TestScope,
        activationRepository: VisaActivationRepository,
    ): OnboardingVisaInProgressModel {
        val card = mockk<CardDTO> {
            every { cardId } returns "mock-card-id"
            every { cardPublicKey } returns byteArrayOf(1)
        }
        val scanResponse = mockk<ScanResponse> {
            every { this@mockk.card } returns card
        }
        val config = OnboardingVisaInProgressComponent.Config(
            scanResponse = scanResponse,
            type = OnboardingVisaInProgressComponent.Config.Type.AfterApprove,
        )
        val paramsContainer = mockk<ParamsContainer> {
            every { require<OnboardingVisaInProgressComponent.Config>() } returns config
        }
        val activationRepositoryFactory = mockk<VisaActivationRepository.Factory> {
            every { create(any()) } returns activationRepository
        }

        return OnboardingVisaInProgressModel(
            paramsContainer = paramsContainer,
            visaActivationRepositoryFactory = activationRepositoryFactory,
            dispatchers = testScope.createTestingCoroutineDispatcherProvider(),
            visaAuthRemoteDataSource = mockk<VisaAuthRemoteDataSource>(),
            visaAuthTokenStorage = mockk<VisaAuthTokenStorage>(),
            otpStorage = mockk<VisaOTPStorage>(),
            visaActivationStatusRepository = mockk<VisaActivationStatusRepository>(),
            coldUserWalletBuilderFactory = mockk<ColdUserWalletBuilder.Factory>(),
            saveWalletUseCase = mockk<SaveWalletUseCase>(),
            uiMessageSender = mockk<UiMessageSender>(relaxUnitFun = true),
            analyticsEventHandler = mockk<AnalyticsEventHandler>(relaxUnitFun = true),
        )
    }

    private fun TestScope.createTestingCoroutineDispatcherProvider(): TestingCoroutineDispatcherProvider {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return TestingCoroutineDispatcherProvider(
            main = dispatcher,
            mainImmediate = dispatcher,
            io = dispatcher,
            default = dispatcher,
            single = dispatcher,
        )
    }
}
