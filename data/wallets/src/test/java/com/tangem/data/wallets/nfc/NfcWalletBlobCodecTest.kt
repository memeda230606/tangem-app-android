package com.tangem.data.wallets.nfc

import com.google.common.truth.Truth.assertThat
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.wallets.nfc.NfcWalletBlob
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class NfcWalletBlobCodecTest {

    private val codec = NfcWalletBlobCodec()

    @Test
    fun `encode and decode round trip`() {
        val blob = createBlob()

        val actual = codec.decode(codec.encode(blob))

        assertThat(actual).isEqualTo(blob.copy(checksum = actual.checksum))
        assertThat(actual.checksum).hasLength(32)
    }

    @Test
    fun `decode fails for corrupted payload`() {
        val payload = codec.encode(createBlob())
        val corrupted = payload.copyOf().also { it[it.lastIndex - 1] = (it[it.lastIndex - 1].toInt() xor 1).toByte() }

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(corrupted)
        }
    }

    @Test
    fun `decode fails for invalid magic`() {
        val payload = codec.encode(createBlob())
        val corrupted = payload.copyOf().also { it[0] = 0x00 }

        assertThrows(IllegalArgumentException::class.java) {
            codec.decode(corrupted)
        }
    }

    private fun createBlob(): NfcWalletBlob {
        return NfcWalletBlob(
            version = NfcWalletBlobCodec.CURRENT_VERSION,
            walletId = UserWalletId(WALLET_ID),
            cardInstanceId = "card-instance-id",
            backupSetId = "backup-set-id",
            localKeyId = "local-key-id",
            nonce = ByteArray(12) { it.toByte() },
            ciphertext = ByteArray(24) { (it + 1).toByte() },
            tag = ByteArray(16) { (it + 2).toByte() },
            checksum = ByteArray(0),
        )
    }

    private companion object {
        const val WALLET_ID = "00112233445566778899AABBCCDDEEFF"
    }
}
