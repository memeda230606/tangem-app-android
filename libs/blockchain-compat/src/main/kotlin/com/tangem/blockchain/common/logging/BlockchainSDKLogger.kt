package com.tangem.blockchain.common.logging

interface BlockchainSDKLogger {

    fun log(level: Level, message: String) = Unit

    enum class Level {
        Debug,
        Info,
        Warning,
        Error,
    }
}
