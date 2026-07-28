package com.tangem.common.core

import com.tangem.common.CardFilter
import com.tangem.common.CompletionResult
import com.tangem.common.card.Card
import com.tangem.common.services.secure.InMemorySecureStorage
import com.tangem.common.services.secure.SecureStorage

open class TangemError(
    val code: Int,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    open var customMessage: String = message ?: code.toString()
    open val messageResId: Int? = null
    open val silent: Boolean = false
}

open class TangemSdkError(
    code: Int,
    message: String? = null,
    cause: Throwable? = null,
) : TangemError(code = code, message = message, cause = cause) {
    class TagLost : TangemSdkError(1)
    class UserCancelled : TangemSdkError(2)
    class ExceptionError(val throwable: Throwable?) : TangemSdkError(3, throwable?.message, throwable)
    class CardVerificationFailed : TangemSdkError(4)
    class WrongAccessCode : TangemSdkError(5)
    class WrongCardNumber(val cardId: String = "") : TangemSdkError(6)
    class WrongPasscode : TangemSdkError(7)
    class MnemonicException(
        message: String? = null,
        val mnemonicResult: com.tangem.crypto.bip39.MnemonicErrorResult? = null,
    ) : TangemSdkError(8, message)
    class WalletNotFound : TangemSdkError(9)
    class CardError(message: String? = null) : TangemSdkError(10, message)
    class UnknownError : TangemSdkError(11)
    class WalletAlreadyCreated : TangemSdkError(12)
    class WalletIsNotCreated : TangemSdkError(13)
    class FileNotFound : TangemSdkError(14)
    class InsNotSupported : TangemSdkError(15)
    class MissingPreflightRead : TangemSdkError(16)
    class NotSupportedFirmwareVersion : TangemSdkError(17)
    class AuthenticationAlreadyInProgress : TangemSdkError(18)
    class AuthenticationCanceled : TangemSdkError(19)
    class AuthenticationLockout : TangemSdkError(20)
    class AuthenticationPermanentLockout : TangemSdkError(21)
    class AuthenticationUnavailable : TangemSdkError(22)
    class KeystoreInvalidated(cause: Throwable? = null) : TangemSdkError(23, cause?.message, cause)
    class AuthenticationFailed : TangemSdkError(24)
    class NonHardenedDerivationNotSupported : TangemSdkError(25)
    class NfcFeatureIsUnavailable : TangemSdkError(26)
    class BackupFailedNotEmptyWallets(val cardId: String = "") : TangemSdkError(27)
    class IssuerSignatureLoadingFailed : TangemSdkError(28)
}

typealias CompletionCallback<T> = com.tangem.common.CompletionCallback<T>

interface CardSessionRunnable<T> {
    val allowsRequestAccessCodeFromRepository: Boolean
        get() = true

    fun preflightReadMode(): com.tangem.operations.PreflightReadMode =
        com.tangem.operations.PreflightReadMode.FullCardRead

    fun run(session: CardSession, callback: CompletionCallback<T>)
}

open class CardSession(
    val environment: SessionEnvironment = SessionEnvironment(),
) {

    fun setMessage(message: com.tangem.Message) = Unit
}

open class SessionEnvironment(
    var card: Card? = null,
) {
    val secureStorage: SecureStorage = InMemorySecureStorage()
    val config: Config = Config()
    var walletData: com.tangem.common.card.WalletData? = null
}

class Config(
    var userCodeRequestPolicy: UserCodeRequestPolicy = UserCodeRequestPolicy.Default,
    var cardIdDisplayFormat: CardIdDisplayFormat = CardIdDisplayFormat.Full,
    var linkedTerminal: Boolean? = null,
    var filter: CardFilter = CardFilter(),
) {
    var tangemApiBaseUrl: String? = null
    var attestationMode: com.tangem.operations.attestation.AttestationMode =
        com.tangem.operations.attestation.AttestationMode.Offline

    var productType: ProductType = ProductType.ANY
        private set

    fun setupForProduct(type: ProductType) {
        productType = type
    }
}

enum class ProductType {
    ANY,
    CARD,
    RING,
}

sealed class UserCodeRequestPolicy {
    data class Always(val codeType: com.tangem.common.UserCodeType? = null) : UserCodeRequestPolicy()
    object Never : UserCodeRequestPolicy()
    object Default : UserCodeRequestPolicy()
    data class AlwaysWithBiometrics(val codeType: com.tangem.common.UserCodeType) : UserCodeRequestPolicy()
}

sealed class CardIdDisplayFormat {
    object Full : CardIdDisplayFormat()
    data class LastDigits(val numbers: Int = 4) : CardIdDisplayFormat()
    data class LastLuhn(val numbers: Int = 4) : CardIdDisplayFormat()
}
