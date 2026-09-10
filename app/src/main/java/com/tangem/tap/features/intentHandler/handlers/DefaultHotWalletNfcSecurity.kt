package com.tangem.tap.features.intentHandler.handlers

import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.wallets.hot.HotWalletAccessor
import com.tangem.domain.wallets.hot.HotWalletNfcSecurity
import com.tangem.domain.wallets.hot.RecoveredWalletMaterial
import com.tangem.wallet.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * External-build bridge between an authenticated NTAG 424 DNA and the real mobile-wallet signer.
 *
 * The card never receives a blockchain private key and never signs a transaction. It proves physical presence;
 * key generation, encrypted storage and signing remain inside Tangem Hot SDK/Android Keystore.
 */
internal class DefaultHotWalletNfcSecurity(
    private val scanController: ExternalNdefScanController,
    private val hotWalletAccessor: HotWalletAccessor,
    private val client: TangemTestnetClient = TangemTestnetClient(),
    private val recoveryCoordinator: TangemTestnetRecoveryCoordinator = TangemTestnetRecoveryCoordinator(client),
) : HotWalletNfcSecurity {

    override val isEnabled: Boolean = BuildConfig.BUILD_TYPE == EXTERNAL_BUILD_TYPE

    override fun supportsCard(scanResponse: ScanResponse): Boolean {
        if (!isEnabled || scanResponse.card.batchId != TESTNET_BATCH_ID) return false

        val expectedCardId = runCatching {
            scanController.requireAcceptedIdentity().cardInstanceId
                .replace("-", "")
                .uppercase()
                .take(CARD_ID_LENGTH)
        }.getOrNull()

        return expectedCardId != null && scanResponse.card.cardId == expectedCardId
    }

    override fun matchesBinding(userWallet: UserWallet.Hot, boundWalletId: String): Boolean {
        return isEnabled && boundWalletId == backendWalletId(userWallet).toString()
    }

    override suspend fun bindWallet(userWallet: UserWallet.Hot) {
        if (!isEnabled) return

        withContext(Dispatchers.IO) {
            val identity = scanController.requireAcceptedIdentity()
            val walletId = backendWalletId(userWallet)
            if (identity.boundWalletId != null) {
                require(identity.boundWalletId == walletId.toString()) {
                    "This card is already bound to a different wallet"
                }
                return@withContext
            }
            val verificationToken = requireNotNull(identity.verificationToken) {
                "Card verification token is missing"
            }
            val fingerprint = walletFingerprint(userWallet)
            check(client.claim(verificationToken, walletId.toString(), fingerprint)) {
                "Unable to bind the card to the new wallet"
            }
            scanController.updateAcceptedIdentity {
                it.copy(boundWalletId = walletId.toString(), bindingRole = PRIMARY_CARD_ROLE)
            }
        }
    }

    override suspend fun createRecoveryPackage(userWallet: UserWallet.Hot): String {
        check(isEnabled) { "External-card recovery is unavailable in this build" }

        val scanResult = scanController.awaitScan()
        check(scanResult == ExternalNdefScanController.Result.Accepted) { "Recovery card scan was cancelled" }
        val identity = scanController.requireAcceptedIdentity()
        require(identity.boundWalletId == backendWalletId(userWallet).toString()) {
            "The scanned card belongs to a different wallet"
        }

        return withContext(Dispatchers.IO) {
            val seedPhrase = hotWalletAccessor.exportSeedPhrase(userWallet.hotWalletId)
            val payload = JSONObject()
                .put("version", RECOVERY_PAYLOAD_VERSION)
                .put("mnemonic", JSONArray(seedPhrase.mnemonic.mnemonicComponents))
                .toString()
                .toByteArray(Charsets.UTF_8)

            try {
                recoveryCoordinator.setup(
                    tag = scanController.requireAcceptedTag(),
                    walletId = backendWalletId(userWallet),
                    rootPublicKeyHash = walletFingerprint(userWallet),
                    recoveryPayload = payload,
                ).customerRecoveryCode
            } finally {
                payload.fill(0)
            }
        }
    }

    override suspend fun recoverWallet(customerRecoveryCode: String): RecoveredWalletMaterial {
        check(isEnabled) { "External-card recovery is unavailable in this build" }
        require(customerRecoveryCode.trim().startsWith(RECOVERY_CODE_PREFIX)) { "Invalid recovery code" }

        val scanResult = scanController.awaitScan()
        check(scanResult == ExternalNdefScanController.Result.Accepted) { "Recovery card scan was cancelled" }
        return withContext(Dispatchers.IO) {
            val recovered = recoveryCoordinator.recoverPackageWithCustomerCodeAndCard(
                tag = scanController.requireAcceptedTag(),
                customerRecoveryCode = customerRecoveryCode,
            )
            try {
                val json = JSONObject(recovered.payload.toString(Charsets.UTF_8))
                require(json.getInt("version") == RECOVERY_PAYLOAD_VERSION) { "Unsupported recovery payload" }
                val wordsJson = json.getJSONArray("mnemonic")
                val words = List(wordsJson.length(), wordsJson::getString)
                require(words.size == 12 || words.size == 24) { "Invalid recovered mnemonic" }
                RecoveredWalletMaterial(
                    mnemonicWords = words,
                    rootPublicKeyHash = recovered.rootPublicKeyHash,
                )
            } finally {
                recovered.payload.fill(0)
            }
        }
    }

    override fun verifyRecoveredWallet(userWallet: UserWallet.Hot, material: RecoveredWalletMaterial) {
        check(isEnabled) { "External-card recovery is unavailable in this build" }
        require(walletFingerprint(userWallet) == material.rootPublicKeyHash) {
            "Recovered wallet public-key fingerprint mismatch"
        }
        val boundWalletId = scanController.requireAcceptedIdentity().boundWalletId
        require(boundWalletId != null && matchesBinding(userWallet, boundWalletId)) {
            "Recovered wallet does not match the scanned card"
        }
    }

    override suspend fun authorizeSigning(userWallet: UserWallet.Hot, hashes: List<ByteArray>) {
        if (!isEnabled) return
        require(hashes.isNotEmpty() && hashes.none { it.isEmpty() }) { "Transaction hash is missing" }

        val scanResult = scanController.awaitScan()
        check(scanResult == ExternalNdefScanController.Result.Accepted) { "Transaction card scan was cancelled" }
        val identity = scanController.requireAcceptedIdentity()
        require(identity.boundWalletId == backendWalletId(userWallet).toString()) {
            "The scanned card belongs to a different wallet"
        }
    }

    private fun backendWalletId(userWallet: UserWallet.Hot): UUID = UUID.nameUUIDFromBytes(
        "$WALLET_ID_NAMESPACE${userWallet.walletId.stringValue}".toByteArray(Charsets.UTF_8),
    )

    private fun walletFingerprint(userWallet: UserWallet.Hot): String {
        val publicKeys = requireNotNull(userWallet.wallets) { "Wallet must be unlocked before card authorization" }
            .map { it.publicKey }
        require(publicKeys.isNotEmpty()) { "Wallet public keys are missing" }
        return TangemTestnetClient.walletFingerprint(publicKeys)
    }

    private companion object {
        const val EXTERNAL_BUILD_TYPE = "external"
        const val TESTNET_BATCH_ID = "L0T1"
        const val CARD_ID_LENGTH = 16
        const val PRIMARY_CARD_ROLE = "PRIMARY"
        const val RECOVERY_PAYLOAD_VERSION = 1
        const val RECOVERY_CODE_PREFIX = "NBRC1-"
        const val WALLET_ID_NAMESPACE = "tangem-external-wallet:"
    }
}
