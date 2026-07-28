package com.tangem.operations.files

import com.tangem.common.CompletionResult
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.operations.CommandResponse

data class CardFile(
    val data: ByteArray,
    val counter: Int,
    val signature: ByteArray,
) : CommandResponse

class ReadFilesTask(
    private val fileName: String,
    private val walletPublicKey: ByteArray?,
) : CardSessionRunnable<List<CardFile>> {

    override fun run(session: CardSession, callback: (result: CompletionResult<List<CardFile>>) -> Unit) {
        callback(CompletionResult.Success(emptyList()))
    }
}

data class PreparedFileHashes(
    val startingSignature: ByteArray? = null,
    val finalizingSignature: ByteArray? = byteArrayOf(),
)

object FileHashHelper {

    fun prepareHashes(
        cardId: String,
        fileData: ByteArray,
        fileCounter: Int,
        fileName: String?,
        privateKey: ByteArray,
    ): PreparedFileHashes {
        return PreparedFileHashes(finalizingSignature = byteArrayOf())
    }
}
