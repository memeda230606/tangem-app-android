package com.tangem.data.pay.repository

import arrow.core.Either
import arrow.core.right
import com.tangem.data.visa.MockServerLogger
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.pay.model.OrderData
import com.tangem.domain.pay.model.OrderStatus
import com.tangem.domain.pay.repository.CustomerOrderRepository
import com.tangem.domain.visa.error.VisaApiError
import javax.inject.Inject
import javax.inject.Singleton

/** Completed order fixture that keeps mocked card-issue and withdrawal screens offline. */
@Singleton
internal class MockCustomerOrderRepository @Inject constructor() : CustomerOrderRepository {

    override suspend fun getOrderData(userWalletId: UserWalletId, orderId: String): Either<VisaApiError, OrderData> {
        return MockServerLogger.respond("tangem_pay.order.get") {
            OrderData(
                customerId = "mock-customer",
                status = OrderStatus.COMPLETED,
                withdrawTxHash = MOCK_WITHDRAW_TX_HASH,
            ).right()
        }
    }

    private companion object {
        const val MOCK_WITHDRAW_TX_HASH =
            "0x0000000000000000000000000000000000000000000000000000000000000001"
    }
}
