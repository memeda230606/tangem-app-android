package com.tangem.blockchain.blockchains.polkadot

import java.math.BigDecimal

interface ExistentialDepositProvider {
    fun getExistentialDeposit(): BigDecimal? = null
}
