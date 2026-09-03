package com.tangem.tap.features.intentHandler.handlers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coordinates the button-driven NDEF scan used by the External build. */
class ExternalNdefScanController(
    val isEnabled: Boolean,
) {

    private val lock = Any()
    private var pendingScan: CompletableDeferred<Result>? = null
    @Volatile
    private var acceptedIdentity: CardIdentity? = null

    private val _isWaitingForTag = MutableStateFlow(false)
    val isWaitingForTag = _isWaitingForTag.asStateFlow()

    suspend fun awaitScan(): Result {
        check(isEnabled) { "External NDEF scanning is disabled for this build" }

        val request = synchronized(lock) {
            pendingScan ?: CompletableDeferred<Result>().also {
                pendingScan = it
                _isWaitingForTag.value = true
            }
        }

        return try {
            request.await()
        } finally {
            synchronized(lock) {
                if (pendingScan === request) {
                    pendingScan = null
                    _isWaitingForTag.value = false
                }
            }
        }
    }

    fun onNfcIntentResult(result: Result, identity: CardIdentity? = null): Boolean {
        if (result == Result.Accepted) {
            acceptedIdentity = identity
        }
        return synchronized(lock) {
            pendingScan?.complete(result) == true
        }
    }

    fun requireAcceptedIdentity(): CardIdentity {
        return checkNotNull(acceptedIdentity) { "Accepted scan is missing card identity" }
    }

    enum class Result {
        Accepted,
        Rejected,
    }

    data class CardIdentity(
        val cardInstanceId: String,
        val keyVersion: Int,
        val targetUrl: String,
        val verificationToken: String? = null,
        val cardRef: String? = null,
        val boundWalletId: String? = null,
        val bindingRole: String? = null,
    )
}
