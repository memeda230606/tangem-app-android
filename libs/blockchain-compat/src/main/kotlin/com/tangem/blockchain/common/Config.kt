package com.tangem.blockchain.common

data class BlockchainSdkConfig(
    val blockchairCredentials: BlockchairCredentials = BlockchairCredentials(),
    val blockcypherTokens: Set<String> = emptySet(),
    val quickNodeSolanaCredentials: QuickNodeCredentials = QuickNodeCredentials(),
    val quickNodeBscCredentials: QuickNodeCredentials = QuickNodeCredentials(),
    val quickNodePlasmaCredentials: QuickNodeCredentials = QuickNodeCredentials(),
    val quickNodeMonadCredentials: QuickNodeCredentials = QuickNodeCredentials(),
    val infuraProjectId: String = "",
    val tronGridApiKey: String = "",
    val nowNodeCredentials: NowNodeCredentials = NowNodeCredentials(),
    val getBlockCredentials: GetBlockCredentials = GetBlockCredentials(),
    val kaspaSecondaryApiUrl: String = "",
    val tonCenterCredentials: TonCenterCredentials = TonCenterCredentials(),
    val chiaFireAcademyApiKey: String = "",
    val chiaTangemApiKey: String = "",
    val hederaArkhiaApiKey: String = "",
    val polygonScanApiKey: String = "",
    val bittensorDwellirApiKey: String = "",
    val bittensorOnfinalityApiKey: String = "",
    val dwellirApiKey: String = "",
    val koinosProApiKey: String = "",
    val alephiumApiKey: String = "",
    val moralisApiKey: String = "",
    val etherscanApiKey: String = "",
    val blinkApiKey: String = "",
    val tatumApiKey: String = "",
    val alchemyApiKey: String = "",
)

data class BlockchairCredentials(
    val apiKey: List<String> = emptyList(),
    val authToken: String = "",
)

data class QuickNodeCredentials(
    val apiKey: String = "",
    val subdomain: String = "",
)

data class NowNodeCredentials(
    val apiKey: String = "",
)

data class TonCenterCredentials(
    val mainnetApiKey: String = "",
    val testnetApiKey: String = "",
)

data class GetBlockAccessToken(
    val jsonRpc: String = "",
    val rest: String = "",
    val rosetta: String = "",
    val blockBookRest: String = "",
)

data class GetBlockCredentials(
    val xrp: GetBlockAccessToken = GetBlockAccessToken(),
    val cardano: GetBlockAccessToken = GetBlockAccessToken(),
    val avalanche: GetBlockAccessToken = GetBlockAccessToken(),
    val eth: GetBlockAccessToken = GetBlockAccessToken(),
    val etc: GetBlockAccessToken = GetBlockAccessToken(),
    val fantom: GetBlockAccessToken = GetBlockAccessToken(),
    val rsk: GetBlockAccessToken = GetBlockAccessToken(),
    val bsc: GetBlockAccessToken = GetBlockAccessToken(),
    val polygon: GetBlockAccessToken = GetBlockAccessToken(),
    val gnosis: GetBlockAccessToken = GetBlockAccessToken(),
    val cronos: GetBlockAccessToken = GetBlockAccessToken(),
    val solana: GetBlockAccessToken = GetBlockAccessToken(),
    val ton: GetBlockAccessToken = GetBlockAccessToken(),
    val tron: GetBlockAccessToken = GetBlockAccessToken(),
    val cosmos: GetBlockAccessToken = GetBlockAccessToken(),
    val near: GetBlockAccessToken = GetBlockAccessToken(),
    val aptos: GetBlockAccessToken = GetBlockAccessToken(),
    val dogecoin: GetBlockAccessToken = GetBlockAccessToken(),
    val litecoin: GetBlockAccessToken = GetBlockAccessToken(),
    val dash: GetBlockAccessToken = GetBlockAccessToken(),
    val bitcoin: GetBlockAccessToken = GetBlockAccessToken(),
    val algorand: GetBlockAccessToken = GetBlockAccessToken(),
    val zkSyncEra: GetBlockAccessToken = GetBlockAccessToken(),
    val polygonZkEvm: GetBlockAccessToken = GetBlockAccessToken(),
    val base: GetBlockAccessToken = GetBlockAccessToken(),
    val blast: GetBlockAccessToken = GetBlockAccessToken(),
    val filecoin: GetBlockAccessToken = GetBlockAccessToken(),
    val arbitrum: GetBlockAccessToken = GetBlockAccessToken(),
    val bitcoinCash: GetBlockAccessToken = GetBlockAccessToken(),
    val kusama: GetBlockAccessToken = GetBlockAccessToken(),
    val moonbeam: GetBlockAccessToken = GetBlockAccessToken(),
    val optimism: GetBlockAccessToken = GetBlockAccessToken(),
    val polkadot: GetBlockAccessToken = GetBlockAccessToken(),
    val shibarium: GetBlockAccessToken = GetBlockAccessToken(),
    val sui: GetBlockAccessToken = GetBlockAccessToken(),
    val telos: GetBlockAccessToken = GetBlockAccessToken(),
    val tezos: GetBlockAccessToken = GetBlockAccessToken(),
    val monad: GetBlockAccessToken = GetBlockAccessToken(),
    val stellar: GetBlockAccessToken = GetBlockAccessToken(),
)
