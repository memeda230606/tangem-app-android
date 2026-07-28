package com.tangem.blockchain.blockchains.cardano

import com.tangem.crypto.hdWallet.DerivationPath

object CardanoUtils {
    fun extendedDerivationPath(derivationPath: DerivationPath): DerivationPath {
        return DerivationPath("${derivationPath.rawPath}/2")
    }
}
