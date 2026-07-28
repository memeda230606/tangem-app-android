package com.tangem.hot.sdk

import com.tangem.crypto.bip39.Mnemonic
import com.tangem.hot.sdk.model.*

interface TangemHotSdk {
    suspend fun importWallet(mnemonic: Mnemonic, passphrase: CharArray?, auth: HotAuth): HotWalletId =
        HotWalletId(id = "local-hot-wallet")

    suspend fun generateWallet(auth: HotAuth, mnemonicType: MnemonicType): HotWalletId =
        HotWalletId(id = "local-hot-wallet")

    suspend fun delete(id: HotWalletId) = Unit
    suspend fun clearUnlockContext(hotWalletId: HotWalletId) = Unit
    suspend fun changeAuth(unlockHotWallet: UnlockHotWallet, auth: HotAuth): HotWalletId = unlockHotWallet.hotWalletId
    suspend fun removeBiometryAuthIfPresented(id: HotWalletId): HotWalletId = id
    suspend fun exportMnemonic(unlockHotWallet: UnlockHotWallet): SeedPhrasePrivateInfo = SeedPhrasePrivateInfo()
    suspend fun exportBackup(unlockHotWallet: UnlockHotWallet): ByteArray = byteArrayOf()
    suspend fun getContextUnlock(unlockHotWallet: UnlockHotWallet): UnlockHotWallet = unlockHotWallet

    suspend fun derivePublicKey(unlockHotWallet: UnlockHotWallet, request: DeriveWalletRequest): DerivedPublicKeyResponse =
        DerivedPublicKeyResponse()

    suspend fun derivePublicKeys(unlockHotWallet: UnlockHotWallet, request: DeriveWalletRequest): DerivedPublicKeyResponse =
        derivePublicKey(unlockHotWallet, request)

    suspend fun signHashes(unlockHotWallet: UnlockHotWallet, dataToSign: List<DataToSign>): List<SignedData> =
        dataToSign.map { data -> SignedData(signatures = data.hashes.map { byteArrayOf() }, curve = data.curve) }

    companion object
}
