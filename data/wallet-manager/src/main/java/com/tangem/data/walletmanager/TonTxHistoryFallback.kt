package com.tangem.data.walletmanager

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.tangem.blockchain.common.Blockchain
import com.tangem.datasource.di.NetworkMoshi
import com.tangem.domain.models.network.TxInfo
import com.tangem.domain.txhistory.models.Page
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

/** Read-only native TON transfer history backed by TonAPI account events. */
internal class TonTxHistoryFallback @Inject constructor(
    @NetworkMoshi moshi: Moshi,
    private val dispatchers: CoroutineDispatcherProvider,
) {
    private val adapter = moshi.adapter(TonAccountEventsResponse::class.java)

    suspend fun getTransactionsCount(address: String, blockchain: Blockchain): Int = withContext(dispatchers.io) {
        requireRequestIsSupported(address, blockchain)
        if (requestEvents(address, blockchain, Page.Initial, 1).events.isEmpty()) 0 else 1
    }

    suspend fun getTransactions(
        address: String,
        blockchain: Blockchain,
        page: Page,
        pageSize: Int,
    ): KeylessTxHistoryPage = withContext(dispatchers.io) {
        requireRequestIsSupported(address, blockchain)
        if (page is Page.LastPage) return@withContext KeylessTxHistoryPage(emptyList(), Page.LastPage)
        val limit = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        convertResponse(requestEvents(address, blockchain, page, limit), limit)
    }

    internal fun convertResponse(response: TonAccountEventsResponse, pageSize: Int): KeylessTxHistoryPage =
        KeylessTxHistoryPage(
            items = response.events.flatMap { event ->
                event.actions.mapNotNull { action -> action.toTxInfo(event) }
            },
            nextPage = if (response.events.size >= pageSize && response.nextFrom > 0) {
                Page.Next(response.nextFrom.toString())
            } else {
                Page.LastPage
            },
        )

    private fun requestEvents(
        address: String,
        blockchain: Blockchain,
        page: Page,
        limit: Int,
    ): TonAccountEventsResponse {
        val encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8.name())
        val before = (page as? Page.Next)?.value?.toLongOrNull()?.let { "&before_lt=$it" }.orEmpty()
        val url = URL("${blockchain.baseUrl()}/v2/accounts/$encodedAddress/events?limit=$limit&subject_only=true$before")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                throw IOException("TonAPI transaction history request failed with HTTP ${connection.responseCode}")
            }
            return requireNotNull(adapter.fromJson(connection.inputStream.bufferedReader().use { it.readText() })) {
                "TonAPI returned an empty response"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun TonAction.toTxInfo(event: TonAccountEvent): TxInfo? {
        val transfer = tonTransfer ?: return null
        val walletAddress = event.account.address
        val outgoing = transfer.sender.address == walletAddress
        val incoming = transfer.recipient.address == walletAddress
        if (!outgoing && !incoming) return null
        val peer = if (outgoing) transfer.recipient.address else transfer.sender.address
        return TxInfo(
            txHash = event.eventId,
            timestampInMillis = event.timestamp * MILLIS_IN_SECOND,
            isOutgoing = outgoing,
            destinationType = TxInfo.DestinationType.Single(TxInfo.AddressType.User(transfer.recipient.address)),
            sourceType = TxInfo.SourceType.Single(transfer.sender.address),
            interactionAddressType = TxInfo.InteractionAddressType.User(peer),
            status = when {
                event.inProgress -> TxInfo.TransactionStatus.Unconfirmed
                status == "ok" -> TxInfo.TransactionStatus.Confirmed
                else -> TxInfo.TransactionStatus.Failed
            },
            type = TxInfo.TransactionType.Transfer,
            amount = BigDecimal(transfer.amount).movePointLeft(TON_DECIMALS),
        )
    }

    private fun requireRequestIsSupported(address: String, blockchain: Blockchain) {
        require(blockchain == Blockchain.TON || blockchain == Blockchain.TONTestnet) {
            "$blockchain is not supported by the TON fallback"
        }
        require(blockchain.validateAddress(address)) { "Invalid TON address" }
    }

    private fun Blockchain.baseUrl(): String = when (this) {
        Blockchain.TON -> "https://tonapi.io"
        Blockchain.TONTestnet -> "https://testnet.tonapi.io"
        else -> error("Unsupported TON network: $this")
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_PAGE_SIZE = 100
        const val TON_DECIMALS = 9
        const val MILLIS_IN_SECOND = 1_000L
    }
}

@JsonClass(generateAdapter = true)
internal data class TonAccountEventsResponse(
    val events: List<TonAccountEvent> = emptyList(),
    @Json(name = "next_from") val nextFrom: Long = 0,
)

@JsonClass(generateAdapter = true)
internal data class TonAccountEvent(
    @Json(name = "event_id") val eventId: String,
    val timestamp: Long,
    val actions: List<TonAction> = emptyList(),
    val account: TonAccountAddress,
    @Json(name = "in_progress") val inProgress: Boolean = false,
)

@JsonClass(generateAdapter = true)
internal data class TonAction(
    val type: String,
    val status: String,
    @Json(name = "TonTransfer") val tonTransfer: TonTransferAction? = null,
)

@JsonClass(generateAdapter = true)
internal data class TonTransferAction(
    val sender: TonAccountAddress,
    val recipient: TonAccountAddress,
    val amount: Long,
)

@JsonClass(generateAdapter = true)
internal data class TonAccountAddress(val address: String)
