package com.tangem.tap.domain.sdk.mocks.content

import android.content.Context
import android.content.SharedPreferences
import com.tangem.common.card.EllipticCurve
import com.tangem.common.extensions.ByteArrayKey
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.operations.derivation.DerivationTaskResponse
import com.tangem.operations.derivation.ExtendedPublicKeysMap
import com.tangem.sdk.api.CreateProductWalletTaskResponse
import com.tangem.tap.domain.sdk.mocks.MockContent
import com.tangem.tap.features.intentHandler.handlers.ExternalNdefScanController
import com.tangem.tap.features.intentHandler.handlers.TangemTestnetClient

/**
 * Wallet 2 demo used by the External build when a supported NDEF tag is scanned.
 *
 * A regular NDEF tag cannot participate in Tangem's cryptographic backup protocol, so this demo card explicitly
 * reports that backups are unavailable. This keeps the External flow honest and lets onboarding continue without
 * invoking the real SDK backup service.
 */
object ExternalNdefWalletMockContent : MockContent by Wallet2NoBackupNoWalletsMockContent {

    private var preferences: SharedPreferences? = null
    private var selectedCard: ExternalNdefScanController.CardIdentity = defaultCardIdentity()

    private val externalCardId: String
        get() = selectedCard.cardInstanceId.replace("-", "").uppercase().take(CARD_ID_LENGTH)

    private val initialCardDto: CardDTO
        get() = Wallet2NoBackupNoWalletsMockContent.cardDto.copy(
            cardId = externalCardId,
            batchId = TESTNET_BATCH_ID,
            settings = Wallet2NoBackupNoWalletsMockContent.cardDto.settings.copy(
                isBackupAllowed = false,
            ),
        )

    private val createdCardDto: CardDTO
        get() = Wallet2NoBackupMockContent.cardDto.copy(
            cardId = externalCardId,
            batchId = TESTNET_BATCH_ID,
            backupStatus = null,
            settings = Wallet2NoBackupMockContent.cardDto.settings.copy(
                isBackupAllowed = false,
            ),
        )

    private val baseCreatedWalletDerivations: Map<ByteArrayKey, ExtendedPublicKeysMap>
        get() = rekeyDerivations(sourceEntries = WalletMockContent.derivationTaskResponse.entries)

    private var isWalletCreated = false
    private var rememberedDerivationPaths: Set<String> = emptySet()
    private var currentWalletId: String? = null

    private val createdWalletDerivations: Map<ByteArrayKey, ExtendedPublicKeysMap>
        get() = baseCreatedWalletDerivations.mapValues { (_, availableKeys) ->
            val fallbackKey = availableKeys.values.firstOrNull()
            if (fallbackKey == null || rememberedDerivationPaths.isEmpty()) {
                availableKeys
            } else {
                val mergedKeys = availableKeys.entries.associate { it.toPair() }.toMutableMap()
                rememberedDerivationPaths.forEach { rawPath ->
                    mergedKeys.putIfAbsent(DerivationPath(rawPath), fallbackKey)
                }

                ExtendedPublicKeysMap(mergedKeys)
            }
        }

    override val cardDto: CardDTO
        get() = if (isWalletCreated) createdCardDto else initialCardDto

    override val scanResponse: ScanResponse
        get() = if (isWalletCreated) {
            Wallet2NoBackupMockContent.scanResponse.copy(
                card = createdCardDto,
                derivedKeys = createdWalletDerivations,
            )
        } else {
            Wallet2NoBackupNoWalletsMockContent.scanResponse.copy(card = initialCardDto)
        }

    override val derivationTaskResponse: DerivationTaskResponse
        get() = DerivationTaskResponse(entries = createdWalletDerivations)

    override val createProductWalletTaskResponse: CreateProductWalletTaskResponse
        get() {
            isWalletCreated = true
            preferences?.edit()?.putBoolean(walletCreatedKey(), true)?.apply()

            return Wallet2NoBackupMockContent.createProductWalletTaskResponse.copy(
                card = createdCardDto,
                derivedKeys = createdWalletDerivations,
                primaryCard = null,
            )
        }

    private fun rekeyDerivations(
        sourceEntries: Map<ByteArrayKey, ExtendedPublicKeysMap>,
    ): Map<ByteArrayKey, ExtendedPublicKeysMap> {
        val sourceValues = sourceEntries.values.toList()
        val secp256k1Derivations = sourceValues.getOrNull(index = 0)
        val ed25519Derivations = sourceValues.getOrNull(index = 1)

        return buildMap {
            createdCardDto.wallets.forEach { wallet ->
                val derivations = when (wallet.curve) {
                    EllipticCurve.Secp256k1,
                    EllipticCurve.Secp256r1,
                    EllipticCurve.Bip0340,
                    -> secp256k1Derivations
                    EllipticCurve.Ed25519,
                    EllipticCurve.Ed25519Slip0010,
                    -> ed25519Derivations
                    else -> null
                }

                derivations?.let { put(ByteArrayKey(wallet.publicKey), it) }
            }
        }
    }

    internal fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        reloadSelectedCardState()
    }

    internal fun selectCard(identity: ExternalNdefScanController.CardIdentity) {
        selectedCard = identity
        reloadSelectedCardState()
        if (identity.boundWalletId != null) {
            currentWalletId = identity.boundWalletId
            isWalletCreated = true
            preferences?.edit()
                ?.putString(walletIdKey(), identity.boundWalletId)
                ?.putBoolean(walletCreatedKey(), true)
                ?.apply()
        }
    }

    internal fun claimCurrentCard(client: TangemTestnetClient = TangemTestnetClient()): Boolean {
        if (selectedCard.boundWalletId != null) return true
        val verificationToken = selectedCard.verificationToken ?: return rollbackWalletCreation()
        val walletId = currentWalletId ?: TangemTestnetClient.newWalletId().also {
            currentWalletId = it
            preferences?.edit()?.putString(walletIdKey(), it)?.apply()
        }
        val fingerprint = TangemTestnetClient.walletFingerprint(createdCardDto.wallets.map { it.publicKey })
        if (runCatching { client.claim(verificationToken, walletId, fingerprint) }.getOrDefault(false)) return true
        return rollbackWalletCreation()
    }

    internal fun rememberDerivations(derivations: Map<ByteArrayKey, List<DerivationPath>>) {
        val requestedPaths = derivations.values.flatten().mapTo(mutableSetOf()) { it.rawPath }
        if (requestedPaths.isEmpty()) return

        rememberedDerivationPaths = rememberedDerivationPaths + requestedPaths
        preferences?.edit()?.putStringSet(derivationPathsKey(), rememberedDerivationPaths)?.apply()
    }

    internal fun reset() {
        isWalletCreated = false
        rememberedDerivationPaths = emptySet()
        preferences?.edit()
            ?.remove(walletCreatedKey())
            ?.remove(derivationPathsKey())
            ?.remove(walletIdKey())
            ?.apply()
        preferences = null
        selectedCard = defaultCardIdentity()
        currentWalletId = null
    }

    private fun reloadSelectedCardState() {
        isWalletCreated = preferences?.getBoolean(walletCreatedKey(), false) == true
        rememberedDerivationPaths = preferences?.getStringSet(derivationPathsKey(), emptySet()).orEmpty()
        currentWalletId = preferences?.getString(walletIdKey(), null)
    }

    private fun rollbackWalletCreation(): Boolean {
        isWalletCreated = false
        preferences?.edit()?.putBoolean(walletCreatedKey(), false)?.apply()
        return false
    }

    private fun walletCreatedKey(): String = "$WALLET_CREATED_KEY_PREFIX.${selectedCard.cardInstanceId}"

    private fun derivationPathsKey(): String = "$DERIVATION_PATHS_KEY_PREFIX.${selectedCard.cardInstanceId}"

    private fun walletIdKey(): String = "$WALLET_ID_KEY_PREFIX.${selectedCard.cardInstanceId}"

    private const val PREFERENCES_NAME = "external_ndef_wallet_mock"
    private const val WALLET_CREATED_KEY_PREFIX = "wallet_created"
    private const val DERIVATION_PATHS_KEY_PREFIX = "derived_paths"
    private const val WALLET_ID_KEY_PREFIX = "wallet_id"
    private const val TESTNET_BATCH_ID = "L0T1"
    private const val CARD_ID_LENGTH = 16

    private fun defaultCardIdentity() = ExternalNdefScanController.CardIdentity(
        cardInstanceId = "00000000-0000-0000-0000-000000000000",
        keyVersion = 1,
        targetUrl = "https://hm.niubtmd.com/tangem/testnet",
    )
}
