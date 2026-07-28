package com.tangem.blockchain.common.network.providers

sealed class ProviderType {
    data class Public(val url: String) : ProviderType()
    object NowNodes : ProviderType()
    object GetBlock : ProviderType()
    object QuickNode : ProviderType()
    object Alchemy : ProviderType()
    object Mock : ProviderType()
    object Blink : ProviderType()

    sealed class BitcoinLike : ProviderType() {
        object Blockchair : BitcoinLike()
        object Blockcypher : BitcoinLike()
    }

    sealed class Cardano : ProviderType() {
        object Adalite : Cardano()
        object Rosetta : Cardano()
    }

    sealed class Chia : ProviderType() {
        object FireAcademy : Chia()
        object Tangem : Chia()
        object TangemNew : Chia()
    }

    sealed class EthereumLike : ProviderType() {
        object Infura : EthereumLike()
    }

    sealed class Hedera : ProviderType() {
        object Arkhia : Hedera()
    }

    sealed class Kaspa : ProviderType() {
        object SecondaryAPI : Kaspa()
    }

    sealed class Solana : ProviderType() {
        object Official : Solana()
    }

    sealed class Ton : ProviderType() {
        object TonCentral : Ton()
    }

    sealed class Tron : ProviderType() {
        object TronGrid : Tron()
    }

    sealed class Bittensor : ProviderType() {
        object Dwellir : Bittensor()
        object Onfinality : Bittensor()
    }

    sealed class AlephZero : ProviderType() {
        object Dwellir : AlephZero()
    }

    sealed class Koinos : ProviderType() {
        object KoinosPro : Koinos()
    }

    sealed class Alephium : ProviderType() {
        object Tangem : Alephium()
    }

    sealed class PolkadotLike : ProviderType() {
        object Tatum : PolkadotLike()
    }
}
