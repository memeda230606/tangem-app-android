package com.tangem.data.walletmanager;

import com.tangem.blockchain.blockchains.bitcoincash.BitcoinCashFeesCalculator;
import com.tangem.blockchain.blockchains.bitcoincash.BitcoinCashTransactionBuilder;
import com.tangem.blockchain.blockchains.bitcoincash.BitcoinCashWalletManager;
import com.tangem.blockchain.blockchains.bitcoin.network.BitcoinNetworkProvider;
import com.tangem.blockchain.blockchains.ton.TonWalletManager;
import com.tangem.blockchain.blockchains.ton.network.TonNetworkProvider;
import com.tangem.blockchain.common.Wallet;
import com.tangem.blockchain.common.WalletManager;
import com.tangem.blockchain.transactionhistory.DefaultTransactionHistoryProvider;

import java.util.Collections;

/**
 * Java bridge for SDK implementations that are public on the JVM but marked internal in Kotlin metadata.
 */
final class SdkReadOnlyWalletManagerFactory {
    private SdkReadOnlyWalletManagerFactory() {}

    static WalletManager createBitcoinCash(Wallet wallet, BitcoinNetworkProvider provider) {
        return new BitcoinCashWalletManager(
                wallet,
                new BitcoinCashTransactionBuilder(wallet.getPublicKey().getBlockchainKey(), wallet.getBlockchain()),
                DefaultTransactionHistoryProvider.INSTANCE,
                new BitcoinCashFeesCalculator(wallet.getBlockchain()),
                provider
        );
    }

    static WalletManager createTon(Wallet wallet, TonNetworkProvider provider) {
        return new TonWalletManager(wallet, Collections.singletonList(provider));
    }
}
