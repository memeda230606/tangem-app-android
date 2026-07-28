package com.tangem.data.visa.di

import com.tangem.data.visa.InMemoryVisaActivationStatusRepository
import com.tangem.data.visa.MockVisaRepository
import com.tangem.domain.visa.repository.VisaActivationStatusRepository
import com.tangem.domain.visa.repository.VisaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface VisaDataModule {

    @Binds
    fun bindVisaRepository(repository: MockVisaRepository): VisaRepository

    @Binds
    @Singleton
    fun bindVisaActivationStatusRepository(
        repository: InMemoryVisaActivationStatusRepository,
    ): VisaActivationStatusRepository
}
