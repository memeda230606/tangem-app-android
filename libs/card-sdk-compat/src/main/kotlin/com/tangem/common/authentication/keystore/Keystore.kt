package com.tangem.common.authentication.keystore

import javax.crypto.SecretKey
import java.util.concurrent.ConcurrentHashMap

interface KeystoreManager {
    data class MasterKeyConfig(
        val userAuthenticationRequired: Boolean = false,
        val invalidatedByBiometricEnrollment: Boolean = true,
        val authenticationValidityDurationSeconds: Int = -1,
    )

    suspend fun get(masterKeyConfig: MasterKeyConfig, keyAlias: String, forceAuthentication: Boolean): SecretKey? = null

    suspend fun get(
        masterKeyConfig: MasterKeyConfig,
        keyAliases: Set<String>,
        forceAuthentication: Boolean,
    ): Map<String, SecretKey> = keyAliases.mapNotNull { alias -> get(masterKeyConfig, alias, forceAuthentication)?.let { alias to it } }
        .toMap()

    suspend fun store(masterKeyConfig: MasterKeyConfig, keyAlias: String, key: SecretKey) = Unit
}

class DummyKeystoreManager : KeystoreManager {
    private val keys = ConcurrentHashMap<String, SecretKey>()

    override suspend fun get(
        masterKeyConfig: KeystoreManager.MasterKeyConfig,
        keyAlias: String,
        forceAuthentication: Boolean,
    ): SecretKey? = keys[keyAlias]

    override suspend fun store(masterKeyConfig: KeystoreManager.MasterKeyConfig, keyAlias: String, key: SecretKey) {
        keys[keyAlias] = key
    }
}
