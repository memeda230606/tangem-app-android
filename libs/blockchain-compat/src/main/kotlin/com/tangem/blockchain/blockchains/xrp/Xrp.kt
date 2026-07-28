package com.tangem.blockchain.blockchains.xrp

import com.tangem.blockchain.common.TransactionExtras

data class XrpTaggedAddress(
    val address: String,
    val destinationTag: Long?,
)

object XrpAddressService {
    fun decodeXAddress(xAddress: String): XrpTaggedAddress? = XrpTaggedAddress(address = xAddress, destinationTag = null)
}

object XrpTransactionBuilder {
    data class XrpTransactionExtras(val destinationTag: Long) : TransactionExtras
}
class XrpTokenAddressConverter {
    fun normalizeAddress(contractAddress: String): String? = contractAddress.ifBlank { null }
}
