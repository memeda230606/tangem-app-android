package com.tangem.data.wallets.nfc

import com.google.common.truth.Truth.assertThat
import com.tangem.domain.models.wallet.UserWalletId
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import javax.crypto.AEADBadTagException

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NfcWalletCryptoTest {

    private val crypto = NfcWalletCrypto()

    @Test
    fun `encrypt and decrypt seed`() {
        val seed = "seed phrase bytes".encodeToByteArray()
        val aesKey = crypto.generateAesKey()
        val blob = crypto.encryptSeed(
            seed = seed,
            aesKey = aesKey,
            walletId = UserWalletId(WALLET_ID),
            cardInstanceId = "card-instance-id",
            backupSetId = "backup-set-id",
            localKeyId = "local-key-id",
        )

        val actual = crypto.decryptSeed(blob, aesKey)

        assertThat(actual).isEqualTo(seed)
        assertThat(blob.nonce).hasLength(12)
        assertThat(blob.tag).hasLength(16)
    }

    @Test
    fun `decrypt fails when aad does not match`() {
        val seed = "seed phrase bytes".encodeToByteArray()
        val aesKey = crypto.generateAesKey()
        val blob = crypto.encryptSeed(
            seed = seed,
            aesKey = aesKey,
            walletId = UserWalletId(WALLET_ID),
            cardInstanceId = "card-instance-id",
            backupSetId = "backup-set-id",
            localKeyId = "local-key-id",
        )

        assertThrows(AEADBadTagException::class.java) {
            crypto.decryptSeed(blob.copy(walletId = UserWalletId(WRONG_WALLET_ID)), aesKey)
        }
    }

    @Test
    fun `decrypt fails with wrong key`() {
        val seed = "seed phrase bytes".encodeToByteArray()
        val blob = crypto.encryptSeed(
            seed = seed,
            aesKey = crypto.generateAesKey(),
            walletId = UserWalletId(WALLET_ID),
            cardInstanceId = "card-instance-id",
            backupSetId = "backup-set-id",
            localKeyId = "local-key-id",
        )

        assertThrows(AEADBadTagException::class.java) {
            crypto.decryptSeed(blob, crypto.generateAesKey())
        }
    }

    private companion object {
        const val WALLET_ID = "00112233445566778899AABBCCDDEEFF"
        const val WRONG_WALLET_ID = "FFEEDDCCBBAA99887766554433221100"
    }
}
