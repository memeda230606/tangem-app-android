package com.tangem.data.wallets.hot

import com.tangem.blockchain.common.Wallet
import com.tangem.common.CompletionResult
import com.tangem.domain.models.MobileWallet
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.wallets.hot.HotWalletAccessor
import com.tangem.domain.wallets.hot.HotWalletNfcSecurity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class TangemHotWalletSignerTest {

    @Test
    fun `does not release a signature when NFC authorization fails`() = runTest {
        val seedKey = byteArrayOf(1, 2, 3)
        val blockchainPublicKey = mockk<Wallet.PublicKey> {
            every { this@mockk.seedKey } returns seedKey
        }
        val mobileWallet = mockk<MobileWallet> {
            every { publicKey } returns seedKey
        }
        val userWallet = mockk<UserWallet.Hot> {
            every { wallets } returns listOf(mobileWallet)
        }
        val accessor = mockk<HotWalletAccessor>()
        val security = mockk<HotWalletNfcSecurity>()
        val hashes = listOf(byteArrayOf(9, 8, 7))
        coEvery { security.authorizeSigning(userWallet, hashes) } throws SecurityException("wrong card")

        val result = TangemHotWalletSigner(userWallet, accessor, security).sign(hashes, blockchainPublicKey)

        assertTrue(result is CompletionResult.Failure)
        coVerify(exactly = 0) { accessor.signHashes(any(), any()) }
    }
}
