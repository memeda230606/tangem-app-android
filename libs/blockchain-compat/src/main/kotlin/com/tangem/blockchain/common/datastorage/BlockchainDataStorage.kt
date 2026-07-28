package com.tangem.blockchain.common.datastorage

interface BlockchainDataStorage {
    suspend fun getOrNull(key: String): String?
    suspend fun store(key: String, value: String)
    suspend fun remove(key: String)
}
