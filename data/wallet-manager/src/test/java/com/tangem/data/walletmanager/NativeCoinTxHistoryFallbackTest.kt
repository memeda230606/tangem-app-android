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

internal class NativeCoinTxHistoryFallbackTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val dispatchers = mockk<CoroutineDispatcherProvider>()

    @Test
    fun `Blockbook removes change from outgoing amount`() {
        val result = BlockbookTxHistoryFallback(moshi, dispatchers).convertResponse(
            response = BlockbookAddressResponse(
                transactions = listOf(
                    BlockbookTransaction(
                        txid = HASH,
                        vin = listOf(BlockbookIo(listOf(WALLET), "100000")),
                        vout = listOf(
                            BlockbookIo(listOf(PEER), "60000"),
                            BlockbookIo(listOf(WALLET), "39000"),
                        ),
                        confirmations = 2,
                        blockTime = 1_700_000_000,
                    ),
                ),
            ),
            walletAddress = WALLET,
            decimals = 8,
            nextPageNumber = 2,
        )

        assertTransfer(result, outgoing = true, amount = "0.00061")
    }

    @Test
    fun `BlockCypher removes change from outgoing amount`() {
        val result = BlockbookTxHistoryFallback(moshi, dispatchers).convertBlockCypherResponse(
            response = BlockCypherAddressResponse(
                hasMore = true,
                transactions = listOf(
                    BlockCypherTransaction(
                        hash = HASH,
                        blockHeight = 123,
                        confirmed = "2023-11-14T22:13:20Z",
                        inputs = listOf(BlockCypherInput(listOf(WALLET), 100_000)),
                        outputs = listOf(
                            BlockCypherOutput(listOf(PEER), 60_000),
                            BlockCypherOutput(listOf(WALLET), 39_000),
                        ),
                    ),
                ),
            ),
            walletAddress = WALLET,
            decimals = 8,
        )

        assertTransfer(result, outgoing = true, amount = "0.00061")
        assertThat(result.nextPage).isEqualTo(Page.Next("blockcypher:123"))
    }

    @Test
    fun `3xpl maps Bitcoin Cash address effect`() {
        val result = BlockbookTxHistoryFallback(moshi, dispatchers).convertThreeXplResponse(
            response = ThreeXplAddressResponse(
                data = ThreeXplAddressData(
                    events = mapOf(
                        "bitcoin-cash-main" to listOf(
                            ThreeXplAddressEvent(
                                transaction = HASH,
                                time = "2026-08-28T23:36:44Z",
                                effect = "-320000000",
                            ),
                        ),
                    ),
                ),
            ),
            walletAddress = WALLET,
            decimals = 8,
            currentOffset = 0,
            pageSize = 1,
        )

        assertTransfer(result, outgoing = true, amount = "3.2")
        assertThat(result.nextPage).isEqualTo(Page.Next("3xpl:1"))
    }

    @Test
    fun `XRP maps delivered drops and Ripple epoch`() {
        val result = XrpTxHistoryFallback(moshi, dispatchers).convertResponse(
            response = XrpAccountTxResponse(
                XrpAccountTxResult(
                    transactions = listOf(
                        XrpAccountTransaction(
                            hash = HASH,
                            txJson = XrpTransaction(
                                account = PEER,
                                destination = WALLET,
                                transactionType = "Payment",
                                date = 753_315_200,
                            ),
                            meta = XrpMeta(transactionResult = "tesSUCCESS", deliveredAmount = "2500000"),
                            validated = true,
                        ),
                    ),
                    marker = XrpMarker(ledger = 10, seq = 2),
                ),
            ),
            walletAddress = WALLET,
        )

        assertTransfer(result, outgoing = false, amount = "2.5")
        assertThat(result.nextPage).isEqualTo(Page.Next("10:2"))
    }

    @Test
    fun `TON maps transfer relative to normalized event account`() {
        val result = TonTxHistoryFallback(moshi, dispatchers).convertResponse(
            response = TonAccountEventsResponse(
                events = listOf(
                    TonAccountEvent(
                        eventId = HASH,
                        timestamp = 1_700_000_000,
                        account = TonAccountAddress(WALLET),
                        actions = listOf(
                            TonAction(
                                type = "TonTransfer",
                                status = "ok",
                                tonTransfer = TonTransferAction(
                                    sender = TonAccountAddress(WALLET),
                                    recipient = TonAccountAddress(PEER),
                                    amount = 1_250_000_000,
                                ),
                            ),
                        ),
                    ),
                ),
                nextFrom = 123,
            ),
            pageSize = 1,
        )

        assertTransfer(result, outgoing = true, amount = "1.25")
        assertThat(result.nextPage).isEqualTo(Page.Next("123"))
    }

    @Test
    fun `Cardano removes change from outgoing amount`() {
        val result = CardanoTxHistoryFallback(moshi, dispatchers).convertTransactions(
            transactions = listOf(
                CardanoTransactionInfo(
                    txHash = HASH,
                    txTimestamp = 1_700_000_000,
                    inputs = listOf(CardanoUtxo("5000000", CardanoPaymentAddress(WALLET))),
                    outputs = listOf(
                        CardanoUtxo("3000000", CardanoPaymentAddress(PEER)),
                        CardanoUtxo("1800000", CardanoPaymentAddress(WALLET)),
                    ),
                ),
            ),
            walletAddress = WALLET,
            hasNextPage = false,
            nextOffset = 1,
        )

        assertTransfer(result, outgoing = true, amount = "3.2")
    }

    private fun assertTransfer(page: KeylessTxHistoryPage, outgoing: Boolean, amount: String) {
        val item = page.items.single()
        assertThat(item.isOutgoing).isEqualTo(outgoing)
        assertThat(item.amount.compareTo(BigDecimal(amount))).isEqualTo(0)
        assertThat(item.status).isEqualTo(TxInfo.TransactionStatus.Confirmed)
    }

    private companion object {
        const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val WALLET = "wallet-address"
        const val PEER = "peer-address"
    }
}
