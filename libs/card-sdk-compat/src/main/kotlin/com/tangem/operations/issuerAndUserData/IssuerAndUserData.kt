package com.tangem.operations.issuerAndUserData

import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.operations.CommandResponse

data class ReadIssuerDataResponse(
    val issuerData: ByteArray = byteArrayOf(),
    val issuerDataSignature: ByteArray? = null,
    val issuerDataCounter: Int? = null,
) : CommandResponse

class ReadIssuerDataCommand : CardSessionRunnable<ReadIssuerDataResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<ReadIssuerDataResponse>) -> Unit) {
        callback(CompletionResult.Success(ReadIssuerDataResponse()))
    }
}

class WriteIssuerDataCommand(
    private val issuerData: ByteArray,
    private val issuerDataSignature: ByteArray,
    private val issuerDataCounter: Int,
    private val issuerPublicKey: ByteArray,
) : CardSessionRunnable<SuccessResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<SuccessResponse>) -> Unit) {
        callback(CompletionResult.Success(SuccessResponse()))
    }
}
