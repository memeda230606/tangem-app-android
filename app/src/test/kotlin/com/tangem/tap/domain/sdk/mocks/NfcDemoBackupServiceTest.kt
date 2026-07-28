package com.tangem.tap.domain.sdk.mocks

import com.google.common.truth.Truth.assertThat
import com.tangem.common.CompletionResult
import com.tangem.domain.models.scan.CardDTO
import com.tangem.domain.models.scan.ProductType
import com.tangem.domain.models.scan.ScanResponse
import com.tangem.sdk.api.CardBackupService
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NfcDemoBackupServiceTest {

    @BeforeEach
    fun setUp() {
        NfcDemoTapGate.resetForTest()
        MockProvider.resetForTest()
    }

    @AfterEach
    fun tearDown() {
        NfcDemoTapGate.resetForTest()
        MockProvider.resetForTest()
    }

    @Test
    fun `restored final backup card continues after process recreation`() = runTest {
        assertThat(MockProvider.applyNfcDemoTag(DEMO_TAG_URI)).isTrue()
        val card = MockProvider.getCurrentCardDto()
        val storage = FakeStateStorage(
            NfcDemoBackupSnapshot(
                scenarioId = "wallet-2-empty",
                primaryCardId = card.cardId,
                primaryCardBatchId = card.batchId,
                backupCardsCount = 2,
                accessCodeIsSet = true,
                passcodeIsSet = false,
                state = CardBackupService.State.FinalizingBackupCard(index = 2),
                skipCompatibilityChecks = false,
            ),
        )
        val service = NfcDemoBackupService(stateStorage = storage, scope = this)

        assertThat(service.currentState).isEqualTo(CardBackupService.State.FinalizingBackupCard(index = 2))
        assertThat(service.addedBackupCardsCount).isEqualTo(2)

        var result: CompletionResult<CardDTO>? = null
        service.proceedBackup { result = it }
        runCurrent()

        val waiting = NfcDemoTapGate.state.value as NfcDemoTapGate.UiState.Waiting
        assertThat(waiting.operation).isEqualTo("backup_finalize_card_2")
        assertThat(NfcDemoTapGate.consumeTag("wallet-2-empty")).isTrue()
        advanceUntilIdle()

        assertThat(result).isInstanceOf(CompletionResult.Success::class.java)
        assertThat(service.currentState).isEqualTo(CardBackupService.State.Finished)
        assertThat(storage.snapshot?.state).isEqualTo(CardBackupService.State.Finished)
    }

    @Test
    fun `legacy interrupted backup is reconstructed before primary scan`() = runTest {
        val storage = FakeStateStorage()
        val service = NfcDemoBackupService(stateStorage = storage, scope = this)

        var result: CompletionResult<CardDTO>? = null
        service.proceedBackup { result = it }
        runCurrent()

        val waiting = NfcDemoTapGate.state.value as NfcDemoTapGate.UiState.Waiting
        assertThat(waiting.operation).isEqualTo("backup_finalize_primary")
        assertThat(service.addedBackupCardsCount).isEqualTo(2)
        assertThat(storage.snapshot?.scenarioId).isEqualTo("wallet-2-empty")
        assertThat(NfcDemoTapGate.consumeTag("wallet-2-empty")).isTrue()
        advanceUntilIdle()

        assertThat(result).isInstanceOf(CompletionResult.Success::class.java)
        assertThat(service.currentState).isEqualTo(CardBackupService.State.FinalizingBackupCard(index = 1))
    }

    @Test
    fun `finished interrupted backup repeats last tap and restores created wallets`() = runTest {
        assertThat(MockProvider.applyNfcDemoTag(DEMO_TAG_URI)).isTrue()
        val emptyCard = MockProvider.getCurrentCardDto()
        val storage = FakeStateStorage(
            NfcDemoBackupSnapshot(
                scenarioId = "wallet-2-empty",
                primaryCardId = emptyCard.cardId,
                primaryCardBatchId = emptyCard.batchId,
                backupCardsCount = 2,
                accessCodeIsSet = true,
                passcodeIsSet = false,
                state = CardBackupService.State.Finished,
                skipCompatibilityChecks = false,
            ),
        )
        val service = NfcDemoBackupService(stateStorage = storage, scope = this)

        val restored = service.prepareInterruptedBackup(
            ScanResponse(
                card = emptyCard,
                productType = ProductType.Wallet2,
                walletData = null,
            ),
        )

        assertThat(restored.card.wallets).isNotEmpty()
        assertThat(restored.primaryCard).isNotNull()
        assertThat(service.currentState).isEqualTo(CardBackupService.State.FinalizingBackupCard(index = 2))
        assertThat(storage.snapshot?.state).isEqualTo(CardBackupService.State.FinalizingBackupCard(index = 2))
    }

    private class FakeStateStorage(
        var snapshot: NfcDemoBackupSnapshot? = null,
    ) : NfcDemoBackupStateStorage {

        override fun load(): NfcDemoBackupSnapshot? = snapshot

        override fun save(snapshot: NfcDemoBackupSnapshot) {
            this.snapshot = snapshot
        }

        override fun clear() {
            snapshot = null
        }
    }

    private companion object {
        const val DEMO_TAG_URI = "https://www.tangem.com/ndef/demo/v1/wallet-2-empty"
    }
}
