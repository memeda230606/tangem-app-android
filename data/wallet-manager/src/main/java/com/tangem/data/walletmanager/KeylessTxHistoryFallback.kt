package com.tangem.data.walletmanager

import com.tangem.blockchain.common.Blockchain
import com.tangem.blockchainsdk.utils.toBlockchain
import com.tangem.domain.models.currency.CryptoCurrency
import com.tangem.domain.models.network.TxInfo
import com.tangem.domain.txhistory.models.Page
import javax.inject.Inject

internal class KeylessTxHistoryFallback @Inject constructor(
    private val evmTxHistoryFallback: EvmTxHistoryFallback,
    private val bitcoinTxHistoryFallback: BitcoinTxHistoryFallback,
    private val blockbookTxHistoryFallback: BlockbookTxHistoryFallback,
    private val xrpTxHistoryFallback: XrpTxHistoryFallback,
    private val cardanoTxHistoryFallback: CardanoTxHistoryFallback,
    private val tonTxHistoryFallback: TonTxHistoryFallback,
) {

    fun supports(currency: CryptoCurrency): Boolean {
        val blockchain = currency.network.id.toBlockchain()

        return when {
            blockchain.isEvm() -> blockchain.getChainId() != null
            currency is CryptoCurrency.Coin -> blockchain in NATIVE_COIN_BLOCKCHAINS
            else -> false
        }
    }

    suspend fun getTransactionsCount(address: String, currency: CryptoCurrency): Int {
        val blockchain = currency.network.id.toBlockchain()

        return when {
            blockchain.isEvm() -> evmTxHistoryFallback.getTransactionsCount(
                address = address,
                currency = currency,
                blockchain = blockchain,
            )
            currency is CryptoCurrency.Coin && blockchain in BITCOIN_BLOCKCHAINS -> {
                bitcoinTxHistoryFallback.getTransactionsCount(address = address, blockchain = blockchain)
            }
            currency is CryptoCurrency.Coin && blockchain in BlockbookTxHistoryFallback.SUPPORTED_BLOCKCHAINS -> {
                blockbookTxHistoryFallback.getTransactionsCount(address = address, blockchain = blockchain)
            }
            currency is CryptoCurrency.Coin && blockchain == Blockchain.XRP -> {
                xrpTxHistoryFallback.getTransactionsCount(address = address, blockchain = blockchain)
            }
            currency is CryptoCurrency.Coin && blockchain == Blockchain.Cardano -> {
                cardanoTxHistoryFallback.getTransactionsCount(address = address, blockchain = blockchain)
            }
            currency is CryptoCurrency.Coin && blockchain in TON_BLOCKCHAINS -> {
                tonTxHistoryFallback.getTransactionsCount(address = address, blockchain = blockchain)
            }
            else -> unsupported(blockchain)
        }
    }

    suspend fun getTransactions(
        address: String,
        currency: CryptoCurrency,
        page: Page,
        pageSize: Int,
    ): KeylessTxHistoryPage {
        val blockchain = currency.network.id.toBlockchain()

        return when {
            blockchain.isEvm() -> evmTxHistoryFallback.getTransactions(
                address = address,
                currency = currency,
                blockchain = blockchain,
                page = page,
                pageSize = pageSize,
            )
            currency is CryptoCurrency.Coin && blockchain in BITCOIN_BLOCKCHAINS -> {
                bitcoinTxHistoryFallback.getTransactions(
                    address = address,
                    blockchain = blockchain,
                    page = page,
                )
            }
            currency is CryptoCurrency.Coin && blockchain in BlockbookTxHistoryFallback.SUPPORTED_BLOCKCHAINS -> {
                blockbookTxHistoryFallback.getTransactions(
                    address = address,
                    blockchain = blockchain,
                    page = page,
                    pageSize = pageSize,
                )
            }
            currency is CryptoCurrency.Coin && blockchain == Blockchain.XRP -> {
                xrpTxHistoryFallback.getTransactions(
                    address = address,
                    blockchain = blockchain,
                    page = page,
                    pageSize = pageSize,
                )
            }
            currency is CryptoCurrency.Coin && blockchain == Blockchain.Cardano -> {
                cardanoTxHistoryFallback.getTransactions(
                    address = address,
                    blockchain = blockchain,
                    page = page,
                    pageSize = pageSize,
                )
            }
            currency is CryptoCurrency.Coin && blockchain in TON_BLOCKCHAINS -> {
                tonTxHistoryFallback.getTransactions(
                    address = address,
                    blockchain = blockchain,
                    page = page,
                    pageSize = pageSize,
                )
            }
            else -> unsupported(blockchain)
        }
    }

    private fun unsupported(blockchain: Blockchain): Nothing {
        throw UnsupportedOperationException("Keyless transaction history is not available for $blockchain")
    }

    private companion object {
        val BITCOIN_BLOCKCHAINS = setOf(Blockchain.Bitcoin, Blockchain.BitcoinTestnet)
        val TON_BLOCKCHAINS = setOf(Blockchain.TON, Blockchain.TONTestnet)
        val NATIVE_COIN_BLOCKCHAINS = BITCOIN_BLOCKCHAINS + BlockbookTxHistoryFallback.SUPPORTED_BLOCKCHAINS +
            setOf(Blockchain.XRP, Blockchain.Cardano) + TON_BLOCKCHAINS
    }
}

internal data class KeylessTxHistoryPage(
    val items: List<TxInfo>,
    val nextPage: Page,
)
