package com.tangem.domain.wallets.nfc

import com.tangem.domain.models.wallet.UserWalletId

interface NfcWalletKeyRepository {

    suspend fun save(key: NfcWalletAesKey, accessCode: CharArray?)

    suspend fun get(userWalletId: UserWalletId, localKeyId: String, accessCode: CharArray?): NfcWalletAesKey?

    suspend fun delete(userWalletId: UserWalletId)
}
