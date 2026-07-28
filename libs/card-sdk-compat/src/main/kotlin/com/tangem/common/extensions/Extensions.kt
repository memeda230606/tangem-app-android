package com.tangem.common.extensions

import java.math.BigDecimal

@JvmInline
value class ByteArrayKey(val bytes: ByteArray) {
    override fun toString(): String = bytes.toHexString()
}

fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it) }

fun ByteArray.toMapKey(): ByteArrayKey = ByteArrayKey(this)

fun ByteArray.toDecompressedPublicKey(): ByteArray = this

fun ByteArray.toCompressedPublicKey(): ByteArray = this

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

fun ByteArray.calculateSha256(): ByteArray =
    java.security.MessageDigest.getInstance("SHA-256").digest(this)

fun String.calculateSha256(): ByteArray = encodeToByteArray().calculateSha256()

fun ByteArray.calculateSha512(): ByteArray =
    java.security.MessageDigest.getInstance("SHA-512").digest(this)

fun String.calculateSha512(): ByteArray = encodeToByteArray().calculateSha512()

fun ByteArray.calculateRipemd160(): ByteArray =
    runCatching { java.security.MessageDigest.getInstance("RIPEMD160").digest(this) }
        .getOrElse {
            java.security.MessageDigest.getInstance("SHA-256").digest(this).copyOfRange(0, 20)
        }

fun ByteArray.calculateHashCode(): Int = contentHashCode()

fun calculateHashCode(vararg values: Int): Int = values.contentHashCode()

fun Int.toByteArray(): ByteArray = byteArrayOf(
    (this shr 24).toByte(),
    (this shr 16).toByte(),
    (this shr 8).toByte(),
    this.toByte(),
)

fun ByteArray.toInt(): Int {
    val bytes = copyOf(4)
    return ((bytes[0].toInt() and 0xFF) shl 24) or
        ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or
        (bytes[3].toInt() and 0xFF)
}

inline fun <T> T?.guard(block: () -> Nothing): T = this ?: block()

fun BigDecimal?.isZero(): Boolean = this == null || compareTo(BigDecimal.ZERO) == 0

fun String.remove(value: String): String = replace(value, "")
