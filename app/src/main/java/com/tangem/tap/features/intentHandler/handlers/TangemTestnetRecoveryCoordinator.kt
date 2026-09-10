package com.tangem.tap.features.intentHandler.handlers

import android.nfc.Tag
import android.nfc.tech.IsoDep
import com.niubtmd.securenfc.Hex
import com.niubtmd.securenfc.Ntag424Dna
import com.niubtmd.securenfc.RecoveryCardStore
import com.niubtmd.securenfc.RecoveryPackageCrypto
import com.niubtmd.securenfc.RecoverySecretSharing
import com.niubtmd.securenfc.SecureCardKeys
import com.niubtmd.securenfc.SecureCardIdentityStore
import com.tangem.domain.wallets.hot.HotWalletNfcRecoveryException
import com.tangem.wallet.BuildConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * TESTNET recovery integration. Plain recovery material remains inside the wallet process and is never logged.
 * The production wallet engine must provide the opaque payload and root-public-key derivation callback.
 */
internal class TangemTestnetRecoveryCoordinator(
    private val client: TangemTestnetClient = TangemTestnetClient(),
    private val random: SecureRandom = SecureRandom(),
) {

    fun setup(
        tag: Tag,
        walletId: UUID,
        rootPublicKeyHash: String,
        recoveryPayload: ByteArray,
    ): SetupResult {
        val localIdentity = atSetupStage(SETUP_CARD_IDENTITY_CODE) { readIdentity(tag) }
        val verified = atSetupStage(SETUP_SERVER_VERIFICATION_CODE) {
            requireNotNull(
                client.verify(localIdentity, TangemTestnetClient.VerificationPurpose.RECOVERY_SETUP),
            ) { "Recovery setup card verification failed" }
        }
        atSetupStage(SETUP_WALLET_BINDING_CODE) {
            require(verified.boundWalletId == walletId.toString()) { "Card is not bound to this wallet" }
        }

        val created = atSetupStage(SETUP_PACKAGE_ENCRYPTION_CODE) {
            RecoveryPackageCrypto.create(recoveryPayload, walletId, rootPublicKeyHash, random)
        }
        atSetupStage(SETUP_CARD_SHARE_CODE) {
            withAuthenticatedCard(tag) { card, session ->
                RecoveryCardStore.write(card, session, created.cardShare)
                val readBack = RecoveryCardStore.read(card, session)
                require(readBack.recoverySetId == created.recoverySetId) { "Card recovery share was not persisted" }
                require(constantTimeEquals(
                    RecoveryPackageCrypto.fingerprint(readBack.value),
                    created.cardShareFingerprint,
                )) { "Card recovery share verification failed" }
            }
        }

        atSetupStage(SETUP_SERVER_PACKAGE_CODE) {
            val token = requireNotNull(verified.verificationToken) { "Recovery setup token is missing" }
            check(client.createRecoveryPackage(token, created)) { "Recovery package upload failed" }
        }
        return SetupResult(
            recoverySetId = created.recoverySetId,
            customerRecoveryCode = created.customerRecoveryCode,
        )
    }

    fun recoverWithCustomerCodeAndCard(
        tag: Tag,
        customerRecoveryCode: String,
        deriveRootPublicKeyHash: (ByteArray) -> String,
    ): ByteArray {
        val recovered = recoverPackageWithCustomerCodeAndCard(tag, customerRecoveryCode)
        val plaintext = recovered.payload
        val derivedFingerprint = runCatching { deriveRootPublicKeyHash(plaintext) }.getOrElse {
            plaintext.fill(0)
            throw it
        }
        if (!constantTimeEquals(derivedFingerprint, recovered.rootPublicKeyHash)) {
            plaintext.fill(0)
            throw SecurityException("Recovered wallet public-key fingerprint mismatch")
        }
        return plaintext
    }

    fun recoverPackageWithCustomerCodeAndCard(
        tag: Tag,
        customerRecoveryCode: String,
    ): RecoveredPackage {
        val localIdentity = readIdentity(tag)
        val verified = requireNotNull(
            client.verify(localIdentity, TangemTestnetClient.VerificationPurpose.RECOVERY),
        ) { "Recovery card verification failed" }
        val walletId = requireNotNull(verified.boundWalletId) { "Recovery card is not bound" }
        val token = requireNotNull(verified.verificationToken) { "Recovery token is missing" }
        val lookup = requireNotNull(client.lookupRecoveryPackage(token, walletId)) { "Recovery package lookup failed" }
        val customerShare = RecoverySecretSharing.Share.decodeRecoveryCode(customerRecoveryCode)
        val cardShare = withAuthenticatedCard(tag) { card, session -> RecoveryCardStore.read(card, session) }

        require(customerShare.recoverySetId == lookup.encryptedPackage.recoverySetId) {
            "Customer recovery code belongs to a different recovery set"
        }
        require(cardShare.recoverySetId == lookup.encryptedPackage.recoverySetId) {
            "Card recovery share belongs to a different recovery set"
        }
        require(constantTimeEquals(
            RecoveryPackageCrypto.fingerprint(customerShare.value),
            lookup.customerShareFingerprint,
        )) { "Customer recovery share fingerprint mismatch" }
        require(constantTimeEquals(
            RecoveryPackageCrypto.fingerprint(cardShare.value),
            lookup.cardShareFingerprint,
        )) { "Card recovery share fingerprint mismatch" }

        return RecoveredPackage(
            payload = RecoveryPackageCrypto.decrypt(lookup.encryptedPackage, customerShare, cardShare),
            rootPublicKeyHash = lookup.encryptedPackage.rootPublicKeyHash,
        )
    }

    private fun readIdentity(tag: Tag): ExternalNdefScanController.CardIdentity =
        withAuthenticatedCard(tag) { card, session ->
            val payload = SecureCardIdentityStore.read(card, session)
            ExternalNdefScanController.CardIdentity(
                cardInstanceId = payload.cardInstanceId.toString(),
                keyVersion = payload.keyVersion,
                targetUrl = payload.targetUrl,
            )
        }

    private fun <T> withAuthenticatedCard(tag: Tag, block: (Ntag424Dna, Ntag424Dna.Session) -> T): T {
        val uid = requireNotNull(tag.id) { "Card UID is unavailable" }
        require(uid.size == 7) { "Unsupported card UID" }
        val isoDep = requireNotNull(IsoDep.get(tag)) { "Card does not support ISO-DEP" }
        return try {
            isoDep.connect()
            isoDep.timeout = 5_000
            val card = Ntag424Dna(isoDep::transceive)
            card.selectNdefApplication()
            require(card.isNtag424Dna) { "Unsupported NFC card" }
            val readRoot = Hex.decode(BuildConfig.SECURE_CARD_READ_ROOT)
            val readKey = SecureCardKeys.derive(readRoot, uid, SecureCardKeys.READ_KEY)
            val session = card.authenticate(SecureCardKeys.READ_KEY, readKey)
            block(card, session)
        } finally {
            runCatching { isoDep.close() }
        }
    }

    private fun constantTimeEquals(left: String, right: String): Boolean = MessageDigest.isEqual(
        left.lowercase().toByteArray(Charsets.US_ASCII),
        right.lowercase().toByteArray(Charsets.US_ASCII),
    )

    private inline fun <T> atSetupStage(code: String, block: () -> T): T {
        return try {
            block()
        } catch (error: HotWalletNfcRecoveryException) {
            throw error
        } catch (error: Throwable) {
            throw HotWalletNfcRecoveryException(code, error)
        }
    }

    data class SetupResult(
        val recoverySetId: UUID,
        val customerRecoveryCode: String,
    )

    data class RecoveredPackage(
        val payload: ByteArray,
        val rootPublicKeyHash: String,
    )

    private companion object {
        const val SETUP_CARD_IDENTITY_CODE = "RCV-201"
        const val SETUP_SERVER_VERIFICATION_CODE = "RCV-202"
        const val SETUP_WALLET_BINDING_CODE = "RCV-203"
        const val SETUP_PACKAGE_ENCRYPTION_CODE = "RCV-204"
        const val SETUP_CARD_SHARE_CODE = "RCV-205"
        const val SETUP_SERVER_PACKAGE_CODE = "RCV-206"
    }
}
