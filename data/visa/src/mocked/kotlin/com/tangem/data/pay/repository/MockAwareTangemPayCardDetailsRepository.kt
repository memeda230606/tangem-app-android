package com.tangem.data.pay.repository

import arrow.core.Either
import arrow.core.right
import com.tangem.core.error.UniversalError
import com.tangem.data.visa.MockServerLogger
import com.tangem.domain.models.account.CardDisplayName
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.pay.model.SetPinResult
import com.tangem.domain.pay.model.OrderStatus
import com.tangem.domain.pay.model.TangemPayCardBalance
import com.tangem.domain.pay.model.TangemPayCardDetails
import com.tangem.domain.pay.model.TangemPayOrderInfo
import com.tangem.domain.pay.repository.TangemPayCardDetailsRepository
import com.tangem.domain.visa.model.TangemPayCardFrozenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** In MOCK env returns local card fixtures and does not contact a payment service. */
@Singleton
internal class MockAwareTangemPayCardDetailsRepository @Inject constructor(
    private val real: DefaultTangemPayCardDetailsRepository,
    private val fixturePolicy: TangemPayFixturePolicy,
) : TangemPayCardDetailsRepository {

    private val addToWalletDone: MutableSet<UserWalletId> = ConcurrentHashMap.newKeySet()
    private val frozenStates = ConcurrentHashMap<String, MutableStateFlow<TangemPayCardFrozenState>>()

    override suspend fun getCardBalance(userWalletId: UserWalletId): Either<UniversalError, TangemPayCardBalance> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.card.balance") { MOCK_CARD_BALANCE.right() }
        }
        return real.getCardBalance(userWalletId)
    }

    override suspend fun revealCardDetails(
        userWalletId: UserWalletId,
    ): Either<UniversalError, TangemPayCardDetails> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.card.reveal") {
                TangemPayCardDetails(
                    pan = MOCK_PAN,
                    cvv = MOCK_CVV,
                    expirationYear = MOCK_EXPIRATION_YEAR,
                    expirationMonth = MOCK_EXPIRATION_MONTH,
                ).right()
            }
        }
        return real.revealCardDetails(userWalletId)
    }

    override suspend fun getPin(userWalletId: UserWalletId, cardId: String): Either<UniversalError, String?> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.card.get_pin") { MOCK_PIN.right() }
        }
        return real.getPin(userWalletId, cardId)
    }

    override suspend fun setPin(userWalletId: UserWalletId, pin: String): Either<UniversalError, SetPinResult> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.card.set_pin") { SetPinResult.SUCCESS.right() }
        }
        return real.setPin(userWalletId, pin)
    }

    override suspend fun isAddToWalletDone(userWalletId: UserWalletId): Either<UniversalError, Boolean> {
        if (fixturePolicy.useFixture(userWalletId)) return (userWalletId in addToWalletDone).right()
        return real.isAddToWalletDone(userWalletId)
    }

    override suspend fun setAddToWalletAsDone(userWalletId: UserWalletId): Either<UniversalError, Unit> {
        if (fixturePolicy.useFixture(userWalletId)) {
            addToWalletDone.add(userWalletId)
            MockServerLogger.event("add_to_wallet_state_changed", "done=true")
            return Unit.right()
        }
        return real.setAddToWalletAsDone(userWalletId)
    }

    override suspend fun freezeCard(
        userWalletId: UserWalletId,
        cardId: String,
    ): Either<UniversalError, TangemPayOrderInfo> {
        if (fixturePolicy.useFixture(userWalletId)) {
            stateFlow(cardId).value = TangemPayCardFrozenState.Frozen
            MockServerLogger.event("card_frozen_state_changed", "state=frozen")
            return mockCompletedOrder(cardId).right()
        }
        return real.freezeCard(userWalletId, cardId)
    }

    override suspend fun unfreezeCard(
        userWalletId: UserWalletId,
        cardId: String,
    ): Either<UniversalError, TangemPayOrderInfo> {
        if (fixturePolicy.useFixture(userWalletId)) {
            stateFlow(cardId).value = TangemPayCardFrozenState.Unfrozen
            MockServerLogger.event("card_frozen_state_changed", "state=unfrozen")
            return mockCompletedOrder(cardId).right()
        }
        return real.unfreezeCard(userWalletId, cardId)
    }

    override fun cardFrozenState(cardId: String): Flow<TangemPayCardFrozenState> {
        if (fixturePolicy.useFixtureForGlobalRequest()) return stateFlow(cardId)
        return real.cardFrozenState(cardId)
    }

    override suspend fun cardFrozenStateSync(cardId: String): TangemPayCardFrozenState? {
        if (fixturePolicy.useFixtureForGlobalRequest()) return stateFlow(cardId).value
        return real.cardFrozenStateSync(cardId)
    }

    override suspend fun setCardFrozenState(cardId: String, state: TangemPayCardFrozenState) {
        if (fixturePolicy.useFixtureForGlobalRequest()) {
            stateFlow(cardId).value = state
            MockServerLogger.event("card_frozen_state_changed", "state=${state::class.simpleName}")
            return
        }
        real.setCardFrozenState(cardId, state)
    }

    override suspend fun getOrderInfo(
        userWalletId: UserWalletId,
        orderId: String,
    ): Either<UniversalError, TangemPayOrderInfo> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.card.order_info") { mockCompletedOrder(orderId).right() }
        }
        return real.getOrderInfo(userWalletId, orderId)
    }

    override suspend fun updateCardDisplayName(
        cardId: String,
        userWalletId: UserWalletId,
        displayName: CardDisplayName,
    ): Either<UniversalError, Unit> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.card.update_name") { Unit.right() }
        }
        return real.updateCardDisplayName(cardId, userWalletId, displayName)
    }

    override suspend fun updateCardLimit(
        cardId: String,
        userWalletId: UserWalletId,
        limit: String,
    ): Either<UniversalError, Unit> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.card.update_limit") { Unit.right() }
        }
        return real.updateCardLimit(cardId, userWalletId, limit)
    }

    private fun stateFlow(cardId: String): MutableStateFlow<TangemPayCardFrozenState> {
        return frozenStates.getOrPut(cardId) { MutableStateFlow(TangemPayCardFrozenState.Unfrozen) }
    }

    private fun mockCompletedOrder(orderId: String): TangemPayOrderInfo {
        return TangemPayOrderInfo(orderId = orderId, orderStatus = OrderStatus.COMPLETED)
    }

    private companion object {
        const val MOCK_PAN = "4242 4242 4242 4242"
        const val MOCK_CVV = "123"
        const val MOCK_EXPIRATION_YEAR = "2028"
        const val MOCK_EXPIRATION_MONTH = "12"
        const val MOCK_PIN = "1234"
        val MOCK_CARD_BALANCE = TangemPayCardBalance(
            fiatBalance = BigDecimal("250.00"),
            currencyCode = "EUR",
            cryptoBalance = BigDecimal("270.00"),
            availableForWithdrawal = BigDecimal("200.00"),
            chainId = 137,
            depositAddress = "0x0000000000000000000000000000000000000002",
            contractAddress = "0x0000000000000000000000000000000000000001",
        )
    }
}
