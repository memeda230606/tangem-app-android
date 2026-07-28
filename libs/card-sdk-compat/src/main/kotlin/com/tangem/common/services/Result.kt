package com.tangem.common.services

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val error: Throwable = IllegalStateException("Request failed")) : Result<Nothing>()
}

suspend inline fun <T> performRequest(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (throwable: Throwable) {
        Result.Failure(throwable)
    }
}
