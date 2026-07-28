package com.tangem.tap.domain.sdk.mocks

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Makes visual-only NFC demo operations wait for a new physical NDEF discovery.
 *
 * The tag remains a trigger only. It never provides a signature, key, PIN, or authentication material.
 */
internal object NfcDemoTapGate {

    sealed interface UiState {
        data object Idle : UiState
        data class Waiting(val operation: String) : UiState
        data object Reading : UiState
        data object Success : UiState
        data object Failure : UiState
    }

    enum class Result {
        Success,
        RealCard,
        TagLost,
        Cancelled,
        Timeout,
    }

    private data class Request(
        val operation: String,
        val result: CompletableDeferred<Result>,
        var readingStarted: Boolean = false,
    )

    private val lock = Any()
    private val mutableState = MutableStateFlow<UiState>(UiState.Idle)
    private var currentRequest: Request? = null

    val state: StateFlow<UiState> = mutableState.asStateFlow()

    suspend fun awaitTap(
        operation: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        feedbackMillis: Long = DEFAULT_FEEDBACK_MILLIS,
    ): Result {
        val request = Request(operation = operation, result = CompletableDeferred())

        synchronized(lock) {
            currentRequest?.result?.complete(Result.Cancelled)
            currentRequest = request
            mutableState.value = UiState.Waiting(operation)
        }

        NfcDemoLogger.event("physical_gate_waiting", "operation=$operation")

        val result = withTimeoutOrNull(timeoutMillis) { request.result.await() } ?: Result.Timeout
        mutableState.value = if (result == Result.Success) UiState.Success else UiState.Failure
        NfcDemoLogger.event(
            name = "physical_gate_finished",
            details = "operation=$operation result=$result",
        )

        if (feedbackMillis > 0) delay(feedbackMillis)

        synchronized(lock) {
            if (currentRequest === request) {
                currentRequest = null
                mutableState.value = UiState.Idle
            }
        }

        return result
    }

    /**
     * Returns true when this discovery belongs to a currently waiting demo operation and must not change routes.
     */
    fun consumeTag(scenarioId: String?, isDemoTag: Boolean = true): Boolean {
        val request = synchronized(lock) {
            currentRequest?.takeUnless { it.result.isCompleted }
        } ?: return synchronized(lock) { currentRequest?.result?.isCompleted == true }

        mutableState.value = UiState.Reading
        val result = when {
            scenarioId == TAG_LOST_SCENARIO -> Result.TagLost
            isDemoTag -> Result.Success
            else -> Result.RealCard
        }
        request.result.complete(result)
        NfcDemoLogger.event(
            name = "physical_gate_tag_received",
            details = "operation=${request.operation} scenario=$scenarioId result=$result",
        )
        return true
    }

    /**
     * Moves the active request to the reading state without completing it.
     *
     * Reader Mode uses this while it verifies that the physical tag stays in range long enough to finish the
     * visual operation. Returning false prevents duplicate reader callbacks from starting concurrent checks.
     */
    fun beginPhysicalTagRead(): Boolean {
        val request = synchronized(lock) {
            currentRequest
                ?.takeUnless { it.result.isCompleted || it.readingStarted }
                ?.also { it.readingStarted = true }
        } ?: return false

        mutableState.value = UiState.Reading
        NfcDemoLogger.event(
            name = "physical_gate_reading",
            details = "operation=${request.operation}",
        )
        return true
    }

    fun completePhysicalTagRead(result: Result): Boolean {
        val request = synchronized(lock) {
            currentRequest?.takeUnless { it.result.isCompleted }
        } ?: return false

        val completed = request.result.complete(result)
        if (completed) {
            NfcDemoLogger.event(
                name = "physical_gate_tag_received",
                details = "operation=${request.operation} scenario=physical result=$result",
            )
        }
        return completed
    }

    fun cancel() {
        val request = synchronized(lock) { currentRequest } ?: return
        request.result.complete(Result.Cancelled)
    }

    internal fun resetForTest() {
        synchronized(lock) {
            currentRequest?.result?.complete(Result.Cancelled)
            currentRequest = null
            mutableState.value = UiState.Idle
        }
    }

    private const val TAG_LOST_SCENARIO = "tag-lost"
    private const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    private const val DEFAULT_FEEDBACK_MILLIS = 750L
}
