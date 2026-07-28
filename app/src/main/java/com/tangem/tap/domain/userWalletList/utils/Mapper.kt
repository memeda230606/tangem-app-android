package com.tangem.tap.domain.userWalletList.utils

import com.tangem.domain.models.scan.KeyWalletPublicKey
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.models.wallet.isMultiCurrency
import com.tangem.operations.derivation.ExtendedPublicKeysMap
import com.tangem.tap.domain.userWalletList.model.UserWalletPublicInformation
import com.tangem.tap.domain.userWalletList.model.UserWalletSensitiveInformation

internal val UserWallet.sensitiveInformation: UserWalletSensitiveInformation
    get() = when (this) {
        is UserWallet.Cold -> UserWalletSensitiveInformation(
            wallets = scanResponse.card.wallets,
            // visaCardActivationStatus = scanResponse.visaCardActivationStatus,
            mobileWallets = null,
        )
        is UserWallet.Hot -> UserWalletSensitiveInformation(
            wallets = null,
            mobileWallets = this.wallets,
        )
        is UserWallet.NfcEncrypted -> UserWalletSensitiveInformation(
            wallets = null,
            mobileWallets = this.wallets,
        )
    }

internal val UserWallet.publicInformation: UserWalletPublicInformation
    get() = when (this) {
        is UserWallet.Cold -> UserWalletPublicInformation(
            name = name,
            walletId = walletId,
            cardsInWallet = cardsInWallet,
            isMultiCurrency = isMultiCurrency,
            scanResponse = scanResponse.copy(
                card = scanResponse.card.copy(
                    wallets = emptyList(),
                ),
                // visaCardActivationStatus = null,
            ),
            hasBackupError = hasBackupError,
            walletType = null,
            hotWalletId = null,
            backedUp = null,
            localKeyId = null,
            backupSetId = null,
        )
        is UserWallet.Hot -> UserWalletPublicInformation(
            name = name,
            walletId = walletId,
            isMultiCurrency = isMultiCurrency,
            cardsInWallet = emptySet(),
            scanResponse = null,
            walletType = WALLET_TYPE_HOT,
            hotWalletId = hotWalletId,
            backedUp = backedUp,
            localKeyId = null,
            backupSetId = null,
        )
        is UserWallet.NfcEncrypted -> UserWalletPublicInformation(
            name = name,
            walletId = walletId,
            isMultiCurrency = isMultiCurrency,
            cardsInWallet = cardsInWallet,
            scanResponse = null,
            walletType = WALLET_TYPE_NFC_ENCRYPTED,
            hotWalletId = hotWalletId,
            backedUp = backedUp,
            localKeyId = localKeyId,
            backupSetId = backupSetId,
        )
    }

internal fun UserWalletPublicInformation.toUserWallet(): UserWallet {
    return when {
        walletType == WALLET_TYPE_NFC_ENCRYPTED -> UserWallet.NfcEncrypted(
            name = name,
            walletId = walletId,
            cardsInWallet = cardsInWallet,
            hotWalletId = requireNotNull(hotWalletId),
            wallets = null,
            localKeyId = requireNotNull(localKeyId),
            backupSetId = requireNotNull(backupSetId),
            backedUp = requireNotNull(backedUp),
        )
        hotWalletId != null -> UserWallet.Hot(
            name = name,
            walletId = walletId,
            hotWalletId = hotWalletId,
            wallets = null,
            backedUp = requireNotNull(backedUp),
        )
        else -> UserWallet.Cold(
            name = name,
            walletId = walletId,
            cardsInWallet = cardsInWallet,
            scanResponse = requireNotNull(scanResponse),
            isMultiCurrency = isMultiCurrency,
            hasBackupError = hasBackupError,
        )
    }
}

internal fun List<UserWalletPublicInformation>.toUserWallets(): List<UserWallet> {
    return this.map { it.toUserWallet() }
}

internal fun UserWallet.updateWith(
    sensitiveInformation: UserWalletSensitiveInformation,
    derivedKeys: Map<KeyWalletPublicKey, ExtendedPublicKeysMap>?,
): UserWallet {
    return when (this) {
        is UserWallet.Cold -> {
            copy(
                scanResponse = scanResponse.copy(
                    card = scanResponse.card.copy(
                        wallets = requireNotNull(sensitiveInformation.wallets),
                    ),
                    derivedKeys = derivedKeys ?: scanResponse.derivedKeys,
                    // visaCardActivationStatus = sensitiveInformation.visaCardActivationStatus,
                ),
            )
        }
        is UserWallet.Hot -> copy(
            wallets = sensitiveInformation.mobileWallets,
        )
        is UserWallet.NfcEncrypted -> copy(
            wallets = sensitiveInformation.mobileWallets,
        )
    }
}

internal fun List<UserWallet>.updateWith(
    walletIdToSensitiveInformation: Map<UserWalletId, UserWalletSensitiveInformation>,
    walletIdToDerivedKeys: Map<UserWalletId, Map<KeyWalletPublicKey, ExtendedPublicKeysMap>>? = null,
): List<UserWallet> {
    return if (walletIdToSensitiveInformation.isEmpty()) {
        this
    } else {
        this.map { wallet ->
            val sensitiveInformation = walletIdToSensitiveInformation[wallet.walletId]
            val derivedKeys = walletIdToDerivedKeys?.get(wallet.walletId)

            if (sensitiveInformation != null) {
                wallet.updateWith(sensitiveInformation, derivedKeys)
            } else {
                wallet
            }
        }
    }
}

internal fun UserWallet.lock(): UserWallet = when (this) {
    is UserWallet.Cold -> {
        copy(
            scanResponse = scanResponse.copy(
                card = scanResponse.card.copy(
                    wallets = emptyList(),
                ),
                // visaCardActivationStatus = null,
            ),
        )
    }
    is UserWallet.Hot -> copy(wallets = null)
    is UserWallet.NfcEncrypted -> copy(wallets = null)
}

private const val WALLET_TYPE_HOT = "hot"
private const val WALLET_TYPE_NFC_ENCRYPTED = "nfc_encrypted"
