package com.tangem.data.visa

import arrow.core.Either
import arrow.core.right
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.pay.WithdrawalSignatureResult
import com.tangem.domain.pay.datasource.TangemPayAuthDataSource
import com.tangem.domain.visa.model.TangemPayInitialCredentials
import javax.inject.Inject
import javax.inject.Singleton

/** Mocked auth never asks a cold card or hot wallet to produce a real signature. */
@Singleton
internal class MockTangemPayAuthDataSource @Inject constructor() : TangemPayAuthDataSource {

    override suspend fun produceInitialCredentials(
        userWallet: UserWallet,
    ): Either<Throwable, TangemPayInitialCredentials> {
        return MockServerLogger.respond("tangem_pay.local_auth.credentials") {
            TangemPayInitialCredentials(
                customerWalletAddress = MockVisaFixtures.CUSTOMER_WALLET_ADDRESS,
                authTokens = MockVisaFixtures.tangemPayAuthTokens,
            ).right()
        }
    }

    override suspend fun getWithdrawalSignature(
        userWallet: UserWallet,
        hash: String,
    ): Either<Throwable, WithdrawalSignatureResult> {
        return MockServerLogger.respond("tangem_pay.local_auth.withdrawal_signature") {
            WithdrawalSignatureResult.Success(MockVisaFixtures.HASH_TO_SIGN).right()
        }
    }
}
