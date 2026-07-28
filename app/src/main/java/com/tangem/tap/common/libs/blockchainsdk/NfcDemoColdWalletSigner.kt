package com.tangem.tap.common.libs.blockchainsdk

import com.tangem.blockchain.common.TransactionSigner
import com.tangem.blockchain.common.Wallet
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import com.tangem.operations.sign.SignData
import com.tangem.tap.domain.sdk.mocks.NfcDemoLogger

/**
 * Prevents an ordinary-NFC fixture from being treated as a production cryptographic signer.
 * Real signing in the Hybrid build is available only through [com.tangem.data.wallets.hot.TangemHotWalletSigner].
 */
internal object NfcDemoColdWalletSigner : TransactionSigner {

    override suspend fun sign(
        hashes: List<ByteArray>,
        publicKey: Wallet.PublicKey,
    ): CompletionResult<List<ByteArray>> = blocked()

    override suspend fun sign(hash: ByteArray, publicKey: Wallet.PublicKey): CompletionResult<ByteArray> = blocked()

    override suspend fun multiSign(
        dataToSign: List<SignData>,
        publicKey: Wallet.PublicKey,
    ): CompletionResult<Map<ByteArray, ByteArray>> = blocked()

    private fun <T> blocked(): CompletionResult<T> {
        NfcDemoLogger.warning(
            name = "real_signing_blocked",
            details = "walletType=cold reason=ordinary_nfc_is_not_a_signer",
        )

        return CompletionResult.Failure(
            TangemSdkError.ExceptionError(
                IllegalStateException("An ordinary NFC demo card cannot sign a real transaction"),
            ),
        )
    }
}

internal object NfcDemoColdWalletSigningPolicy {

    enum class Mode {
        Block,
        Simulate,
        Real,
    }

    fun resolve(nfcDemoEnabled: Boolean, mockDataSource: Boolean): Mode {
        return when {
            !nfcDemoEnabled -> Mode.Real
            mockDataSource -> Mode.Simulate
            else -> Mode.Block
        }
    }
}
