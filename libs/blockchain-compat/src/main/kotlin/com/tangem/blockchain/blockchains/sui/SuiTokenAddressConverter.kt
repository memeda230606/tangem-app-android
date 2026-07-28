package com.tangem.blockchain.blockchains.sui

class SuiTokenAddressConverter {
    fun normalizeAddress(contractAddress: String): String? {
        val value = contractAddress.trim()
        if (value.isEmpty()) return null
        return if (value.startsWith("0x", ignoreCase = true)) value.lowercase() else "0x${value.lowercase()}"
    }
}
