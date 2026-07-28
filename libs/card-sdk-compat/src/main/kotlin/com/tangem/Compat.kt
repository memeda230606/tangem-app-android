package com.tangem

import com.tangem.common.CompletionResult
import com.tangem.common.authentication.AuthenticationManager
import com.tangem.common.core.Config
import com.tangem.common.core.CardSession
import com.tangem.common.core.CardSessionRunnable
import com.tangem.common.core.SessionEnvironment
import com.tangem.common.authentication.keystore.DummyKeystoreManager
import com.tangem.common.authentication.keystore.KeystoreManager
import com.tangem.common.services.secure.InMemorySecureStorage
import com.tangem.common.services.secure.SecureStorage
import com.tangem.crypto.bip39.Wordlist
import com.tangem.operations.preflightread.PreflightReadFilter

interface TangemSdkLogger {
    fun log(message: () -> String, level: Log.Level)
}

interface LogFormat {
    fun format(message: () -> String, level: Log.Level): String

    class StairsFormatter : LogFormat {
        override fun format(message: () -> String, level: Log.Level): String = "[${level.name}] ${message()}"
    }
}

object Log {
    enum class Level {
        ApduCommand,
        Apdu,
        Tlv,
        Nfc,
        Command,
        Session,
        View,
        Network,
        Error,
        Biometric,
        Info,
        Warning,
    }

    private val loggers = mutableListOf<TangemSdkLogger>()

    fun addLogger(logger: TangemSdkLogger) {
        loggers += logger
    }

    fun info(message: () -> String) = log(message, Level.Info)

    fun warning(message: () -> String) = log(message, Level.Warning)

    fun error(message: () -> String) = log(message, Level.Error)

    private fun log(message: () -> String, level: Level) {
        loggers.forEach { it.log(message, level) }
    }
}

data class Message(
    val body: String? = null,
    val header: String? = null,
)

class TangemSdk(
    val reader: Any? = null,
    val viewDelegate: Any? = null,
    val nfcAvailabilityProvider: Any? = null,
    val secureStorage: SecureStorage = InMemorySecureStorage(),
    val authenticationManager: AuthenticationManager = object : AuthenticationManager {},
    val keystoreManager: KeystoreManager = DummyKeystoreManager(),
    val wordlist: Wordlist = Wordlist.getWordlist(),
    val config: Config = Config(),
) {
    fun forceDisableReaderMode() = Unit

    fun forceEnableReaderMode() = Unit

    fun uiVisibility(): kotlinx.coroutines.flow.Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false)

    fun <T> startSessionWithRunnable(
        runnable: CardSessionRunnable<T>,
        cardId: String? = null,
        initialMessage: Message? = null,
        accessCode: String? = null,
        preflightReadFilter: PreflightReadFilter? = null,
        iconScanRes: Int? = null,
        callback: (CompletionResult<T>) -> Unit,
    ) {
        runnable.run(CardSession(environment = SessionEnvironment()), callback)
    }

    companion object {
        fun initNfcManager(activity: Any?): com.tangem.sdk.nfc.NfcManager = com.tangem.sdk.nfc.NfcManager()

        fun initAuthenticationManager(activity: Any?): com.tangem.common.authentication.AuthenticationManager {
            return object : com.tangem.common.authentication.AuthenticationManager {}
        }

        fun initKeystoreManager(
            authenticationManager: AuthenticationManager,
            secureStorage: SecureStorage,
        ): com.tangem.common.authentication.keystore.KeystoreManager {
            return DummyKeystoreManager()
        }
    }
}
