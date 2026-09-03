package com.tangem.data.walletmanager

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.tangem.blockchain.common.Blockchain
import com.tangem.datasource.di.NetworkMoshi
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.min

/**
 * Read-only balance fallback for networks whose SDK providers require credentials.
 *
 * Requests are deduplicated per address, bounded per process, cached briefly and backed off after failures. A fallback
 * result is used only after a provider has returned and parsed an explicit balance; transport failures never become a
 * zero balance.
 */
internal class ExternalNetworkBalanceFallback @Inject constructor(
    @NetworkMoshi moshi: Moshi,
    private val dispatchers: CoroutineDispatcherProvider,
) {
    private val threeXplAdapter = moshi.adapter(ThreeXplBalanceResponse::class.java)
    private val threeXplAddressAdapter = moshi.adapter(ThreeXplAddressResponse::class.java)
    private val cardanoAdapter = moshi.adapter<List<CardanoAddressInfo>>(
        Types.newParameterizedType(List::class.java, CardanoAddressInfo::class.java),
    )
    private val cardanoRequestAdapter = moshi.adapter(CardanoAddressRequest::class.java)
    private val tonAdapter = moshi.adapter(TonAccountInfo::class.java)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val failures = ConcurrentHashMap<String, FailureEntry>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val requestSlots = Semaphore(MAX_CONCURRENT_REQUESTS)

    fun supports(blockchain: Blockchain): Boolean = blockchain in SUPPORTED_BLOCKCHAINS

    suspend fun getBalance(address: String, blockchain: Blockchain): BigDecimal = withContext(dispatchers.io) {
        require(supports(blockchain)) { "$blockchain is not supported by the balance fallback" }
        require(blockchain.validateAddress(address)) { "Invalid $blockchain address" }

        val key = "${blockchain.id}:$address"
        getFreshCachedValue(key)?.let { return@withContext it }

        locks.getOrPut(key, ::Mutex).withLock {
            getFreshCachedValue(key)?.let { return@withLock it }
            enforceBackoff(key)

            try {
                val balance = requestSlots.withPermit {
                    when (blockchain) {
                        Blockchain.BitcoinCash -> requestBitcoinCashBalance(address)
                        Blockchain.Cardano -> requestCardanoBalance(address)
                        Blockchain.TON -> requestTonBalance(address)
                        else -> error("Unsupported balance fallback network: $blockchain")
                    }
                }

                cache[key] = CacheEntry(
                    value = balance,
                    expiresAtMillis = System.currentTimeMillis() + CACHE_TTL_MILLIS,
                )
                failures.remove(key)
                balance
            } catch (error: Throwable) {
                rememberFailure(key)
                throw error
            }
        }
    }

    private fun requestBitcoinCashBalance(address: String): BigDecimal {
        val encodedAddress = address.substringAfter(':').urlEncode()
        val response = get(
            "$THREE_XPL_BASE_URL/bitcoin-cash/address/$encodedAddress" +
                "?data=address,balances&from=$THREE_XPL_MODULE&limit=1&token=$THREE_XPL_PUBLIC_TEST_TOKEN",
            serviceName = "3xpl BCH balance",
        )
        val addressSummary = threeXplAddressAdapter.fromJson(response)?.data?.address
        if (addressSummary?.balances?.get(THREE_XPL_MODULE) == 0) return BigDecimal.ZERO

        val body = requireNotNull(threeXplAdapter.fromJson(response)) { "3xpl returned an empty balance response" }
        val rawBalance = body.data.balances[THREE_XPL_MODULE]
            ?.get(THREE_XPL_CURRENCY)
            ?.balance
            ?: throw IOException("3xpl response did not contain a BCH balance")

        return rawBalance.toExternalCoinBalance(Blockchain.BitcoinCash.decimals())
    }

    private fun requestCardanoBalance(address: String): BigDecimal {
        val body = cardanoRequestAdapter.toJson(CardanoAddressRequest(listOf(address)))
        val response = post(
            url = "$KOIOS_BASE_URL/address_info",
            body = body,
            serviceName = "Koios Cardano balance",
        )
        val addresses = requireNotNull(cardanoAdapter.fromJson(response)) {
            "Koios returned an empty balance response"
        }

        // Koios returns an empty list only when the address has never appeared on-chain.
        return (addresses.firstOrNull()?.balance ?: "0").toExternalCoinBalance(Blockchain.Cardano.decimals())
    }

    private fun requestTonBalance(address: String): BigDecimal {
        val response = get(
            "$TON_API_BASE_URL/v2/accounts/${address.urlEncode()}",
            serviceName = "TonAPI balance",
        )
        val rawBalance = requireNotNull(tonAdapter.fromJson(response)?.balance) {
            "TonAPI response did not contain a TON balance"
        }

        return rawBalance.toString().toExternalCoinBalance(Blockchain.TON.decimals())
    }

    private fun get(url: String, serviceName: String): String = request(url, serviceName, method = "GET")

    private fun post(url: String, body: String, serviceName: String): String =
        request(url, serviceName, method = "POST", body = body)

    private fun request(url: String, serviceName: String, method: String, body: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter().use { it.write(body) }
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("$serviceName request failed with HTTP $responseCode")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun getFreshCachedValue(key: String): BigDecimal? {
        val entry = cache[key] ?: return null
        if (entry.expiresAtMillis > System.currentTimeMillis()) return entry.value

        cache.remove(key, entry)
        return null
    }

    private fun enforceBackoff(key: String) {
        val failure = failures[key] ?: return
        val remaining = failure.retryAtMillis - System.currentTimeMillis()
        if (remaining > 0) {
            throw IOException("Balance fallback is cooling down for ${remaining}ms")
        }
    }

    private fun rememberFailure(key: String) {
        val attempt = (failures[key]?.attempt ?: 0) + 1
        val shift = min(attempt - 1, MAX_BACKOFF_SHIFT)
        val delay = min(INITIAL_BACKOFF_MILLIS shl shift, MAX_BACKOFF_MILLIS)
        failures[key] = FailureEntry(attempt = attempt, retryAtMillis = System.currentTimeMillis() + delay)
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private data class CacheEntry(val value: BigDecimal, val expiresAtMillis: Long)
    private data class FailureEntry(val attempt: Int, val retryAtMillis: Long)

    private companion object {
        val SUPPORTED_BLOCKCHAINS = setOf(Blockchain.BitcoinCash, Blockchain.Cardano, Blockchain.TON)

        const val THREE_XPL_BASE_URL = "https://api.3xpl.com"
        const val THREE_XPL_MODULE = "bitcoin-cash-main"
        const val THREE_XPL_CURRENCY = "bitcoin-cash"
        const val THREE_XPL_PUBLIC_TEST_TOKEN = "3A0_t3st3xplor3rpub11cb3t4efcd21748a5e"
        const val KOIOS_BASE_URL = "https://api.koios.rest/api/v1"
        const val TON_API_BASE_URL = "https://tonapi.io"

        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val CACHE_TTL_MILLIS = 60_000L
        const val INITIAL_BACKOFF_MILLIS = 5_000L
        const val MAX_BACKOFF_MILLIS = 5 * 60_000L
        const val MAX_BACKOFF_SHIFT = 6
        const val MAX_CONCURRENT_REQUESTS = 4
    }
}

@JsonClass(generateAdapter = true)
internal data class ThreeXplBalanceResponse(val data: ThreeXplBalanceData = ThreeXplBalanceData())

@JsonClass(generateAdapter = true)
internal data class ThreeXplBalanceData(
    val balances: Map<String, Map<String, ThreeXplCurrencyBalance>> = emptyMap(),
)

@JsonClass(generateAdapter = true)
internal data class ThreeXplCurrencyBalance(val balance: String)

@JsonClass(generateAdapter = true)
internal data class CardanoAddressInfo(val balance: String)

@JsonClass(generateAdapter = true)
internal data class TonAccountInfo(@Json(name = "balance") val balance: Long?)

internal fun String.toExternalCoinBalance(decimals: Int): BigDecimal =
    toBigDecimalOrNull()?.movePointLeft(decimals)
        ?: throw IOException("Balance provider returned an invalid numeric value")
