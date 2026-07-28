package com.tangem.data.express

import arrow.core.Either
import arrow.core.right
import com.tangem.domain.core.lce.Lce
import com.tangem.domain.core.utils.lceContent
import com.tangem.domain.express.ExpressServiceFetcher
import com.tangem.domain.express.models.ExpressAsset
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.utils.logging.TangemLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Completes Express initialization locally without requesting the redacted mocked endpoint. */
internal class MockExpressServiceFetcher @Inject constructor() : ExpressServiceFetcher {

    private val statuses = ConcurrentHashMap<UserWalletId, MutableStateFlow<Lce<Throwable, List<ExpressAsset>>>>()

    override suspend fun fetch(
        userWalletId: UserWalletId,
        assetIds: Set<ExpressAsset.ID>,
    ): Either<Throwable, Unit> = serve(userWalletId)

    override suspend fun fetch(
        userWallet: UserWallet,
        assetIds: Set<ExpressAsset.ID>,
    ): Either<Throwable, Unit> = serve(userWallet.walletId)

    override fun getInitializationStatus(userWalletId: UserWalletId): Flow<Lce<Throwable, List<ExpressAsset>>> {
        return status(userWalletId).asStateFlow()
    }

    private fun serve(userWalletId: UserWalletId): Either<Throwable, Unit> {
        status(userWalletId).value = emptyList<ExpressAsset>().lceContent()
        TangemLogger.i("[MockedExpress] Local empty asset list served")
        return Unit.right()
    }

    private fun status(userWalletId: UserWalletId): MutableStateFlow<Lce<Throwable, List<ExpressAsset>>> {
        return statuses.getOrPut(userWalletId) {
            MutableStateFlow(emptyList<ExpressAsset>().lceContent())
        }
    }
}
