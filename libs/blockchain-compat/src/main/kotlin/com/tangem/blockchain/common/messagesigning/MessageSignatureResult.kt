package com.tangem.blockchain.common.messagesigning

data class MessageSignatureResult(
    val address: String,
    val signature: String,
    val messageHash: String? = null,
)
