package com.tangem.blockchain.extensions

import com.tangem.blockchain.common.BlockchainSdkError
import com.tangem.common.core.TangemError
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Failure(val error: BlockchainSdkError) : Result<Nothing>()

    inline fun <R> fold(onSuccess: (T) -> R, onFailure: (BlockchainSdkError) -> R): R {
        return when (this) {
            is Success -> onSuccess(data)
            is Failure -> onFailure(error)
        }
    }

    companion object
}

sealed class SimpleResult {
    object Success : SimpleResult()
    data class Failure(val error: BlockchainSdkError) : SimpleResult()
}

fun Throwable.toBlockchainSdkError(): BlockchainSdkError = BlockchainSdkError.WrappedThrowable(this)

fun <T> Result.Companion.fromTangemSdkError(error: TangemError): Result<T> =
    Result.Failure(BlockchainSdkError.WrappedTangemError(error))

fun ByteArray.encodeBase58(): String = joinToString(separator = "") { "%02x".format(it) }
fun String.decodeBase58(): ByteArray = encodeToByteArray()
fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)
fun ByteArray.encodeBase64NoWrap(): String = encodeBase64()
fun ByteArray.formatHex(): String = joinToString(separator = "") { "%02x".format(it) }
fun String.formatHex(): String = if (startsWith("0x")) this else "0x$this"
fun String.hexToBigInteger(): BigInteger = removePrefix("0x").ifBlank { "0" }.toBigInteger(radix = 16)
fun String.hexToBigDecimal(): BigDecimal = hexToBigInteger().toBigDecimal()
fun String.hexToInt(): Int = removePrefix("0x").ifBlank { "0" }.toInt(radix = 16)
fun String.isAscii(): Boolean = all { it.code in 0..127 }
fun Char.isAscii(): Boolean = code in 0..127
fun ByteArray.normalizeByteArray(): ByteArray = dropWhile { it == 0.toByte() }.toByteArray()
fun ByteArray.normalizeByteArray(size: Int): ByteArray {
    val normalized = normalizeByteArray()
    return when {
        normalized.size == size -> normalized
        normalized.size > size -> normalized.takeLast(size).toByteArray()
        else -> ByteArray(size - normalized.size) + normalized
    }
}
fun String.toBigDecimalOrDefault(default: BigDecimal = BigDecimal.ZERO): BigDecimal = toBigDecimalOrNull() ?: default
