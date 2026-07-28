package com.tangem.data.pay.di

import com.tangem.data.pay.repository.MockAwareOnboardingRepository
import com.tangem.data.pay.repository.MockAwareTangemPayCardDetailsRepository
import com.tangem.data.pay.repository.MockCustomerOrderRepository
import com.tangem.data.pay.repository.MockKycRepository
import com.tangem.data.pay.repository.MockTangemPayReissueCardRepository
import com.tangem.data.pay.repository.MockTangemPayTxHistoryRepository
import com.tangem.data.pay.repository.MockTangemPayWithdrawRepository
import com.tangem.data.visa.MockTangemPayAuthDataSource
import com.tangem.data.visa.MockTangemPayRemoteDataSource
import com.tangem.data.visa.MockVisaActivationRepositoryFactory
import com.tangem.data.visa.MockVisaAuthRemoteDataSource
import com.tangem.domain.pay.datasource.TangemPayAuthDataSource
import com.tangem.domain.pay.repository.CustomerOrderRepository
import com.tangem.domain.pay.repository.KycRepository
import com.tangem.domain.pay.repository.OnboardingRepository
import com.tangem.domain.pay.repository.TangemPayCardDetailsRepository
import com.tangem.domain.pay.repository.TangemPayReissueCardRepository
import com.tangem.domain.pay.repository.TangemPayWithdrawRepository
import com.tangem.domain.tangempay.repository.TangemPayTxHistoryRepository
import com.tangem.domain.visa.datasource.TangemPayRemoteDataSource
import com.tangem.domain.visa.datasource.VisaAuthRemoteDataSource
import com.tangem.domain.visa.repository.VisaActivationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface TangemPayDataMockedModule {

    @Binds
    @Singleton
    fun bindOnboardingRepository(repository: MockAwareOnboardingRepository): OnboardingRepository

    @Binds
    @Singleton
    fun bindCardDetailsRepository(repository: MockAwareTangemPayCardDetailsRepository): TangemPayCardDetailsRepository

    @Binds
    @Singleton
    fun bindKycRepository(repository: MockKycRepository): KycRepository

    @Binds
    @Singleton
    fun bindTxHistoryRepository(repository: MockTangemPayTxHistoryRepository): TangemPayTxHistoryRepository

    @Binds
    @Singleton
    fun bindWithdrawRepository(repository: MockTangemPayWithdrawRepository): TangemPayWithdrawRepository

    @Binds
    @Singleton
    fun bindCustomerOrderRepository(repository: MockCustomerOrderRepository): CustomerOrderRepository

    @Binds
    @Singleton
    fun bindReissueCardRepository(repository: MockTangemPayReissueCardRepository): TangemPayReissueCardRepository

    @Binds
    @Singleton
    fun bindVisaAuthRemoteDataSource(repository: MockVisaAuthRemoteDataSource): VisaAuthRemoteDataSource

    @Binds
    @Singleton
    fun bindTangemPayRemoteDataSource(repository: MockTangemPayRemoteDataSource): TangemPayRemoteDataSource

    @Binds
    @Singleton
    fun bindVisaActivationRepositoryFactory(
        repository: MockVisaActivationRepositoryFactory,
    ): VisaActivationRepository.Factory

    @Binds
    @Singleton
    fun bindTangemPayAuthDataSource(repository: MockTangemPayAuthDataSource): TangemPayAuthDataSource
}
