package com.tangem.tap.features.intentHandler.handlers

import android.nfc.Tag
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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

        assertEquals(ExternalNdefScanController.Result.Accepted, reader.read(tag))
        assertEquals(identity, reader.lastIdentity)
    }

    @Test
    fun `reader rejects a card when secure authentication fails`() {
        val tag = mockk<Tag>()
        val reader = ExternalNdefTagReader(secureReader = { null }, serverVerifier = { it })

        assertEquals(ExternalNdefScanController.Result.Rejected, reader.read(tag))
    }

    @Test
    fun `reader rejects a card when transport throws`() {
        val tag = mockk<Tag>()
        val reader = ExternalNdefTagReader(secureReader = { error("lost") }, serverVerifier = { it })

        assertEquals(ExternalNdefScanController.Result.Rejected, reader.read(tag))
    }
}
