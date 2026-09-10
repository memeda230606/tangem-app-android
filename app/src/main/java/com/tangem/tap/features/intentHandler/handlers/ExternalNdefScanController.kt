package com.tangem.tap.features.intentHandler.handlers

import android.nfc.Tag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coordinates the button-driven NDEF scan used by the External build. */
class ExternalNdefScanController(
    val isEnabled: Boolean,
) {

    private val lock = Any()
    private var pendingScan: CompletableDeferred<Result>? = null
    private var activeRead: ReadAttempt? = null
    @Volatile
    private var acceptedIdentity: CardIdentity? = null
    @Volatile
    private var acceptedTag: Tag? = null

    private val _isWaitingForTag = MutableStateFlow(false)
    val isWaitingForTag = _isWaitingForTag.asStateFlow()

    suspend fun awaitScan(): Result {
        check(isEnabled) { "External NDEF scanning is disabled for this build" }

        val request = synchronized(lock) {
            pendingScan ?: CompletableDeferred<Result>().also {
                pendingScan = it
                acceptedIdentity = null
                acceptedTag = null
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
        return onNfcIntentResult(result, identity, tag = null)
    }

    fun onNfcIntentResult(result: Result, identity: CardIdentity?, tag: Tag?): Boolean {
        return synchronized(lock) {
            completePending(result, identity, tag)
        }
    }

    /** One physical read at a time, tied to the exact button request that started it. */
    fun beginRead(): ReadAttempt? = synchronized(lock) {
        val request = pendingScan?.takeIf { it.isActive } ?: return null
        if (activeRead != null) return null
        ReadAttempt(request).also { activeRead = it }
    }

    /** Retry outcomes leave the same scan dialog/request pending. Late results never reach a newer scan. */
    fun finishRead(attempt: ReadAttempt, outcome: ExternalNdefTagReader.ReadResult, tag: Tag): Boolean = synchronized(lock) {
        if (activeRead !== attempt) return false
        activeRead = null
        if (pendingScan !== attempt.request || !attempt.request.isActive) return false
        when (outcome) {
            is ExternalNdefTagReader.ReadResult.Accepted -> completePending(Result.Accepted, outcome.identity, tag)
            ExternalNdefTagReader.ReadResult.Unsupported,
            ExternalNdefTagReader.ReadResult.VerificationRejected,
            -> completePending(Result.Rejected, null, null)
            ExternalNdefTagReader.ReadResult.RetryTap,
            ExternalNdefTagReader.ReadResult.VerificationUnavailable,
            -> true
        }
    }

    fun abandonRead(attempt: ReadAttempt) = synchronized(lock) {
        if (activeRead === attempt) activeRead = null
    }

    private fun completePending(result: Result, identity: CardIdentity?, tag: Tag?): Boolean {
        val request = pendingScan?.takeIf { it.isActive } ?: return false
        if (result == Result.Accepted && identity == null) return false
        // Store the identity before resuming awaiters, but only for the first result of an active request.
        acceptedIdentity = if (result == Result.Accepted) identity else null
        acceptedTag = if (result == Result.Accepted) tag else null
        return request.complete(result)
    }

    class ReadAttempt internal constructor(internal val request: CompletableDeferred<Result>)

    fun requireAcceptedIdentity(): CardIdentity {
        return checkNotNull(acceptedIdentity) { "Accepted scan is missing card identity" }
    }

    fun requireAcceptedTag(): Tag {
        return checkNotNull(acceptedTag) { "Accepted scan is missing the physical NFC tag" }
    }

    fun updateAcceptedIdentity(transform: (CardIdentity) -> CardIdentity) {
        acceptedIdentity = transform(requireAcceptedIdentity())
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
