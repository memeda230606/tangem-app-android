package com.tangem.operations.usersetttings

import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable

class SetUserCodeRecoveryAllowedTask(
    private val enabled: Boolean,
) : CardSessionRunnable<SuccessResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<SuccessResponse>) -> Unit) {
        callback(CompletionResult.Success(SuccessResponse()))
    }
}
