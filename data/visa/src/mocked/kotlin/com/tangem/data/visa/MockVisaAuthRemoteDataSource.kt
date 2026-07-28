package com.tangem.data.visa

import arrow.core.Either
import arrow.core.right
import com.tangem.domain.visa.datasource.VisaAuthRemoteDataSource
import com.tangem.domain.visa.error.VisaApiError
import com.tangem.domain.visa.model.VisaAuthChallenge
import com.tangem.domain.visa.model.VisaAuthSignedChallenge
import com.tangem.domain.visa.model.VisaAuthTokens
import javax.inject.Inject
import javax.inject.Singleton

/** Static authorization responses used by mocked NFC/visa flows. */
@Singleton
internal class MockVisaAuthRemoteDataSource @Inject constructor() : VisaAuthRemoteDataSource {

    override suspend fun getCardAuthChallenge(
        cardId: String,
        cardPublicKey: String,
    ): Either<VisaApiError, VisaAuthChallenge.Card> = MockServerLogger.respond("visa.auth.card_challenge") {
        MockVisaFixtures.cardChallenge.right()
    }

    override suspend fun getCardWalletAuthChallenge(
        cardId: String,
        cardWalletAddress: String,
    ): Either<VisaApiError, VisaAuthChallenge.Wallet> = MockServerLogger.respond("visa.auth.wallet_challenge") {
        MockVisaFixtures.walletChallenge.right()
    }

    override suspend fun getAccessTokens(
        signedChallenge: VisaAuthSignedChallenge,
    ): Either<VisaApiError, VisaAuthTokens> = MockServerLogger.respond("visa.auth.access_tokens") {
        MockVisaFixtures.visaAuthTokens.right()
    }

    override suspend fun refreshAccessTokens(
        refreshToken: VisaAuthTokens.RefreshToken,
    ): Either<VisaApiError, VisaAuthTokens> = MockServerLogger.respond("visa.auth.refresh_tokens") {
        MockVisaFixtures.visaAuthTokens.right()
    }

    override suspend fun exchangeAccessToken(tokens: VisaAuthTokens): Either<VisaApiError, VisaAuthTokens> {
        return MockServerLogger.respond("visa.auth.exchange_token") {
            MockVisaFixtures.visaAuthTokens.right()
        }
    }
}
