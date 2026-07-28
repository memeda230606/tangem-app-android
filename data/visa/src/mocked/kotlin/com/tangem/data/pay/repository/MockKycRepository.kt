package com.tangem.data.pay.repository

import arrow.core.Either
import arrow.core.right
import com.tangem.core.error.UniversalError
import com.tangem.data.visa.MockServerLogger
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.pay.KycStartInfo
import com.tangem.domain.pay.repository.KycRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Local KYC launch data used only by the mocked build type. */
@Singleton
internal class MockKycRepository @Inject constructor() : KycRepository {

    override suspend fun getKycStartInfo(userWalletId: UserWalletId): Either<UniversalError, KycStartInfo> {
        return MockServerLogger.respond("tangem_pay.kyc.start") {
            KycStartInfo(token = MOCK_KYC_TOKEN, locale = "en").right()
        }
    }

    private companion object {
        const val MOCK_KYC_TOKEN = "mock-kyc-token"
    }
}
