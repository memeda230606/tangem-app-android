package com.tangem.domain.wallets.nfc

interface NfcCardGateway {

    suspend fun readWalletBlob(): NfcWalletBlob?

    suspend fun writeWalletBlob(blob: NfcWalletBlob)

    suspend fun eraseWalletBlob()
}
