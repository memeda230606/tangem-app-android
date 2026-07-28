package com.tangem.data.pay.repository

import arrow.core.Either
import arrow.core.right
import com.tangem.core.error.UniversalError
import com.tangem.data.visa.MockServerLogger
import com.tangem.domain.models.pay.TangemPayReissueCardFee
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.pay.model.OrderStatus
import com.tangem.domain.pay.model.TangemPayOrderInfo
import com.tangem.domain.pay.repository.TangemPayReissueCardRepository
import com.tangem.domain.visa.error.VisaApiError
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory reissue fixtures. No card order is created outside the mocked process. */
@Singleton
internal class MockTangemPayReissueCardRepository @Inject constructor() : TangemPayReissueCardRepository {

    private val orderIdsByCardId = ConcurrentHashMap<String, String>()

    override suspend fun getReissueCardFee(userWalletId: UserWalletId): Either<VisaApiError, TangemPayReissueCardFee> {
        return MockServerLogger.respond("tangem_pay.reissue.fee") {
            TangemPayReissueCardFee(amount = BigDecimal("10.00"), currencyCode = "EUR").right()
        }
    }

    override suspend fun reissueCard(
        userWalletId: UserWalletId,
        cardId: String,
    ): Either<VisaApiError, TangemPayOrderInfo> {
        return MockServerLogger.respond("tangem_pay.reissue.create") {
            val orderId = "mock-reissue-$cardId"
            orderIdsByCardId[cardId] = orderId
            completedOrder(orderId).right()
        }
    }

    override suspend fun storeReissueOrderId(cardId: String, orderId: String): Either<UniversalError, Unit> {
        return MockServerLogger.respond("tangem_pay.reissue.store_order") {
            orderIdsByCardId[cardId] = orderId
            Unit.right()
        }
    }

    override suspend fun getReissueOrderInfo(
        userWalletId: UserWalletId,
        cardId: String,
    ): Either<UniversalError, TangemPayOrderInfo?> {
        return MockServerLogger.respond("tangem_pay.reissue.order_info") {
            orderIdsByCardId[cardId]?.let(::completedOrder).right()
        }
    }

    private fun completedOrder(orderId: String): TangemPayOrderInfo {
        return TangemPayOrderInfo(orderId = orderId, orderStatus = OrderStatus.COMPLETED)
    }
}
