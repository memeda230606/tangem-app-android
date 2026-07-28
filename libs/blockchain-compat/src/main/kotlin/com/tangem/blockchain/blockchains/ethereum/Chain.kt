package com.tangem.blockchain.blockchains.ethereum

import com.tangem.blockchain.common.Blockchain

enum class Chain(
    val id: Int,
    val blockchain: Blockchain,
) {
    Ethereum(1, Blockchain.Ethereum),
    EthereumTestnet(11155111, Blockchain.EthereumTestnet),
    BSC(56, Blockchain.BSC),
    BSCTestnet(97, Blockchain.BSCTestnet),
    Polygon(137, Blockchain.Polygon),
    PolygonTestnet(80002, Blockchain.PolygonTestnet),
    Arbitrum(42161, Blockchain.Arbitrum),
    ArbitrumTestnet(421614, Blockchain.ArbitrumTestnet),
    Optimism(10, Blockchain.Optimism),
    OptimismTestnet(11155420, Blockchain.OptimismTestnet),
    Base(8453, Blockchain.Base),
    BaseTestnet(84532, Blockchain.BaseTestnet),
    Avalanche(43114, Blockchain.Avalanche),
    AvalancheTestnet(43113, Blockchain.AvalancheTestnet),
    Fantom(250, Blockchain.Fantom),
    Gnosis(100, Blockchain.Gnosis),
}
