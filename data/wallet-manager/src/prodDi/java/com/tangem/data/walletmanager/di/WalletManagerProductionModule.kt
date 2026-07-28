package com.tangem.data.walletmanager.di

import com.tangem.data.walletmanager.DefaultWalletManagersFacade
import com.tangem.domain.walletmanager.WalletManagersFacade
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface WalletManagerProductionModule {

    @Binds
    @Singleton
    fun bindWalletManagerFacade(impl: DefaultWalletManagersFacade): WalletManagersFacade
}
