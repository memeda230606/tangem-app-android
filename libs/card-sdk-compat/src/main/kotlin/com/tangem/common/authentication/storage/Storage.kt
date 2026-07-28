package com.tangem.common.authentication.storage

import com.tangem.common.authentication.keystore.KeystoreManager
import com.tangem.common.services.secure.SecureStorage

class AuthenticatedStorage(
    private val secureStorage: SecureStorage,
    private val keystoreManager: KeystoreManager,
) {
    fun store(keyAlias: String, data: ByteArray) {
        secureStorage.store(data = data, account = keyAlias)
    }

    fun get(keyAliases: List<String>): Map<String, ByteArray> {
        return keyAliases.mapNotNull { alias -> secureStorage.get(alias)?.let { alias to it } }.toMap()
    }

    fun delete(keyAlias: String) {
        secureStorage.delete(keyAlias)
    }
}
