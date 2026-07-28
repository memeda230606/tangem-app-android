package com.tangem.common.services.secure

import java.util.concurrent.ConcurrentHashMap

interface SecureStorage {
    fun get(account: String): ByteArray?

    fun store(data: ByteArray, account: String)

    fun delete(account: String)

    companion object
}

open class InMemorySecureStorage(
    private val name: String = "default",
) : SecureStorage {

    override fun get(account: String): ByteArray? = storage[storageKey(account)]?.copyOf()

    override fun store(data: ByteArray, account: String) {
        storage[storageKey(account)] = data.copyOf()
    }

    override fun delete(account: String) {
        storage.remove(storageKey(account))
    }

    private fun storageKey(account: String): String = "$name:$account"

    private companion object {
        val storage = ConcurrentHashMap<String, ByteArray>()
    }
}
