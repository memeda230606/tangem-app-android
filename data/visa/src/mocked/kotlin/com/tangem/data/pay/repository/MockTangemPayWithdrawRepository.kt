package com.tangem.data.pay.repository

import arrow.core.Either
import arrow.core.right
import com.tangem.core.error.UniversalError
import com.tangem.data.visa.MockServerLogger
import com.tangem.domain.models.currency.CryptoCurrency
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.pay.TangemPayWithdrawExchangeState
import com.tangem.domain.pay.WithdrawalResult
import com.tangem.domain.pay.repository.TangemPayWithdrawRepository
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** A successful withdrawal is visual-only in the mocked build; it cannot submit an on-chain transaction. */
@Singleton
internal class MockTangemPayWithdrawRepository @Inject constructor() : TangemPayWithdrawRepository {

    override suspend fun withdraw(
        userWallet: UserWallet,
        receiverAddress: String,
        cryptoAmount: BigDecimal,
        cryptoCurrencyId: CryptoCurrency.RawID,
        exchangeData: TangemPayWithdrawExchangeState,
    ): Either<UniversalError, WithdrawalResult> {
        return MockServerLogger.respond("tangem_pay.withdraw", "result=visual_only") {
            WithdrawalResult.Success.right()
        }
    }

    override suspend fun hasWithdrawOrder(userWalletId: UserWalletId): Boolean {
        return MockServerLogger.respond("tangem_pay.withdraw.has_order") { false }
    }

    override suspend fun pollWithdrawOrdersIfNeeds(userWallet: UserWallet) {
        MockServerLogger.event("withdraw_poll_skipped", "reason=local_fixture")
    }
}
