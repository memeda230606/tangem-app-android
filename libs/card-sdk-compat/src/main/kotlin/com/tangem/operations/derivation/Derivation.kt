package com.tangem.operations.derivation

import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.extensions.ByteArrayKey
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.operations.CommandResponse

class ExtendedPublicKeysMap(
    private val entriesMap: Map<DerivationPath, ExtendedPublicKey> = emptyMap(),
) : Map<DerivationPath, ExtendedPublicKey> by entriesMap

data class DerivationTaskResponse(
    val entries: Map<ByteArrayKey, ExtendedPublicKeysMap> = emptyMap(),
) : CommandResponse

class DeriveMultipleWalletPublicKeysTask(
    private val derivations: Map<ByteArrayKey, List<DerivationPath>> = emptyMap(),
) : CardSessionRunnable<DerivationTaskResponse> {

    override fun run(session: CardSession, callback: (result: CompletionResult<DerivationTaskResponse>) -> Unit) {
        callback(
            CompletionResult.Success(
                DerivationTaskResponse(
                    entries = derivations.mapValues { ExtendedPublicKeysMap() },
                ),
            ),
        )
    }
}

class DeriveWalletPublicKeyTask(
    private val walletPublicKey: ByteArray = byteArrayOf(),
    private val derivationPath: DerivationPath? = null,
) : CardSessionRunnable<ExtendedPublicKey> {

    override fun run(session: CardSession, callback: (result: CompletionResult<ExtendedPublicKey>) -> Unit) {
        callback(
            CompletionResult.Success(
                ExtendedPublicKey(
                    publicKey = walletPublicKey,
                    chainCode = ByteArray(size = 32),
                ),
            ),
        )
    }
}
