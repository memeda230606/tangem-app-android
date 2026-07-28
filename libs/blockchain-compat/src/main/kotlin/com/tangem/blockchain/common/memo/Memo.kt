package com.tangem.blockchain.common.memo

import com.tangem.blockchain.common.BlockchainSdkConfig
import com.tangem.blockchain.extensions.Result

class MemoValidatorFactory(
    val config: BlockchainSdkConfig,
    val blockchainProviderTypes: Map<*, *> = emptyMap<Any, Any>(),
) {
    fun create(blockchain: com.tangem.blockchain.common.Blockchain): MemoValidator = NoOpMemoValidator
}

interface MemoValidator {
    suspend fun isMemoRequired(destinationAddress: String): Result<Boolean> = Result.Success(false)
    suspend fun validateMemo(memo: String): Result<MemoState> = Result.Success(MemoState.Valid)
}

private data object NoOpMemoValidator : MemoValidator

sealed class MemoState {
    object Valid : MemoState()
    object Invalid : MemoState()
    object NotSupported : MemoState()
}
