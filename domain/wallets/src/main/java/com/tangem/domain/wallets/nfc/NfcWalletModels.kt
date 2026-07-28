package com.tangem.domain.wallets.nfc

import com.tangem.domain.models.wallet.UserWalletId

data class NfcWalletBlob(
    val version: Int,
    val walletId: UserWalletId,
    val cardInstanceId: String,
    val backupSetId: String,
    val localKeyId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val tag: ByteArray,
    val checksum: ByteArray,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NfcWalletBlob) return false

        if (version != other.version) return false
        if (walletId != other.walletId) return false
        if (cardInstanceId != other.cardInstanceId) return false
        if (backupSetId != other.backupSetId) return false
        if (localKeyId != other.localKeyId) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!tag.contentEquals(other.tag)) return false
        if (!checksum.contentEquals(other.checksum)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + walletId.hashCode()
        result = 31 * result + cardInstanceId.hashCode()
        result = 31 * result + backupSetId.hashCode()
        result = 31 * result + localKeyId.hashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + tag.contentHashCode()
        result = 31 * result + checksum.contentHashCode()
        return result
    }
}

data class NfcWalletAesKey(
    val walletId: UserWalletId,
    val localKeyId: String,
    val key: ByteArray,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NfcWalletAesKey) return false

        if (walletId != other.walletId) return false
        if (localKeyId != other.localKeyId) return false
        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = walletId.hashCode()
        result = 31 * result + localKeyId.hashCode()
        result = 31 * result + key.contentHashCode()
        return result
    }
}
