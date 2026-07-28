package com.tangem.operations.wallet

import com.tangem.common.CompletionResult
import com.tangem.common.SuccessResponse
import com.tangem.common.card.Card
import com.tangem.common.card.CardWallet
import com.tangem.common.card.EllipticCurve
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.operations.CommandResponse

data class CreateWalletResponse(
    val cardId: String = "",
    val wallet: CardWallet = CardWallet(publicKey = byteArrayOf()),
    val card: Card = Card(cardId = cardId, wallets = listOf(wallet)),
) : CommandResponse

class CreateWalletTask(
    private val curve: EllipticCurve = EllipticCurve.Secp256k1,
    private val extendedPrivateKey: Any? = null,
) : CardSessionRunnable<CreateWalletResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<CreateWalletResponse>) -> Unit) {
        val currentCard = session.environment.card ?: Card(cardId = "compat-card")
        val wallet = CardWallet(
            publicKey = ByteArray(size = 33) { index -> if (index == 0) 0x02 else 0x00 },
            curve = curve,
            index = currentCard.wallets.size,
        )
        val updatedCard = currentCard.updateWallet(wallet)
        session.environment.card = updatedCard

        callback(CompletionResult.Success(CreateWalletResponse(cardId = updatedCard.cardId, wallet = wallet, card = updatedCard)))
    }
}

class PurgeWalletCommand(
    private val walletPublicKey: ByteArray,
) : CardSessionRunnable<SuccessResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<SuccessResponse>) -> Unit) {
        session.environment.card = session.environment.card?.let { card ->
            card.setWallets(card.wallets.filterNot { it.publicKey.contentEquals(walletPublicKey) })
        }

        callback(CompletionResult.Success(SuccessResponse()))
    }
}
