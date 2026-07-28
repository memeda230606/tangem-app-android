package com.tangem.blockchain.common

import com.tangem.blockchain.common.address.Address
import com.tangem.blockchain.common.address.AddressType
import com.tangem.blockchain.common.derivation.DerivationStyle
import com.tangem.common.card.EllipticCurve
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey

@Suppress("EnumEntryName", "LargeClass")
enum class Blockchain {
    Unknown,
    Adi,
    AdiTestnet,
    AlephZero,
    AlephZeroTestnet,
    Alephium,
    AlephiumTestnet,
    Algorand,
    AlgorandTestnet,
    ApeChain,
    ApeChainTestnet,
    Aptos,
    AptosTestnet,
    Arbitrum,
    ArbitrumNova,
    ArbitrumTestnet,
    Areon,
    AreonTestnet,
    Aurora,
    AuroraTestnet,
    Avalanche,
    AvalancheTestnet,
    BSC,
    BSCTestnet,
    Base,
    BaseTestnet,
    Binance,
    BinanceTestnet,
    Bitcoin,
    BitcoinCash,
    BitcoinCashTestnet,
    BitcoinTestnet,
    Bitrock,
    BitrockTestnet,
    Bittensor,
    Blast,
    BlastTestnet,
    Canxium,
    Cardano,
    Casper,
    CasperTestnet,
    Chia,
    ChiaTestnet,
    Chiliz,
    ChilizTestnet,
    Clore,
    Core,
    CoreTestnet,
    Cosmos,
    CosmosTestnet,
    Cronos,
    Cyber,
    CyberTestnet,
    Dash,
    Decimal,
    DecimalTestnet,
    Dischain,
    Dogecoin,
    Ducatus,
    EnergyWebChain,
    EnergyWebChainTestnet,
    EnergyWebX,
    EnergyWebXTestnet,
    Ethereum,
    EthereumClassic,
    EthereumClassicTestnet,
    EthereumPow,
    EthereumPowTestnet,
    EthereumTestnet,
    Fact0rn,
    Fantom,
    FantomTestnet,
    Filecoin,
    Flare,
    FlareTestnet,
    Gnosis,
    Hedera,
    HederaTestnet,
    Hyperliquid,
    HyperliquidTestnet,
    InternetComputer,
    Joystream,
    Kaspa,
    KaspaTestnet,
    Kava,
    KavaTestnet,
    Koinos,
    KoinosTestnet,
    Kusama,
    Linea,
    LineaTestnet,
    Litecoin,
    Manta,
    MantaTestnet,
    Mantle,
    MantleTestnet,
    Monad,
    MonadTestnet,
    Moonbeam,
    MoonbeamTestnet,
    Moonriver,
    MoonriverTestnet,
    Near,
    NearTestnet,
    Nexa,
    NexaTestnet,
    OctaSpace,
    OctaSpaceTestnet,
    OdysseyChain,
    OdysseyChainTestnet,
    Optimism,
    OptimismTestnet,
    Pepecoin,
    PepecoinTestnet,
    Plasma,
    PlasmaTestnet,
    Playa3ull,
    Polkadot,
    PolkadotTestnet,
    Polygon,
    PolygonTestnet,
    PolygonZkEVM,
    PolygonZkEVMTestnet,
    PulseChain,
    PulseChainTestnet,
    Quai,
    QuaiTestnet,
    RSK,
    Radiant,
    Ravencoin,
    RavencoinTestnet,
    Scroll,
    ScrollTestnet,
    Sei,
    SeiEvm,
    SeiEvmTestnet,
    SeiTestnet,
    Shibarium,
    ShibariumTestnet,
    Solana,
    SolanaTestnet,
    Sonic,
    SonicTestnet,
    Stellar,
    StellarTestnet,
    Sui,
    SuiTestnet,
    TON,
    TONTestnet,
    Taraxa,
    TaraxaTestnet,
    Telos,
    TelosTestnet,
    TerraV1,
    TerraV2,
    Tezos,
    Tron,
    TronTestnet,
    VanarChain,
    VanarChainTestnet,
    VeChain,
    VeChainTestnet,
    XDC,
    XDCTestnet,
    XRP,
    Xodex,
    ZkLinkNova,
    ZkLinkNovaTestnet,
    ZkSyncEra,
    ZkSyncEraTestnet,
    ;

    val id: String
        get() = name
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .lowercase()
            .removeSuffix("-testnet")
            .let { if (isTestnet()) "$it/test" else it }

    val fullName: String
        get() = name

    val currency: String
        get() = name.uppercase()

    fun decimals(): Int = when (this) {
        Bitcoin, BitcoinTestnet, Litecoin, Dogecoin, BitcoinCash, BitcoinCashTestnet, Dash, Ravencoin,
        RavencoinTestnet, Clore, Pepecoin, PepecoinTestnet, Fact0rn,
        -> 8
        Cardano -> 6
        Solana, SolanaTestnet -> 9
        else -> 18
    }

    fun isEvm(): Boolean = this in evmBlockchains

    fun getChainId(): Int? = getEvmChainIdFor(this)

    fun canHandleTokens(): Boolean = isEvm() || this in tokenBlockchains

    fun canHandleNFTs(): Boolean = isEvm() || this in nftBlockchains

    fun isFeeApproximate(amountType: AmountType): Boolean = false

    fun isNetworkFeeZero(): Boolean = false

    fun reformatContractAddress(contractAddress: String?): String? {
        val normalized = contractAddress?.trim().orEmpty()
        if (normalized.isEmpty()) return null

        return when {
            isEvm() -> {
                val withoutPrefix = normalized.removePrefix("0x").removePrefix("0X")
                if (withoutPrefix.length != 40 || !withoutPrefix.isHex()) {
                    null
                } else {
                    "$HEX_PREFIX${withoutPrefix.lowercase()}"
                }
            }
            else -> normalized
        }
    }

    fun validateAddress(address: String): Boolean {
        val value = address.trim()
        if (value.isEmpty()) return false

        return when {
            isEvm() -> value.removePrefix("0x").removePrefix("0X").let { it.length == 40 && it.isHex() }
            this == Tron || this == TronTestnet -> value.length == 34 && value.startsWith("T")
            this == Solana || this == SolanaTestnet -> value.length in 32..44 && value.isBase58()
            this == XRP -> value.length in 25..35 && value.startsWith("r")
            this == Bitcoin || this == BitcoinTestnet -> {
                value.startsWith("bc1", ignoreCase = true) ||
                    value.startsWith("tb1", ignoreCase = true) ||
                    value.firstOrNull() in setOf('1', '3', 'm', 'n', '2')
            }
            this == Litecoin -> {
                value.startsWith("ltc1", ignoreCase = true) ||
                    value.firstOrNull() in setOf('L', 'M', '3')
            }
            this == Dogecoin -> value.firstOrNull() in setOf('D', 'A', '9')
            this == BitcoinCash || this == BitcoinCashTestnet -> {
                value.startsWith("bitcoincash:", ignoreCase = true) ||
                    value.startsWith("bchtest:", ignoreCase = true) ||
                    value.firstOrNull() in setOf('q', 'p')
            }
            this == Binance || this == BinanceTestnet -> {
                value.startsWith("bnb", ignoreCase = true) || value.startsWith("tbnb", ignoreCase = true)
            }
            this == Stellar || this == StellarTestnet -> value.length == 56 && value.startsWith("G")
            else -> false
        }
    }

    fun validateContractAddress(contractAddress: String): Boolean {
        return contractAddress.isNotBlank()
    }

    fun getShareScheme(): List<String> {
        return when (this) {
            Bitcoin, BitcoinTestnet -> listOf("bitcoin:")
            Litecoin -> listOf("litecoin:")
            Binance, BinanceTestnet -> listOf("bnb:", "binance:")
            Dogecoin -> listOf("dogecoin:")
            XRP -> listOf("ripple:", "xrp:")
            Ethereum -> listOf("ethereum:")
            Solana, SolanaTestnet -> listOf("solana:")
            Tron, TronTestnet -> listOf("tron:")
            else -> emptyList()
        }
    }

    fun validateShareScheme(scheme: String): Boolean {
        val normalized = scheme.trim().removeSuffix(":")
        return getShareScheme().any { it.removeSuffix(":").equals(normalized, ignoreCase = true) }
    }

    fun isTestnet(): Boolean = name.endsWith("Testnet")

    fun isL2EthereumNetwork(): Boolean = this in l2Blockchains

    fun isBip44DerivationStyleXPUB(): Boolean = this in bip44XpubBlockchains

    fun getCoinName(): String = fullName

    fun feePaidCurrency(): FeePaidCurrency = FeePaidCurrency.Coin

    fun getSupportedCurves(): Set<EllipticCurve> = getSupportedCurvesFor(this)

    fun derivationPath(style: DerivationStyle? = DerivationStyle.V3): DerivationPath? = derivationPathFor(
        blockchain = this,
        style = style,
    )

    fun makeAddresses(
        walletPublicKey: ByteArray,
        pairPublicKey: ByteArray? = null,
        curve: EllipticCurve = EllipticCurve.Secp256k1,
    ): Set<Address> = makeAddressesFor(
        blockchain = this,
        walletPublicKey = walletPublicKey,
        pairPublicKey = pairPublicKey,
        curve = curve,
    )

    fun makeAddressesFromExtendedPublicKey(
        extendedPublicKey: ExtendedPublicKey,
        rawPath: String? = null,
        cachedIndex: Int? = null,
    ): DerivedAddressData = DerivedAddressData(address = makeAddresses(extendedPublicKey.publicKey).first().value)

    fun getTestnetVersion(): Blockchain? {
        if (isTestnet() || this == Unknown) return null

        return entries.firstOrNull { it.name == "${name}Testnet" }
    }

    fun getTestnetTopUpUrl(): String? = null

    fun getExploreUrl(address: String, contractAddress: String? = null): String {
        val target = contractAddress ?: address
        return "https://explorer.local/${id}/$target"
    }

    fun getExploreTxUrl(txHash: String): com.tangem.blockchain.externallinkprovider.TxExploreState {
        return com.tangem.blockchain.externallinkprovider.TxExploreState.Url(
            url = "https://explorer.local/${id}/tx/$txHash",
        )
    }

    fun getTokenExplorerTxUrl(txHash: String): com.tangem.blockchain.externallinkprovider.TxExploreState {
        return getExploreTxUrl(txHash)
    }

    fun getNFTExploreUrl(assetIdentifier: com.tangem.blockchain.nft.models.NFTAsset.Identifier): String? {
        return "https://explorer.local/${id}/nft/${assetIdentifier.hashCode()}"
    }

    companion object {
        fun fromId(id: String?): Blockchain = entries.firstOrNull { it.id == id } ?: Unknown

        fun fromNetworkId(networkId: String?): Blockchain? {
            if (networkId == null) return null
            return entries.firstOrNull { it.id == networkId || it.name.equals(networkId, ignoreCase = true) }
        }

        fun fromChainId(chainId: Int): Blockchain? {
            return entries.firstOrNull { it.getChainId() == chainId }
        }

        fun fromCurve(curve: EllipticCurve): List<Blockchain> {
            return entries.filter { blockchain -> curve in blockchain.getSupportedCurves() }
        }

        fun secp256k1Blockchains(isTestnet: Boolean): List<Blockchain> {
            return entries.filter { blockchain ->
                blockchain.isTestnet() == isTestnet && EllipticCurve.Secp256k1 in blockchain.getSupportedCurves()
            }
        }

        fun ed25519Blockchains(isTestnet: Boolean): List<Blockchain> {
            return entries.filter { blockchain ->
                blockchain.isTestnet() == isTestnet && (
                    EllipticCurve.Ed25519 in blockchain.getSupportedCurves() ||
                        EllipticCurve.Ed25519Slip0010 in blockchain.getSupportedCurves()
                    )
            }
        }

        fun yieldSupplySupportedBlockchains(): List<Blockchain> {
            return listOf(Ethereum, Polygon, BSC, Arbitrum, Optimism, Avalanche, Base)
        }

        private val evmBlockchains: Set<Blockchain>
            get() = setOf(
                Ethereum, EthereumTestnet, EthereumClassic, EthereumClassicTestnet, EthereumPow, EthereumPowTestnet,
                BSC, BSCTestnet, Polygon, PolygonTestnet, Avalanche, AvalancheTestnet, Fantom, FantomTestnet,
                Arbitrum, ArbitrumTestnet, ArbitrumNova, Optimism, OptimismTestnet, Gnosis, Cronos, Kava,
                KavaTestnet, Telos, TelosTestnet, OctaSpace, OctaSpaceTestnet, XDC, XDCTestnet, Shibarium,
                ShibariumTestnet, Areon, AreonTestnet, PulseChain, PulseChainTestnet, Aurora, AuroraTestnet,
                Manta, MantaTestnet, ZkSyncEra, ZkSyncEraTestnet, Moonbeam, MoonbeamTestnet, PolygonZkEVM,
                PolygonZkEVMTestnet, Moonriver, MoonriverTestnet, Mantle, MantleTestnet, Flare, FlareTestnet,
                Taraxa, TaraxaTestnet, Base, BaseTestnet, Blast, BlastTestnet, Cyber, CyberTestnet,
                EnergyWebChain, EnergyWebChainTestnet, EnergyWebX, EnergyWebXTestnet, Core, CoreTestnet,
                Chiliz, ChilizTestnet, VanarChain, VanarChainTestnet, Xodex, Canxium, OdysseyChain,
                OdysseyChainTestnet, Bitrock, BitrockTestnet, Sonic, SonicTestnet, ApeChain, ApeChainTestnet,
                Scroll, ScrollTestnet, ZkLinkNova, ZkLinkNovaTestnet, Hyperliquid, HyperliquidTestnet,
                Quai, QuaiTestnet, Linea, LineaTestnet, Plasma, PlasmaTestnet, SeiEvm, SeiEvmTestnet,
                Monad, MonadTestnet, RSK, Dischain,
            )

        private val tokenBlockchains: Set<Blockchain>
            get() = setOf(
                Solana, SolanaTestnet, Stellar, StellarTestnet, Tron, TronTestnet, TON, TONTestnet,
                Cardano, Hedera, HederaTestnet, Aptos, AptosTestnet, Sui, SuiTestnet, XRP, Algorand,
                AlgorandTestnet, Tezos, Alephium, AlephiumTestnet, InternetComputer,
            )

        private val nftBlockchains: Set<Blockchain>
            get() = setOf(Solana, SolanaTestnet, TON, TONTestnet)

        private val l2Blockchains: Set<Blockchain>
            get() = setOf(
                Arbitrum, ArbitrumNova, Optimism, Polygon, Base, Blast, Linea, Scroll, ZkSyncEra,
                PolygonZkEVM, Mantle, Manta,
            )

        private val bip44XpubBlockchains: Set<Blockchain>
            get() = setOf(
                Bitcoin,
                BitcoinTestnet,
                Litecoin,
                Dogecoin,
                BitcoinCash,
                BitcoinCashTestnet,
            )
    }
}

private fun getSupportedCurvesFor(blockchain: Blockchain): Set<EllipticCurve> {
    return when (blockchain) {
        Blockchain.Unknown -> emptySet()
        Blockchain.Solana, Blockchain.SolanaTestnet,
        Blockchain.Stellar, Blockchain.StellarTestnet,
        Blockchain.Algorand, Blockchain.AlgorandTestnet,
        Blockchain.Aptos, Blockchain.AptosTestnet,
        Blockchain.Sui, Blockchain.SuiTestnet,
        Blockchain.Near, Blockchain.NearTestnet,
        Blockchain.Polkadot, Blockchain.PolkadotTestnet,
        Blockchain.Kusama,
        Blockchain.TON, Blockchain.TONTestnet,
        Blockchain.Tezos,
        Blockchain.Hedera, Blockchain.HederaTestnet,
        -> setOf(EllipticCurve.Ed25519, EllipticCurve.Ed25519Slip0010)
        Blockchain.Chia, Blockchain.ChiaTestnet -> setOf(EllipticCurve.Bls12381G2Aug)
        else -> setOf(EllipticCurve.Secp256k1)
    }
}

fun Blockchain.getSupportedCurves(): Set<EllipticCurve> = getSupportedCurvesFor(this)

fun Blockchain.getChainId(): Int? = getEvmChainIdFor(this)

private fun String.isHex(): Boolean = all { char ->
    char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
}

private fun String.isBase58(): Boolean = all { char ->
    char in "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
}

@Suppress("ComplexMethod")
private fun getEvmChainIdFor(blockchain: Blockchain): Int? {
    return when (blockchain) {
        Blockchain.Ethereum -> 1
        Blockchain.EthereumTestnet -> 11155111
        Blockchain.EthereumClassic -> 61
        Blockchain.EthereumClassicTestnet -> 63
        Blockchain.EthereumPow -> 10001
        Blockchain.EthereumPowTestnet -> 10002
        Blockchain.BSC -> 56
        Blockchain.BSCTestnet -> 97
        Blockchain.Polygon -> 137
        Blockchain.PolygonTestnet -> 80002
        Blockchain.Avalanche -> 43114
        Blockchain.AvalancheTestnet -> 43113
        Blockchain.Fantom -> 250
        Blockchain.FantomTestnet -> 4002
        Blockchain.Arbitrum -> 42161
        Blockchain.ArbitrumTestnet -> 421614
        Blockchain.ArbitrumNova -> 42170
        Blockchain.Optimism -> 10
        Blockchain.OptimismTestnet -> 11155420
        Blockchain.Gnosis -> 100
        Blockchain.Cronos -> 25
        Blockchain.Kava -> 2222
        Blockchain.KavaTestnet -> 2221
        Blockchain.Telos -> 40
        Blockchain.TelosTestnet -> 41
        Blockchain.OctaSpace -> 800001
        Blockchain.OctaSpaceTestnet -> 800002
        Blockchain.XDC -> 50
        Blockchain.XDCTestnet -> 51
        Blockchain.Shibarium -> 109
        Blockchain.ShibariumTestnet -> 157
        Blockchain.Areon -> 463
        Blockchain.AreonTestnet -> 462
        Blockchain.PulseChain -> 369
        Blockchain.PulseChainTestnet -> 943
        Blockchain.Aurora -> 1313161554
        Blockchain.AuroraTestnet -> 1313161555
        Blockchain.Manta -> 169
        Blockchain.MantaTestnet -> 3441006
        Blockchain.ZkSyncEra -> 324
        Blockchain.ZkSyncEraTestnet -> 300
        Blockchain.Moonbeam -> 1284
        Blockchain.MoonbeamTestnet -> 1287
        Blockchain.PolygonZkEVM -> 1101
        Blockchain.PolygonZkEVMTestnet -> 2442
        Blockchain.Moonriver -> 1285
        Blockchain.MoonriverTestnet -> 1285
        Blockchain.Mantle -> 5000
        Blockchain.MantleTestnet -> 5003
        Blockchain.Flare -> 14
        Blockchain.FlareTestnet -> 114
        Blockchain.Taraxa -> 841
        Blockchain.TaraxaTestnet -> 842
        Blockchain.Base -> 8453
        Blockchain.BaseTestnet -> 84532
        Blockchain.Blast -> 81457
        Blockchain.BlastTestnet -> 168587773
        Blockchain.Cyber -> 7560
        Blockchain.CyberTestnet -> 111557560
        Blockchain.EnergyWebChain -> 246
        Blockchain.EnergyWebChainTestnet -> 73799
        Blockchain.EnergyWebX -> 246
        Blockchain.EnergyWebXTestnet -> 73799
        Blockchain.Core -> 1116
        Blockchain.CoreTestnet -> 1115
        Blockchain.Chiliz -> 88888
        Blockchain.ChilizTestnet -> 88882
        Blockchain.VanarChain -> 2040
        Blockchain.VanarChainTestnet -> 78600
        Blockchain.Xodex -> 2415
        Blockchain.Canxium -> 3003
        Blockchain.OdysseyChain -> 153153
        Blockchain.OdysseyChainTestnet -> 131313
        Blockchain.Bitrock -> 7171
        Blockchain.BitrockTestnet -> 7771
        Blockchain.Sonic -> 146
        Blockchain.SonicTestnet -> 57054
        Blockchain.ApeChain -> 33139
        Blockchain.ApeChainTestnet -> 33111
        Blockchain.Scroll -> 534352
        Blockchain.ScrollTestnet -> 534351
        Blockchain.ZkLinkNova -> 810180
        Blockchain.ZkLinkNovaTestnet -> 810181
        Blockchain.Hyperliquid -> 999
        Blockchain.HyperliquidTestnet -> 998
        Blockchain.Quai -> 994
        Blockchain.QuaiTestnet -> 9000
        Blockchain.Linea -> 59144
        Blockchain.LineaTestnet -> 59141
        Blockchain.Plasma -> 9745
        Blockchain.PlasmaTestnet -> 9746
        Blockchain.SeiEvm -> 1329
        Blockchain.SeiEvmTestnet -> 1328
        Blockchain.Monad -> 143
        Blockchain.MonadTestnet -> 10143
        Blockchain.RSK -> 30
        Blockchain.Dischain -> 513100
        else -> null
    }
}

private fun derivationPathFor(blockchain: Blockchain, style: DerivationStyle? = DerivationStyle.V3): DerivationPath? {
    if (blockchain == Blockchain.Unknown) return null

    val purpose = when (style) {
        DerivationStyle.LEGACY, DerivationStyle.V1 -> 44
        DerivationStyle.NEW, DerivationStyle.V2, DerivationStyle.V3, null -> 44
    }
    val coinType = when (blockchain) {
        Blockchain.Bitcoin, Blockchain.BitcoinTestnet -> 0
        Blockchain.Ethereum, Blockchain.EthereumTestnet,
        Blockchain.Polygon, Blockchain.PolygonTestnet,
        Blockchain.BSC, Blockchain.BSCTestnet,
        -> 60
        Blockchain.Solana, Blockchain.SolanaTestnet -> 501
        Blockchain.Cardano -> 1815
        else -> blockchain.ordinal.coerceAtLeast(0)
    }

    return DerivationPath("m/$purpose'/$coinType'/0'/0/0")
}

fun Blockchain.derivationPath(style: DerivationStyle? = DerivationStyle.V3): DerivationPath? =
    derivationPathFor(blockchain = this, style = style)

private fun makeAddressesFor(
    blockchain: Blockchain,
    walletPublicKey: ByteArray,
    pairPublicKey: ByteArray? = null,
    curve: EllipticCurve = EllipticCurve.Secp256k1,
): Set<Address> {
    val key = walletPublicKey.joinToString(separator = "") { "%02x".format(it) }.take(40)
    val value = when {
        blockchain.isEvm() || blockchain == Blockchain.Polygon -> "0x${key.padEnd(40, '0')}"
        else -> "${blockchain.id}_${key.ifBlank { "address" }}"
    }
    return setOf(Address(value = value, type = AddressType.Default))
}

fun Blockchain.makeAddresses(
    walletPublicKey: ByteArray,
    pairPublicKey: ByteArray? = null,
    curve: EllipticCurve = EllipticCurve.Secp256k1,
): Set<Address> = makeAddressesFor(
    blockchain = this,
    walletPublicKey = walletPublicKey,
    pairPublicKey = pairPublicKey,
    curve = curve,
)

data class DerivedAddressData(val address: String)

fun Blockchain.makeAddressesFromExtendedPublicKey(
    extendedPublicKey: ExtendedPublicKey,
    rawPath: String? = null,
    cachedIndex: Int? = null,
): DerivedAddressData {
    return DerivedAddressData(address = makeAddresses(extendedPublicKey.publicKey).first().value)
}

fun Blockchain.isBip44DerivationStyleXPUB(): Boolean = this in setOf(
    Blockchain.Bitcoin,
    Blockchain.BitcoinTestnet,
    Blockchain.Litecoin,
    Blockchain.Dogecoin,
    Blockchain.BitcoinCash,
    Blockchain.BitcoinCashTestnet,
)

val Blockchain.isUTXO: Boolean
    get() = this in setOf(
        Blockchain.Bitcoin,
        Blockchain.BitcoinTestnet,
        Blockchain.BitcoinCash,
        Blockchain.BitcoinCashTestnet,
        Blockchain.Litecoin,
        Blockchain.Dogecoin,
        Blockchain.Dash,
        Blockchain.Ravencoin,
        Blockchain.RavencoinTestnet,
        Blockchain.Kaspa,
        Blockchain.KaspaTestnet,
        Blockchain.Nexa,
        Blockchain.NexaTestnet,
        Blockchain.Radiant,
        Blockchain.Clore,
        Blockchain.Pepecoin,
        Blockchain.PepecoinTestnet,
        Blockchain.Fact0rn,
    )
