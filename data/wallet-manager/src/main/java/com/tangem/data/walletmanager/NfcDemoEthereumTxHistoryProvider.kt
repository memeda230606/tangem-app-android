package com.tangem.data.walletmanager

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tangem.blockchain.common.pagination.Page
import com.tangem.domain.models.network.TxInfo
import com.tangem.domain.txhistory.models.Page as DomainPage
import com.tangem.domain.txhistory.models.PaginationWrapper
import com.tangem.domain.txhistory.models.TxHistoryState
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import com.tangem.utils.logging.TangemLogger
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import javax.inject.Inject

/**
 * Keyless Ethereum transaction-history fallback for the ordinary-NFC demo wallet.
 *
 * RouteScan returns up to 50 transactions per request without an API key. That is sufficient for the local demo
 * wallet and avoids embedding a third-party credential in the APK.
 */
internal class NfcDemoEthereumTxHistoryProvider @Inject constructor(
    private val dispatchers: CoroutineDispatcherProvider,
) {

    suspend fun getState(address: String, decimals: Int): TxHistoryState {
        return runCatching { fetch(address = address, decimals = decimals) }
            .fold(
                onSuccess = { items ->
                    if (items.isEmpty()) {
                        TxHistoryState.Success.Empty
                    } else {
                        TxHistoryState.Success.HasTransactions(txCount = items.size)
                    }
                },
                onFailure = { error ->
                    TangemLogger.e("[NfcDemo] RouteScan Ethereum transaction history failed", error)
                    TxHistoryState.Failed.FetchError(
                        exception = error as? Exception ?: IllegalStateException(error),
                    )
                },
            )
    }

    suspend fun getItems(address: String, decimals: Int, page: Page): PaginationWrapper<TxInfo> {
        val currentPage = page.toDomain()
        if (page !is Page.Initial) {
            return PaginationWrapper(
                currentPage = currentPage,
                nextPage = DomainPage.LastPage,
                items = emptyList(),
            )
        }

        return PaginationWrapper(
            currentPage = currentPage,
            nextPage = DomainPage.LastPage,
            items = fetch(address = address, decimals = decimals),
        )
    }

    private suspend fun fetch(address: String, decimals: Int): List<TxInfo> = withContext(dispatchers.io) {
        require(ETHEREUM_ADDRESS_REGEX.matches(address)) { "Invalid Ethereum address" }

        val connection = URI("$BASE_URL/$address/transactions?limit=$PAGE_SIZE")
            .toURL()
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("RouteScan transaction history failed: HTTP $responseCode $errorBody")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseTransactions(json = body, walletAddress = address, decimals = decimals)
        } finally {
            connection.disconnect()
        }
    }

    private fun Page.toDomain(): DomainPage = when (this) {
        is Page.Initial -> DomainPage.Initial
        is Page.LastPage -> DomainPage.LastPage
        is Page.Next -> DomainPage.Next(value = value)
    }

    internal companion object {
        private const val BASE_URL = "https://api.routescan.io/v2/network/mainnet/evm/1/address"
        private const val PAGE_SIZE = 50
        private const val CONNECT_TIMEOUT_MILLIS = 15_000
        private const val READ_TIMEOUT_MILLIS = 20_000
        private val ETHEREUM_ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")

        private val responseAdapter = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
            .adapter(RouteScanResponse::class.java)

        fun parseTransactions(json: String, walletAddress: String, decimals: Int): List<TxInfo> {
            val response = requireNotNull(responseAdapter.fromJson(json)) {
                "RouteScan returned an empty response"
            }

            return response.items.mapNotNull { transaction ->
                transaction.toTxInfo(walletAddress = walletAddress, decimals = decimals)
            }
        }

        private fun RouteScanTransaction.toTxInfo(walletAddress: String, decimals: Int): TxInfo? {
            val source = from ?: return null
            val destination = to ?: return null
            val amount = value.toBigDecimalOrNull()?.movePointLeft(decimals) ?: return null
            val isOutgoing = source.equals(walletAddress, ignoreCase = true)
            val interactionAddressType = if (isOutgoing) {
                TxInfo.InteractionAddressType.User(destination)
            } else {
                TxInfo.InteractionAddressType.User(source)
            }

            return TxInfo(
                txHash = id,
                timestampInMillis = timestamp
                    ?.let { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }
                    ?: 0L,
                isOutgoing = isOutgoing,
                destinationType = TxInfo.DestinationType.Single(TxInfo.AddressType.User(destination)),
                sourceType = TxInfo.SourceType.Single(source),
                interactionAddressType = interactionAddressType,
                status = when (status) {
                    true -> TxInfo.TransactionStatus.Confirmed
                    false -> TxInfo.TransactionStatus.Failed
                    null -> TxInfo.TransactionStatus.Unconfirmed
                },
                type = TxInfo.TransactionType.Transfer,
                amount = amount,
            )
        }
    }
}

private data class RouteScanResponse(
    val items: List<RouteScanTransaction> = emptyList(),
)

private data class RouteScanTransaction(
    val id: String,
    val status: Boolean? = null,
    val timestamp: String? = null,
    val from: String? = null,
    val to: String? = null,
    val value: String = "0",
)
