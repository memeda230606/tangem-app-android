package com.tangem.data.wallets.nfc

import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.wallets.nfc.NfcWalletBlob
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class NfcWalletCrypto(
    private val secureRandom: SecureRandom = SecureRandom(),
) {

    fun generateAesKey(): ByteArray {
        return ByteArray(AES_KEY_SIZE_BYTES).also(secureRandom::nextBytes)
    }

    fun encryptSeed(
        seed: ByteArray,
        aesKey: ByteArray,
        walletId: UserWalletId,
        cardInstanceId: String,
        backupSetId: String,
        localKeyId: String,
    ): NfcWalletBlob {
        require(aesKey.size == AES_KEY_SIZE_BYTES) { "NFC wallet AES key must be 256-bit" }

        val nonce = ByteArray(GCM_NONCE_SIZE_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, AES_ALGORITHM), GCMParameterSpec(GCM_TAG_SIZE_BITS, nonce))
        cipher.updateAAD(aad(NfcWalletBlobCodec.CURRENT_VERSION, walletId, cardInstanceId, backupSetId, localKeyId))

        val encrypted = cipher.doFinal(seed)
        val ciphertext = encrypted.copyOfRange(0, encrypted.size - GCM_TAG_SIZE_BYTES)
        val tag = encrypted.copyOfRange(encrypted.size - GCM_TAG_SIZE_BYTES, encrypted.size)

        return NfcWalletBlob(
            version = NfcWalletBlobCodec.CURRENT_VERSION,
            walletId = walletId,
            cardInstanceId = cardInstanceId,
            backupSetId = backupSetId,
            localKeyId = localKeyId,
            nonce = nonce,
            ciphertext = ciphertext,
            tag = tag,
            checksum = ByteArray(0),
        )
    }

    fun decryptSeed(blob: NfcWalletBlob, aesKey: ByteArray): ByteArray {
        require(aesKey.size == AES_KEY_SIZE_BYTES) { "NFC wallet AES key must be 256-bit" }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, AES_ALGORITHM), GCMParameterSpec(GCM_TAG_SIZE_BITS, blob.nonce))
        cipher.updateAAD(aad(blob.version, blob.walletId, blob.cardInstanceId, blob.backupSetId, blob.localKeyId))

        return cipher.doFinal(blob.ciphertext + blob.tag)
    }

    private fun aad(
        version: Int,
        walletId: UserWalletId,
        cardInstanceId: String,
        backupSetId: String,
        localKeyId: String,
    ): ByteArray {
        return listOf(version.toString(), walletId.stringValue, cardInstanceId, backupSetId, localKeyId)
            .joinToString(separator = AAD_SEPARATOR)
            .encodeToByteArray()
    }

    private companion object {
        const val AES_KEY_SIZE_BYTES = 32
        const val GCM_NONCE_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BYTES = 16
        const val GCM_TAG_SIZE_BITS = GCM_TAG_SIZE_BYTES * 8
        const val AES_ALGORITHM = "AES"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val AAD_SEPARATOR = "|"
    }
}
