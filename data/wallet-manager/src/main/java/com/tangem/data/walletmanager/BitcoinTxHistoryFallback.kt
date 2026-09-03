package com.tangem.data.walletmanager

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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

/** Read-only Bitcoin transaction history fallback backed by the public mempool.space API. */
internal class BitcoinTxHistoryFallback @Inject constructor(
    @NetworkMoshi moshi: Moshi,
    private val dispatchers: CoroutineDispatcherProvider,
) {
    private val addressAdapter = moshi.adapter(MempoolAddressResponse::class.java)
    private val transactionsAdapter = moshi.adapter<List<MempoolTransaction>>(
        Types.newParameterizedType(List::class.java, MempoolTransaction::class.java),
    )

    suspend fun getTransactionsCount(address: String, blockchain: Blockchain): Int = withContext(dispatchers.io) {
        requireRequestIsSupported(address = address, blockchain = blockchain)
        val body = request(URL("${blockchain.baseUrl()}/address/$address"))
        val response = requireNotNull(addressAdapter.fromJson(body)) { "Mempool returned an empty address response" }

        response.chainStats.txCount + response.mempoolStats.txCount
    }

    suspend fun getTransactions(
        address: String,
        blockchain: Blockchain,
        page: Page,
    ): KeylessTxHistoryPage = withContext(dispatchers.io) {
        requireRequestIsSupported(address = address, blockchain = blockchain)

        if (page is Page.LastPage) {
            return@withContext KeylessTxHistoryPage(emptyList(), Page.LastPage)
        }

        val pagePath = when (page) {
            Page.Initial -> "txs"
            is Page.Next -> "txs/chain/${page.value.urlEncode()}"
            Page.LastPage -> error("Handled above")
        }
        val body = request(URL("${blockchain.baseUrl()}/address/$address/$pagePath"))
        val transactions = requireNotNull(transactionsAdapter.fromJson(body)) {
            "Mempool returned an empty transactions response"
        }

        convertTransactions(
            transactions = transactions,
            walletAddress = address,
            nowMillis = System.currentTimeMillis(),
        )
    }

    internal fun convertTransactions(
        transactions: List<MempoolTransaction>,
        walletAddress: String,
        nowMillis: Long,
    ): KeylessTxHistoryPage {
        val confirmedTransactions = transactions.filter { it.status.confirmed }
        val nextPage = if (confirmedTransactions.size >= CONFIRMED_PAGE_SIZE) {
            Page.Next(confirmedTransactions.last().txid)
        } else {
            Page.LastPage
        }

        return KeylessTxHistoryPage(
            items = transactions.mapNotNull {
                it.toTxInfo(walletAddress = walletAddress, nowMillis = nowMillis)
            },
            nextPage = nextPage,
        )
    }

    private fun request(url: URL): String {
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Mempool transaction history request failed with HTTP $responseCode")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun requireRequestIsSupported(address: String, blockchain: Blockchain) {
        require(blockchain in SUPPORTED_BLOCKCHAINS) { "$blockchain is not supported by the Bitcoin fallback" }
        require(blockchain.validateAddress(address)) { "Invalid Bitcoin address" }
    }

    private fun Blockchain.baseUrl(): String = when (this) {
        Blockchain.Bitcoin -> MAINNET_BASE_URL
        Blockchain.BitcoinTestnet -> TESTNET_BASE_URL
        else -> error("Unsupported Bitcoin network: $this")
    }

    private fun MempoolTransaction.toTxInfo(walletAddress: String, nowMillis: Long): TxInfo? {
        val inputAddresses = vin.mapNotNull { it.prevout?.scriptpubkeyAddress }.distinct()
        val outputsWithAddresses = vout.mapNotNull { output ->
            output.scriptpubkeyAddress?.let { address -> address to output.value }
        }
        val outputAddresses = outputsWithAddresses.map { it.first }.distinct()
        val walletInput = vin.sumOf { input ->
            input.prevout?.takeIf { it.scriptpubkeyAddress == walletAddress }?.value ?: 0L
        }
        val walletOutput = outputsWithAddresses.sumOf { (address, value) ->
            if (address == walletAddress) value else 0L
        }
        val outgoing = walletInput > 0L
        val interactionAddresses = if (outgoing) {
            outputAddresses.filterNot { it == walletAddress }.ifEmpty { listOf(walletAddress) }
        } else {
            inputAddresses.ifEmpty { listOf(COINBASE_SOURCE) }
        }
        val destinations = if (outgoing) {
            outputAddresses.filterNot { it == walletAddress }.ifEmpty { listOf(walletAddress) }
        } else {
            listOf(walletAddress)
        }
        val transferredSatoshis = if (outgoing) walletInput - walletOutput else walletOutput

        if (transferredSatoshis < 0L || destinations.isEmpty()) return null

        return TxInfo(
            txHash = txid,
            timestampInMillis = status.blockTime?.times(MILLIS_IN_SECOND) ?: nowMillis,
            isOutgoing = outgoing,
            destinationType = destinations.toDestinationType(),
            sourceType = inputAddresses.ifEmpty { listOf(COINBASE_SOURCE) }.toSourceType(),
            interactionAddressType = interactionAddresses.toInteractionAddressType(),
            status = if (status.confirmed) {
                TxInfo.TransactionStatus.Confirmed
            } else {
                TxInfo.TransactionStatus.Unconfirmed
            },
            type = TxInfo.TransactionType.Transfer,
            amount = BigDecimal(transferredSatoshis).movePointLeft(BITCOIN_DECIMALS),
        )
    }

    private fun List<String>.toDestinationType(): TxInfo.DestinationType {
        val addressTypes = map(TxInfo.AddressType::User)
        return if (addressTypes.size == 1) {
            TxInfo.DestinationType.Single(addressTypes.single())
        } else {
            TxInfo.DestinationType.Multiple(addressTypes)
        }
    }

    private fun List<String>.toSourceType(): TxInfo.SourceType = if (size == 1) {
        TxInfo.SourceType.Single(single())
    } else {
        TxInfo.SourceType.Multiple(this)
    }

    private fun List<String>.toInteractionAddressType(): TxInfo.InteractionAddressType = if (size == 1) {
        TxInfo.InteractionAddressType.User(single())
    } else {
        TxInfo.InteractionAddressType.Multiple(this)
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private companion object {
        const val MAINNET_BASE_URL = "https://mempool.space/api"
        const val TESTNET_BASE_URL = "https://mempool.space/testnet/api"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val CONFIRMED_PAGE_SIZE = 25
        const val BITCOIN_DECIMALS = 8
        const val MILLIS_IN_SECOND = 1_000L
        const val COINBASE_SOURCE = "Coinbase"
        val SUPPORTED_BLOCKCHAINS = setOf(Blockchain.Bitcoin, Blockchain.BitcoinTestnet)
    }
}

@JsonClass(generateAdapter = true)
internal data class MempoolAddressResponse(
    @com.squareup.moshi.Json(name = "chain_stats") val chainStats: MempoolAddressStats,
    @com.squareup.moshi.Json(name = "mempool_stats") val mempoolStats: MempoolAddressStats,
)

@JsonClass(generateAdapter = true)
internal data class MempoolAddressStats(
    @com.squareup.moshi.Json(name = "tx_count") val txCount: Int,
)

@JsonClass(generateAdapter = true)
internal data class MempoolTransaction(
    val txid: String,
    val vin: List<MempoolInput> = emptyList(),
    val vout: List<MempoolOutput> = emptyList(),
    val status: MempoolTransactionStatus,
)

@JsonClass(generateAdapter = true)
internal data class MempoolInput(
    val prevout: MempoolOutput? = null,
)

@JsonClass(generateAdapter = true)
internal data class MempoolOutput(
    @com.squareup.moshi.Json(name = "scriptpubkey_address") val scriptpubkeyAddress: String? = null,
    val value: Long,
)

@JsonClass(generateAdapter = true)
internal data class MempoolTransactionStatus(
    val confirmed: Boolean,
    @com.squareup.moshi.Json(name = "block_time") val blockTime: Long? = null,
)
