package com.tangem.crypto.operations

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object AESCipherOperations {

    const val KEY_ALGORITHM = "AES"

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    fun initEncryptionCipher(secretKey: SecretKey): Cipher {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)

        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
    }

    fun initDecryptionCipher(secretKey: SecretKey, iv: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
    }

    fun encrypt(cipher: Cipher, decryptedData: ByteArray): ByteArray = cipher.doFinal(decryptedData)

    fun decrypt(cipher: Cipher, encryptedData: ByteArray): ByteArray = cipher.doFinal(encryptedData)
}
