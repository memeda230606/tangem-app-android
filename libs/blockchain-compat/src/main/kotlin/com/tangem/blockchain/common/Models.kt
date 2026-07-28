package com.tangem.blockchain.common

import com.tangem.blockchain.common.address.Address
import com.tangem.blockchain.common.smartcontract.SmartContractCallData
import com.tangem.blockchain.common.transaction.TransactionFee
import com.tangem.blockchain.common.transaction.TransactionSendResult
import com.tangem.blockchain.common.transaction.TransactionsSendResult
import com.tangem.blockchain.extensions.Result
import com.tangem.blockchain.extensions.SimpleResult
import java.math.BigDecimal
import java.math.BigInteger
import java.util.EnumSet

const val HEX_PREFIX = "0x"

data class Token(
    val symbol: String,
    val contractAddress: String,
    val decimals: Int,
    val name: String = symbol,
    val id: String? = null,
)

sealed class AmountType {
    object Coin : AmountType()
    data class Token(val token: com.tangem.blockchain.common.Token) : AmountType()
    data class TokenYieldSupply(
        val token: com.tangem.blockchain.common.Token,
        val isActive: Boolean = false,
        val isInitialized: Boolean = false,
        val isAllowedToSpend: Boolean = false,
        val effectiveProtocolBalance: BigDecimal? = null,
    ) : AmountType()
    data class FeeResource(val name: String = "") : AmountType()
    data class Reserve(val name: String = "") : AmountType()
}

data class Amount(
    val value: BigDecimal?,
    val blockchain: Blockchain = Blockchain.Unknown,
    val type: AmountType = AmountType.Coin,
    val currencySymbol: String = when (val amountType = type) {
        AmountType.Coin -> blockchain.currency
        is AmountType.Token -> amountType.token.symbol
        is AmountType.TokenYieldSupply -> amountType.token.symbol
        is AmountType.FeeResource -> amountType.name
        is AmountType.Reserve -> amountType.name
    },
    val decimals: Int = when (val amountType = type) {
        AmountType.Coin -> blockchain.decimals()
        is AmountType.Token -> amountType.token.decimals
        is AmountType.TokenYieldSupply -> amountType.token.decimals
        is AmountType.FeeResource -> blockchain.decimals()
        is AmountType.Reserve -> blockchain.decimals()
    },
) {
    constructor(
        token: Token,
        value: BigDecimal?,
        blockchain: Blockchain = Blockchain.Unknown,
    ) : this(
        value = value,
        blockchain = blockchain,
        type = AmountType.Token(token),
    )
}

sealed class CryptoCurrencyType {
    data object Coin : CryptoCurrencyType()
    data class Token(val info: com.tangem.blockchain.common.Token) : CryptoCurrencyType()
}

sealed class DerivationParams {
    data class Default(val style: com.tangem.blockchain.common.derivation.DerivationStyle) : DerivationParams()
    data class Custom(val path: com.tangem.crypto.hdWallet.DerivationPath) : DerivationParams()

    fun getPath(blockchain: Blockchain): com.tangem.crypto.hdWallet.DerivationPath? {
        return when (this) {
            is Default -> blockchain.derivationPath(style)
            is Custom -> path
        }
    }
}

data class BlockchainFeatureToggles(
    val isYieldSupplyEnabled: Boolean = false,
    val isPendingTransactionsEnabled: Boolean = false,
    val isSolanaTxHistoryEnabled: Boolean = false,
    val isSolanaScaledUiAmountEnabled: Boolean = false,
    val isHederaErc20Enabled: Boolean = false,
)

fun interface AccountCreator {
    suspend fun createAccount(blockchain: Blockchain, walletPublicKey: ByteArray): Result<String>
}

interface WalletManager : TransactionSender {
    val wallet: Wallet

    val outputsCount: Int?
        get() = null

    val currentHost: String
        get() = ""

    val cardTokens: Set<Token>
        get() = wallet.tokens

    val isSelfSendAvailable: Boolean
        get() = true

    val dustValue: BigDecimal?
        get() = null

    suspend fun update(): Result<Unit> = Result.Success(Unit)

    suspend fun update(forceUpdate: Boolean): Result<Unit> = update()

    fun addTokens(tokens: List<Token>) = Unit

    fun removeToken(token: Token) = Unit

    override suspend fun getFee(transactionData: TransactionData): Result<TransactionFee> =
        Result.Failure(BlockchainSdkError.CustomError("Fee calculation is not implemented"))

    override suspend fun getFee(
        amount: Amount,
        destination: String,
        callData: SmartContractCallData?,
    ): Result<TransactionFee> = Result.Failure(BlockchainSdkError.CustomError("Fee calculation is not implemented"))

    override suspend fun estimateFee(amount: Amount, destination: String): Result<TransactionFee> =
        Result.Failure(BlockchainSdkError.CustomError("Fee estimation is not implemented"))

    override suspend fun estimateFee(
        amount: Amount,
        destination: String,
        callData: SmartContractCallData?,
    ): Result<TransactionFee> = estimateFee(amount, destination)

    fun validateTransaction(amount: Amount, fee: Amount?): EnumSet<TransactionError> =
        EnumSet.noneOf(TransactionError::class.java)

    fun createTransaction(
        amount: Amount,
        fee: com.tangem.blockchain.common.transaction.Fee,
        destination: String,
    ): TransactionData.Uncompiled =
        TransactionData.Uncompiled(amount = amount, fee = fee, destinationAddress = destination)

    fun getTransactionHistoryState(
        address: String,
        filterType: com.tangem.blockchain.transactionhistory.models.TransactionHistoryRequest.FilterType,
    ): com.tangem.blockchain.transactionhistory.TransactionHistoryState {
        return com.tangem.blockchain.transactionhistory.TransactionHistoryState.NotImplemented
    }

    suspend fun getTransactionsHistory(
        request: com.tangem.blockchain.transactionhistory.models.TransactionHistoryRequest,
    ): Result<com.tangem.blockchain.transactionhistory.models.TransactionHistoryResponse> {
        return Result.Success(com.tangem.blockchain.transactionhistory.models.TransactionHistoryResponse())
    }

    suspend fun getCollections(address: String): List<com.tangem.blockchain.nft.models.NFTCollection> = emptyList()

    suspend fun getAssets(
        address: String,
        collectionIdentifier: com.tangem.blockchain.nft.models.NFTCollection.Identifier,
    ): List<com.tangem.blockchain.nft.models.NFTAsset> = emptyList()

    suspend fun getAsset(
        collectionIdentifier: com.tangem.blockchain.nft.models.NFTCollection.Identifier,
        assetIdentifier: com.tangem.blockchain.nft.models.NFTAsset.Identifier,
    ): com.tangem.blockchain.nft.models.NFTAsset? = null

    suspend fun getSalePrice(
        collectionIdentifier: com.tangem.blockchain.nft.models.NFTCollection.Identifier,
        assetIdentifier: com.tangem.blockchain.nft.models.NFTAsset.Identifier,
    ): com.tangem.blockchain.nft.models.NFTAsset.SalePrice? = null

    suspend fun getYieldSupplyStatus(tokenContractAddress: String): YieldSupplyStatus? = null

    suspend fun getEffectiveProtocolBalance(
        token: Token,
        effectiveProtocolBalance: BigDecimal? = null,
    ): BigDecimal? = effectiveProtocolBalance

    suspend fun isAllowedToSpend(token: Token): Boolean = false

    fun getYieldSupplyContractAddresses(): YieldSupplyContractAddresses? = null

    fun calculateYieldModuleAddress(): String? = wallet.address.ifBlank { null }

    fun getAddresses(
        filterOptions: List<String>? = null,
    ): Result<List<com.tangem.blockchain.blockchains.bitcoin.walletconnect.models.BitcoinAddressInfo>> {
        return Result.Success(
            wallet.addresses.map { address ->
                com.tangem.blockchain.blockchains.bitcoin.walletconnect.models.BitcoinAddressInfo(
                    address = address.value,
                    publicKey = wallet.publicKey.blockchainKey.joinToString(separator = "") { "%02x".format(it) },
                    derivationPath = wallet.publicKey.derivationPath?.rawPath,
                    metadata = emptyMap(),
                )
            },
        )
    }

    suspend fun signMessage(
        message: String,
        address: String,
        protocol: String?,
        signer: TransactionSigner,
    ): Result<com.tangem.blockchain.common.messagesigning.MessageSignatureResult> {
        return Result.Failure(BlockchainSdkError.CustomError("Bitcoin message signing is not implemented"))
    }

    suspend fun signPsbt(
        psbtBase64: String,
        signInputs: List<com.tangem.blockchain.blockchains.bitcoin.walletconnect.models.SignInput>,
        signer: TransactionSigner,
    ): Result<String> {
        return Result.Failure(BlockchainSdkError.CustomError("PSBT signing is not implemented"))
    }

    suspend fun broadcastPsbt(psbtBase64: String): Result<String> {
        return Result.Failure(BlockchainSdkError.CustomError("PSBT broadcasting is not implemented"))
    }
}

data class YieldSupplyStatus(
    val isActive: Boolean = false,
    val isInitialized: Boolean = false,
)

data class YieldSupplyContractAddresses(
    val factoryContractAddress: String? = null,
)

open class WalletManagerFactory(
    val config: BlockchainSdkConfig = BlockchainSdkConfig(),
    val blockchainProviderTypes: Map<Blockchain, List<Any>> = emptyMap(),
    val accountCreator: AccountCreator? = null,
    val featureToggles: BlockchainFeatureToggles = BlockchainFeatureToggles(),
    val blockchainDataStorage: Any? = null,
    val loggers: List<Any> = emptyList(),
) {
    fun createLegacyWalletManager(
        blockchain: Blockchain,
        walletPublicKey: ByteArray,
        curve: com.tangem.common.card.EllipticCurve,
    ): WalletManager {
        val addresses = blockchain.makeAddresses(walletPublicKey, curve = curve)
        return DefaultWalletManager(
            Wallet(
                blockchain = blockchain,
                addresses = addresses,
                publicKey = Wallet.PublicKey(seedKey = walletPublicKey, derivationType = Wallet.PublicKey.DerivationType.None),
            ),
        )
    }

    fun createWalletManager(
        blockchain: Blockchain,
        publicKey: Wallet.PublicKey,
        curve: com.tangem.common.card.EllipticCurve,
    ): WalletManager {
        return DefaultWalletManager(
            Wallet(
                blockchain = blockchain,
                addresses = blockchain.makeAddresses(publicKey.blockchainKey, curve = curve),
                publicKey = publicKey,
            ),
        )
    }

    fun createTwinWalletManager(
        walletPublicKey: ByteArray,
        pairPublicKey: ByteArray,
        blockchain: Blockchain,
        curve: com.tangem.common.card.EllipticCurve,
    ): WalletManager {
        val addresses = blockchain.makeAddresses(
            walletPublicKey = walletPublicKey,
            pairPublicKey = pairPublicKey,
            curve = curve,
        )
        return DefaultWalletManager(
            Wallet(
                blockchain = blockchain,
                addresses = addresses,
                publicKey = Wallet.PublicKey(seedKey = walletPublicKey, derivationType = Wallet.PublicKey.DerivationType.None),
            ),
        )
    }
}

private class DefaultWalletManager(
    override val wallet: Wallet,
) : WalletManager

data class Wallet(
    val blockchain: Blockchain,
    val addresses: Set<Address> = emptySet(),
    val publicKey: PublicKey = PublicKey(seedKey = byteArrayOf(), derivationType = null),
    val tokens: Set<Token> = emptySet(),
    val amounts: MutableMap<AmountType, Amount> = mutableMapOf(),
    val recentTransactions: MutableSet<TransactionData.Uncompiled> = mutableSetOf(),
    val ens: String? = null,
) {
    val address: String
        get() = addresses.firstOrNull()?.value.orEmpty()

    data class HDKey(
        val extendedPublicKey: com.tangem.crypto.hdWallet.bip32.ExtendedPublicKey,
        val path: com.tangem.crypto.hdWallet.DerivationPath,
    )

    data class PublicKey(
        val seedKey: ByteArray,
        val derivationType: DerivationType?,
        val derivationPath: com.tangem.crypto.hdWallet.DerivationPath? = null,
    ) {
        val blockchainKey: ByteArray
            get() = derivationType?.hdKey?.extendedPublicKey?.publicKey ?: seedKey

        sealed class DerivationType {
            open val hdKey: HDKey? = null

            object None : DerivationType()
            data class Plain(override val hdKey: HDKey) : DerivationType()
            data class Extended(override val hdKey: HDKey) : DerivationType()
            data class Double(override val hdKey: HDKey, val secondHDKey: HDKey) : DerivationType()
        }
    }

    fun setAmount(amount: Amount) {
        amounts[amount.type] = amount
    }

    fun fundsAvailable(amount: Amount, fee: Amount?): Boolean {
        val balance = amounts[amount.type]?.value ?: BigDecimal.ZERO
        val required = amount.value ?: BigDecimal.ZERO
        val feeValue = fee?.value ?: BigDecimal.ZERO

        return balance >= required + feeValue
    }

    fun fundsAvailable(type: AmountType): BigDecimal = amounts[type]?.value ?: BigDecimal.ZERO

    @JvmName("getTokensCompat")
    fun getTokens(): Set<Token> = tokens

    fun getExploreUrl(address: String): String {
        return blockchain.getExploreUrl(address = address, contractAddress = null)
    }
}

enum class TransactionError

interface TransactionSigner {
    suspend fun sign(hash: ByteArray, publicKey: Wallet.PublicKey): com.tangem.common.CompletionResult<ByteArray>

    suspend fun sign(
        hashes: List<ByteArray>,
        publicKey: Wallet.PublicKey,
    ): com.tangem.common.CompletionResult<List<ByteArray>>

    suspend fun sign(
        hashes: Map<ByteArray, ByteArray>,
    ): com.tangem.common.CompletionResult<Map<ByteArray, ByteArray>> =
        com.tangem.common.CompletionResult.Success(emptyMap())

    suspend fun multiSign(
        dataToSign: List<com.tangem.operations.sign.SignData>,
        publicKey: Wallet.PublicKey,
    ): com.tangem.common.CompletionResult<Map<ByteArray, ByteArray>> =
        com.tangem.common.CompletionResult.Success(emptyMap())
}

interface TransactionSender {
    suspend fun getFee(transactionData: TransactionData): Result<TransactionFee> =
        Result.Failure(BlockchainSdkError.CustomError("Fee calculation is not implemented"))
    suspend fun getFee(amount: Amount, destination: String): Result<TransactionFee> = getFee(
        TransactionData.Uncompiled(
            amount = amount,
            fee = com.tangem.blockchain.common.transaction.Fee.Common(amount),
            destinationAddress = destination,
        ),
    )

    suspend fun getFee(
        amount: Amount,
        destination: String,
        callData: SmartContractCallData? = null,
    ): Result<TransactionFee> = getFee(amount, destination)

    suspend fun estimateFee(amount: Amount, destination: String): Result<TransactionFee> = getFee(amount, destination)
    suspend fun estimateFee(
        amount: Amount,
        destination: String,
        callData: SmartContractCallData? = null,
    ): Result<TransactionFee> = estimateFee(amount, destination)

    suspend fun send(transactionData: TransactionData, signer: TransactionSigner): Result<TransactionSendResult> =
        Result.Failure(BlockchainSdkError.CustomError("Sending is not implemented"))

    suspend fun sendMultiple(
        transactionDataList: List<TransactionData>,
        signer: TransactionSigner,
        sendMode: MultipleTransactionSendMode = MultipleTransactionSendMode.DEFAULT,
    ): Result<TransactionsSendResult> = Result.Failure(BlockchainSdkError.CustomError("Multiple send is not implemented"))

    enum class MultipleTransactionSendMode {
        DEFAULT,
        WAIT_AFTER_FIRST,
        All,
        FirstSuccessful,
    }
}

interface MessageSigner {
    suspend fun signMessage(message: String, signer: TransactionSigner): com.tangem.common.CompletionResult<String> =
        com.tangem.common.CompletionResult.Failure(
            com.tangem.common.core.TangemSdkError.UnknownError(),
        )
}
interface Approver {
    suspend fun getAllowance(spenderAddress: String, token: Token): Result<BigDecimal> {
        return Result.Success(BigDecimal.ZERO)
    }
}
interface NameResolver {
    suspend fun resolve(name: String): ResolveAddressResult = ResolveAddressResult.NotSupported
    suspend fun reverseResolve(address: String): ReverseResolveAddressResult = ReverseResolveAddressResult.NotSupported
}
interface DynamicAddressesManager {
    val isDynamicAddressesEnabled: Boolean
        get() = false

    val usedAddresses: List<UsedAddress>
        get() = emptyList()

    data class UsedAddress(
        val address: String,
        val derivationPath: String,
        val balance: BigDecimal = BigDecimal.ZERO,
    )

    fun enableDynamicAddresses(xpub: String) = Unit

    fun disableDynamicAddresses() = Unit

    fun findFirstUnusedReceiveAddress(): UsedAddress? = null

    suspend fun probeHasFundsOnNonBaseAddresses(xpub: String): Result<Boolean> = Result.Success(false)

    suspend fun createConsolidationTransaction(
        fee: com.tangem.blockchain.common.transaction.Fee,
    ): Result<TransactionData> = Result.Failure(
        BlockchainSdkError.CustomError("Consolidation transaction creation is not implemented"),
    )
}

interface TransactionValidator {
    fun validate(transactionData: TransactionData): kotlin.Result<Unit> = kotlin.Result.success(Unit)
}

interface TransactionPreparer {
    suspend fun prepareForSend(transactionData: TransactionData, signer: TransactionSigner): Result<ByteArray> {
        return Result.Failure(BlockchainSdkError.CustomError("Transaction preparation is not implemented"))
    }

    suspend fun prepareForSendMultiple(
        transactionData: List<TransactionData>,
        signer: TransactionSigner,
    ): Result<List<ByteArray>> {
        return Result.Failure(BlockchainSdkError.CustomError("Multiple transaction preparation is not implemented"))
    }

    suspend fun prepareAndSign(transactionData: TransactionData, signer: TransactionSigner): Result<ByteArray> {
        return Result.Failure(BlockchainSdkError.CustomError("Transaction signing is not implemented"))
    }

    suspend fun prepareAndSignMultiple(
        transactionData: List<TransactionData>,
        signer: TransactionSigner,
    ): Result<List<ByteArray>> {
        return Result.Failure(BlockchainSdkError.CustomError("Multiple transaction signing is not implemented"))
    }
}
interface ReserveAmountProvider {
    fun getReserveAmount(): BigDecimal
    fun isAccountFunded(address: String): Boolean = true
}
interface MinimumSendAmountProvider {
    fun getMinimumSendAmount(): BigDecimal? = null
}
interface FeeResourceAmountProvider {
    fun getFeeResource(): FeeResourceAmount = FeeResourceAmount(value = BigDecimal.ZERO, maxValue = BigDecimal.ZERO)
    fun isFeeEnough(amount: BigDecimal): Boolean = true
}
interface UtxoAmountLimitProvider {
    fun checkUtxoAmountLimit(amount: BigDecimal, fee: BigDecimal): UtxoAmountLimit? = null
}
interface InitializableAccount {
    val accountInitializationState: State

    enum class State {
        INITIALIZED,
        NOT_INITIALIZED,
    }
}

interface AssetRequirementsManager {
    suspend fun requirementsCondition(
        currencyType: CryptoCurrencyType,
    ): com.tangem.blockchain.common.trustlines.AssetRequirementsCondition? = null

    suspend fun fulfillRequirements(currencyType: CryptoCurrencyType, signer: TransactionSigner): SimpleResult =
        SimpleResult.Success

    suspend fun discardRequirements(currencyType: CryptoCurrencyType): SimpleResult = SimpleResult.Success
}

data class UtxoAmountLimit(
    val limit: BigDecimal,
    val availableToSpend: BigDecimal = limit,
    val availableToSend: BigDecimal = availableToSpend,
)

data class FeeResourceAmount(
    val value: BigDecimal,
    val maxValue: BigDecimal,
)

sealed class ResolveAddressResult {
    data class Resolved(val address: String) : ResolveAddressResult()
    data class Error(val error: Throwable) : ResolveAddressResult()
    object NotSupported : ResolveAddressResult()
}

sealed class ReverseResolveAddressResult {
    data class Resolved(val name: String) : ReverseResolveAddressResult()
    data class Error(val error: Exception) : ReverseResolveAddressResult()
    object NotSupported : ReverseResolveAddressResult()
}

sealed class TransactionData {
    fun requireUncompiled(): Uncompiled = this as? Uncompiled ?: error("TransactionData must be Uncompiled")

    data class Uncompiled(
        val amount: Amount,
        val fee: com.tangem.blockchain.common.transaction.Fee?,
        val sourceAddress: String = "",
        val destinationAddress: String,
        val changeAddress: String? = null,
        val memo: String? = null,
        val extras: TransactionExtras? = null,
        val status: TransactionStatus = TransactionStatus.Unconfirmed,
        val hash: String? = null,
        val date: java.util.Calendar? = null,
    ) : TransactionData() {
        val contractAddress: String?
            get() = when (val amountType = amount.type) {
                is AmountType.Token -> amountType.token.contractAddress
                is AmountType.TokenYieldSupply -> amountType.token.contractAddress
                else -> null
            }
    }

    data class Compiled(
        val value: Data = Data.Bytes(byteArrayOf()),
        val fee: com.tangem.blockchain.common.transaction.Fee? = null,
        val amount: Amount? = null,
        val status: TransactionStatus = TransactionStatus.Unconfirmed,
        val extras: TransactionExtras? = null,
    ) : TransactionData() {
        val hashToSign: ByteArray
            get() = when (value) {
                is Data.Bytes -> value.data
                is Data.RawString -> value.data.encodeToByteArray()
            }

        val hashes: List<ByteArray>
            get() = listOf(hashToSign)

        sealed class Data {
            data class Bytes(val data: ByteArray) : Data() {
                override fun equals(other: Any?): Boolean {
                    return other is Bytes && data.contentEquals(other.data)
                }

                override fun hashCode(): Int = data.contentHashCode()
            }

            data class RawString(val data: String) : Data()
        }
    }
}

interface TransactionExtras

interface SignatureCountValidator {
    suspend fun validateSignatureCount(signedHashes: Int): SimpleResult = SimpleResult.Success
}

interface YieldModuleAddressProvider {
    fun getYieldModuleAddress(): String
}

fun WalletManager.getYieldModuleAddress(): String {
    return (this as? YieldModuleAddressProvider)?.getYieldModuleAddress() ?: wallet.address
}

enum class TransactionStatus {
    Unconfirmed,
    Confirmed,
    Failed,
}

sealed class FeePaidCurrency {
    object Coin : FeePaidCurrency()
    object SameCurrency : FeePaidCurrency()
    data class Token(val token: com.tangem.blockchain.common.Token, val balance: BigDecimal? = null) : FeePaidCurrency()
    data class FeeResource(val currency: String) : FeePaidCurrency()
}

object IconsUtil {
    fun getTokenIconUri(blockchain: Blockchain, token: com.tangem.blockchain.common.Token): java.net.URI? {
        val tokenId = token.id ?: token.contractAddress.ifBlank { token.symbol }
        return runCatching {
            java.net.URI.create("https://s3.eu-central-1.amazonaws.com/tangem.api/coins/large/$tokenId.png")
        }.getOrNull()
    }
}
data class UnmarshalledSignature(private val signature: ByteArray) {
    val recId: Int
        get() = 0

    val r: BigInteger
        get() = BigInteger.ZERO

    val s: BigInteger
        get() = BigInteger.ZERO

    fun asRSVLegacyEVM(): ByteArray = signature
}

object UnmarshalHelper {
    fun unmarshalSignatureExtended(signature: ByteArray, hash: ByteArray, publicKey: ByteArray): UnmarshalledSignature {
        return UnmarshalledSignature(signature)
    }
}

interface ExceptionHandlerOutput {

    fun handleApiSwitch(currentHost: String, nextHost: String, message: String, blockchain: Blockchain)
}

object ExceptionHandler {

    private val outputs = java.util.concurrent.CopyOnWriteArrayList<ExceptionHandlerOutput>()

    fun append(output: ExceptionHandlerOutput) {
        outputs += output
    }

    fun handleApiSwitch(currentHost: String, nextHost: String, message: String, blockchain: Blockchain) {
        outputs.forEach { output ->
            output.handleApiSwitch(
                currentHost = currentHost,
                nextHost = nextHost,
                message = message,
                blockchain = blockchain,
            )
        }
    }
}
