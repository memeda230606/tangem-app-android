package com.tangem.data.walletmanager

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.tangem.blockchain.common.Blockchain
import com.tangem.datasource.di.NetworkMoshi
import com.tangem.domain.models.currency.CryptoCurrency
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

/** Read-only transaction history fallback for EVM networks indexed by Routescan. */
internal class EvmTxHistoryFallback @Inject constructor(
    @NetworkMoshi moshi: Moshi,
    private val dispatchers: CoroutineDispatcherProvider,
) {
    private val transactionsAdapter = moshi.adapter(RoutescanTransactionsResponse::class.java)
    private val tokenTransfersAdapter = moshi.adapter(EtherscanTokenTransfersResponse::class.java)

    suspend fun getTransactionsCount(
        address: String,
        currency: CryptoCurrency,
        blockchain: Blockchain,
    ): Int = withContext(dispatchers.io) {
        requireRequestIsSupported(address = address, blockchain = blockchain)

        when (currency) {
            is CryptoCurrency.Coin -> requestTransactions(
                address = address,
                blockchain = blockchain,
                page = Page.Initial,
                limit = 1,
                includeCount = true,
            ).let { it.count ?: it.items.size }
            is CryptoCurrency.Token -> requestTokenTransfers(
                address = address,
                currency = currency,
                blockchain = blockchain,
                page = Page.Initial,
                limit = 1,
            ).result.size
        }
    }

    suspend fun getTransactions(
        address: String,
        currency: CryptoCurrency,
        blockchain: Blockchain,
        page: Page,
        pageSize: Int,
    ): KeylessTxHistoryPage = withContext(dispatchers.io) {
        requireRequestIsSupported(address = address, blockchain = blockchain)

        if (page is Page.LastPage) {
            return@withContext KeylessTxHistoryPage(emptyList(), Page.LastPage)
        }

        when (currency) {
            is CryptoCurrency.Coin -> {
                val response = requestTransactions(
                    address = address,
                    blockchain = blockchain,
                    page = page,
                    limit = pageSize.coerceIn(1, MAX_PAGE_SIZE),
                    includeCount = false,
                )
                KeylessTxHistoryPage(
                    items = response.items.mapNotNull {
                        it.toTxInfo(walletAddress = address, decimals = currency.decimals)
                    },
                    nextPage = response.link.toNextPage(),
                )
            }
            is CryptoCurrency.Token -> {
                val limit = pageSize.coerceIn(1, MAX_PAGE_SIZE)
                val response = requestTokenTransfers(
                    address = address,
                    currency = currency,
                    blockchain = blockchain,
                    page = page,
                    limit = limit,
                )
                KeylessTxHistoryPage(
                    items = response.result.mapNotNull {
                        it.toTxInfo(walletAddress = address, decimals = currency.decimals)
                    },
                    nextPage = if (response.result.size >= limit) {
                        Page.Next((page.toPageNumber() + 1).toString())
                    } else {
                        Page.LastPage
                    },
                )
            }
        }
    }

    internal fun convertTransactions(
        body: String,
        walletAddress: String,
        decimals: Int,
        pageSize: Int,
    ): List<TxInfo> {
        val response = requireNotNull(transactionsAdapter.fromJson(body)) { "Routescan returned an empty response" }

        return response.items
            .asSequence()
            .mapNotNull { it.toTxInfo(walletAddress = walletAddress, decimals = decimals) }
            .take(pageSize.coerceAtLeast(0))
            .toList()
    }

    internal fun convertTokenTransactions(body: String, walletAddress: String, decimals: Int): List<TxInfo> {
        val response = requireNotNull(tokenTransfersAdapter.fromJson(body)) {
            "Routescan returned an empty token response"
        }

        return response.result.mapNotNull { it.toTxInfo(walletAddress = walletAddress, decimals = decimals) }
    }

    private fun requestTransactions(
        address: String,
        blockchain: Blockchain,
        page: Page,
        limit: Int,
        includeCount: Boolean,
    ): RoutescanTransactionsResponse {
        val body = request(
            buildUrl(
                blockchain = blockchain,
                path = "address/$address/transactions",
                page = page,
                limit = limit,
                includeCount = includeCount,
                extraQuery = "sort=desc",
            ),
        )
        return requireNotNull(transactionsAdapter.fromJson(body)) { "Routescan returned an empty response" }
    }

    private fun requestTokenTransfers(
        address: String,
        currency: CryptoCurrency.Token,
        blockchain: Blockchain,
        page: Page,
        limit: Int,
    ): EtherscanTokenTransfersResponse {
        val network = if (blockchain.isTestnet()) "testnet" else "mainnet"
        val chainId = requireNotNull(blockchain.getChainId()) { "Missing EVM chain ID for $blockchain" }
        val query = listOf(
            "module=account",
            "action=tokentx",
            "address=${address.urlEncode()}",
            "contractaddress=${currency.contractAddress.urlEncode()}",
            "page=${page.toPageNumber()}",
            "offset=${limit.coerceIn(1, MAX_PAGE_SIZE)}",
            "startblock=0",
            "endblock=99999999",
            "sort=desc",
        ).joinToString(separator = "&")
        val body = request(URL("$BASE_URL/$network/evm/$chainId/etherscan/api?$query"))

        return requireNotNull(tokenTransfersAdapter.fromJson(body)) {
            "Routescan returned an empty response for ${currency.symbol}"
        }
    }

    private fun buildUrl(
        blockchain: Blockchain,
        path: String,
        page: Page,
        limit: Int,
        includeCount: Boolean,
        extraQuery: String,
    ): URL {
        val network = if (blockchain.isTestnet()) "testnet" else "mainnet"
        val chainId = requireNotNull(blockchain.getChainId()) { "Missing EVM chain ID for $blockchain" }
        val query = buildList {
            add("limit=${limit.coerceIn(1, MAX_PAGE_SIZE)}")
            add(extraQuery)
            if (includeCount) add("count=true")
            if (page is Page.Next) {
                add("next=${URLEncoder.encode(page.value, StandardCharsets.UTF_8.name())}")
            }
        }.joinToString(separator = "&")

        return URL("$BASE_URL/$network/evm/$chainId/$path?$query")
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
                throw IOException("Routescan transaction history request failed with HTTP $responseCode")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun requireRequestIsSupported(address: String, blockchain: Blockchain) {
        require(blockchain.isEvm()) { "$blockchain is not an EVM network" }
        require(blockchain.getChainId() != null) { "Missing EVM chain ID for $blockchain" }
        require(ETHEREUM_ADDRESS_REGEX.matches(address)) { "Invalid EVM address" }
    }

    private fun RoutescanTransaction.toTxInfo(walletAddress: String, decimals: Int): TxInfo? {
        val destination = to ?: return null
        val outgoing = from.equals(walletAddress, ignoreCase = true)

        return TxInfo(
            txHash = id,
            timestampInMillis = timestamp.toEpochMillisOrNull() ?: return null,
            isOutgoing = outgoing,
            destinationType = TxInfo.DestinationType.Single(TxInfo.AddressType.User(destination)),
            sourceType = TxInfo.SourceType.Single(from),
            interactionAddressType = TxInfo.InteractionAddressType.User(if (outgoing) destination else from),
            status = if (status) TxInfo.TransactionStatus.Confirmed else TxInfo.TransactionStatus.Failed,
            type = TxInfo.TransactionType.Transfer,
            amount = value.toAmountOrNull(decimals) ?: return null,
        )
    }

    private fun EtherscanTokenTransfer.toTxInfo(walletAddress: String, decimals: Int): TxInfo? {
        val outgoing = from.equals(walletAddress, ignoreCase = true)

        return TxInfo(
            txHash = hash,
            timestampInMillis = timeStamp.toLongOrNull()?.times(MILLIS_IN_SECOND) ?: return null,
            isOutgoing = outgoing,
            destinationType = TxInfo.DestinationType.Single(TxInfo.AddressType.User(to)),
            sourceType = TxInfo.SourceType.Single(from),
            interactionAddressType = TxInfo.InteractionAddressType.User(if (outgoing) to else from),
            status = TxInfo.TransactionStatus.Confirmed,
            type = TxInfo.TransactionType.Transfer,
            amount = value.toAmountOrNull(tokenDecimal.toIntOrNull() ?: decimals) ?: return null,
        )
    }

    private fun RoutescanLink.toNextPage(): Page = nextToken?.takeIf(String::isNotBlank)?.let(Page::Next)
        ?: Page.LastPage

    private fun String.toEpochMillisOrNull(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun Page.toPageNumber(): Int = (this as? Page.Next)?.value?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    private fun String.toAmountOrNull(decimals: Int): BigDecimal? = runCatching {
        BigDecimal(this).movePointLeft(decimals)
    }.getOrNull()

    private companion object {
        const val BASE_URL = "https://api.routescan.io/v2/network"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_PAGE_SIZE = 100
        const val MILLIS_IN_SECOND = 1_000L
        val ETHEREUM_ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")
    }
}

@JsonClass(generateAdapter = true)
internal data class RoutescanTransactionsResponse(
    val items: List<RoutescanTransaction> = emptyList(),
    val count: Int? = null,
    val link: RoutescanLink = RoutescanLink(),
)

@JsonClass(generateAdapter = true)
internal data class RoutescanTransaction(
    val id: String,
    val timestamp: String,
    val status: Boolean,
    val value: String,
    val from: String,
    val to: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class EtherscanTokenTransfersResponse(
    val status: String,
    val message: String,
    val result: List<EtherscanTokenTransfer> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class EtherscanTokenTransfer(
    val hash: String,
    val timeStamp: String,
    val value: String,
    val from: String,
    val to: String,
    val contractAddress: String,
    val tokenDecimal: String,
)

@JsonClass(generateAdapter = true)
internal data class RoutescanLink(
    val nextToken: String? = null,
)
