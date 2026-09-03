package com.tangem.tap.features.intentHandler.handlers

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExternalNdefScanControllerTest {

    private val identity = ExternalNdefScanController.CardIdentity(
        cardInstanceId = "8debe2c2-3ea9-4ea9-a870-994f553221c5",
        keyVersion = 1,
        targetUrl = "https://hm.niubtmd.com/tangem/testnet",
    )

    @Test
    fun `button scan waits until a supported NDEF intent arrives`() = runTest {
        val controller = ExternalNdefScanController(isEnabled = true)

        val result = async { controller.awaitScan() }
        runCurrent()

        assertTrue(controller.isWaitingForTag.value)
        assertFalse(result.isCompleted)

        assertTrue(controller.onNfcIntentResult(ExternalNdefScanController.Result.Accepted, identity))
        runCurrent()

        assertEquals(ExternalNdefScanController.Result.Accepted, result.await())
        assertEquals(identity, controller.requireAcceptedIdentity())
        assertFalse(controller.isWaitingForTag.value)
    }

    @Test
    fun `NDEF intent scanned before button is not reused by a later scan`() = runTest {
        val controller = ExternalNdefScanController(isEnabled = true)
        assertFalse(controller.onNfcIntentResult(ExternalNdefScanController.Result.Accepted, identity))

        val result = async { controller.awaitScan() }
        runCurrent()

        assertFalse(result.isCompleted)
        result.cancel()
    }

    @Test
    fun `only the first tag result completes a pending scan`() = runTest {
        val controller = ExternalNdefScanController(isEnabled = true)
        val result = async { controller.awaitScan() }
        runCurrent()

        assertTrue(controller.onNfcIntentResult(ExternalNdefScanController.Result.Accepted, identity))
        assertFalse(controller.onNfcIntentResult(ExternalNdefScanController.Result.Rejected))
        assertEquals(ExternalNdefScanController.Result.Accepted, result.await())
    }
}
