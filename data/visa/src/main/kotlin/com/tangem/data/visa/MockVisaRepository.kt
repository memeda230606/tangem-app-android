package com.tangem.data.visa

import androidx.paging.PagingData
import com.tangem.domain.appcurrency.model.AppCurrency
import com.tangem.domain.models.currency.CryptoCurrency
import com.tangem.domain.models.network.Network
import com.tangem.domain.models.network.NetworkAddress
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.visa.model.VisaCurrency
import com.tangem.domain.visa.model.VisaTxDetails
import com.tangem.domain.visa.model.VisaTxHistoryItem
import com.tangem.domain.visa.repository.VisaRepository
import kotlinx.coroutines.flow.flowOf
import org.joda.time.DateTime
import java.math.BigDecimal
import java.util.Currency
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Visual-only Visa data fixtures. This repository is intentionally disconnected from Visa services. */
internal class MockVisaRepository @Inject constructor() : VisaRepository {

    override suspend fun getVisaCurrency(userWalletId: UserWalletId, isRefresh: Boolean): VisaCurrency {
        return MOCK_CURRENCY
    }

    override fun getTxHistory(
        userWalletId: UserWalletId,
        pageSize: Int,
        isRefresh: Boolean,
    ): Flow<PagingData<VisaTxHistoryItem>> {
        return flowOf(PagingData.from(MOCK_TX_HISTORY))
    }

    override suspend fun getTxDetails(userWalletId: UserWalletId, txId: String): VisaTxDetails {
        return VisaTxDetails(
            id = txId,
            type = "payment",
            status = "completed",
            blockchainAmount = BigDecimal("12.50"),
            transactionAmount = BigDecimal("12.50"),
            transactionCurrencyCode = 978,
            merchantName = "Mock Coffee",
            merchantCity = "Berlin",
            merchantCountryCode = "DE",
            merchantCategoryCode = "5814",
            fiatCurrency = Currency.getInstance("EUR"),
            requests = listOf(
                VisaTxDetails.Request(
                    id = "mock-request-1",
                    billingAmount = BigDecimal("12.50"),
                    billingCurrencyCode = 978,
                    blockchainAmount = BigDecimal("12.50"),
                    errorCode = 0,
                    requestDate = DateTime(2026, 1, 15, 12, 0),
                    requestStatus = "completed",
                    requestType = "authorization",
                    transactionAmount = BigDecimal("12.50"),
                    txCurrencyCode = 978,
                    txHash = MOCK_TX_HASH,
                    txStatus = "confirmed",
                    exploreUrl = null,
                    fiatCurrency = Currency.getInstance("EUR"),
                ),
            ),
        )
    }

    private companion object {
        const val MOCK_TOKEN_CONTRACT = "0x3c499c542cef5e3811e1192ce70d8cc03d5c3359"
        const val MOCK_PAYMENT_ADDRESS = "0x0000000000000000000000000000000000000002"
        const val MOCK_TX_HASH = "0x0000000000000000000000000000000000000000000000000000000000000001"

        val MOCK_NETWORK = Network(
            id = Network.ID(value = "polygon-pos", derivationPath = Network.DerivationPath.None),
            name = "Polygon PoS",
            currencySymbol = "POL",
            derivationPath = Network.DerivationPath.None,
            isTestnet = false,
            standardType = Network.StandardType.ERC20,
            hasFiatFeeRate = false,
            canHandleTokens = true,
            transactionExtrasType = Network.TransactionExtrasType.NONE,
            nameResolvingType = Network.NameResolvingType.NONE,
        )

        val MOCK_TOKEN = CryptoCurrency.Token(
            id = CryptoCurrency.ID(
                prefix = CryptoCurrency.ID.Prefix.TOKEN_PREFIX,
                body = CryptoCurrency.ID.Body.NetworkId("polygon-pos"),
                suffix = CryptoCurrency.ID.Suffix.ContractAddress(MOCK_TOKEN_CONTRACT),
            ),
            network = MOCK_NETWORK,
            name = "USDC",
            symbol = "USDC",
            decimals = 6,
            iconUrl = null,
            isCustom = false,
            contractAddress = MOCK_TOKEN_CONTRACT,
        )

        val MOCK_CURRENCY = VisaCurrency(
            cryptoCurrency = MOCK_TOKEN,
            networkName = "Polygon PoS",
            symbol = "USDC",
            decimals = 6,
            fiatRate = BigDecimal.ONE,
            priceChange = BigDecimal.ZERO,
            fiatCurrency = AppCurrency(code = "EUR", name = "Euro", symbol = "€"),
            balances = VisaCurrency.Balances(
                total = BigDecimal("250.00"),
                verified = BigDecimal("250.00"),
                available = BigDecimal("200.00"),
                blocked = BigDecimal.ZERO,
                debt = BigDecimal.ZERO,
            ),
            limits = VisaCurrency.Limits(
                remainingOtp = BigDecimal("500.00"),
                remainingNoOtp = BigDecimal("50.00"),
                singleTransaction = BigDecimal("100.00"),
                expirationDate = DateTime(2026, 12, 31, 0, 0),
            ),
            paymentAccountAddress = NetworkAddress.Single(
                NetworkAddress.Address(
                    value = MOCK_PAYMENT_ADDRESS,
                    type = NetworkAddress.Address.Type.Primary,
                ),
            ),
        )

        val MOCK_TX_HISTORY = listOf(
            VisaTxHistoryItem(
                id = "mock-visa-tx-1",
                date = DateTime(2026, 1, 15, 12, 0),
                amount = BigDecimal("-12.50"),
                fiatAmount = BigDecimal("-12.50"),
                merchantName = "Mock Coffee",
                status = "completed",
                fiatCurrency = Currency.getInstance("EUR"),
            ),
        )
    }
}
