package com.tangem.data.visa

import arrow.core.Either
import arrow.core.right
import com.tangem.datasource.local.visa.VisaAuthTokenStorage
import com.tangem.domain.visa.error.VisaApiError
import com.tangem.domain.visa.model.VisaActivationRemoteState
import com.tangem.domain.visa.model.VisaActivationInput
import com.tangem.domain.visa.model.VisaCardId
import com.tangem.domain.visa.model.VisaCardActivationStatus
import com.tangem.domain.visa.model.VisaCardWalletDataToSignRequest
import com.tangem.domain.visa.model.VisaCustomerWalletDataToSignRequest
import com.tangem.domain.visa.model.VisaDataToSignByCardWallet
import com.tangem.domain.visa.model.VisaDataToSignByCustomerWallet
import com.tangem.domain.visa.model.VisaEncryptedPinCode
import com.tangem.domain.visa.model.VisaSignedActivationDataByCardWallet
import com.tangem.domain.visa.model.VisaSignedDataByCustomerWallet
import com.tangem.domain.visa.repository.VisaActivationRepository
import com.tangem.domain.visa.repository.VisaActivationStatusRepository
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory activation responses for the mocked build; activation state is never sent to a provider. */
@Singleton
internal class MockVisaActivationRepositoryFactory @Inject constructor(
    private val activationStatusRepository: VisaActivationStatusRepository,
    private val visaAuthTokenStorage: VisaAuthTokenStorage,
) : VisaActivationRepository.Factory {

    override fun create(cardId: VisaCardId): VisaActivationRepository {
        MockServerLogger.event("activation_repository_created")
        return MockVisaActivationRepository(cardId, activationStatusRepository, visaAuthTokenStorage)
    }
}

private class MockVisaActivationRepository(
    private val visaCardId: VisaCardId,
    private val activationStatusRepository: VisaActivationStatusRepository,
    private val visaAuthTokenStorage: VisaAuthTokenStorage,
) : VisaActivationRepository {

    override suspend fun getActivationRemoteState(): Either<VisaApiError, VisaActivationRemoteState> {
        return MockServerLogger.respond("visa.activation.state") {
            when (val status = activationStatusRepository.get(visaCardId.cardId)) {
                is VisaCardActivationStatus.ActivationStarted -> status.remoteState.right()
                is VisaCardActivationStatus.Activated -> VisaActivationRemoteState.Activated.right()
                else -> VisaActivationRemoteState.CardWalletSignatureRequired(MockVisaFixtures.activationOrderInfo).right()
            }
        }
    }

    override suspend fun getCardWalletAcceptanceData(
        request: VisaCardWalletDataToSignRequest,
    ): Either<VisaApiError, VisaDataToSignByCardWallet> {
        return MockServerLogger.respond("visa.activation.card_acceptance_data") {
            VisaDataToSignByCardWallet(request, MockVisaFixtures.HASH_TO_SIGN).right()
        }
    }

    override suspend fun getCustomerWalletAcceptanceData(
        request: VisaCustomerWalletDataToSignRequest,
    ): Either<VisaApiError, VisaDataToSignByCustomerWallet> {
        return MockServerLogger.respond("visa.activation.customer_acceptance_data") {
            VisaDataToSignByCustomerWallet(
                hashToSign = MockVisaFixtures.HASH_TO_SIGN,
                request = request,
            ).right()
        }
    }

    override suspend fun activateCard(signedData: VisaSignedActivationDataByCardWallet): Either<VisaApiError, Unit> {
        return MockServerLogger.respondSuspend("visa.activation.activate_card") {
            visaAuthTokenStorage.store(visaCardId.cardId, MockVisaFixtures.visaAuthTokens)
            activationStatusRepository.set(
                cardId = visaCardId.cardId,
                status = VisaCardActivationStatus.ActivationStarted(
                    activationInput = activationInput(),
                    authTokens = MockVisaFixtures.visaAuthTokens,
                    remoteState = VisaActivationRemoteState.CustomerWalletSignatureRequired(
                        activationOrderInfo = signedData.dataToSign.request.activationOrderInfo,
                    ),
                    cardWalletAddress = signedData.dataToSign.request.cardWalletAddress,
                ),
            )
            MockServerLogger.event("activation_state_changed", "state=customer_wallet_signature_required")
            Unit.right()
        }
    }

    override suspend fun approveByCustomerWallet(
        signedData: VisaSignedDataByCustomerWallet,
    ): Either<VisaApiError, Unit> {
        return MockServerLogger.respond("visa.activation.customer_approval") {
            activationStatusRepository.set(
                cardId = visaCardId.cardId,
                status = VisaCardActivationStatus.ActivationStarted(
                    activationInput = activationInput(),
                    authTokens = MockVisaFixtures.visaAuthTokens,
                    remoteState = VisaActivationRemoteState.AwaitingPinCode(
                        activationOrderInfo = MockVisaFixtures.activationOrderInfo,
                        status = VisaActivationRemoteState.AwaitingPinCode.Status.WaitingForPinCode,
                    ),
                    cardWalletAddress = MockVisaFixtures.CARD_WALLET_ADDRESS,
                ),
            )
            MockServerLogger.event("activation_state_changed", "state=awaiting_pin")
            Unit.right()
        }
    }

    override suspend fun sendPinCode(pinCode: VisaEncryptedPinCode): Either<VisaApiError, Unit> {
        return MockServerLogger.respond("visa.activation.pin") {
            activationStatusRepository.set(
                cardId = visaCardId.cardId,
                status = VisaCardActivationStatus.ActivationStarted(
                    activationInput = activationInput(),
                    authTokens = MockVisaFixtures.visaAuthTokens,
                    remoteState = VisaActivationRemoteState.Activated,
                    cardWalletAddress = MockVisaFixtures.CARD_WALLET_ADDRESS,
                ),
            )
            MockServerLogger.event("activation_state_changed", "state=activated")
            Unit.right()
        }
    }

    override suspend fun getPinCodeRsaEncryptionPublicKey(): String {
        return MockServerLogger.respond("visa.activation.pin_public_key") { MOCK_RSA_PUBLIC_KEY }
    }

    private fun activationInput(): VisaActivationInput {
        val currentStatus = activationStatusRepository.get(visaCardId.cardId)
        return when (currentStatus) {
            is VisaCardActivationStatus.ActivationStarted -> currentStatus.activationInput
            is VisaCardActivationStatus.NotStartedActivation -> currentStatus.activationInput
            else -> VisaActivationInput(
                cardId = visaCardId.cardId,
                cardPublicKey = visaCardId.cardPublicKey,
                isAccessCodeSet = false,
            )
        }
    }

    private companion object {
        const val MOCK_RSA_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApAqWLXgB/nTa97oJxnrpCuTxB5s519dSUvvK" +
                "WUzBlSar0ZkkJZldoHNAnA/xGdiaH/ZTYARfBgOMvAOORaWjgnLXNSCeLMBCRG0C6wC8GK8shS934fdl" +
                "+rs2hMCr4XuTBuT/0X9vdFmYjLgEhxCNbyeJqckOrHdDMR/oLN2ni9tBs/aM9jcULnlaLNhNwWqUlIDnC" +
                "Nqmv2OesFBRW90/OQ9SBOediOGDOpJqec21HwukxWcwOEZrhyWeejiQcu8JSRXg/U/gjF2T6QUPVLVaEu" +
                "+0TNYfQUcuTzzmrlkKGOHeytKtPUXt0zmYwTrV6f6rIkutXtubajXwflPLJgCfJQIDAQAB"
    }
}
