package com.tangem.blockchain.common.address

data class Address(
    val value: String,
    val type: AddressType = AddressType.Default,
)

enum class AddressType {
    Default,
    Legacy,
    Segwit,
}

class EstimationFeeAddressFactory {
    fun makeAddress(blockchain: com.tangem.blockchain.common.Blockchain): String {
        return when {
            blockchain.isEvm() -> "0x0000000000000000000000000000000000000000"
            else -> blockchain.id
        }
    }
}
