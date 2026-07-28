package com.tangem.data.pay.repository

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.tangem.core.error.UniversalError
import com.tangem.data.visa.MockServerLogger
import com.tangem.domain.models.account.PaymentAccountStatusValue
import com.tangem.domain.models.kyc.KycStatus
import com.tangem.domain.models.pay.TangemPayEligibilityType
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.pay.model.CustomerInfo
import com.tangem.domain.pay.repository.OnboardingRepository
import com.tangem.domain.visa.error.VisaApiError
import com.tangem.domain.visa.model.TangemPayCardFrozenState
import java.math.BigDecimal
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** In MOCK env returns local onboarding fixtures without contacting a payment service. */
@Singleton
internal class MockAwareOnboardingRepository @Inject constructor(
    private val real: DefaultOnboardingRepository,
    private val fixturePolicy: TangemPayFixturePolicy,
) : OnboardingRepository {

    private val mockOrderIds: MutableSet<UserWalletId> = ConcurrentHashMap.newKeySet()

    override suspend fun validateDeeplink(link: String): Either<UniversalError, Boolean> {
        if (fixturePolicy.useFixtureForGlobalRequest()) {
            return MockServerLogger.respond("tangem_pay.onboarding.validate_deeplink") { true.right() }
        }
        if (fixturePolicy.shouldSuppressGlobalRequest()) return false.right()
        return real.validateDeeplink(link)
    }

    override suspend fun isTangemPayInitialDataProduced(userWalletId: UserWalletId): Boolean {
        if (fixturePolicy.useFixture(userWalletId)) return true
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return false
        return real.isTangemPayInitialDataProduced(userWalletId)
    }

    override suspend fun produceInitialData(userWalletId: UserWalletId) {
        if (fixturePolicy.useFixture(userWalletId)) {
            MockServerLogger.event("initial_data_produced", "source=local_fixture")
            return
        }
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return
        real.produceInitialData(userWalletId)
    }

    override suspend fun getCustomerInfo(userWalletId: UserWalletId): Either<VisaApiError, CustomerInfo> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.onboarding.customer_info") { MOCK_CUSTOMER_INFO.right() }
        }
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return VisaApiError.NotPaeraCustomer.left()
        return real.getCustomerInfo(userWalletId)
    }

    override suspend fun createOrder(userWalletId: UserWalletId): Either<VisaApiError, String> {
        if (fixturePolicy.useFixture(userWalletId)) {
            mockOrderIds.add(userWalletId)
            MockServerLogger.event("onboarding_order_created", "status=completed")
            return MOCK_ORDER_ID.right()
        }
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return VisaApiError.NotPaeraCustomer.left()
        return real.createOrder(userWalletId)
    }

    override suspend fun clearOrderId(userWalletId: UserWalletId) {
        if (fixturePolicy.useFixture(userWalletId)) {
            mockOrderIds.remove(userWalletId)
            MockServerLogger.event("onboarding_order_cleared")
            return
        }
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return
        real.clearOrderId(userWalletId)
    }

    override suspend fun getOrderId(userWalletId: UserWalletId): String? {
        if (fixturePolicy.useFixture(userWalletId)) return MOCK_ORDER_ID.takeIf { userWalletId in mockOrderIds }
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return null
        return real.getOrderId(userWalletId)
    }

    override suspend fun hasTangemPayInWallet(userWalletId: UserWalletId): Either<VisaApiError, Boolean> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.onboarding.wallet_presence") { true.right() }
        }
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return false.right()
        return real.hasTangemPayInWallet(userWalletId)
    }

    override suspend fun checkCustomerEligibility(): List<TangemPayEligibilityType> {
        if (fixturePolicy.useFixtureForGlobalRequest()) {
            return MockServerLogger.respond("tangem_pay.onboarding.check_eligibility") { MOCK_ELIGIBILITY }
        }
        if (fixturePolicy.shouldSuppressGlobalRequest()) return emptyList()
        return real.checkCustomerEligibility()
    }

    override suspend fun getCustomerEligibility(): List<TangemPayEligibilityType> {
        if (fixturePolicy.useFixtureForGlobalRequest()) {
            return MockServerLogger.respond("tangem_pay.onboarding.get_eligibility") { MOCK_ELIGIBILITY }
        }
        if (fixturePolicy.shouldSuppressGlobalRequest()) return emptyList()
        return real.getCustomerEligibility()
    }

    override fun getSavedCustomerInfo(userWalletId: UserWalletId): CustomerInfo? {
        if (fixturePolicy.useFixture(userWalletId)) return MOCK_CUSTOMER_INFO
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return null
        return real.getSavedCustomerInfo(userWalletId)
    }

    override suspend fun getHideMainOnboardingBanner(userWalletId: UserWalletId): Boolean {
        if (fixturePolicy.useFixture(userWalletId)) return false
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return true
        return real.getHideMainOnboardingBanner(userWalletId)
    }

    override suspend fun setHideMainOnboardingBanner(userWalletId: UserWalletId) {
        if (fixturePolicy.useFixture(userWalletId)) return
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return
        real.setHideMainOnboardingBanner(userWalletId)
    }

    override suspend fun disableTangemPay(userWalletId: UserWalletId): Either<VisaApiError, Unit> {
        if (fixturePolicy.useFixture(userWalletId)) {
            return MockServerLogger.respond("tangem_pay.onboarding.disable") { Unit.right() }
        }
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return VisaApiError.NotPaeraCustomer.left()
        return real.disableTangemPay(userWalletId)
    }

    override suspend fun isTangemPayDeactivated(userWalletId: UserWalletId): Boolean {
        if (fixturePolicy.useFixture(userWalletId)) return false
        if (!fixturePolicy.shouldExposeTangemPay(userWalletId)) return false
        return real.isTangemPayDeactivated(userWalletId)
    }

    private companion object {
        const val MOCK_ORDER_ID = "mock-order-id"
        val MOCK_ELIGIBILITY = listOf(TangemPayEligibilityType.BANNER, TangemPayEligibilityType.DETAILS)
        val MOCK_CUSTOMER_INFO = CustomerInfo(
            customerId = "mock-customer",
            productInstance = CustomerInfo.ProductInstance(
                id = "mock-product-instance",
                cardId = "mock-payment-card",
                frozenState = TangemPayCardFrozenState.Unfrozen,
                displayName = null,
                actualCardLimit = null,
                adminCardLimit = null,
                status = CustomerInfo.ProductInstance.Status.ACTIVE,
            ),
            kycStatus = KycStatus.APPROVED,
            cardInfo = CustomerInfo.CardInfo(
                lastFourDigits = "4242",
                balance = BigDecimal("250.00"),
                currencyCode = "EUR",
                depositAddress = "0x0000000000000000000000000000000000000002",
                isPinSet = true,
                fiatBalance = PaymentAccountStatusValue.FiatBalance(
                    availableBalance = BigDecimal("250.00"),
                    currency = "EUR",
                ),
                cryptoBalance = PaymentAccountStatusValue.CryptoBalance(
                    id = "usd-coin",
                    chainId = 137,
                    depositAddress = "0x0000000000000000000000000000000000000002",
                    tokenContractAddress = "0x3c499c542cef5e3811e1192ce70d8cc03d5c3359",
                    balance = BigDecimal("270.00"),
                ),
                availableForWithdrawal = BigDecimal("200.00"),
            ),
            state = CustomerInfo.State.ACTIVE,
            fiatBalance = PaymentAccountStatusValue.FiatBalance(
                availableBalance = BigDecimal("250.00"),
                currency = "EUR",
            ),
            cryptoBalance = PaymentAccountStatusValue.CryptoBalance(
                id = "usd-coin",
                chainId = 137,
                depositAddress = "0x0000000000000000000000000000000000000002",
                tokenContractAddress = "0x3c499c542cef5e3811e1192ce70d8cc03d5c3359",
                balance = BigDecimal("270.00"),
            ),
        )
    }
}
