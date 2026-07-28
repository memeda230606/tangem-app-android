package com.tangem.domain.wallets.nfc

import com.tangem.crypto.bip39.Mnemonic
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.wallet.UserWallet

interface NfcEncryptedWalletOnboardingRepository {

    suspend fun preparePrimaryCard(): ScanResponse

    suspend fun createPrimaryWallet(mnemonic: Mnemonic, passphrase: String?): ScanResponse

    suspend fun addBackupCard(): CardDTO

    suspend fun verifyPrimaryCard()

    suspend fun verifyBackupCard(cardIndex: Int)

    suspend fun finalize(accessCode: CharArray?): UserWallet.NfcEncrypted

    fun isNfcScanResponse(scanResponse: ScanResponse): Boolean

    fun clear()
}
