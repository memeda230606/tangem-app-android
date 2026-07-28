package com.tangem.blockchain.yieldsupply

interface YieldSupplyProvider {
    suspend fun isSupported(): Boolean = false
}
