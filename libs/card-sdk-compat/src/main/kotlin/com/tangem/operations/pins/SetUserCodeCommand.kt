package com.tangem.operations.pins

import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable

class SetUserCodeCommand private constructor(
    private val code: String?,
) : CardSessionRunnable<SuccessResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<SuccessResponse>) -> Unit) {
        callback(CompletionResult.Success(SuccessResponse()))
    }

    companion object {
        fun changeAccessCode(accessCode: String?): SetUserCodeCommand = SetUserCodeCommand(accessCode)

        fun changePasscode(passcode: String?): SetUserCodeCommand = SetUserCodeCommand(passcode)

        fun resetUserCodes(): SetUserCodeCommand = SetUserCodeCommand(null)
    }
}
