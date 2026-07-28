package com.tangem.hot.sdk.model

import com.tangem.common.card.EllipticCurve
import com.tangem.crypto.bip39.DefaultMnemonic
import com.tangem.crypto.bip39.EntropyLength
import com.tangem.crypto.bip39.Mnemonic
import com.tangem.crypto.bip39.Wordlist
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import kotlinx.serialization.Serializable

@Serializable
data class HotWalletId(
    val id: String,
    val authType: AuthType = AuthType.NoPassword,
) {
    val value: String
        get() = id

    enum class AuthType {
        NoPassword,
        Password,
        Biometry,
    }
}

sealed class HotAuth {
    object NoAuth : HotAuth()
    object Biometry : HotAuth()
    data class Password(val password: CharArray) : HotAuth() {
        val value: CharArray
            get() = password
    }
}

enum class MnemonicType {
    Words12,
    Words24,
}

data class UnlockHotWallet(
    val walletId: HotWalletId,
    val auth: HotAuth,
) {
    val hotWalletId: HotWalletId
        get() = walletId
}

data class SeedPhrasePrivateInfo(
    val mnemonic: Mnemonic = DefaultMnemonic(
        entropy = EntropyLength.Bits128Length,
        wordlist = Wordlist.getWordlist(),
    ),
    val passphrase: CharArray? = null,
)

data class DataToSign(
    val curve: EllipticCurve,
    val hashes: List<ByteArray>,
    val derivationPath: DerivationPath? = null,
)

data class SignedData(
    val signatures: List<ByteArray>,
    val curve: EllipticCurve = EllipticCurve.Secp256k1,
) {
    val signature: ByteArray
        get() = signatures.firstOrNull() ?: byteArrayOf()
}

data class DeriveWalletRequest(
    val requests: List<Request>,
) {
    data class Request(
        val curve: EllipticCurve,
        val paths: List<DerivationPath>,
    )
}

data class DerivedPublicKeyResponse(
    val responses: List<Response> = emptyList(),
) {
    data class SeedKey(
        val publicKey: ByteArray,
        val chainCode: ByteArray? = null,
    )

    data class Response(
        val seedKey: SeedKey,
        val curve: EllipticCurve,
        val publicKeys: Map<DerivationPath, ExtendedPublicKey>,
    )

    val entries: Map<DerivationPath, ExtendedPublicKey>
        get() = responses.flatMap { it.publicKeys.entries }.associate { it.toPair() }
}
