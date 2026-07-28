package com.tangem.operations

import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.card.Card
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.TangemSdkError
import com.tangem.common.services.secure.SecureStorage

interface CommandResponse

open class ScanTask(
    override val allowsRequestAccessCodeFromRepository: Boolean = true,
) : CardSessionRunnable<Card> {

    override fun run(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        val card = session.environment.card
        if (card == null) {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
        } else {
            callback(CompletionResult.Success(card))
        }
    }
}

enum class PreflightReadMode {
    FullCardRead,
    FullCardReadWithAccessCodeCheck,
    ReadCardOnly,
}

open class PreflightReadTask(
    private val readMode: PreflightReadMode = PreflightReadMode.FullCardRead,
    private val filter: Any? = null,
    private val secureStorage: SecureStorage? = null,
) : CardSessionRunnable<Card> {

    override fun run(session: CardSession, callback: (result: CompletionResult<Card>) -> Unit) {
        val card = session.environment.card
        if (card == null) {
            callback(CompletionResult.Failure(TangemSdkError.MissingPreflightRead()))
        } else {
            callback(CompletionResult.Success(card))
        }
    }
}

data class GenerateOTPResponse(
    val rootOTP: ByteArray = byteArrayOf(),
    val rootOTPCounter: Int = 0,
) : CommandResponse

open class GenerateOTPCommand : CardSessionRunnable<GenerateOTPResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<GenerateOTPResponse>) -> Unit) {
        callback(CompletionResult.Success(GenerateOTPResponse()))
    }
}
