package com.tangem.blockchain.blockchains.bitcoin.walletconnect.models

data class BitcoinAddressInfo(
    val address: String,
    val publicKey: String? = null,
    val derivationPath: String? = null,
    val metadata: Map<String, Any?>? = null,
)

data class AccountAddress(
    val address: String,
    val publicKey: String? = null,
    val path: String? = null,
    val intention: String? = null,
)

data class SignInput(
    val address: String? = null,
    val index: Int? = null,
    val sighashTypes: List<Int>? = null,
)
