package com.tangem.tap.domain.sdk.mocks

import android.content.Context
import com.tangem.common.card.EllipticCurve
import com.tangem.common.extensions.ByteArrayKey
import com.tangem.domain.common.wallets.UserWalletsListRepository
import com.tangem.domain.models.MobileWallet
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.models.wallet.UserWalletId
import com.tangem.domain.wallets.builder.HotUserWalletBuilder
import com.tangem.domain.wallets.builder.UserWalletIdBuilder
import com.tangem.hot.sdk.TangemHotSdk
import com.tangem.hot.sdk.model.HotAuth
import com.tangem.hot.sdk.model.HotWalletId
import com.tangem.hot.sdk.model.MnemonicType
import com.tangem.hot.sdk.model.UnlockHotWallet
import com.tangem.operations.derivation.ExtendedPublicKeysMap
import com.tangem.sdk.api.CreateProductWalletTaskResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Binds a visual DemoCard profile to key material owned exclusively by Hot SDK.
 * Only the opaque Hot SDK identifier is persisted here; seed words and private keys never leave Hot SDK storage.
 */
@Singleton
internal class NfcDemoHotWalletBridge @Inject constructor(
    @ApplicationContext context: Context,
    private val hotSdk: TangemHotSdk,
    private val hotUserWalletBuilderFactory: HotUserWalletBuilder.Factory,
    private val userWalletsListRepository: UserWalletsListRepository,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createWalletResponse(
        cardId: String,
        fixture: CreateProductWalletTaskResponse,
    ): CreateProductWalletTaskResponse {
        val existing = bindingForCard(cardId)
        val hotWalletId = existing?.hotWalletId ?: hotSdk.generateWallet(HotAuth.NoAuth, MnemonicType.Words12)
        val hotWallet = hotUserWalletBuilderFactory.create(hotWalletId).build()
        saveBinding(cardId = cardId, walletId = hotWallet.walletId, hotWalletId = hotWalletId)
        return fixture.withHotWalletKeys(hotWallet)
    }

    suspend fun protectCurrentWallet(accessCode: String) {
        if (accessCode.isBlank()) return
        val walletId = preferences.getString(KEY_CURRENT_WALLET, null) ?: return
        val binding = bindingForWalletValue(walletId) ?: return
        if (binding.hotWalletId.authType != HotWalletId.AuthType.NoPassword) return

        val protectedId = hotSdk.changeAuth(
            unlockHotWallet = UnlockHotWallet(binding.hotWalletId, HotAuth.NoAuth),
            auth = HotAuth.Password(accessCode.toCharArray()),
        )
        saveHotWalletId(walletId, protectedId)
        NfcDemoLogger.event("hot_wallet_protected", "walletId=${walletId.takeLast(8)}")
    }

    fun isDemoWallet(userWalletId: UserWalletId): Boolean = bindingForWallet(userWalletId) != null

    fun signerWallet(userWalletId: UserWalletId): UserWallet.Hot? {
        val binding = bindingForWallet(userWalletId) ?: return null
        val coldWallet = userWalletsListRepository.userWallets.value.orEmpty()
            .firstOrNull { it is UserWallet.Cold && it.walletId == userWalletId } as? UserWallet.Cold
            ?: return null

        return UserWallet.Hot(
            name = coldWallet.name,
            walletId = coldWallet.walletId,
            hotWalletId = binding.hotWalletId,
            wallets = coldWallet.scanResponse.card.wallets.map {
                MobileWallet(
                    publicKey = it.publicKey,
                    chainCode = it.chainCode,
                    curve = it.curve,
                    derivedKeys = it.derivedKeys,
                )
            },
            backedUp = coldWallet.scanResponse.card.backupStatus?.isActive == true,
        )
    }

    fun saveCreatedScanResponse(cardId: String, scanResponse: ScanResponse) {
        preferences.edit()
            .putString(scanResponseKey(cardId), json.encodeToString(scanResponse))
            .apply()
        NfcDemoLogger.event(
            "hot_wallet_scan_saved",
            "cardId=${cardId.takeLast(4)} wallets=${scanResponse.card.wallets.size}",
        )
    }

    fun restoreSavedScanResponse(cardId: String, fallback: ScanResponse): ScanResponse {
        val binding = bindingForCard(cardId) ?: return fallback
        val persisted = preferences.getString(scanResponseKey(cardId), null)
        val restored = persisted?.let { encoded ->
            runCatching { json.decodeFromString<ScanResponse>(encoded) }.onFailure {
                NfcDemoLogger.warning(
                    "hot_wallet_scan_restore_failed",
                    "walletId=${binding.walletId.stringValue.takeLast(8)} type=${it::class.simpleName}",
                )
            }.getOrNull()
        }
        if (restored != null) {
            val restoredWalletId = UserWalletIdBuilder.scanResponse(restored).build()
            NfcDemoLogger.event(
                "hot_wallet_scan_restored",
                "walletId=${binding.walletId.stringValue.takeLast(8)} " +
                    "restoredId=${restoredWalletId?.stringValue?.takeLast(8)} " +
                    "matches=${restoredWalletId == binding.walletId} wallets=${restored.card.wallets.size}",
            )
            return restored
        }

        return (
            userWalletsListRepository.userWallets.value.orEmpty()
                .firstOrNull { it is UserWallet.Cold && it.walletId == binding.walletId } as? UserWallet.Cold
            )
            ?.scanResponse
            ?: fallback
    }

    private fun CreateProductWalletTaskResponse.withHotWalletKeys(
        hotWallet: UserWallet.Hot,
    ): CreateProductWalletTaskResponse {
        val templates = card.wallets.ifEmpty {
            error("Demo create-wallet fixture must contain wallet templates")
        }
        val mobileWallets = hotWallet.wallets.orEmpty()
        val walletsByCurve = mobileWallets.associateBy { it.curve }
        NfcDemoLogger.event(
            "hot_wallet_keys_available",
            "curves=${mobileWallets.joinToString { it.curve.name }}",
        )
        val cardWallets = templates.mapNotNull { template ->
            val wallet = walletsByCurve[template.curve]
            when {
                wallet != null -> template.copy(
                    publicKey = wallet.publicKey,
                    chainCode = wallet.chainCode,
                    curve = wallet.curve,
                    index = template.index,
                    derivedKeys = wallet.derivedKeys,
                    extendedPublicKey = wallet.extendedPublicKey,
                )
                template.curve == EllipticCurve.Bip0340 -> template.copy(
                    derivedKeys = emptyMap(),
                    extendedPublicKey = null,
                )
                else -> null
            }
        }
        val derivedKeys = cardWallets.associate { wallet ->
            ByteArrayKey(wallet.publicKey) to ExtendedPublicKeysMap(wallet.derivedKeys)
        }
        NfcDemoLogger.event(
            "hot_wallet_keys_mapped",
            "curves=${cardWallets.joinToString { it.curve.name }}",
        )
        return copy(card = card.copy(wallets = cardWallets), derivedKeys = derivedKeys)
    }

    private fun saveBinding(cardId: String, walletId: UserWalletId, hotWalletId: HotWalletId) {
        preferences.edit()
            .putString(cardKey(cardId), walletId.stringValue)
            .putString(KEY_CURRENT_WALLET, walletId.stringValue)
            .apply()
        saveHotWalletId(walletId.stringValue, hotWalletId)
    }

    private fun saveHotWalletId(walletId: String, hotWalletId: HotWalletId) {
        preferences.edit()
            .putString(hotValueKey(walletId), hotWalletId.value)
            .putString(hotAuthKey(walletId), hotWalletId.authType.name)
            .apply()
    }

    private fun bindingForCard(cardId: String): Binding? {
        val walletId = preferences.getString(cardKey(cardId), null) ?: return null
        return bindingForWalletValue(walletId)
    }

    private fun bindingForWallet(userWalletId: UserWalletId): Binding? = bindingForWalletValue(userWalletId.stringValue)

    private fun bindingForWalletValue(walletId: String): Binding? {
        val value = preferences.getString(hotValueKey(walletId), null) ?: return null
        val authName = preferences.getString(hotAuthKey(walletId), null) ?: return null
        val authType = runCatching { HotWalletId.AuthType.valueOf(authName) }.getOrNull() ?: return null
        return Binding(UserWalletId(walletId), HotWalletId(value, authType))
    }

    private data class Binding(val walletId: UserWalletId, val hotWalletId: HotWalletId)

    private companion object {
        const val PREFERENCES = "nfc_demo_hot_wallet_bindings"
        const val KEY_CURRENT_WALLET = "current_wallet"
        fun cardKey(cardId: String) = "card.$cardId"
        fun scanResponseKey(cardId: String) = "card.$cardId.scan_response"
        fun hotValueKey(walletId: String) = "wallet.$walletId.hot_value"
        fun hotAuthKey(walletId: String) = "wallet.$walletId.hot_auth"
    }
}
