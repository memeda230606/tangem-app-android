package com.tangem.blockchain.blockchains.cardano

class CardanoTokenAddressConverter {
    fun convertToFingerprint(contractAddress: String, symbol: String?): String? {
        return contractAddress.ifBlank { null }
    }
}
