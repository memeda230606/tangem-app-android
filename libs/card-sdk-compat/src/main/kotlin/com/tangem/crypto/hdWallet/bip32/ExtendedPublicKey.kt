package com.tangem.crypto.hdWallet.bip32

import com.tangem.crypto.NetworkType
import java.nio.ByteBuffer

data class ExtendedPublicKey(
    val publicKey: ByteArray,
    val chainCode: ByteArray,
    val depth: Int = 0,
    val parentFingerprint: ByteArray = byteArrayOf(),
    val childNumber: Long = 0,
) {
    fun serialize(networkType: NetworkType): String {
        val version = when (networkType) {
            NetworkType.Mainnet -> "xpub"
            NetworkType.Testnet -> "tpub"
        }

        val metadata = ByteBuffer.allocate(9)
            .put(depth.toByte())
            .put(parentFingerprint.copyOf(4))
            .putInt(childNumber.toInt())
            .array()

        return buildString {
            append(version)
            append(":")
            append(metadata.toHexString())
            append(":")
            append(chainCode.toHexString())
            append(":")
            append(publicKey.toHexString())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtendedPublicKey) return false
        return publicKey.contentEquals(other.publicKey) &&
            chainCode.contentEquals(other.chainCode) &&
            depth == other.depth &&
            parentFingerprint.contentEquals(other.parentFingerprint) &&
            childNumber == other.childNumber
    }

    override fun hashCode(): Int {
        var result = publicKey.contentHashCode()
        result = 31 * result + chainCode.contentHashCode()
        result = 31 * result + depth
        result = 31 * result + parentFingerprint.contentHashCode()
        result = 31 * result + childNumber.hashCode()
        return result
    }
}

private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it) }
