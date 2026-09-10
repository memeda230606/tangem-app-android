package com.tangem.tap.features.intentHandler.handlers

import android.nfc.Tag
import com.tangem.tap.features.intentHandler.handlers.ExternalNdefTagReader.ReadResult
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
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
        assertThrows(IllegalStateException::class.java) { controller.requireAcceptedIdentity() }

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
        assertFalse(controller.onNfcIntentResult(ExternalNdefScanController.Result.Accepted, identity.copy(cardInstanceId = "other")))
        assertFalse(controller.onNfcIntentResult(ExternalNdefScanController.Result.Rejected))
        assertEquals(ExternalNdefScanController.Result.Accepted, result.await())
        assertEquals(identity, controller.requireAcceptedIdentity())
    }

    @Test
    fun `quick tap keeps the dialog open and the next verified tap proceeds`() = runTest {
        val controller = ExternalNdefScanController(true)
        val result = async { controller.awaitScan() }
        runCurrent()
        val firstRead = requireNotNull(controller.beginRead())
        assertNull(controller.beginRead()) // do not overlap RF operations
        assertTrue(controller.finishRead(firstRead, ReadResult.RetryTap, mockk()))
        assertTrue(controller.isWaitingForTag.value)
        assertFalse(result.isCompleted)
        assertThrows(IllegalStateException::class.java) { controller.requireAcceptedIdentity() }
        val tag = mockk<Tag>()
        val secondRead = requireNotNull(controller.beginRead())
        assertTrue(controller.finishRead(secondRead, ReadResult.Accepted(identity), tag))
        assertEquals(ExternalNdefScanController.Result.Accepted, result.await())
        assertEquals(tag, controller.requireAcceptedTag())
        assertEquals(identity, controller.requireAcceptedIdentity())
    }

    @Test
    fun `retries do not eventually label an unreadable card unsupported`() = runTest {
        val controller = ExternalNdefScanController(true)
        val result = async { controller.awaitScan() }
        runCurrent()
        repeat(4) {
            assertTrue(controller.finishRead(requireNotNull(controller.beginRead()), ReadResult.RetryTap, mockk()))
        }
        assertFalse(result.isCompleted)
        assertTrue(controller.isWaitingForTag.value)
        controller.finishRead(requireNotNull(controller.beginRead()), ReadResult.Unsupported, mockk())
        assertEquals(ExternalNdefScanController.Result.Rejected, result.await())
    }

    @Test
    fun `cancelled read cannot complete a later request or change its identity`() = runTest {
        val controller = ExternalNdefScanController(true)
        val first = async { controller.awaitScan() }
        runCurrent()
        val oldAttempt = requireNotNull(controller.beginRead())
        first.cancel()
        runCurrent()
        val second = async { controller.awaitScan() }
        runCurrent()
        assertFalse(controller.finishRead(oldAttempt, ReadResult.Accepted(identity), mockk()))
        assertFalse(second.isCompleted)
        val newAttempt = requireNotNull(controller.beginRead())
        controller.abandonRead(oldAttempt) // old coroutine cleanup cannot release the newer read
        assertNull(controller.beginRead())
        val newIdentity = identity.copy(cardInstanceId = "new-card")
        controller.finishRead(newAttempt, ReadResult.Accepted(newIdentity), mockk())
        assertEquals(ExternalNdefScanController.Result.Accepted, second.await())
        assertEquals(newIdentity, controller.requireAcceptedIdentity())
    }

    @Test
    fun `network retry remains pending and server policy rejection blocks advancement`() = runTest {
        val controller = ExternalNdefScanController(true)
        val result = async { controller.awaitScan() }
        runCurrent()
        controller.finishRead(requireNotNull(controller.beginRead()), ReadResult.VerificationUnavailable, mockk())
        assertFalse(result.isCompleted)
        controller.finishRead(requireNotNull(controller.beginRead()), ReadResult.VerificationRejected, mockk())
        assertEquals(ExternalNdefScanController.Result.Rejected, result.await())
        assertThrows(IllegalStateException::class.java) { controller.requireAcceptedIdentity() }
    }

    @Test
    fun `accepted without authenticated identity cannot complete the scan`() = runTest {
        val controller = ExternalNdefScanController(true)
        val result = async { controller.awaitScan() }
        runCurrent()
        assertFalse(controller.onNfcIntentResult(ExternalNdefScanController.Result.Accepted))
        assertFalse(result.isCompleted)
        result.cancel()
    }
}
