package com.tangem.data.tokens.di

import com.tangem.data.tokens.MockMultiWalletAccountListFetcher
import com.tangem.domain.tokens.MultiWalletAccountListFetcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface MultiWalletAccountListFetcherMockedModule {

    @Binds
    @Singleton
    fun bindMultiWalletAccountListFetcher(impl: MockMultiWalletAccountListFetcher): MultiWalletAccountListFetcher
}
