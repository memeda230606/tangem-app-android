package com.tangem.blockchain.common.smartcontract

import com.tangem.blockchain.blockchains.ethereum.tokenmethods.ApprovalERC20TokenCallData
import com.tangem.blockchain.blockchains.ethereum.tokenmethods.TransferERC20TokenCallData
import com.tangem.blockchain.common.Amount
import com.tangem.blockchain.common.Blockchain
import com.tangem.blockchain.nft.models.NFTAsset

interface SmartContractCallData {
    val data: ByteArray
        get() = byteArrayOf()

    val dataHex: String
        get() = data.joinToString(separator = "") { "%02x".format(it) }
}

data class CompiledSmartContractCallData(
    override val data: ByteArray = byteArrayOf(),
) : SmartContractCallData {
    override fun equals(other: Any?): Boolean {
        return other is CompiledSmartContractCallData && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}

object SmartContractCallDataProviderFactory {
    fun getTokenTransferCallData(
        destinationAddress: String,
        amount: Amount,
        blockchain: Blockchain,
    ): SmartContractCallData {
        return TransferERC20TokenCallData(destination = destinationAddress, amount = amount)
    }

    fun getApprovalCallData(
        spenderAddress: String,
        amount: Amount? = null,
        blockchain: Blockchain = Blockchain.Unknown,
    ): SmartContractCallData {
        return ApprovalERC20TokenCallData(spenderAddress = spenderAddress, amount = amount)
    }

    fun getNFTTransferCallData(
        contractAddress: String,
        tokenId: String,
        sourceAddress: String,
        destinationAddress: String,
        blockchain: Blockchain,
    ): SmartContractCallData {
        return CompiledSmartContractCallData(
            data = "nft:$contractAddress:$tokenId:$sourceAddress:$destinationAddress:${blockchain.id}".encodeToByteArray(),
        )
    }

    fun getNFTTransferCallData(
        destinationAddress: String,
        ownerAddress: String,
        nftAsset: NFTAsset,
        blockchain: Blockchain,
    ): SmartContractCallData {
        val contractAddress = when (val identifier = nftAsset.identifier) {
            is NFTAsset.Identifier.EVM -> identifier.tokenAddress
            is NFTAsset.Identifier.Solana -> identifier.tokenAddress
            is NFTAsset.Identifier.TON -> identifier.tokenAddress
            NFTAsset.Identifier.Unknown -> ""
        }
        val tokenId = when (val identifier = nftAsset.identifier) {
            is NFTAsset.Identifier.EVM -> identifier.tokenId.toString()
            else -> nftAsset.identifier.hashCode().toString()
        }
        return getNFTTransferCallData(
            contractAddress = contractAddress,
            tokenId = tokenId,
            sourceAddress = ownerAddress,
            destinationAddress = destinationAddress,
            blockchain = blockchain,
        )
    }
}
