package com.tangem.data.walletmanager

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ExternalNetworkBalanceFallbackTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun `parses BCH balance response and converts satoshis`() {
        val response = moshi.adapter(ThreeXplBalanceResponse::class.java).fromJson(
            """
            {
              "data": {
                "balances": {
                  "bitcoin-cash-main": {
                    "bitcoin-cash": { "balance": "123456789" }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val raw = response!!.data.balances.getValue("bitcoin-cash-main").getValue("bitcoin-cash").balance
        assertEquals(BigDecimal("1.23456789"), raw.toExternalCoinBalance(decimals = 8))
    }

    @Test
    fun `recognizes a new BCH address with no balance records as explicit zero`() {
        val response = moshi.adapter(ThreeXplAddressResponse::class.java).fromJson(
            """
            {
              "data": {
                "address": {
                  "balances": { "bitcoin-cash-main": 0 }
                },
                "balances": { "bitcoin-cash-main": [] }
              }
            }
            """.trimIndent(),
        )

        assertEquals(0, response!!.data.address!!.balances.getValue("bitcoin-cash-main"))
    }

    @Test
    fun `parses Cardano lovelace balance response`() {
        val type = Types.newParameterizedType(List::class.java, CardanoAddressInfo::class.java)
        val response = moshi.adapter<List<CardanoAddressInfo>>(type).fromJson("[{\"balance\":\"2500000\"}]")

        assertEquals(BigDecimal("2.500000"), response!!.single().balance.toExternalCoinBalance(decimals = 6))
    }

    @Test
    fun `parses numeric TON balance response`() {
        val response = moshi.adapter(TonAccountInfo::class.java).fromJson("{\"balance\":10640307070552}")

        assertEquals(
            BigDecimal("10640.307070552"),
            response!!.balance.toString().toExternalCoinBalance(decimals = 9),
        )
    }

    @Test
    fun `rejects malformed provider balance`() {
        org.junit.jupiter.api.assertThrows<Exception> {
            "not-a-number".toExternalCoinBalance(decimals = 8)
        }
    }
}
