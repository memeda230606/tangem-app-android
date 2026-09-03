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
import java.time.Instant
import javax.inject.Inject

/** Read-only transaction history fallback for Bitcoin-like networks not reliably covered by the SDK. */
internal class BlockbookTxHistoryFallback @Inject constructor(
    @NetworkMoshi moshi: Moshi,
    private val dispatchers: CoroutineDispatcherProvider,
) {
    private val blockCypherAdapter = moshi.adapter(BlockCypherAddressResponse::class.java)
    private val threeXplAdapter = moshi.adapter(ThreeXplAddressResponse::class.java)

    suspend fun getTransactionsCount(address: String, blockchain: Blockchain): Int = withContext(dispatchers.io) {
        requireRequestIsSupported(address, blockchain)
        when (blockchain) {
            Blockchain.Litecoin, Blockchain.Dogecoin -> {
                requestBlockCypherAddress(address, blockchain, Page.Initial, pageSize = 1).transactionCount
            }
            Blockchain.BitcoinCash -> {
                requestThreeXplAddress(address, Page.Initial, pageSize = 1)
                    .data.address?.events?.get(THREE_XPL_MODULE)
                    ?: 0
            }
            else -> error("Unsupported Bitcoin-like network: $blockchain")
        }
    }

    suspend fun getTransactions(
        address: String,
        blockchain: Blockchain,
        page: Page,
        pageSize: Int,
    ): KeylessTxHistoryPage = withContext(dispatchers.io) {
        requireRequestIsSupported(address, blockchain)
        if (page is Page.LastPage) return@withContext KeylessTxHistoryPage(emptyList(), Page.LastPage)

        when (blockchain) {
            Blockchain.Litecoin, Blockchain.Dogecoin -> convertBlockCypherResponse(
                response = requestBlockCypherAddress(
                    address = address,
                    blockchain = blockchain,
                    page = page,
                    pageSize = pageSize.coerceIn(1, BLOCKCYPHER_MAX_PAGE_SIZE),
                ),
                walletAddress = address,
                decimals = blockchain.decimals(),
            )
            Blockchain.BitcoinCash -> {
                val threeXplPageSize = pageSize.toThreeXplPageSize()
                convertThreeXplResponse(
                    response = requestThreeXplAddress(address, page, threeXplPageSize),
                    walletAddress = address,
                    decimals = blockchain.decimals(),
                    currentOffset = page.toThreeXplOffset(),
                    pageSize = threeXplPageSize,
                )
            }
            else -> error("Unsupported Bitcoin-like network: $blockchain")
        }
    }

    internal fun convertResponse(
        response: BlockbookAddressResponse,
        walletAddress: String,
        decimals: Int,
        nextPageNumber: Int,
    ): KeylessTxHistoryPage = KeylessTxHistoryPage(
        items = response.transactions.mapNotNull { it.toTxInfo(walletAddress, decimals) },
        nextPage = if (response.page < response.totalPages) Page.Next(nextPageNumber.toString()) else Page.LastPage,
    )

    internal fun convertBlockCypherResponse(
        response: BlockCypherAddressResponse,
        walletAddress: String,
        decimals: Int,
    ): KeylessTxHistoryPage = KeylessTxHistoryPage(
        items = response.transactions.mapNotNull { it.toTxInfo(walletAddress, decimals) },
        nextPage = response.transactions.lastOrNull()?.blockHeight
            ?.takeIf { response.hasMore && it > 0 }
            ?.let { Page.Next("$BLOCKCYPHER_PAGE_PREFIX$it") }
            ?: Page.LastPage,
    )

    private fun requestBlockCypherAddress(
        address: String,
        blockchain: Blockchain,
        page: Page,
        pageSize: Int,
    ): BlockCypherAddressResponse {
        val encodedAddress = URLEncoder.encode(address, StandardCharsets.UTF_8.name())
        val beforeHeight = (page as? Page.Next)?.value
            ?.removePrefix(BLOCKCYPHER_PAGE_PREFIX)
            ?.toIntOrNull()
            ?.let { "&before=$it" }
            .orEmpty()
        val url = URL(
            "https://api.blockcypher.com/v1/${blockchain.blockCypherCoin()}/main/addrs/" +
                "$encodedAddress/full?limit=$pageSize$beforeHeight",
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                throw IOException("BlockCypher transaction history request failed with HTTP ${connection.responseCode}")
            }
            return requireNotNull(
                blockCypherAdapter.fromJson(connection.inputStream.bufferedReader().use { it.readText() }),
            ) { "BlockCypher returned an empty response" }
        } finally {
            connection.disconnect()
        }
    }

    internal fun convertThreeXplResponse(
        response: ThreeXplAddressResponse,
        walletAddress: String,
        decimals: Int,
        currentOffset: Int,
        pageSize: Int,
    ): KeylessTxHistoryPage {
        val events = response.data.events[THREE_XPL_MODULE].orEmpty()
        return KeylessTxHistoryPage(
            items = events.mapNotNull { it.toTxInfo(walletAddress, decimals) },
            nextPage = if (events.size >= pageSize) {
                Page.Next("$THREE_XPL_PAGE_PREFIX${currentOffset + pageSize}")
            } else {
                Page.LastPage
            },
        )
    }

    private fun requestThreeXplAddress(
        address: String,
        page: Page,
        pageSize: Int,
    ): ThreeXplAddressResponse {
        val encodedAddress = URLEncoder.encode(address.substringAfter(':'), StandardCharsets.UTF_8.name())
        val offset = page.toThreeXplOffset()
        val url = URL(
            "https://api.3xpl.com/bitcoin-cash/address/$encodedAddress" +
                "?data=address,events&from=$THREE_XPL_MODULE&limit=$pageSize&page=-$offset" +
                "&token=$THREE_XPL_PUBLIC_TEST_TOKEN",
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) {
                throw IOException("3xpl transaction history request failed with HTTP ${connection.responseCode}")
            }
            return requireNotNull(
                threeXplAdapter.fromJson(connection.inputStream.bufferedReader().use { it.readText() }),
            ) { "3xpl returned an empty response" }
        } finally {
            connection.disconnect()
        }
    }

    private fun BlockbookTransaction.toTxInfo(walletAddress: String, decimals: Int): TxInfo? {
        val normalizedWallet = walletAddress.normalizedAddress()
        val inputAddresses = vin.flatMap(BlockbookIo::addresses).distinct()
        val outputAddresses = vout.flatMap(BlockbookIo::addresses).distinct()
        val walletInput = vin.filter { io -> io.addresses.any { it.normalizedAddress() == normalizedWallet } }
            .sumOfDecimal(BlockbookIo::value)
        val walletOutput = vout.filter { io -> io.addresses.any { it.normalizedAddress() == normalizedWallet } }
            .sumOfDecimal(BlockbookIo::value)
        val outgoing = walletInput.signum() > 0
        val peers = if (outgoing) {
            outputAddresses.filterNot { it.normalizedAddress() == normalizedWallet }
        } else {
            inputAddresses.filterNot { it.normalizedAddress() == normalizedWallet }
        }.ifEmpty { listOf(walletAddress) }
        val destinations = if (outgoing) peers else listOf(walletAddress)
        val sources = inputAddresses.ifEmpty { listOf(COINBASE_SOURCE) }
        val value = if (outgoing) walletInput.subtract(walletOutput) else walletOutput
        if (value.signum() < 0) return null

        return TxInfo(
            txHash = txid,
            timestampInMillis = blockTime?.times(MILLIS_IN_SECOND) ?: 0L,
            isOutgoing = outgoing,
            destinationType = destinations.toDestinationType(),
            sourceType = sources.toSourceType(),
            interactionAddressType = peers.toInteractionAddressType(),
            status = if (confirmations > 0) TxInfo.TransactionStatus.Confirmed else TxInfo.TransactionStatus.Unconfirmed,
            type = TxInfo.TransactionType.Transfer,
            amount = value.movePointLeft(decimals),
        )
    }

    private fun BlockCypherTransaction.toTxInfo(walletAddress: String, decimals: Int): TxInfo? {
        val normalizedWallet = walletAddress.normalizedAddress()
        val inputAddresses = inputs.flatMap(BlockCypherInput::addresses).distinct()
        val outputAddresses = outputs.flatMap(BlockCypherOutput::addresses).distinct()
        val walletInput = inputs
            .filter { input -> input.addresses.any { it.normalizedAddress() == normalizedWallet } }
            .fold(BigDecimal.ZERO) { total, input -> total.add(input.outputValue.toBigDecimal()) }
        val walletOutput = outputs
            .filter { output -> output.addresses.any { it.normalizedAddress() == normalizedWallet } }
            .fold(BigDecimal.ZERO) { total, output -> total.add(output.value.toBigDecimal()) }
        val outgoing = walletInput.signum() > 0
        val peers = if (outgoing) {
            outputAddresses.filterNot { it.normalizedAddress() == normalizedWallet }
        } else {
            inputAddresses.filterNot { it.normalizedAddress() == normalizedWallet }
        }.ifEmpty { listOf(walletAddress) }
        val destinations = if (outgoing) peers else listOf(walletAddress)
        val sources = inputAddresses.ifEmpty { listOf(COINBASE_SOURCE) }
        val value = if (outgoing) walletInput.subtract(walletOutput) else walletOutput
        if (value.signum() < 0) return null

        return TxInfo(
            txHash = hash,
            timestampInMillis = (confirmed ?: received)
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                ?: 0L,
            isOutgoing = outgoing,
            destinationType = destinations.toDestinationType(),
            sourceType = sources.toSourceType(),
            interactionAddressType = peers.toInteractionAddressType(),
            status = if (confirmed != null && (blockHeight ?: 0) > 0) {
                TxInfo.TransactionStatus.Confirmed
            } else {
                TxInfo.TransactionStatus.Unconfirmed
            },
            type = TxInfo.TransactionType.Transfer,
            amount = value.movePointLeft(decimals),
        )
    }

    private fun ThreeXplAddressEvent.toTxInfo(walletAddress: String, decimals: Int): TxInfo? {
        val rawValue = effect.toBigDecimalOrNull() ?: return null
        val outgoing = rawValue.signum() < 0
        val peer = UNKNOWN_PEER
        return TxInfo(
            txHash = transaction,
            timestampInMillis = runCatching { Instant.parse(time).toEpochMilli() }.getOrNull() ?: 0L,
            isOutgoing = outgoing,
            destinationType = TxInfo.DestinationType.Single(
                TxInfo.AddressType.User(if (outgoing) peer else walletAddress),
            ),
            sourceType = TxInfo.SourceType.Single(if (outgoing) walletAddress else peer),
            interactionAddressType = TxInfo.InteractionAddressType.User(peer),
            status = if (failed) TxInfo.TransactionStatus.Failed else TxInfo.TransactionStatus.Confirmed,
            type = TxInfo.TransactionType.Transfer,
            amount = rawValue.abs().movePointLeft(decimals),
        )
    }

    private fun Iterable<BlockbookIo>.sumOfDecimal(selector: (BlockbookIo) -> String): BigDecimal =
        fold(BigDecimal.ZERO) { total, item -> total.add(selector(item).toBigDecimalOrNull() ?: BigDecimal.ZERO) }

    private fun List<String>.toDestinationType(): TxInfo.DestinationType {
        val values = map(TxInfo.AddressType::User)
        return if (values.size == 1) TxInfo.DestinationType.Single(values.single()) else TxInfo.DestinationType.Multiple(values)
    }

    private fun List<String>.toSourceType(): TxInfo.SourceType =
        if (size == 1) TxInfo.SourceType.Single(single()) else TxInfo.SourceType.Multiple(this)

    private fun List<String>.toInteractionAddressType(): TxInfo.InteractionAddressType =
        if (size == 1) TxInfo.InteractionAddressType.User(single()) else TxInfo.InteractionAddressType.Multiple(this)

    private fun String.normalizedAddress(): String = substringAfter(':').lowercase()
    private fun Page.toPageNumber(): Int = (this as? Page.Next)?.value?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    private fun Page.toThreeXplOffset(): Int = (this as? Page.Next)?.value
        ?.removePrefix(THREE_XPL_PAGE_PREFIX)
        ?.toIntOrNull()
        ?.coerceAtLeast(0)
        ?: 0
    private fun Int.toThreeXplPageSize(): Int = when {
        this <= 1 -> 1
        this <= 10 -> 10
        else -> 100
    }

    private fun requireRequestIsSupported(address: String, blockchain: Blockchain) {
        require(blockchain in SUPPORTED_BLOCKCHAINS) { "$blockchain is not supported by Blockbook fallback" }
        require(blockchain.validateAddress(address)) { "Invalid $blockchain address" }
    }

    private fun Blockchain.blockCypherCoin(): String = when (this) {
        Blockchain.Litecoin -> "ltc"
        Blockchain.Dogecoin -> "doge"
        else -> error("Unsupported BlockCypher network: $this")
    }

    internal companion object {
        val SUPPORTED_BLOCKCHAINS = setOf(Blockchain.Litecoin, Blockchain.Dogecoin, Blockchain.BitcoinCash)
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val BLOCKCYPHER_MAX_PAGE_SIZE = 50
        private const val BLOCKCYPHER_PAGE_PREFIX = "blockcypher:"
        private const val THREE_XPL_MODULE = "bitcoin-cash-main"
        private const val THREE_XPL_PAGE_PREFIX = "3xpl:"
        private const val THREE_XPL_PUBLIC_TEST_TOKEN = "3A0_t3st3xplor3rpub11cb3t4efcd21748a5e"
        private const val MILLIS_IN_SECOND = 1_000L
        private const val COINBASE_SOURCE = "Coinbase"
        private const val UNKNOWN_PEER = "Unknown"
    }
}

@JsonClass(generateAdapter = true)
internal data class BlockbookAddressResponse(
    val page: Int = 1,
    val totalPages: Int = 1,
    val txs: Int = 0,
    val transactions: List<BlockbookTransaction> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class BlockbookTransaction(
    val txid: String,
    val vin: List<BlockbookIo> = emptyList(),
    val vout: List<BlockbookIo> = emptyList(),
    val confirmations: Int = 0,
    val blockTime: Long? = null,
)

@JsonClass(generateAdapter = true)
internal data class BlockbookIo(
    val addresses: List<String> = emptyList(),
    val value: String = "0",
)

@JsonClass(generateAdapter = true)
internal data class BlockCypherAddressResponse(
    @Json(name = "n_tx") val transactionCount: Int = 0,
    @Json(name = "hasMore") val hasMore: Boolean = false,
    @Json(name = "txs") val transactions: List<BlockCypherTransaction> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class BlockCypherTransaction(
    val hash: String,
    @Json(name = "block_height") val blockHeight: Int? = null,
    val confirmed: String? = null,
    val received: String? = null,
    val inputs: List<BlockCypherInput> = emptyList(),
    val outputs: List<BlockCypherOutput> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class BlockCypherInput(
    val addresses: List<String> = emptyList(),
    @Json(name = "output_value") val outputValue: Long = 0,
)

@JsonClass(generateAdapter = true)
internal data class BlockCypherOutput(
    val addresses: List<String> = emptyList(),
    val value: Long = 0,
)

@JsonClass(generateAdapter = true)
internal data class ThreeXplAddressResponse(val data: ThreeXplAddressData = ThreeXplAddressData())

@JsonClass(generateAdapter = true)
internal data class ThreeXplAddressData(
    val address: ThreeXplAddressSummary? = null,
    val events: Map<String, List<ThreeXplAddressEvent>> = emptyMap(),
)

@JsonClass(generateAdapter = true)
internal data class ThreeXplAddressSummary(
    val events: Map<String, Int> = emptyMap(),
    val balances: Map<String, Int> = emptyMap(),
)

@JsonClass(generateAdapter = true)
internal data class ThreeXplAddressEvent(
    val transaction: String,
    val time: String,
    val effect: String,
    val failed: Boolean = false,
)
