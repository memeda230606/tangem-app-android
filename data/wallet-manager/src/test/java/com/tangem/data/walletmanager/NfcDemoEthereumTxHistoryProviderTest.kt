package com.tangem.data.walletmanager

import com.google.common.truth.Truth.assertThat
import com.tangem.domain.models.network.TxInfo
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NfcDemoEthereumTxHistoryProviderTest {

    @Test
    fun `parseTransactions maps incoming and outgoing Ethereum transfers`() {
        val items = NfcDemoEthereumTxHistoryProvider.parseTransactions(
            json = RESPONSE,
            walletAddress = WALLET_ADDRESS,
            decimals = 18,
        )

        assertThat(items).hasSize(2)

        val outgoing = items.first()
        assertThat(outgoing.txHash).isEqualTo("0xoutgoing")
        assertThat(outgoing.isOutgoing).isTrue()
        assertThat(outgoing.amount.compareTo(BigDecimal("0.001"))).isEqualTo(0)
        assertThat(outgoing.status).isEqualTo(TxInfo.TransactionStatus.Confirmed)
        assertThat(outgoing.type).isEqualTo(TxInfo.TransactionType.Transfer)
        assertThat(outgoing.interactionAddressType).isEqualTo(
            TxInfo.InteractionAddressType.User(RECIPIENT_ADDRESS),
        )

        val incoming = items.last()
        assertThat(incoming.txHash).isEqualTo("0xincoming")
        assertThat(incoming.isOutgoing).isFalse()
        assertThat(incoming.amount.compareTo(BigDecimal("0.018882421492847932"))).isEqualTo(0)
        assertThat(incoming.interactionAddressType).isEqualTo(
            TxInfo.InteractionAddressType.User(RECIPIENT_ADDRESS),
        )
    }

    @Test
    fun `parseTransactions skips entries without a destination`() {
        val items = NfcDemoEthereumTxHistoryProvider.parseTransactions(
            json = RESPONSE_WITHOUT_DESTINATION,
            walletAddress = WALLET_ADDRESS,
            decimals = 18,
        )

        assertThat(items).isEmpty()
    }

    private companion object {
        const val WALLET_ADDRESS = "0x97751A07c632c25B235c1574bcaa9020B19BDdFA"
        const val RECIPIENT_ADDRESS = "0x1d426A8CE14FC85561D7177299B0bFad18881F58"

        val RESPONSE = """
            {
              "items": [
                {
                  "type": "evm_tx",
                  "id": "0xoutgoing",
                  "status": true,
                  "timestamp": "2026-07-27T06:26:47.000Z",
                  "from": "$WALLET_ADDRESS",
                  "to": "$RECIPIENT_ADDRESS",
                  "value": "1000000000000000"
                },
                {
                  "type": "evm_tx",
                  "id": "0xincoming",
                  "status": true,
                  "timestamp": "2026-07-27T04:57:35.000Z",
                  "from": "$RECIPIENT_ADDRESS",
                  "to": "$WALLET_ADDRESS",
                  "value": "18882421492847932"
                }
              ],
              "link": {}
            }
        """.trimIndent()

        val RESPONSE_WITHOUT_DESTINATION = """
            {
              "items": [
                {
                  "type": "evm_tx",
                  "id": "0xcontractcreation",
                  "status": true,
                  "timestamp": "2026-07-27T06:26:47.000Z",
                  "from": "$WALLET_ADDRESS",
                  "to": null,
                  "value": "0"
                }
              ]
            }
        """.trimIndent()
    }
}
