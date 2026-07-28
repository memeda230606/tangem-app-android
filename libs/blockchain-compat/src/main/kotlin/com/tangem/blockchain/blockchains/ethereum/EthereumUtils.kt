package com.tangem.blockchain.blockchains.ethereum

import java.security.MessageDigest

object EthereumUtils {
    const val ZERO_ADDRESS: String = "0x0000000000000000000000000000000000000000"

    fun ByteArray.toKeccak(): ByteArray {
        return MessageDigest.getInstance("SHA3-256").digest(this)
    }

    fun makeTypedDataHash(data: String): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(data.encodeToByteArray())
    }
}
