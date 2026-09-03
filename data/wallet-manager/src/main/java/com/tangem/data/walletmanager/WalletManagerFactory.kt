package com.tangem.data.walletmanager

import com.tangem.blockchain.common.Blockchain
import com.tangem.blockchain.common.DerivationParams
import com.tangem.blockchain.common.Wallet
import com.tangem.blockchain.common.WalletManager
import com.tangem.blockchainsdk.BlockchainSDKFactory
import com.tangem.common.extensions.toMapKey
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.data.walletmanager.extensions.makePublicKey
import com.tangem.data.walletmanager.extensions.makeWalletManagerForApp
import com.tangem.data.walletmanager.extensions.selectWallet
import com.tangem.domain.card.common.TapWorkarounds.isTestCard
import com.tangem.domain.card.common.util.cardTypesResolver
import com.tangem.domain.card.configs.CardConfig
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.wallet.UserWallet
import com.tangem.domain.wallets.config.curvesConfig
import com.tangem.domain.wallets.derivations.DerivationStyleProvider
import com.tangem.domain.wallets.derivations.derivationStyleProvider
import com.tangem.utils.logging.TangemLogger

internal class WalletManagerFactory(
    private val blockchainSDKFactory: BlockchainSDKFactory,
    private val externalNetworkBalanceFallback: ExternalNetworkBalanceFallback,
) {

    suspend fun createWalletManager(
        scanResponse: ScanResponse,
        blockchain: Blockchain,
        derivationPath: DerivationPath?,
    ): WalletManager? {
        val derivationParams = getDerivationParams(derivationPath, scanResponse.derivationStyleProvider)

        createReadOnlyWalletManager(
            scanResponse = scanResponse,
            blockchain = blockchain,
            derivationParams = derivationParams,
        )?.let { return it }

        val sdkWalletManager = try {
            blockchainSDKFactory.getWalletManagerFactorySync()?.makeWalletManagerForApp(
                scanResponse = scanResponse,
                blockchain = blockchain,
                derivationParams = derivationParams,
            )
        } catch (e: Throwable) {
            TangemLogger.w("Failed to create wallet manager for $blockchain", e)
            null
        }

        return sdkWalletManager ?: createReadOnlyWalletManager(
            scanResponse = scanResponse,
            blockchain = blockchain,
            derivationParams = derivationParams,
        )
    }

    suspend fun createWalletManagerForHot(
        hotWallet: UserWallet.Hot,
        blockchain: Blockchain,
        derivationPath: DerivationPath?,
    ): WalletManager? {
        val curve = hotWallet.curvesConfig.primaryCurve(blockchain)
        val selectedWallet = hotWallet.wallets.orEmpty().firstOrNull { it.curve == curve }
            ?: return null

        val fallbackPublicKey = if (derivationPath == null) {
            Wallet.PublicKey(
                seedKey = selectedWallet.publicKey,
                derivationType = null,
            )
        } else {
            makePublicKey(
                seedKey = selectedWallet.publicKey,
                blockchain = blockchain,
                derivationPath = derivationPath,
                derivedWalletKeys = selectedWallet.derivedKeys,
                isWallet2 = true,
            )
        }

        fallbackPublicKey?.let { publicKey ->
            createReadOnlyWalletManager(
                blockchain = blockchain,
                publicKey = publicKey,
                curve = selectedWallet.curve,
            )?.let { return it }
        }

        val sdkWalletManager = try {
            val factory = blockchainSDKFactory.getWalletManagerFactorySync() ?: return null

            if (derivationPath == null) {
                factory.createLegacyWalletManager(
                    blockchain = blockchain,
                    walletPublicKey = selectedWallet.publicKey,
                    curve = selectedWallet.curve,
                )
            } else {
                factory.createWalletManager(
                    blockchain = blockchain,
                    publicKey = makePublicKey(
                        seedKey = selectedWallet.publicKey,
                        blockchain = blockchain,
                        derivationPath = derivationPath,
                        derivedWalletKeys = selectedWallet.derivedKeys,
                        isWallet2 = true,
                    ) ?: return null,
                    curve = selectedWallet.curve,
                )
            }
        } catch (e: Throwable) {
            TangemLogger.w("Failed to create wallet manager for $blockchain", e)
            null
        }

        if (sdkWalletManager != null) return sdkWalletManager

        if (derivationPath == null) {
            return createReadOnlyWalletManager(
                blockchain = blockchain,
                publicKey = Wallet.PublicKey(
                    seedKey = selectedWallet.publicKey,
                    derivationType = null,
                ),
                curve = selectedWallet.curve,
            )
        }

        val publicKey = makePublicKey(
            seedKey = selectedWallet.publicKey,
            blockchain = blockchain,
            derivationPath = derivationPath,
            derivedWalletKeys = selectedWallet.derivedKeys,
            isWallet2 = true,
        ) ?: return null

        return createReadOnlyWalletManager(
            blockchain = blockchain,
            publicKey = publicKey,
            curve = selectedWallet.curve,
        )
    }

    private fun createReadOnlyWalletManager(
        scanResponse: ScanResponse,
        blockchain: Blockchain,
        derivationParams: DerivationParams?,
    ): WalletManager? {
        if (!externalNetworkBalanceFallback.supports(blockchain)) return null

        val card = scanResponse.card
        val cardConfig = CardConfig.createConfig(card)
        val selectedWallet = selectWallet(
            wallets = card.wallets.filter { it.curve in blockchain.getSupportedCurves() },
            cardConfig = cardConfig,
            blockchain = blockchain,
        ) ?: return null
        val publicKey = if (
            card.settings.isHDWalletAllowed &&
            selectedWallet.extendedPublicKey != null &&
            derivationParams != null
        ) {
            val derivationPath = derivationParams.getPath(blockchain) ?: return null

            makePublicKey(
                seedKey = selectedWallet.publicKey,
                blockchain = blockchain,
                derivationPath = derivationPath,
                derivedWalletKeys = scanResponse.derivedKeys[selectedWallet.publicKey.toMapKey()] ?: return null,
                isWallet2 = scanResponse.cardTypesResolver.isWallet2(),
            ) ?: return null
        } else {
            Wallet.PublicKey(
                seedKey = selectedWallet.publicKey,
                derivationType = null,
            )
        }
        val environmentBlockchain = if (card.isTestCard) {
            blockchain.getTestnetVersion() ?: return null
        } else {
            blockchain
        }

        return createReadOnlyWalletManager(
            blockchain = environmentBlockchain,
            publicKey = publicKey,
            curve = selectedWallet.curve,
        )
    }

    private fun createReadOnlyWalletManager(
        blockchain: Blockchain,
        publicKey: Wallet.PublicKey,
        curve: com.tangem.common.card.EllipticCurve,
    ): WalletManager? {
        if (!externalNetworkBalanceFallback.supports(blockchain)) return null

        return try {
            val wallet = Wallet(
                blockchain = blockchain,
                addresses = blockchain.makeAddresses(
                    walletPublicKey = publicKey.blockchainKey,
                    pairPublicKey = null,
                    curve = curve,
                ),
                publicKey = publicKey,
                tokens = emptySet(),
            )

            when (blockchain) {
                Blockchain.BitcoinCash -> SdkReadOnlyWalletManagerFactory.createBitcoinCash(
                    wallet,
                    ExternalBitcoinCashNetworkProvider(externalNetworkBalanceFallback),
                )
                Blockchain.TON -> SdkReadOnlyWalletManagerFactory.createTon(
                    wallet,
                    ExternalTonNetworkProvider(externalNetworkBalanceFallback),
                )
                else -> null
            }
        } catch (e: Throwable) {
            TangemLogger.w("Failed to create read-only wallet manager for $blockchain", e)
            null
        }
    }

    private fun getDerivationParams(
        derivationPath: DerivationPath?,
        derivationStyleProvider: DerivationStyleProvider,
    ): DerivationParams? {
        val derivationStyle = derivationStyleProvider.getDerivationStyle() ?: return null

        return if (derivationPath == null) {
            DerivationParams.Default(derivationStyle)
        } else {
            DerivationParams.Custom(derivationPath)
        }
    }
}
