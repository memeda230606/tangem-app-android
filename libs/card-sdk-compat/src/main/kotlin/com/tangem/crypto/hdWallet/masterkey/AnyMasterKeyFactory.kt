package com.tangem.crypto.hdWallet.masterkey

import com.tangem.common.card.EllipticCurve
import com.tangem.crypto.bip39.Mnemonic

class AnyMasterKeyFactory(
    private val mnemonic: Mnemonic,
    private val passphrase: String,
) {

    fun makeMasterKey(curve: EllipticCurve): ByteArray = byteArrayOf()
}
