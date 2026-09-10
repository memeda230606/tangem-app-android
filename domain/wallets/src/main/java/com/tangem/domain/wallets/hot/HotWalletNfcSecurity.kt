package com.tangem.domain.wallets.hot

import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.models.scan.ScanResponse

/**
 * Optional NFC presence protection for a mobile wallet.
 *
 * The mobile wallet remains the cryptographic signer. Implementations may require an authenticated external card
 * before binding the wallet, creating a recovery package, or releasing a transaction signature.
 */
interface HotWalletNfcSecurity {

    val isEnabled: Boolean

    /** Returns true only for the external TESTNET card represented by this scan response. */
    fun supportsCard(scanResponse: ScanResponse): Boolean = false

    /** Returns true when a backend card binding belongs to this mobile wallet. */
    fun matchesBinding(userWallet: UserWallet.Hot, boundWalletId: String): Boolean

    /** Binds a newly generated mobile wallet to the card that authorized its creation. */
    suspend fun bindWallet(userWallet: UserWallet.Hot)

    /** Creates the card/customer/server recovery package and returns the customer-held recovery code. */
    suspend fun createRecoveryPackage(userWallet: UserWallet.Hot): String

    /** Restores encrypted mnemonic material using customer, card, and server shares. */
    suspend fun recoverWallet(customerRecoveryCode: String): RecoveredWalletMaterial

    /** Verifies that imported key material matches the public-key fingerprint committed by recovery setup. */
    fun verifyRecoveredWallet(userWallet: UserWallet.Hot, material: RecoveredWalletMaterial)

    /** Requires a fresh matching-card scan immediately before the supplied transaction hashes are signed. */
    suspend fun authorizeSigning(userWallet: UserWallet.Hot, hashes: List<ByteArray>)
}

data class RecoveredWalletMaterial(
    val mnemonicWords: List<String>,
    val rootPublicKeyHash: String,
)

/** A customer-safe diagnostic code for an NFC recovery failure. The cause must never contain recovery secrets. */
class HotWalletNfcRecoveryException(
    val supportCode: String,
    cause: Throwable,
) : IllegalStateException("NFC recovery failed ($supportCode)", cause)
