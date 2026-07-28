package com.tangem.data.pay.repository

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class TangemPayFixturePolicyTest {

    @Test
    fun `full mock mode enables fixtures for every wallet`() {
        val actual = TangemPayFixturePolicy.shouldUseWalletFixture(
            isFullMockMode = true,
            isNfcDemoEnabled = false,
            cardId = null,
            isVisaWallet = false,
        )

        assertThat(actual).isTrue()
    }

    @Test
    fun `hybrid enables fixtures for the dedicated Visa demo wallet`() {
        val actual = TangemPayFixturePolicy.shouldUseWalletFixture(
            isFullMockMode = false,
            isNfcDemoEnabled = true,
            cardId = TangemPayFixturePolicy.NFC_DEMO_VISA_CARD_ID,
            isVisaWallet = true,
        )

        assertThat(actual).isTrue()
    }

    @Test
    fun `hybrid does not enable fixtures for a hot wallet`() {
        val actual = TangemPayFixturePolicy.shouldUseWalletFixture(
            isFullMockMode = false,
            isNfcDemoEnabled = true,
            cardId = null,
            isVisaWallet = false,
        )

        assertThat(actual).isFalse()
    }

    @Test
    fun `hybrid does not enable fixtures for a real Visa card`() {
        val actual = TangemPayFixturePolicy.shouldUseWalletFixture(
            isFullMockMode = false,
            isNfcDemoEnabled = true,
            cardId = "REAL_VISA_CARD",
            isVisaWallet = true,
        )

        assertThat(actual).isFalse()
    }

    @Test
    fun `global fixtures require a saved Visa demo wallet in hybrid`() {
        assertThat(
            TangemPayFixturePolicy.shouldUseGlobalFixture(
                isFullMockMode = false,
                isNfcDemoEnabled = true,
                hasNfcDemoVisaWallet = false,
            ),
        ).isFalse()
        assertThat(
            TangemPayFixturePolicy.shouldUseGlobalFixture(
                isFullMockMode = false,
                isNfcDemoEnabled = true,
                hasNfcDemoVisaWallet = true,
            ),
        ).isTrue()
    }

    @Test
    fun `hybrid suppresses Tangem Pay for a wallet without a fixture`() {
        val actual = TangemPayFixturePolicy.shouldSuppressWallet(
            isFullMockMode = false,
            isNfcDemoEnabled = true,
            usesFixture = false,
        )

        assertThat(actual).isTrue()
    }

    @Test
    fun `hybrid keeps Tangem Pay for its Visa fixture`() {
        val actual = TangemPayFixturePolicy.shouldSuppressWallet(
            isFullMockMode = false,
            isNfcDemoEnabled = true,
            usesFixture = true,
        )

        assertThat(actual).isFalse()
    }

    @Test
    fun `production does not suppress Tangem Pay`() {
        val actual = TangemPayFixturePolicy.shouldSuppressWallet(
            isFullMockMode = false,
            isNfcDemoEnabled = false,
            usesFixture = false,
        )

        assertThat(actual).isFalse()
    }
}
