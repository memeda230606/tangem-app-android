package com.tangem.tap.domain.nfc

import com.tangem.common.authentication.storage.AuthenticatedStorage
import com.tangem.common.services.secure.SecureStorage
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.wallets.nfc.NfcWalletAesKey
import com.tangem.domain.wallets.nfc.NfcWalletKeyRepository
import com.tangem.hot.sdk.android.crypto.AESEncryptionProtocol
import com.tangem.utils.coroutines.CoroutineDispatcherProvider
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

internal class DefaultNfcWalletKeyRepository(
    private val authenticatedStorage: AuthenticatedStorage,
    private val secureStorage: SecureStorage,
    private val dispatchers: CoroutineDispatcherProvider,
) : NfcWalletKeyRepository {

    override suspend fun save(key: NfcWalletAesKey, accessCode: CharArray?) {
        withContext(dispatchers.io) {
            val encodedKey = key.encode()

            authenticatedStorage.store(
                keyAlias = StorageKey.Biometric(key.walletId).name,
                data = encodedKey,
            )

            if (accessCode == null) {
                secureStorage.delete(StorageKey.PasswordEncrypted(key.walletId).name)
            } else {
                secureStorage.store(
                    account = StorageKey.PasswordEncrypted(key.walletId).name,
                    data = AESEncryptionProtocol.encryptWithPassword(
                        password = accessCode,
                        content = encodedKey,
                    ),
                )
            }
        }
    }

    override suspend fun get(
        userWalletId: UserWalletId,
        localKeyId: String,
        accessCode: CharArray?,
    ): NfcWalletAesKey? {
        return withContext(dispatchers.io) {
            val encoded = if (accessCode == null) {
                authenticatedStorage.get(listOf(StorageKey.Biometric(userWalletId).name))
                    .values
                    .firstOrNull()
            } else {
                secureStorage.get(StorageKey.PasswordEncrypted(userWalletId).name)
                    ?.let { AESEncryptionProtocol.decryptWithPassword(accessCode, it) }
            } ?: return@withContext null

            encoded.decodeToKey(userWalletId)
                ?.takeIf { it.localKeyId == localKeyId }
        }
    }

    override suspend fun delete(userWalletId: UserWalletId) {
        withContext(dispatchers.io) {
            authenticatedStorage.delete(StorageKey.Biometric(userWalletId).name)
            secureStorage.delete(StorageKey.PasswordEncrypted(userWalletId).name)
        }
    }

    private fun NfcWalletAesKey.encode(): ByteArray {
        val localKeyIdBytes = localKeyId.encodeToByteArray()
        return ByteBuffer.allocate(Int.SIZE_BYTES + localKeyIdBytes.size + key.size)
            .putInt(localKeyIdBytes.size)
            .put(localKeyIdBytes)
            .put(key)
            .array()
    }

    private fun ByteArray.decodeToKey(walletId: UserWalletId): NfcWalletAesKey? {
        if (size < Int.SIZE_BYTES) return null

        val buffer = ByteBuffer.wrap(this)
        val localKeyIdSize = buffer.int
        if (localKeyIdSize < 0 || localKeyIdSize > buffer.remaining()) return null

        val localKeyIdBytes = ByteArray(localKeyIdSize)
        buffer.get(localKeyIdBytes)

        val key = ByteArray(buffer.remaining())
        buffer.get(key)

        return NfcWalletAesKey(
            walletId = walletId,
            localKeyId = localKeyIdBytes.decodeToString(),
            key = key,
        )
    }

    private sealed interface StorageKey {
        val name: String

        class PasswordEncrypted(userWalletId: UserWalletId) : StorageKey {
            override val name: String = "nfc_wallet_aes_key_encrypted_${userWalletId.stringValue}"
        }

        class Biometric(userWalletId: UserWalletId) : StorageKey {
            override val name: String = "nfc_wallet_aes_key_biometric_${userWalletId.stringValue}"
        }
    }
}
