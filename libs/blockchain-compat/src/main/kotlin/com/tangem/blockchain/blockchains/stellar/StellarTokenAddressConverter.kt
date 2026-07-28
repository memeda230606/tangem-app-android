package com.tangem.blockchain.blockchains.stellar

class StellarTokenAddressConverter {
    fun normalizeAddress(contractAddress: String): String? = contractAddress.ifBlank { null }
}
