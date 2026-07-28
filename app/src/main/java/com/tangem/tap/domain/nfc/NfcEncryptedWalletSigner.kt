package com.tangem.tap.domain.nfc

import com.tangem.blockchain.common.TransactionSigner
import com.tangem.blockchain.common.Wallet
import com.tangem.common.CompletionResult
import com.tangem.common.core.TangemSdkError
import com.tangem.data.wallets.hot.TangemHotWalletSigner
import com.tangem.data.wallets.nfc.NfcWalletBlobCodec
import com.tangem.data.wallets.nfc.NfcWalletCrypto
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.wallets.nfc.NfcCardGateway
import com.tangem.domain.wallets.nfc.NfcWalletKeyRepository
import com.tangem.operations.sign.SignData
import com.tangem.utils.coroutines.runSuspendCatching
import com.tangem.utils.logging.TangemLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

internal class NfcEncryptedWalletSigner @AssistedInject constructor(
    @Assisted private val userWallet: UserWallet.NfcEncrypted,
    private val nfcCardGateway: NfcCardGateway,
    private val nfcWalletKeyRepository: NfcWalletKeyRepository,
    tangemHotWalletSignerFactory: TangemHotWalletSigner.Factory,
) : TransactionSigner {

    private val delegate = tangemHotWalletSignerFactory.create(userWallet.toHotWallet())
    private val crypto = NfcWalletCrypto()

    override suspend fun sign(hash: ByteArray, publicKey: Wallet.PublicKey): CompletionResult<ByteArray> {
        return validateNfcCard()
            ?: delegate.sign(hash, publicKey)
    }

    override suspend fun sign(
        hashes: List<ByteArray>,
        publicKey: Wallet.PublicKey,
    ): CompletionResult<List<ByteArray>> {
        return validateNfcCard()
            ?: delegate.sign(hashes, publicKey)
    }

    override suspend fun multiSign(
        dataToSign: List<SignData>,
        publicKey: Wallet.PublicKey,
    ): CompletionResult<Map<ByteArray, ByteArray>> {
        return validateNfcCard()
            ?: delegate.multiSign(dataToSign, publicKey)
    }

    private suspend fun validateNfcCard(): CompletionResult.Failure? {
        runSuspendCatching {
            val blob = nfcCardGateway.readWalletBlob()
                ?: error("NFC encrypted wallet payload not found")

            require(blob.version == NfcWalletBlobCodec.CURRENT_VERSION) {
                "Unsupported NFC encrypted wallet payload version: ${blob.version}"
            }
            require(blob.walletId == userWallet.walletId) {
                "NFC encrypted wallet id does not match selected wallet"
            }
            require(blob.localKeyId == userWallet.localKeyId) {
                "NFC encrypted wallet local key id does not match selected wallet"
            }
            require(blob.backupSetId == userWallet.backupSetId) {
                "NFC encrypted wallet backup set id does not match selected wallet"
            }
            require(userWallet.cardsInWallet.isEmpty() || blob.cardInstanceId in userWallet.cardsInWallet) {
                "NFC encrypted wallet card is not assigned to selected wallet"
            }

            val aesKey = nfcWalletKeyRepository.get(
                userWalletId = userWallet.walletId,
                localKeyId = userWallet.localKeyId,
                accessCode = null,
            ) ?: error("NFC encrypted wallet AES key not found")

            crypto.decryptSeed(blob = blob, aesKey = aesKey.key)
        }.onFailure { throwable ->
            TangemLogger.e("NFC encrypted wallet card validation failed", throwable)
            return CompletionResult.Failure(
                if (throwable is TangemSdkError) {
                    throwable
                } else {
                    TangemSdkError.ExceptionError(throwable)
                },
            )
        }

        return null
    }

    private fun UserWallet.NfcEncrypted.toHotWallet(): UserWallet.Hot {
        return UserWallet.Hot(
            name = name,
            walletId = walletId,
            hotWalletId = hotWalletId,
            wallets = wallets,
            backedUp = backedUp,
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(@Assisted userWallet: UserWallet.NfcEncrypted): NfcEncryptedWalletSigner
    }
}
