package com.tangem.hot.sdk.android.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AESEncryptionProtocol {

    private const val VERSION: Byte = 1
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val KEY_LENGTH_BITS = 256
    private const val ITERATIONS = 120_000
    private const val TAG_LENGTH_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encryptWithPassword(password: CharArray, content: ByteArray): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        val encrypted = cipher.doFinal(content)

        return byteArrayOf(VERSION) + salt + iv + encrypted
    }

    fun decryptWithPassword(password: CharArray, encrypted: ByteArray): ByteArray {
        require(encrypted.size > 1 + SALT_LENGTH_BYTES + IV_LENGTH_BYTES) { "Invalid encrypted payload" }
        require(encrypted.first() == VERSION) { "Unsupported encrypted payload version" }

        val saltStart = 1
        val ivStart = saltStart + SALT_LENGTH_BYTES
        val encryptedStart = ivStart + IV_LENGTH_BYTES
        val salt = encrypted.copyOfRange(saltStart, ivStart)
        val iv = encrypted.copyOfRange(ivStart, encryptedStart)
        val ciphertext = encrypted.copyOfRange(encryptedStart, encrypted.size)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }

        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val keySpec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
