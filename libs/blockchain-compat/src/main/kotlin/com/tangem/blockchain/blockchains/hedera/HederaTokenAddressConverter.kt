package com.tangem.blockchain.blockchains.hedera

class HederaTokenAddressConverter {
    suspend fun resolveTokenId(contractAddress: String, resolver: suspend (String) -> String): String {
        val value = contractAddress.trim()
        if (value.isEmpty()) return value
        return if (value.startsWith("0x", ignoreCase = true)) resolver(value) else value
    }
}

class HederaContractIdResolver(private val baseUrl: String) {
    suspend fun resolve(address: String): String {
        if (!address.startsWith("0x", ignoreCase = true)) return address
        return "0.0.${address.removePrefix("0x").removePrefix("0X").takeLast(8).toLongOrNull(16).orZero()}"
    }

    private fun Long?.orZero(): Long = this ?: 0L
}
