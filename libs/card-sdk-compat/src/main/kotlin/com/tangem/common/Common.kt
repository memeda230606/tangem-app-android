package com.tangem.common

import com.tangem.common.core.TangemError
import com.tangem.common.core.TangemSdkError

sealed class CompletionResult<out T> {
    data class Success<out T>(val data: T) : CompletionResult<T>()
    data class Failure(val error: TangemError) : CompletionResult<Nothing>()
}

data class KeyPair(
    val publicKey: ByteArray,
    val privateKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int = 31 * publicKey.contentHashCode() + privateKey.contentHashCode()
}

open class SuccessResponse(
    val cardId: String? = null,
)

typealias CompletionCallback<T> = (result: CompletionResult<T>) -> Unit

inline fun <T> catching(block: () -> T): CompletionResult<T> {
    return try {
        CompletionResult.Success(block())
    } catch (error: TangemError) {
        CompletionResult.Failure(error)
    } catch (throwable: Throwable) {
        CompletionResult.Failure(TangemSdkError.ExceptionError(throwable))
    }
}

inline fun <T, R> CompletionResult<T>.map(transform: (T) -> R): CompletionResult<R> = when (this) {
    is CompletionResult.Success -> CompletionResult.Success(transform(data))
    is CompletionResult.Failure -> this
}

inline fun <T, R> CompletionResult<T>.flatMap(transform: (T) -> CompletionResult<R>): CompletionResult<R> = when (this) {
    is CompletionResult.Success -> transform(data)
    is CompletionResult.Failure -> this
}

inline fun <T> CompletionResult<T>.doOnSuccess(action: (T) -> Unit): CompletionResult<T> {
    if (this is CompletionResult.Success) action(data)
    return this
}

inline fun <T> CompletionResult<T>.doOnFailure(action: (TangemError) -> Unit): CompletionResult<T> {
    if (this is CompletionResult.Failure) action(error)
    return this
}

inline fun <T> CompletionResult<T>.doOnResult(action: (CompletionResult<T>) -> Unit): CompletionResult<T> {
    action(this)
    return this
}

enum class UserCodeType {
    AccessCode,
    Passcode,
}
