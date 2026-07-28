package com.tangem.data.pay.repository

import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.data.visa.MockServerLogger
import com.tangem.domain.tangempay.model.TangemPayTxHistoryListBatchFlow
import com.tangem.domain.tangempay.model.TangemPayTxHistoryListBatchingContext
import com.tangem.domain.tangempay.repository.TangemPayTxHistoryRepository
import com.tangem.domain.visa.model.TangemPayTxHistoryItem
import com.tangem.pagination.BatchFetchResult
import com.tangem.pagination.BatchListSource
import com.tangem.pagination.fetcher.CursorBatchFetcher
import com.tangem.pagination.toBatchFlow
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import org.joda.time.DateTime
import java.math.BigDecimal
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

/** Static payment-history page used by the mocked build type without a backend request. */
@Singleton
internal class MockTangemPayTxHistoryRepository @Inject constructor(
    private val dispatchers: CoroutineDispatcherProvider,
) : TangemPayTxHistoryRepository {

    override fun getTxHistoryBatchFlow(
        userWalletId: UserWalletId,
        batchSize: Int,
        context: TangemPayTxHistoryListBatchingContext,
    ): TangemPayTxHistoryListBatchFlow {
        MockServerLogger.event("tx_history_flow_created", "batchSize=$batchSize")
        return BatchListSource(
            fetchDispatcher = dispatchers.io,
            context = context,
            generateNewKey = { keys -> keys.lastOrNull()?.inc() ?: 0 },
            batchFetcher = CursorBatchFetcher(
                prefetchDistance = batchSize,
                batchSize = batchSize,
                subFetcher = { request, _, _ ->
                    val data = if (request.cursor == null) FIXTURE_ITEMS else emptyList()
                    MockServerLogger.event(
                        "tx_history_page",
                        "page=${if (request.cursor == null) "first" else "next"} itemCount=${data.size}",
                    )
                    BatchFetchResult.Success(data = data, last = true, empty = data.isEmpty())
                },
                cursorFromItem = { item -> item.id },
            ),
        ).toBatchFlow()
    }

    private companion object {
        val FIXTURE_ITEMS = listOf(
            TangemPayTxHistoryItem.Spend(
                id = "mock-spend-1",
                jsonRepresentation = "{\"fixture\":\"mock-spend-1\"}",
                date = DateTime(2026, 1, 15, 12, 0),
                amount = BigDecimal("-12.50"),
                currency = Currency.getInstance("EUR"),
                authorizedAmount = BigDecimal("-12.50"),
                localAmount = null,
                localCurrency = null,
                enrichedMerchantName = "Mock Coffee",
                merchantName = "Mock Coffee",
                enrichedMerchantCategory = "Food and drink",
                merchantCategoryCode = "5814",
                merchantCategory = "Fast food restaurants",
                status = TangemPayTxHistoryItem.Status.COMPLETED,
                enrichedMerchantIconUrl = null,
                declinedReason = null,
            ),
            TangemPayTxHistoryItem.Payment(
                id = "mock-payment-1",
                jsonRepresentation = "{\"fixture\":\"mock-payment-1\"}",
                date = DateTime(2026, 1, 10, 9, 30),
                amount = BigDecimal("100.00"),
                currency = Currency.getInstance("EUR"),
                transactionHash = "0x0000000000000000000000000000000000000000000000000000000000000001",
            ),
        )
    }
}
