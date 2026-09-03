package com.tangem.data.walletmanager

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tangem.domain.models.network.TxInfo
import com.tangem.domain.txhistory.models.Page
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class BitcoinTxHistoryFallbackTest {

    private val fallback = BitcoinTxHistoryFallback(
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
        dispatchers = mockk<CoroutineDispatcherProvider>(),
    )

    @Test
    fun `convert maps incoming Bitcoin transaction`() {
        val actual = fallback.convertTransactions(
            transactions = listOf(
                transaction(
                    inputs = listOf(SOURCE_ADDRESS to 50_000L),
                    outputs = listOf(WALLET_ADDRESS to 40_000L, SOURCE_ADDRESS to 9_000L),
                ),
            ),
            walletAddress = WALLET_ADDRESS,
            nowMillis = NOW_MILLIS,
        )

        val item = actual.items.single()
        assertThat(item.isOutgoing).isFalse()
        assertThat(item.amount.compareTo(BigDecimal("0.0004"))).isEqualTo(0)
        assertThat(item.status).isEqualTo(TxInfo.TransactionStatus.Confirmed)
        assertThat(item.sourceType).isEqualTo(TxInfo.SourceType.Single(SOURCE_ADDRESS))
        assertThat(actual.nextPage).isEqualTo(Page.LastPage)
    }

    @Test
    fun `convert maps outgoing amount including miner fee`() {
        val actual = fallback.convertTransactions(
            transactions = listOf(
                transaction(
                    inputs = listOf(WALLET_ADDRESS to 100_000L),
                    outputs = listOf(DESTINATION_ADDRESS to 60_000L, WALLET_ADDRESS to 39_000L),
                ),
            ),
            walletAddress = WALLET_ADDRESS,
            nowMillis = NOW_MILLIS,
        ).items.single()

        assertThat(actual.isOutgoing).isTrue()
        assertThat(actual.amount.compareTo(BigDecimal("0.00061"))).isEqualTo(0)
        assertThat(actual.destinationType).isEqualTo(
            TxInfo.DestinationType.Single(TxInfo.AddressType.User(DESTINATION_ADDRESS)),
        )
    }

    private fun transaction(inputs: List<Pair<String, Long>>, outputs: List<Pair<String, Long>>) =
        MempoolTransaction(
            txid = TX_HASH,
            vin = inputs.map { (address, value) ->
                MempoolInput(prevout = MempoolOutput(scriptpubkeyAddress = address, value = value))
            },
            vout = outputs.map { (address, value) ->
                MempoolOutput(scriptpubkeyAddress = address, value = value)
            },
            status = MempoolTransactionStatus(confirmed = true, blockTime = BLOCK_TIME_SECONDS),
        )

    private companion object {
        const val WALLET_ADDRESS = "bc1qwallet"
        const val SOURCE_ADDRESS = "bc1qsource"
        const val DESTINATION_ADDRESS = "bc1qdestination"
        const val TX_HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val BLOCK_TIME_SECONDS = 1_659_424_685L
        const val NOW_MILLIS = 1_700_000_000_000L
    }
}
