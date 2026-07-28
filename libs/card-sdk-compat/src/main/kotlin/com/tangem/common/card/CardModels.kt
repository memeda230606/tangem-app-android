package com.tangem.common.card

import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey
import com.tangem.operations.attestation.Attestation
import java.util.Date

data class Card(
    val cardId: String,
    val batchId: String = "",
    val cardPublicKey: ByteArray = byteArrayOf(),
    val firmwareVersion: FirmwareVersion = FirmwareVersion(),
    val manufacturer: Manufacturer = Manufacturer(),
    val issuer: Issuer = Issuer(),
    val settings: Settings = Settings(),
    val userSettings: UserSettings = UserSettings(),
    val linkedTerminalStatus: LinkedTerminalStatus = LinkedTerminalStatus.None,
    val isAccessCodeSet: Boolean = false,
    val isPasscodeSet: Boolean? = false,
    val supportedCurves: List<EllipticCurve> = emptyList(),
    val wallets: List<CardWallet> = emptyList(),
    val attestation: Attestation = Attestation.NotVerified,
    val backupStatus: BackupStatus? = null,
) {
    fun setWallets(wallets: List<CardWallet>): Card = copy(wallets = wallets)

    fun updateWallet(wallet: CardWallet): Card {
        val index = wallets.indexOfFirst { it.publicKey.contentEquals(wallet.publicKey) }
        val updatedWallets = if (index >= 0) {
            wallets.toMutableList().also { it[index] = wallet }
        } else {
            wallets + wallet
        }

        return copy(wallets = updatedWallets)
    }

    fun wallet(publicKey: ByteArray): CardWallet? = wallets.firstOrNull { it.publicKey.contentEquals(publicKey) }

    data class Manufacturer(
        val name: String = "",
        val manufactureDate: Date = Date(0),
        val signature: ByteArray? = null,
    )

    data class Issuer(
        val name: String = "",
        val publicKey: ByteArray = byteArrayOf(),
    )

    data class Settings(
        val securityDelay: Int = 0,
        val maxWalletsCount: Int = 0,
        val isSettingAccessCodeAllowed: Boolean = true,
        val isSettingPasscodeAllowed: Boolean = true,
        val isRemovingUserCodesAllowed: Boolean = true,
        val isLinkedTerminalEnabled: Boolean = false,
        val isBackupAllowed: Boolean = true,
        val supportedEncryptionModes: List<EncryptionMode> = emptyList(),
        val isFilesAllowed: Boolean = false,
        val isHDWalletAllowed: Boolean = true,
        val isKeysImportAllowed: Boolean = false,
    )

    enum class LinkedTerminalStatus {
        Current,
        Other,
        None,
    }

    sealed class BackupStatus {
        object NoBackup : BackupStatus()
        data class CardLinked(val cardsCount: Int) : BackupStatus()
        data class Active(val cardsCount: Int) : BackupStatus()
    }
}

data class CardWallet(
    val publicKey: ByteArray,
    val chainCode: ByteArray? = null,
    val curve: EllipticCurve = EllipticCurve.Secp256k1,
    val settings: Settings = Settings(),
    val totalSignedHashes: Int? = null,
    val remainingSignatures: Int? = null,
    val index: Int = 0,
    val hasBackup: Boolean = false,
    val derivedKeys: Map<DerivationPath, ExtendedPublicKey> = emptyMap(),
    val extendedPublicKey: ExtendedPublicKey? = null,
    val isImported: Boolean = false,
) {
    data class Settings(
        val isPermanent: Boolean = false,
    )
}

enum class EllipticCurve {
    Secp256k1,
    Secp256r1,
    Ed25519,
    Ed25519Slip0010,
    Bls12381G2,
    Bls12381G2Aug,
    Bls12381G2Pop,
    Bip0340,
    BIP0340,
    ;

    val curve: String
        get() = name
}

enum class EncryptionMode {
    None,
    Fast,
    Strong,
}

data class FirmwareVersion(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val type: FirmwareType = FirmwareType.Release,
) : Comparable<FirmwareVersion> {
    override fun compareTo(other: FirmwareVersion): Int {
        return compareValuesBy(this, other, FirmwareVersion::major, FirmwareVersion::minor, FirmwareVersion::patch)
    }

    val doubleValue: Double
        get() = "$major.$minor".toDouble()

    enum class FirmwareType(val rawValue: String?) {
        Release(null),
        Sdk(" SDK"),
        SDK(" SDK"),
        Special(" Special"),
    }

    companion object {
        val HDWalletAvailable = FirmwareVersion(4, 0)
        val MultiWalletAvailable = FirmwareVersion(4, 22)
        val KeysImportAvailable = FirmwareVersion(4, 35)
        val Ed25519Slip0010Available = FirmwareVersion(6, 0)
        val visaRange: ClosedFloatingPointRange<Double> = 6.0..99.0
    }
}

data class UserSettings(
    val isUserCodeRecoveryAllowed: Boolean = false,
)

data class WalletData(
    val blockchain: String? = null,
    val token: Token? = null,
    val name: String? = null,
) {
    data class Token(
        val name: String,
        val symbol: String,
        val contractAddress: String,
        val decimals: Int,
    )
}
