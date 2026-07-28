package com.tangem.blockchain.assetsdiscovery

import com.tangem.blockchain.assetsdiscovery.models.DiscoveredAsset
import com.tangem.blockchain.common.Blockchain
import com.tangem.blockchain.common.BlockchainSdkConfig

class AssetsDiscoveryServiceFactory(
    val config: BlockchainSdkConfig,
    val providerTypes: Map<*, *> = emptyMap<Any, Any>(),
) {
    fun create(blockchain: Blockchain): AssetsDiscoveryService = NoOpAssetsDiscoveryService
}

interface AssetsDiscoveryService {
    suspend fun discoverAssets(address: String): List<DiscoveredAsset>
}

private data object NoOpAssetsDiscoveryService : AssetsDiscoveryService {
    override suspend fun discoverAssets(address: String): List<DiscoveredAsset> = emptyList()
}
