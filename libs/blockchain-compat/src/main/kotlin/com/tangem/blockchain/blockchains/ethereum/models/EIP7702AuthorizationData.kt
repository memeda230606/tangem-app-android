package com.tangem.blockchain.blockchains.ethereum.models

import java.math.BigInteger

data class EIP7702AuthorizationData(
    val data: ByteArray = byteArrayOf(),
    val executorAddress: String = "",
    val nonce: BigInteger = BigInteger.ZERO,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EIP7702AuthorizationData) return false
        return data.contentEquals(other.data) &&
            executorAddress == other.executorAddress &&
            nonce == other.nonce
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + executorAddress.hashCode()
        result = 31 * result + nonce.hashCode()
        return result
    }
}
