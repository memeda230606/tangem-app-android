package com.tangem.tap.domain.sdk.mocks

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NfcDemoTapGateTest {

    @BeforeEach
    fun setUp() = NfcDemoTapGate.resetForTest()

    @AfterEach
    fun tearDown() = NfcDemoTapGate.resetForTest()

    @Test
    fun `ordinary demo tag completes waiting operation`() = runTest {
        val result = async {
            NfcDemoTapGate.awaitTap(
                operation = "transaction_sign",
                timeoutMillis = 1_000,
                feedbackMillis = 0,
            )
        }
        runCurrent()

        assertThat(NfcDemoTapGate.state.value).isInstanceOf(NfcDemoTapGate.UiState.Waiting::class.java)
        assertThat(NfcDemoTapGate.consumeTag("visa")).isTrue()
        assertThat(result.await()).isEqualTo(NfcDemoTapGate.Result.Success)
        assertThat(NfcDemoTapGate.state.value).isEqualTo(NfcDemoTapGate.UiState.Idle)
    }

    @Test
    fun `tag lost scenario completes waiting operation with failure`() = runTest {
        val result = async {
            NfcDemoTapGate.awaitTap(
                operation = "transaction_sign",
                timeoutMillis = 1_000,
                feedbackMillis = 0,
            )
        }
        runCurrent()

        assertThat(NfcDemoTapGate.consumeTag("tag-lost")).isTrue()
        assertThat(result.await()).isEqualTo(NfcDemoTapGate.Result.TagLost)
    }

    @Test
    fun `non demo tag is classified as a real card`() = runTest {
        val result = async {
            NfcDemoTapGate.awaitTap(
                operation = "identify_card_type",
                timeoutMillis = 1_000,
                feedbackMillis = 0,
            )
        }
        runCurrent()

        assertThat(NfcDemoTapGate.consumeTag(scenarioId = null, isDemoTag = false)).isTrue()
        assertThat(result.await()).isEqualTo(NfcDemoTapGate.Result.RealCard)
    }

    @Test
    fun `physical tag read can complete with tag lost`() = runTest {
        val result = async {
            NfcDemoTapGate.awaitTap(
                operation = "transaction_sign",
                timeoutMillis = 1_000,
                feedbackMillis = 0,
            )
        }
        runCurrent()

        assertThat(NfcDemoTapGate.beginPhysicalTagRead()).isTrue()
        assertThat(NfcDemoTapGate.state.value).isEqualTo(NfcDemoTapGate.UiState.Reading)
        assertThat(NfcDemoTapGate.beginPhysicalTagRead()).isFalse()
        assertThat(NfcDemoTapGate.completePhysicalTagRead(NfcDemoTapGate.Result.TagLost)).isTrue()
        assertThat(result.await()).isEqualTo(NfcDemoTapGate.Result.TagLost)
    }

    @Test
    fun `tag does not change routes only while an operation is waiting`() {
        assertThat(NfcDemoTapGate.consumeTag("visa")).isFalse()
    }

    @Test
    fun `waiting operation times out without a tag`() = runTest {
        val result = async {
            NfcDemoTapGate.awaitTap(
                operation = "transaction_sign",
                timeoutMillis = 1,
                feedbackMillis = 0,
            )
        }

        advanceUntilIdle()

        assertThat(result.await()).isEqualTo(NfcDemoTapGate.Result.Timeout)
        assertThat(NfcDemoTapGate.state.value).isEqualTo(NfcDemoTapGate.UiState.Idle)
    }
}
