package com.tangem.plugin.configuration.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BuildTypeTest {

    @Test
    fun `hybrid keeps NFC fixtures separate from real data sources`() {
        val fields = BuildType.Hybrid.configFields.associate { it.name to it.value }

        assertThat(BuildType.Hybrid.environment).isEqualTo("prod")
        assertThat(fields["NFC_DEMO_ENABLED"]).isEqualTo("true")
        assertThat(fields["MOCK_DATA_SOURCE"]).isEqualTo("false")
    }

    @Test
    fun `mocked keeps all local fixtures enabled`() {
        val fields = BuildType.Mocked.configFields.associate { it.name to it.value }

        assertThat(fields["NFC_DEMO_ENABLED"]).isEqualTo("true")
        assertThat(fields["MOCK_DATA_SOURCE"]).isEqualTo("true")
    }

    @Test
    fun `release does not register NFC demo behavior`() {
        val fields = BuildType.Release.configFields.associate { it.name to it.value }

        assertThat(fields["NFC_DEMO_ENABLED"]).isEqualTo("false")
        assertThat(fields["MOCK_DATA_SOURCE"]).isEqualTo("false")
    }
}
