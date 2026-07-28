package com.tangem.data.quotes.multi

import arrow.core.Either
import com.tangem.data.quotes.store.QuotesStatusesStore
import com.tangem.datasource.api.tangemTech.models.QuotesResponse
import com.tangem.domain.models.currency.CryptoCurrency
import com.tangem.domain.quotes.multi.MultiQuoteStatusFetcher
import com.tangem.utils.logging.TangemLogger
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** Stores deterministic quotes without contacting Tangem Tech in the mocked build. */
@Singleton
internal class MockMultiQuoteStatusFetcher @Inject constructor(
    private val quotesStatusesStore: QuotesStatusesStore,
) : MultiQuoteStatusFetcher {

    override suspend fun invoke(params: MultiQuoteStatusFetcher.Params): Either<Throwable, Unit> = Either.catch {
        val quotes = params.currenciesIds.associate { rawId ->
            rawId.value to rawId.toFixtureQuote()
        }

        quotesStatusesStore.store(values = quotes)
        TangemLogger.i("[MockedQuotes] Local quotes served count=${quotes.size}")
    }

    private fun CryptoCurrency.RawID.toFixtureQuote(): QuotesResponse.Quote {
        val price = when (value.lowercase()) {
            "bitcoin" -> BigDecimal("62000")
            "ethereum" -> BigDecimal("3500")
            "usd-coin", "usdc" -> BigDecimal.ONE
            else -> BigDecimal.TEN
        }

        return QuotesResponse.Quote(
            price = price,
            priceChange24h = BigDecimal("1.25"),
            priceChange1w = null,
            priceChange30d = null,
            priceUsd = price,
        )
    }
}
