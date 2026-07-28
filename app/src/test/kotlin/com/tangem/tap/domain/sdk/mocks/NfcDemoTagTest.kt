package com.tangem.tap.domain.sdk.mocks

import com.google.common.truth.Truth.assertThat
import com.tangem.common.CompletionResult
import com.tangem.domain.demo.models.DemoConfig
import com.tangem.domain.models.scan.ProductType
import org.junit.jupiter.api.Test

class NfcDemoTagTest {

    private val supportedScenarios = listOf(
        "all",
        "wallet-2",
        "wallet",
        "twins",
        "ring",
        "note",
        "wallet-2-no-backup",
        "wallet-2-empty",
        "wallet-2-seed",
        "wallet-2-derivations",
        "shiba",
        "shiba-no-backup",
        "shiba-empty",
        "backup-wallet",
        "dev-wallet",
        "firmware-4-12",
        "ed25519",
        "secp256k1",
        "visa",
        "tag-lost",
    )

    @Test
    fun `GIVEN valid demo tag WHEN parsing scenario THEN returns scenario id`() {
        val scenarioId = NfcDemoTag.scenarioId("tangem-demo://v1/all")

        assertThat(scenarioId).isEqualTo("all")
    }

    @Test
    fun `GIVEN web NFC demo tag WHEN parsing scenario THEN returns scenario id`() {
        val scenarioId = NfcDemoTag.scenarioId("https://www.tangem.com/ndef/demo/v1/visa")

        assertThat(scenarioId).isEqualTo("visa")
    }

    @Test
    fun `GIVEN non demo tag WHEN checking tag type THEN returns false`() {
        assertThat(NfcDemoTag.isDemoTag("https://tangem.com/ndef")).isFalse()
        assertThat(NfcDemoTag.scenarioId("wallet-2")).isNull()
    }

    @Test
    fun `GIVEN malformed demo tag WHEN parsing scenario THEN returns null`() {
        val scenarioId = NfcDemoTag.scenarioId("tangem-demo://v1/wallet-2/extra")

        assertThat(scenarioId).isNull()
    }

    @Test
    fun `GIVEN Visa demo tag WHEN applying scenario THEN uses a Visa card without an access code`() {
        val wasApplied = MockProvider.applyNfcDemoTag("tangem-demo://v1/visa")
        val result = MockProvider.getScanResponse()

        assertThat(wasApplied).isTrue()
        assertThat(result).isInstanceOf(CompletionResult.Success::class.java)

        val response = when (result) {
            is CompletionResult.Success -> result.data
            is CompletionResult.Failure -> error("Visa fixture scan failed: ${result.error}")
        }
        assertThat(response.productType).isEqualTo(ProductType.Visa)
        assertThat(response.card.isAccessCodeSet).isFalse()
    }

    @Test
    fun `GIVEN every documented scenario WHEN applying tag THEN it is accepted`() {
        supportedScenarios.forEach { scenario ->
            assertThat(MockProvider.applyNfcDemoTag("${NfcDemoTag.URI_PREFIX}$scenario")).isTrue()
        }
    }

    @Test
    fun `GIVEN Wallet 2 scenarios WHEN importing wallet THEN a local response is returned`() {
        listOf(
            "wallet-2",
            "wallet-2-no-backup",
            "wallet-2-empty",
            "wallet-2-seed",
            "wallet-2-derivations",
        ).forEach { scenario ->
            MockProvider.applyNfcDemoTag("${NfcDemoTag.URI_PREFIX}$scenario")

            assertThat(MockProvider.getImportWalletResponse())
                .isInstanceOf(CompletionResult.Success::class.java)
        }
    }

    @Test
    fun `GIVEN tag lost scenario WHEN scanning THEN a failure is returned`() {
        MockProvider.applyNfcDemoTag("${NfcDemoTag.URI_PREFIX}tag-lost")

        val result = MockProvider.getScanResponse()

        assertThat(result).isInstanceOf(CompletionResult.Failure::class.java)
    }

    @Test
    fun `GIVEN ordinary NFC card IDs WHEN checking transaction identity THEN they are dedicated demo cards`() {
        assertThat(DemoConfig.isNfcDemoCardId("AF05888888880018")).isTrue()
        assertThat(DemoConfig.isNfcDemoCardId("AE05888888880018")).isTrue()
        assertThat(DemoConfig.isDemoCardId("AF05888888880018")).isTrue()
        assertThat(DemoConfig.isNfcDemoCardId("AF00000000000000")).isFalse()
    }
}
