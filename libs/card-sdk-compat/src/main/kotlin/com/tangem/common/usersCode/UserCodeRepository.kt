package com.tangem.common.usersCode

import com.tangem.common.CompletionResult
import com.tangem.common.UserCodeType
import com.tangem.common.authentication.keystore.KeystoreManager
import com.tangem.common.services.secure.SecureStorage

data class UserCode(
    val type: UserCodeType,
    val stringValue: String,
)

class UserCodeRepository(
    private val keystoreManager: KeystoreManager,
    private val secureStorage: SecureStorage,
) {
    suspend fun save(cardsIds: Set<String>, userCode: UserCode): CompletionResult<Unit> {
        cardsIds.forEach { cardId ->
            secureStorage.store(userCode.stringValue.encodeToByteArray(), "user_code_${userCode.type}_$cardId")
        }

        return CompletionResult.Success(Unit)
    }

    suspend fun delete(cardsIds: Set<String>): CompletionResult<Unit> {
        cardsIds.forEach { cardId ->
            secureStorage.delete("user_code_${UserCodeType.AccessCode}_$cardId")
            secureStorage.delete("user_code_${UserCodeType.Passcode}_$cardId")
        }

        return CompletionResult.Success(Unit)
    }

    suspend fun clear(): CompletionResult<Unit> = CompletionResult.Success(Unit)
}
