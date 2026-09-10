package com.tangem.tap.features.intentHandler.handlers

import android.nfc.Tag
import com.niubtmd.securenfc.Ntag424Dna
import com.tangem.tap.features.intentHandler.handlers.ExternalNdefTagReader.ReadResult
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.CancellationException

internal class ExternalNdefTagReaderTest {

    private val identity = ExternalNdefScanController.CardIdentity(
        cardInstanceId = "8debe2c2-3ea9-4ea9-a870-994f553221c5",
        keyVersion = 1,
        targetUrl = "https://hm.niubtmd.com/tangem/testnet",
    )

    @Test
    fun `reader accepts a card only after secure authentication and payload validation`() {
        val tag = mockk<Tag>()
        val reader = ExternalNdefTagReader(secureReader = { identity }, serverVerifier = { it })

        assertEquals(ReadResult.Accepted(identity), reader.read(tag))
    }

    @Test
    fun `reader rejects a card when secure authentication fails`() {
        val tag = mockk<Tag>()
        val reader = ExternalNdefTagReader(secureReader = { null }, serverVerifier = { it })

        assertEquals(ReadResult.Unsupported, reader.read(tag))
    }

    @Test
    fun `quick tap transport failure asks for another tap without server verification`() {
        val tag = mockk<Tag>()
        val reader = ExternalNdefTagReader(
            secureReader = { throw IOException("Tag was lost") },
            serverVerifier = { throw AssertionError("Incomplete physical reads must not reach verification") },
        )

        assertEquals(ReadResult.RetryTap, reader.read(tag))
    }

    @Test
    fun `complete wrong key authentication response is unsupported`() {
        val reader = ExternalNdefTagReader(secureReader = {
            ExternalNdefTagReader.authenticateReadKey(
                Ntag424Dna { byteArrayOf(0x91.toByte(), 0xAE.toByte()) }, ByteArray(16),
            )?.let { identity }
        }, serverVerifier = { it })
        assertEquals(ReadResult.Unsupported, reader.read(mockk()))
    }

    @Test
    fun `an invalidated session outside authentication asks to retap instead of declaring a wrong card`() {
        val reader = ExternalNdefTagReader(secureReader = {
            // Produces a real CardException with status AE, thrown outside the authentication classifier.
            Ntag424Dna { byteArrayOf(0x91.toByte(), 0xAE.toByte()) }.authenticate(1, ByteArray(16))
            identity
        }, serverVerifier = { it })
        assertEquals(ReadResult.RetryTap, reader.read(mockk()))
    }

    @Test
    fun `incomplete proof or stale tag is retryable but never accepted`() {
        listOf(GeneralSecurityException("MAC mismatch"), SecurityException("Stale tag"),
            IllegalArgumentException("Incomplete payload")).forEach { failure ->
            val reader = ExternalNdefTagReader(secureReader = { throw failure }, serverVerifier = { it })
            assertEquals(ReadResult.RetryTap, reader.read(mockk()))
        }
    }

    @Test
    fun `server timeout is not a wrong card result`() {
        val reader = ExternalNdefTagReader(secureReader = { identity }, serverVerifier = { throw IOException("Timeout") })
        assertEquals(ReadResult.VerificationUnavailable, reader.read(mockk()))
    }

    @Test
    fun `server policy rejection is distinct from wrong hardware and never accepts`() {
        val reader = ExternalNdefTagReader(secureReader = { identity }, serverVerifier = { null })
        assertEquals(ReadResult.VerificationRejected, reader.read(mockk()))
    }

    @Test
    fun `a retry must perform a new physical read before proceeding`() {
        var reads = 0
        var verifications = 0
        val reader = ExternalNdefTagReader(secureReader = {
            if (++reads == 1) throw IOException("Removed too quickly") else identity
        }, serverVerifier = { verifications++; it })
        assertEquals(ReadResult.RetryTap, reader.read(mockk()))
        assertEquals(0, verifications)
        assertEquals(ReadResult.Accepted(identity), reader.read(mockk()))
        assertEquals(2, reads)
        assertEquals(1, verifications)
    }

    @Test
    fun `cancellation is propagated instead of displaying a card error`() {
        val reader = ExternalNdefTagReader(secureReader = { throw CancellationException() }, serverVerifier = { it })
        assertThrows(CancellationException::class.java) { reader.read(mockk()) }
    }

    @Test
    fun `transient HTTP statuses remain retryable verification errors`() {
        listOf(408, 425, 429, 500, 502, 503, 504).forEach {
            assertEquals(true, TangemTestnetClient.isRetryableVerificationStatus(it))
        }
        listOf(200, 400, 401, 403, 404, 409).forEach {
            assertEquals(false, TangemTestnetClient.isRetryableVerificationStatus(it))
        }
    }
}
