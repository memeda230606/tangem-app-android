package com.tangem.data.tokens

import arrow.core.Either
import arrow.core.right
import com.tangem.domain.tokens.MultiWalletAccountListFetcher
import com.tangem.utils.logging.TangemLogger
import javax.inject.Inject

/** Keeps the locally initialized default account list in mocked builds. */
internal class MockMultiWalletAccountListFetcher @Inject constructor() : MultiWalletAccountListFetcher {

    override suspend fun invoke(params: MultiWalletAccountListFetcher.Params): Either<Throwable, Unit> {
        TangemLogger.i("[MockedAccounts] Local wallet accounts retained")
        return Unit.right()
    }
}
