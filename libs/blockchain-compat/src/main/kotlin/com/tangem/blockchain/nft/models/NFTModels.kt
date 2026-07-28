package com.tangem.blockchain.nft.models

import java.math.BigDecimal
import java.math.BigInteger

data class NFTCollection(
    val identifier: Identifier,
    val blockchainId: String,
    val name: String? = null,
    val description: String? = null,
    val logoUrl: String? = null,
    val count: Int = assets.size,
    val assets: List<NFTAsset> = emptyList(),
) {
    sealed class Identifier {
        data class EVM(val tokenAddress: String) : Identifier()
        data class TON(val contractAddress: String?) : Identifier()
        data class Solana(val collectionAddress: String?) : Identifier()
        data object Unknown : Identifier()
    }
}

data class NFTAsset(
    val identifier: Identifier,
    val collectionIdentifier: NFTCollection.Identifier,
    val blockchainId: String,
    val contractType: String,
    val owner: String? = null,
    val name: String? = null,
    val description: String? = null,
    val amount: BigInteger? = null,
    val decimals: Int? = null,
    val salePrice: SalePrice? = null,
    val rarity: Rarity? = null,
    val media: Media? = null,
    val traits: List<Trait> = emptyList(),
) {
    sealed class Identifier {
        data class EVM(
            val tokenAddress: String,
            val tokenId: BigInteger,
            val contractType: ContractType,
        ) : Identifier() {
            enum class ContractType {
                ERC1155,
                ERC721,
                Unknown,
            }
        }

        data class TON(val tokenAddress: String) : Identifier()

        data class Solana(
            val tokenAddress: String,
            val tokenStandard: Int?,
        ) : Identifier()

        data object Unknown : Identifier()
    }

    data class SalePrice(
        val symbol: String? = null,
        val value: BigDecimal,
        val decimals: Int? = null,
    )

    data class Rarity(
        val rank: String,
        val label: String,
    )

    data class Media(
        val animationUrl: String? = null,
        val imageUrl: String? = null,
    )

    data class Trait(
        val name: String,
        val value: String,
    )
}
