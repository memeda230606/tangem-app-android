package com.tangem.data.quotes.di

import com.tangem.data.quotes.multi.MockMultiQuoteStatusFetcher
import com.tangem.data.quotes.single.DefaultSingleQuoteStatusFetcher
import com.tangem.domain.quotes.multi.MultiQuoteStatusFetcher
import com.tangem.domain.quotes.single.SingleQuoteStatusFetcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface QuoteStatusFetcherMockedModule {

    @Binds
    @Singleton
    fun bindMultiQuoteStatusFetcher(impl: MockMultiQuoteStatusFetcher): MultiQuoteStatusFetcher

    @Binds
    @Singleton
    fun bindSingleQuoteStatusFetcher(impl: DefaultSingleQuoteStatusFetcher): SingleQuoteStatusFetcher
}
