package com.tangem.tap.di.libs.blockchainsdk

import com.tangem.core.analytics.store.LastSignedWalletFormStore
import com.tangem.data.card.TransactionSignerFactory
import com.tangem.data.wallets.hot.TangemHotWalletSigner
import com.tangem.domain.common.wallets.UserWalletsListRepository
import com.tangem.tap.common.libs.blockchainsdk.DefaultTransactionSignerFactory
import com.tangem.tap.domain.sdk.mocks.NfcDemoHotWalletBridge
import com.tangem.utils.coroutines.AppCoroutineScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
[REDACTED_AUTHOR]
 */
@Module
@InstallIn(SingletonComponent::class)
internal class TransactionSignerFactoryModule {

    @Provides
    @Singleton
    fun provideTransactionSignerFactory(
        lastSignedWalletFormStore: LastSignedWalletFormStore,
        userWalletsListRepository: UserWalletsListRepository,
        appCoroutineScope: AppCoroutineScope,
        nfcDemoHotWalletBridge: NfcDemoHotWalletBridge,
        hotWalletSignerFactory: TangemHotWalletSigner.Factory,
    ): TransactionSignerFactory {
        return DefaultTransactionSignerFactory(
            lastSignedWalletFormStore = lastSignedWalletFormStore,
            userWalletsListRepository = userWalletsListRepository,
            coroutineScope = appCoroutineScope,
            nfcDemoHotWalletBridge = nfcDemoHotWalletBridge,
            hotWalletSignerFactory = hotWalletSignerFactory,
        )
    }
}
