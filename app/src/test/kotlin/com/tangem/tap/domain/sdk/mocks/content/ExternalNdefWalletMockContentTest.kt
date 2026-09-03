package com.tangem.tap.domain.sdk.mocks.content

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tangem.common.card.EllipticCurve
import com.tangem.common.extensions.ByteArrayKey
import com.tangem.crypto.hdWallet.DerivationPath
import com.tangem.common.json.TangemSdkAdapter
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.domain.models.scan.serialization.ByteArrayKeyAdapter
import com.tangem.domain.models.scan.serialization.CardBackupStatusAdapter
import com.tangem.domain.models.scan.serialization.DerivationPathAdapterWithMigration
import com.tangem.domain.models.scan.serialization.ExtendedPublicKeysMapAdapter
import com.tangem.domain.models.scan.serialization.ScanResponseDerivedKeysMapAdapter
import com.tangem.domain.models.scan.serialization.WalletDerivedKeysMapAdapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExternalNdefWalletMockContentTest {

    @Test
    fun `external NDEF demo changes from empty card to initialized wallet without advertising backup`() {
        ExternalNdefWalletMockContent.reset()

        try {
            assertTrue(ExternalNdefWalletMockContent.scanResponse.card.wallets.isEmpty())
            assertFalse(ExternalNdefWalletMockContent.cardDto.settings.isBackupAllowed)
            assertFalse(ExternalNdefWalletMockContent.scanResponse.card.settings.isBackupAllowed)

            val createdResponse = ExternalNdefWalletMockContent.createProductWalletTaskResponse

            assertTrue(createdResponse.card.wallets.isNotEmpty())
            assertFalse(createdResponse.card.settings.isBackupAllowed)
            assertNull(createdResponse.card.backupStatus)
            assertNull(createdResponse.primaryCard)
            assertTrue(ExternalNdefWalletMockContent.scanResponse.card.wallets.isNotEmpty())
            assertNull(ExternalNdefWalletMockContent.scanResponse.card.backupStatus)

            val createdWalletKeys = createdResponse.card.wallets.mapTo(mutableSetOf()) {
                ByteArrayKey(it.publicKey)
            }

            assertTrue(createdResponse.derivedKeys.isNotEmpty())
            assertTrue(createdResponse.derivedKeys.keys.all(createdWalletKeys::contains))
            assertTrue(ExternalNdefWalletMockContent.derivationTaskResponse.entries.isNotEmpty())
            assertTrue(
                ExternalNdefWalletMockContent.derivationTaskResponse.entries.keys.all(createdWalletKeys::contains),
            )
            assertTrue(ExternalNdefWalletMockContent.scanResponse.derivedKeys.isNotEmpty())
            assertTrue(ExternalNdefWalletMockContent.scanResponse.derivedKeys.keys.all(createdWalletKeys::contains))

            val slip0010WalletKey = createdResponse.card.wallets
                .first { it.curve == EllipticCurve.Ed25519Slip0010 }
                .publicKey
                .let(::ByteArrayKey)
            assertTrue(ExternalNdefWalletMockContent.derivationTaskResponse.entries.containsKey(slip0010WalletKey))
        } finally {
            ExternalNdefWalletMockContent.reset()
        }
    }

    @Test
    fun `external NDEF derived keys survive wallet storage serialization`() {
        ExternalNdefWalletMockContent.reset()

        try {
            ExternalNdefWalletMockContent.createProductWalletTaskResponse
            val original = ExternalNdefWalletMockContent.scanResponse
            val adapter = Moshi.Builder()
                .add(WalletDerivedKeysMapAdapter())
                .add(ScanResponseDerivedKeysMapAdapter())
                .add(ByteArrayKeyAdapter())
                .add(ExtendedPublicKeysMapAdapter())
                .add(CardBackupStatusAdapter())
                .add(DerivationPathAdapterWithMigration())
                .add(TangemSdkAdapter.DateAdapter())
                .add(TangemSdkAdapter.DerivationNodeAdapter())
                .add(TangemSdkAdapter.FirmwareVersionAdapter())
                .addLast(KotlinJsonAdapterFactory())
                .build()
                .adapter(ScanResponse::class.java)

            val restored = requireNotNull(adapter.fromJson(adapter.toJson(original)))

            assertEquals(original.derivedKeys, restored.derivedKeys)
        } finally {
            ExternalNdefWalletMockContent.reset()
        }
    }

    @Test
    fun `external NDEF card remembers derivation paths requested after wallet creation`() {
        ExternalNdefWalletMockContent.reset()

        try {
            ExternalNdefWalletMockContent.createProductWalletTaskResponse
            val walletKey = ExternalNdefWalletMockContent.scanResponse.card.wallets
                .first { it.curve == EllipticCurve.Secp256k1 }
                .publicKey
                .let(::ByteArrayKey)
            val requestedPath = DerivationPath("m/44'/2'/0'/0/0")

            ExternalNdefWalletMockContent.rememberDerivations(
                derivations = mapOf(walletKey to listOf(requestedPath)),
            )

            assertTrue(ExternalNdefWalletMockContent.scanResponse.derivedKeys.getValue(walletKey)[requestedPath] != null)
            assertTrue(ExternalNdefWalletMockContent.derivationTaskResponse.entries.getValue(walletKey)[requestedPath] != null)
        } finally {
            ExternalNdefWalletMockContent.reset()
        }
    }
}
