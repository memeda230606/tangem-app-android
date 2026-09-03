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
import java.time.Instant
import javax.inject.Inject

/** Read-only native XRP payment history backed by the XRPL full-history public server. */
internal class XrpTxHistoryFallback @Inject constructor(
    @NetworkMoshi moshi: Moshi,
    private val dispatchers: CoroutineDispatcherProvider,
) {
    private val requestAdapter = moshi.adapter(XrpAccountTxRequest::class.java)
    private val responseAdapter = moshi.adapter(XrpAccountTxResponse::class.java)

    suspend fun getTransactionsCount(address: String, blockchain: Blockchain): Int = withContext(dispatchers.io) {
        requireRequestIsSupported(address, blockchain)
        if (requestTransactions(address, Page.Initial, 1).result.transactions.isEmpty()) 0 else 1
    }

    suspend fun getTransactions(
        address: String,
        blockchain: Blockchain,
        page: Page,
        pageSize: Int,
    ): KeylessTxHistoryPage = withContext(dispatchers.io) {
        requireRequestIsSupported(address, blockchain)
        if (page is Page.LastPage) return@withContext KeylessTxHistoryPage(emptyList(), Page.LastPage)
        convertResponse(requestTransactions(address, page, pageSize.coerceIn(1, MAX_PAGE_SIZE)), address)
    }

    internal fun convertResponse(response: XrpAccountTxResponse, walletAddress: String): KeylessTxHistoryPage {
        val result = response.result
        return KeylessTxHistoryPage(
            items = result.transactions.mapNotNull { it.toTxInfo(walletAddress) },
            nextPage = result.marker?.let { Page.Next("${it.ledger}:${it.seq}") } ?: Page.LastPage,
        )
    }

    private fun requestTransactions(address: String, page: Page, limit: Int): XrpAccountTxResponse {
        val marker = (page as? Page.Next)?.value?.split(':')?.takeIf { it.size == 2 }?.let {
            XrpMarker(ledger = it[0].toLongOrNull() ?: return@let null, seq = it[1].toLongOrNull() ?: return@let null)
        }
        val body = requestAdapter.toJson(
            XrpAccountTxRequest(
                method = "account_tx",
                params = listOf(
                    XrpAccountTxParams(
                        account = address,
                        limit = limit,
                        marker = marker,
                    ),
                ),
            ),
        )
        val connection = URL(BASE_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body) }
            if (connection.responseCode !in 200..299) {
                throw IOException("XRPL transaction history request failed with HTTP ${connection.responseCode}")
            }
            val response = requireNotNull(
                responseAdapter.fromJson(connection.inputStream.bufferedReader().use { it.readText() }),
            ) { "XRPL returned an empty response" }
            check(response.result.error == null) { "XRPL transaction history request failed: ${response.result.error}" }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun XrpAccountTransaction.toTxInfo(walletAddress: String): TxInfo? {
        val transaction = txJson ?: tx ?: return null
        if (transaction.transactionType != "Payment") return null
        val destination = transaction.destination ?: return null
        val deliveredDrops = (meta.deliveredAmount as? String)?.takeUnless { it == "unavailable" }
            ?: (transaction.deliverMax as? String)
            ?: (transaction.amount as? String)
            ?: return null
        val outgoing = transaction.account == walletAddress
        val source = transaction.account
        return TxInfo(
            txHash = hash ?: transaction.hash ?: return null,
            timestampInMillis = closeTimeIso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: transaction.date?.plus(RIPPLE_EPOCH_SECONDS)?.times(MILLIS_IN_SECOND)
                ?: return null,
            isOutgoing = outgoing,
            destinationType = TxInfo.DestinationType.Single(TxInfo.AddressType.User(destination)),
            sourceType = TxInfo.SourceType.Single(source),
            interactionAddressType = TxInfo.InteractionAddressType.User(if (outgoing) destination else source),
            status = when {
                !validated -> TxInfo.TransactionStatus.Unconfirmed
                meta.transactionResult == "tesSUCCESS" -> TxInfo.TransactionStatus.Confirmed
                else -> TxInfo.TransactionStatus.Failed
            },
            type = TxInfo.TransactionType.Transfer,
            amount = runCatching { BigDecimal(deliveredDrops).movePointLeft(XRP_DECIMALS) }.getOrNull() ?: return null,
        )
    }

    private fun requireRequestIsSupported(address: String, blockchain: Blockchain) {
        require(blockchain == Blockchain.XRP) { "$blockchain is not supported by the XRP fallback" }
        require(blockchain.validateAddress(address)) { "Invalid XRP address" }
    }

    private companion object {
        const val BASE_URL = "https://xrplcluster.com/"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_PAGE_SIZE = 100
        const val XRP_DECIMALS = 6
        const val RIPPLE_EPOCH_SECONDS = 946_684_800L
        const val MILLIS_IN_SECOND = 1_000L
    }
}

@JsonClass(generateAdapter = true)
internal data class XrpAccountTxRequest(
    val method: String,
    val params: List<XrpAccountTxParams>,
)

@JsonClass(generateAdapter = true)
internal data class XrpAccountTxParams(
    val account: String,
    @Json(name = "ledger_index_min") val ledgerIndexMin: Int = -1,
    @Json(name = "ledger_index_max") val ledgerIndexMax: Int = -1,
    val binary: Boolean = false,
    val forward: Boolean = false,
    val limit: Int,
    val marker: XrpMarker? = null,
    @Json(name = "api_version") val apiVersion: Int = 2,
)

@JsonClass(generateAdapter = true)
internal data class XrpMarker(val ledger: Long, val seq: Long)

@JsonClass(generateAdapter = true)
internal data class XrpAccountTxResponse(val result: XrpAccountTxResult)

@JsonClass(generateAdapter = true)
internal data class XrpAccountTxResult(
    val transactions: List<XrpAccountTransaction> = emptyList(),
    val marker: XrpMarker? = null,
    val error: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class XrpAccountTransaction(
    val hash: String? = null,
    @Json(name = "close_time_iso") val closeTimeIso: String? = null,
    @Json(name = "tx_json") val txJson: XrpTransaction? = null,
    val tx: XrpTransaction? = null,
    val meta: XrpMeta = XrpMeta(),
    val validated: Boolean = false,
)

@JsonClass(generateAdapter = true)
internal data class XrpTransaction(
    @Json(name = "Account") val account: String,
    @Json(name = "Destination") val destination: String? = null,
    @Json(name = "TransactionType") val transactionType: String,
    @Json(name = "DeliverMax") val deliverMax: Any? = null,
    @Json(name = "Amount") val amount: Any? = null,
    val hash: String? = null,
    val date: Long? = null,
)

@JsonClass(generateAdapter = true)
internal data class XrpMeta(
    @Json(name = "TransactionResult") val transactionResult: String? = null,
    @Json(name = "delivered_amount") val deliveredAmount: Any? = null,
)
