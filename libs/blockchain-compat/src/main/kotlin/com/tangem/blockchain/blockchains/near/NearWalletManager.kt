package com.tangem.blockchain.blockchains.near

import com.tangem.blockchain.common.WalletManager

interface NearWalletManager : WalletManager {
    fun validateAddress(address: String): Boolean = address.isNotBlank()
}
