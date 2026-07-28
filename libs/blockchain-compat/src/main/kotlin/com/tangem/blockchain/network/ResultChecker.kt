package com.tangem.blockchain.network

import com.tangem.blockchain.extensions.Result

object ResultChecker {
    fun isNetworkError(result: Result.Failure): Boolean {
        return result.error.cause is java.io.IOException
    }
}
