package com.tangem.tap.common.libs.blockchainsdk

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NfcDemoColdWalletSigningPolicyTest {

    @Test
    fun `hybrid blocks cold wallet signing`() {
        assertThat(
            NfcDemoColdWalletSigningPolicy.resolve(
                nfcDemoEnabled = true,
                mockDataSource = false,
            ),
        ).isEqualTo(NfcDemoColdWalletSigningPolicy.Mode.Block)
    }

    @Test
    fun `fully mocked build keeps visual signing fixture available`() {
        assertThat(
            NfcDemoColdWalletSigningPolicy.resolve(
                nfcDemoEnabled = true,
                mockDataSource = true,
            ),
        ).isEqualTo(NfcDemoColdWalletSigningPolicy.Mode.Simulate)
    }

    @Test
    fun `production card build keeps real card signer available`() {
        assertThat(
            NfcDemoColdWalletSigningPolicy.resolve(
                nfcDemoEnabled = false,
                mockDataSource = false,
            ),
        ).isEqualTo(NfcDemoColdWalletSigningPolicy.Mode.Real)
    }
}
