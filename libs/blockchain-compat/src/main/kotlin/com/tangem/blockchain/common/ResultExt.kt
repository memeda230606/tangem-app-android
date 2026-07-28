package com.tangem.blockchain.common

import com.tangem.blockchain.extensions.Result
import com.tangem.common.core.TangemError

fun Throwable.toBlockchainSdkError(): BlockchainSdkError = BlockchainSdkError.WrappedThrowable(this)

fun <T> Result.Companion.fromTangemSdkError(error: TangemError): Result<T> =
    Result.Failure(BlockchainSdkError.WrappedTangemError(error))
