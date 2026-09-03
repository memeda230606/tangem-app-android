package com.tangem.data.walletmanager

import com.squareup.moshi.Json
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
import javax.inject.Inject

/** Read-only native ADA transaction history backed by the public Koios query layer. */
internal class CardanoTxHistoryFallback @Inject constructor(
    @NetworkMoshi moshi: Moshi,
    private val dispatchers: CoroutineDispatcherProvider,
) {
    private val addressRequestAdapter = moshi.adapter(CardanoAddressRequest::class.java)
    private val hashesRequestAdapter = moshi.adapter(CardanoHashesRequest::class.java)
    private val addressTransactionsAdapter = moshi.adapter<List<CardanoAddressTransaction>>(
        Types.newParameterizedType(List::class.java, CardanoAddressTransaction::class.java),
    )
    private val transactionInfoAdapter = moshi.adapter<List<CardanoTransactionInfo>>(
        Types.newParameterizedType(List::class.java, CardanoTransactionInfo::class.java),
    )

    suspend fun getTransactionsCount(address: String, blockchain: Blockchain): Int = withContext(dispatchers.io) {
        requireRequestIsSupported(address, blockchain)
        if (requestAddressTransactions(address, offset = 0, limit = 1).isEmpty()) 0 else 1
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
        val offset = (page as? Page.Next)?.value?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val addressTransactions = requestAddressTransactions(address, offset, limit)
        val details = if (addressTransactions.isEmpty()) {
            emptyList()
        } else {
            requestTransactionInfo(addressTransactions.map(CardanoAddressTransaction::txHash))
        }
        convertTransactions(
            transactions = details,
            walletAddress = address,
            hasNextPage = addressTransactions.size >= limit,
            nextOffset = offset + addressTransactions.size,
        )
    }

    internal fun convertTransactions(
        transactions: List<CardanoTransactionInfo>,
        walletAddress: String,
        hasNextPage: Boolean,
        nextOffset: Int,
    ): KeylessTxHistoryPage = KeylessTxHistoryPage(
        items = transactions.mapNotNull { it.toTxInfo(walletAddress) },
        nextPage = if (hasNextPage) Page.Next(nextOffset.toString()) else Page.LastPage,
    )

    private fun requestAddressTransactions(address: String, offset: Int, limit: Int): List<CardanoAddressTransaction> {
        val body = addressRequestAdapter.toJson(CardanoAddressRequest(listOf(address)))
        return requireNotNull(
            addressTransactionsAdapter.fromJson(
                post("$BASE_URL/address_txs?order=block_height.desc&offset=$offset&limit=$limit", body),
            ),
        ) { "Koios returned an empty address transaction response" }
    }

    private fun requestTransactionInfo(hashes: List<String>): List<CardanoTransactionInfo> {
        val body = hashesRequestAdapter.toJson(CardanoHashesRequest(hashes))
        return requireNotNull(transactionInfoAdapter.fromJson(post("$BASE_URL/tx_info", body))) {
            "Koios returned an empty transaction detail response"
        }
    }

    private fun post(url: String, body: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter().use { it.write(body) }
            if (connection.responseCode !in 200..299) {
                throw IOException("Koios transaction history request failed with HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun CardanoTransactionInfo.toTxInfo(walletAddress: String): TxInfo? {
        val walletInputs = inputs.filter { it.paymentAddress?.bech32 == walletAddress }
        val walletOutputs = outputs.filter { it.paymentAddress?.bech32 == walletAddress }
        val walletInput = walletInputs.sumValues()
        val walletOutput = walletOutputs.sumValues()
        val outgoing = walletInput.signum() > 0
        val value = if (outgoing) walletInput.subtract(walletOutput) else walletOutput
        if (value.signum() < 0) return null
        val inputAddresses = inputs.mapNotNull { it.paymentAddress?.bech32 }.distinct()
        val outputAddresses = outputs.mapNotNull { it.paymentAddress?.bech32 }.distinct()
        val peers = if (outgoing) {
            outputAddresses.filterNot { it == walletAddress }
        } else {
            inputAddresses.filterNot { it == walletAddress }
        }.ifEmpty { listOf(walletAddress) }
        val destinations = if (outgoing) peers else listOf(walletAddress)
        val sources = inputAddresses.ifEmpty { listOf(UNKNOWN_SOURCE) }

        return TxInfo(
            txHash = txHash,
            timestampInMillis = txTimestamp * MILLIS_IN_SECOND,
            isOutgoing = outgoing,
            destinationType = destinations.toDestinationType(),
            sourceType = sources.toSourceType(),
            interactionAddressType = peers.toInteractionAddressType(),
            status = TxInfo.TransactionStatus.Confirmed,
            type = TxInfo.TransactionType.Transfer,
            amount = value.movePointLeft(ADA_DECIMALS),
        )
    }

    private fun List<CardanoUtxo>.sumValues(): BigDecimal = fold(BigDecimal.ZERO) { total, item ->
        total.add(item.value.toBigDecimalOrNull() ?: BigDecimal.ZERO)
    }

    private fun List<String>.toDestinationType(): TxInfo.DestinationType {
        val values = map(TxInfo.AddressType::User)
        return if (values.size == 1) TxInfo.DestinationType.Single(values.single()) else TxInfo.DestinationType.Multiple(values)
    }

    private fun List<String>.toSourceType(): TxInfo.SourceType =
        if (size == 1) TxInfo.SourceType.Single(single()) else TxInfo.SourceType.Multiple(this)

    private fun List<String>.toInteractionAddressType(): TxInfo.InteractionAddressType =
        if (size == 1) TxInfo.InteractionAddressType.User(single()) else TxInfo.InteractionAddressType.Multiple(this)

    private fun requireRequestIsSupported(address: String, blockchain: Blockchain) {
        require(blockchain == Blockchain.Cardano) { "$blockchain is not supported by the Cardano fallback" }
        require(blockchain.validateAddress(address)) { "Invalid Cardano address" }
    }

    private companion object {
        const val BASE_URL = "https://api.koios.rest/api/v1"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_PAGE_SIZE = 100
        const val ADA_DECIMALS = 6
        const val MILLIS_IN_SECOND = 1_000L
        const val UNKNOWN_SOURCE = "Unknown"
    }
}

@JsonClass(generateAdapter = true)
internal data class CardanoAddressRequest(@Json(name = "_addresses") val addresses: List<String>)

@JsonClass(generateAdapter = true)
internal data class CardanoHashesRequest(
    @Json(name = "_tx_hashes") val hashes: List<String>,
    @Json(name = "_inputs") val includeInputs: Boolean = true,
)

@JsonClass(generateAdapter = true)
internal data class CardanoAddressTransaction(@Json(name = "tx_hash") val txHash: String)

@JsonClass(generateAdapter = true)
internal data class CardanoTransactionInfo(
    @Json(name = "tx_hash") val txHash: String,
    @Json(name = "tx_timestamp") val txTimestamp: Long,
    val inputs: List<CardanoUtxo> = emptyList(),
    val outputs: List<CardanoUtxo> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class CardanoUtxo(
    val value: String,
    @Json(name = "payment_addr") val paymentAddress: CardanoPaymentAddress? = null,
)

@JsonClass(generateAdapter = true)
internal data class CardanoPaymentAddress(val bech32: String)
