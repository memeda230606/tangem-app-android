package com.tangem.data.pay.di

import com.tangem.data.pay.repository.DefaultOnboardingRepository
import com.tangem.data.pay.repository.DefaultCustomerOrderRepository
import com.tangem.data.pay.repository.DefaultKycRepository
import com.tangem.data.pay.repository.DefaultReissueCardRepository
import com.tangem.data.pay.repository.DefaultTangemPayCardDetailsRepository
import com.tangem.data.pay.repository.DefaultTangemPayTxHistoryRepository
import com.tangem.data.pay.repository.DefaultTangemPayWithdrawRepository
import com.tangem.domain.pay.repository.CustomerOrderRepository
import com.tangem.domain.pay.repository.KycRepository
import com.tangem.domain.pay.repository.OnboardingRepository
import com.tangem.domain.pay.repository.TangemPayCardDetailsRepository
import com.tangem.domain.pay.repository.TangemPayReissueCardRepository
import com.tangem.domain.pay.repository.TangemPayWithdrawRepository
import com.tangem.domain.tangempay.repository.TangemPayTxHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface TangemPayDataProductionModule {

    @Binds
    @Singleton
    fun bindOnboardingRepository(repository: DefaultOnboardingRepository): OnboardingRepository

    @Binds
    @Singleton
    fun bindCardDetailsRepository(repository: DefaultTangemPayCardDetailsRepository): TangemPayCardDetailsRepository

    @Binds
    @Singleton
    fun bindKycRepository(repository: DefaultKycRepository): KycRepository

    @Binds
    @Singleton
    fun bindTxHistoryRepository(repository: DefaultTangemPayTxHistoryRepository): TangemPayTxHistoryRepository

    @Binds
    @Singleton
    fun bindWithdrawRepository(repository: DefaultTangemPayWithdrawRepository): TangemPayWithdrawRepository

    @Binds
    @Singleton
    fun bindCustomerOrderRepository(repository: DefaultCustomerOrderRepository): CustomerOrderRepository

    @Binds
    @Singleton
    fun bindReissueCardRepository(repository: DefaultReissueCardRepository): TangemPayReissueCardRepository
}
