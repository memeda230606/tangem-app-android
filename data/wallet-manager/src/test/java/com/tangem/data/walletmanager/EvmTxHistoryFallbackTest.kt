package com.tangem.data.walletmanager

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tangem.domain.models.network.TxInfo
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class EvmTxHistoryFallbackTest {

    private val fallback = EvmTxHistoryFallback(
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
        dispatchers = mockk<CoroutineDispatcherProvider>(),
    )

    @Test
    fun `convert maps confirmed incoming EVM transfer`() {
        val actual = fallback.convertTransactions(
            body = RESPONSE,
            walletAddress = WALLET_ADDRESS,
            decimals = 18,
            pageSize = 20,
        ).single()

        assertThat(actual.txHash).isEqualTo(TX_HASH)
        assertThat(actual.timestampInMillis).isEqualTo(1_659_424_685_000L)
        assertThat(actual.isOutgoing).isFalse()
        assertThat(actual.status).isEqualTo(TxInfo.TransactionStatus.Confirmed)
        assertThat(actual.type).isEqualTo(TxInfo.TransactionType.Transfer)
        assertThat(actual.amount.compareTo(BigDecimal("0.00000174126939"))).isEqualTo(0)
        assertThat(actual.sourceType).isEqualTo(TxInfo.SourceType.Single(SOURCE_ADDRESS))
        assertThat(actual.destinationType).isEqualTo(
            TxInfo.DestinationType.Single(TxInfo.AddressType.User(WALLET_ADDRESS)),
        )
    }

    @Test
    fun `convert maps outgoing EVM token transfer`() {
        val actual = fallback.convertTokenTransactions(
            body = TOKEN_RESPONSE,
            walletAddress = WALLET_ADDRESS,
            decimals = 6,
        ).single()

        assertThat(actual.txHash).isEqualTo(TX_HASH)
        assertThat(actual.timestampInMillis).isEqualTo(1_659_424_685_000L)
        assertThat(actual.isOutgoing).isTrue()
        assertThat(actual.amount.compareTo(BigDecimal("12.345678"))).isEqualTo(0)
        assertThat(actual.destinationType).isEqualTo(
            TxInfo.DestinationType.Single(TxInfo.AddressType.User(SOURCE_ADDRESS)),
        )
    }

    private companion object {
        const val WALLET_ADDRESS = "0x2222222222222222222222222222222222222222"
        const val SOURCE_ADDRESS = "0x1111111111111111111111111111111111111111"
        const val TX_HASH = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val RESPONSE = """
            {
              "items": [
                {
                  "id": "$TX_HASH",
                  "timestamp": "2022-08-02T07:18:05Z",
                  "status": true,
                  "value": "1741269390000",
                  "from": "$SOURCE_ADDRESS",
                  "to": "$WALLET_ADDRESS"
                }
              ],
              "link": {}
            }
        """.trimIndent()
        val TOKEN_RESPONSE = """
            {
              "status": "1",
              "message": "OK",
              "result": [
                {
                  "hash": "$TX_HASH",
                  "timeStamp": "1659424685",
                  "value": "12345678",
                  "from": "$WALLET_ADDRESS",
                  "to": "$SOURCE_ADDRESS",
                  "contractAddress": "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "tokenDecimal": "6"
                }
              ]
            }
        """.trimIndent()
    }
}
