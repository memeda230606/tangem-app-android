package com.tangem.data.express.di

import com.tangem.data.express.MockExpressServiceFetcher
import com.tangem.domain.express.ExpressServiceFetcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface ExpressServiceFetcherMockedModule {

    @Binds
    @Singleton
    fun bindExpressServiceFetcher(impl: MockExpressServiceFetcher): ExpressServiceFetcher
}
