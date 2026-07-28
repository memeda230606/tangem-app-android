package com.tangem.domain.wallets.usecase

import com.google.common.truth.Truth.assertThat
import com.tangem.domain.common.wallets.UserWalletsListRepository
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.models.wallet.UserWalletId
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IsNeedToBackupUseCaseTest {

    private val repository = mockk<UserWalletsListRepository>()
    private val useCase = IsNeedToBackupUseCase(repository)

    @Test
    fun `GIVEN backup is allowed and missing WHEN invoked THEN returns true`() = runTest {
        val wallet = createColdWallet(isBackupAllowed = true, backupStatus = CardDTO.BackupStatus.NoBackup)
        every { repository.userWallets } returns MutableStateFlow(listOf(wallet))

        val result = useCase(wallet.walletId).first()

        assertThat(result).isTrue()
    }

    @Test
    fun `GIVEN backup is not supported WHEN invoked THEN returns false`() = runTest {
        val wallet = createColdWallet(isBackupAllowed = false, backupStatus = CardDTO.BackupStatus.NoBackup)
        every { repository.userWallets } returns MutableStateFlow(listOf(wallet))

        val result = useCase(wallet.walletId).first()

        assertThat(result).isFalse()
    }

    @Test
    fun `GIVEN backup is already active WHEN invoked THEN returns false`() = runTest {
        val wallet = createColdWallet(
            isBackupAllowed = true,
            backupStatus = CardDTO.BackupStatus.Active(cardCount = 1),
        )
        every { repository.userWallets } returns MutableStateFlow(listOf(wallet))

        val result = useCase(wallet.walletId).first()

        assertThat(result).isFalse()
    }

    private fun createColdWallet(
        isBackupAllowed: Boolean,
        backupStatus: CardDTO.BackupStatus,
    ): UserWallet.Cold {
        val walletId = UserWalletId("ABCD")
        val settings = mockk<CardDTO.Settings> {
            every { this@mockk.isBackupAllowed } returns isBackupAllowed
        }
        val card = mockk<CardDTO> {
            every { this@mockk.settings } returns settings
            every { this@mockk.backupStatus } returns backupStatus
        }
        val scanResponse = mockk<ScanResponse> {
            every { this@mockk.card } returns card
        }

        return mockk {
            every { this@mockk.walletId } returns walletId
            every { this@mockk.scanResponse } returns scanResponse
        }
    }
}
