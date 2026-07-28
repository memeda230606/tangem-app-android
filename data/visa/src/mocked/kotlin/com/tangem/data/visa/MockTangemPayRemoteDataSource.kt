package com.tangem.data.visa

import arrow.core.Either
import arrow.core.right
import com.tangem.domain.visa.datasource.TangemPayRemoteDataSource
import com.tangem.domain.visa.error.VisaApiError
import com.tangem.domain.visa.model.TangemPayAuthTokens
import com.tangem.domain.visa.model.VisaAuthChallenge
import javax.inject.Inject
import javax.inject.Singleton

/** Local credentials for mocked Tangem Pay authorization. */
@Singleton
internal class MockTangemPayRemoteDataSource @Inject constructor() : TangemPayRemoteDataSource {

    override suspend fun getCustomerWalletAuthChallenge(
        customerWalletAddress: String,
        customerWalletId: String,
    ): Either<VisaApiError, VisaAuthChallenge.Wallet> = MockServerLogger.respond("tangem_pay.auth.challenge") {
        MockVisaFixtures.walletChallenge.right()
    }

    override suspend fun getTokenWithCustomerWallet(
        sessionId: String,
        signature: String,
        nonce: String,
    ): Either<VisaApiError, TangemPayAuthTokens> = MockServerLogger.respond("tangem_pay.auth.tokens") {
        MockVisaFixtures.tangemPayAuthTokens.right()
    }
}
