package com.tangem.operations.read

import com.tangem.common.CompletionResult
import com.tangem.common.card.CardWallet
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.operations.CommandResponse

data class ReadWalletsListResponse(
    val wallets: List<CardWallet> = emptyList(),
) : CommandResponse

class ReadWalletsListCommand : CardSessionRunnable<ReadWalletsListResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<ReadWalletsListResponse>) -> Unit) {
        callback(CompletionResult.Success(ReadWalletsListResponse(wallets = session.environment.card?.wallets.orEmpty())))
    }
}
