package com.tangem.blockchain.blockchains.ethereum.models

data class EthereumCompiledTransaction(
    val from: String,
    val to: String,
    val data: String,
    val value: String,
    val nonce: Int,
    val chainId: Int,
    val gasLimit: String,
    val gasPrice: String?,
    val maxFeePerGas: String?,
    val maxPriorityFeePerGas: String?,
    val type: Int,
)
