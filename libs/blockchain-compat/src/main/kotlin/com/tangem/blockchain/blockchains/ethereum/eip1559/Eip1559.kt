package com.tangem.blockchain.blockchains.ethereum.eip1559

import com.tangem.blockchain.common.Blockchain

val Blockchain.isSupportEIP1559: Boolean
    get() = isEvm()
