package com.tangem.data.walletmanager

import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.common.Blockchain
import com.tangem.blockchain.common.pagination.Page as SdkPage
import com.tangem.blockchainsdk.models.UpdateWalletManagerResult
import com.tangem.blockchainsdk.utils.toBlockchain
import com.tangem.domain.demo.models.DemoConfig
import com.tangem.domain.models.currency.CryptoCurrency
import com.tangem.domain.models.network.Network
import com.tangem.domain.models.network.TxInfo
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.txhistory.models.Page as DomainPage
import com.tangem.domain.txhistory.models.PaginationWrapper
import com.tangem.domain.txhistory.models.TxHistoryState
import com.tangem.domain.walletmanager.WalletManagersFacade
import com.tangem.utils.logging.TangemLogger
import java.math.BigDecimal
import javax.inject.Inject

/** Supplies deterministic balances without contacting public blockchain nodes in the mocked build. */
internal class MockWalletManagersFacade @Inject constructor(
    private val delegate: DefaultWalletManagersFacade,
) : WalletManagersFacade by delegate {

    private val resultFactory = UpdateWalletManagerResultFactory()

    override suspend fun update(
        userWalletId: UserWalletId,
        network: Network,
        extraTokens: Set<CryptoCurrency.Token>,
        xpub: String?,
    ): UpdateWalletManagerResult = getFixtureResult(userWalletId, network)

    override suspend fun updatePendingTransactions(
        userWalletId: UserWalletId,
        network: Network,
    ): UpdateWalletManagerResult = getFixtureResult(userWalletId, network)

    override suspend fun getTxHistoryState(
        userWalletId: UserWalletId,
        currency: CryptoCurrency,
    ): TxHistoryState {
        TangemLogger.i("[MockedWallet] Local transaction history state served for ${currency.network.rawId}")
        return TxHistoryState.Success.HasTransactions(txCount = NfcDemoWalletFixtures.transactions.size)
    }

    override suspend fun getTxHistoryItems(
        userWalletId: UserWalletId,
        currency: CryptoCurrency,
        page: SdkPage,
        pageSize: Int,
    ): PaginationWrapper<TxInfo> {
        TangemLogger.i("[MockedWallet] Local transaction history items served for ${currency.network.rawId}")
        return PaginationWrapper(
            currentPage = DomainPage.Initial,
            nextPage = DomainPage.LastPage,
            items = NfcDemoWalletFixtures.transactions.take(pageSize),
        )
    }

    override suspend fun getRecentTransactions(
        userWalletId: UserWalletId,
        currency: CryptoCurrency,
    ): List<TxInfo> = NfcDemoWalletFixtures.transactions

    private suspend fun getFixtureResult(
        userWalletId: UserWalletId,
        network: Network,
    ): UpdateWalletManagerResult {
        val blockchain = network.toBlockchain()
        val walletManager = delegate.getOrCreateWalletManager(
            userWalletId = userWalletId,
            blockchain = blockchain,
            derivationPath = network.derivationPath.value,
        ) ?: return UpdateWalletManagerResult.Unreachable()

        val amount = if (blockchain == Blockchain.Polygon) {
            Amount(value = VISA_POLYGON_BALANCE, blockchain = blockchain)
        } else {
            DemoConfig.getBalance(blockchain)
        }
        walletManager.wallet.setAmount(amount)
        TangemLogger.i("[MockedWallet] Local balance served for ${network.rawId}")

        return resultFactory.getDemoResult(walletManager = walletManager, demoAmount = amount)
    }

    private companion object {

        val VISA_POLYGON_BALANCE: BigDecimal = BigDecimal("125.75")

    }
}
