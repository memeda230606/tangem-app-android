package com.tangem.tap.domain.sdk.mocks

import android.content.Context
import com.tangem.sdk.api.CardBackupService

/**
 * Persists only non-sensitive demo ceremony metadata.
 *
 * Access codes, keys, mnemonic phrases, signatures, and other secrets are deliberately never stored here or on NFC.
 */
internal interface NfcDemoBackupStateStorage {
    fun load(): NfcDemoBackupSnapshot?

    fun save(snapshot: NfcDemoBackupSnapshot)

    fun clear()
}

internal data class NfcDemoBackupSnapshot(
    val scenarioId: String?,
    val primaryCardId: String,
    val primaryCardBatchId: String,
    val backupCardsCount: Int,
    val accessCodeIsSet: Boolean,
    val passcodeIsSet: Boolean,
    val state: CardBackupService.State,
    val skipCompatibilityChecks: Boolean,
)

internal class SharedPreferencesNfcDemoBackupStateStorage(context: Context) : NfcDemoBackupStateStorage {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): NfcDemoBackupSnapshot? {
        if (!preferences.contains(KEY_PRIMARY_CARD_ID)) return null

        val primaryCardId = preferences.getString(KEY_PRIMARY_CARD_ID, null) ?: return null
        val primaryCardBatchId = preferences.getString(KEY_PRIMARY_CARD_BATCH_ID, null) ?: return null
        val state = when (preferences.getString(KEY_STATE, null)) {
            STATE_PREPARING -> CardBackupService.State.Preparing
            STATE_FINALIZING_PRIMARY -> CardBackupService.State.FinalizingPrimaryCard
            STATE_FINALIZING_BACKUP -> CardBackupService.State.FinalizingBackupCard(
                index = preferences.getInt(KEY_STATE_INDEX, 1).coerceAtLeast(1),
            )
            STATE_FINISHED -> CardBackupService.State.Finished
            else -> return null
        }

        return NfcDemoBackupSnapshot(
            scenarioId = preferences.getString(KEY_SCENARIO_ID, null),
            primaryCardId = primaryCardId,
            primaryCardBatchId = primaryCardBatchId,
            backupCardsCount = preferences.getInt(KEY_BACKUP_CARDS_COUNT, 0).coerceIn(0, 2),
            accessCodeIsSet = preferences.getBoolean(KEY_ACCESS_CODE_IS_SET, false),
            passcodeIsSet = preferences.getBoolean(KEY_PASSCODE_IS_SET, false),
            state = state,
            skipCompatibilityChecks = preferences.getBoolean(KEY_SKIP_COMPATIBILITY_CHECKS, false),
        )
    }

    override fun save(snapshot: NfcDemoBackupSnapshot) {
        val (stateName, stateIndex) = when (val state = snapshot.state) {
            CardBackupService.State.Preparing -> STATE_PREPARING to 0
            CardBackupService.State.FinalizingPrimaryCard -> STATE_FINALIZING_PRIMARY to 0
            is CardBackupService.State.FinalizingBackupCard -> STATE_FINALIZING_BACKUP to state.index
            CardBackupService.State.Finished -> STATE_FINISHED to 0
        }

        preferences.edit()
            .putString(KEY_SCENARIO_ID, snapshot.scenarioId)
            .putString(KEY_PRIMARY_CARD_ID, snapshot.primaryCardId)
            .putString(KEY_PRIMARY_CARD_BATCH_ID, snapshot.primaryCardBatchId)
            .putInt(KEY_BACKUP_CARDS_COUNT, snapshot.backupCardsCount)
            .putBoolean(KEY_ACCESS_CODE_IS_SET, snapshot.accessCodeIsSet)
            .putBoolean(KEY_PASSCODE_IS_SET, snapshot.passcodeIsSet)
            .putString(KEY_STATE, stateName)
            .putInt(KEY_STATE_INDEX, stateIndex)
            .putBoolean(KEY_SKIP_COMPATIBILITY_CHECKS, snapshot.skipCompatibilityChecks)
            .apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "nfc_demo_backup_state"
        const val KEY_SCENARIO_ID = "scenario_id"
        const val KEY_PRIMARY_CARD_ID = "primary_card_id"
        const val KEY_PRIMARY_CARD_BATCH_ID = "primary_card_batch_id"
        const val KEY_BACKUP_CARDS_COUNT = "backup_cards_count"
        const val KEY_ACCESS_CODE_IS_SET = "access_code_is_set"
        const val KEY_PASSCODE_IS_SET = "passcode_is_set"
        const val KEY_STATE = "state"
        const val KEY_STATE_INDEX = "state_index"
        const val KEY_SKIP_COMPATIBILITY_CHECKS = "skip_compatibility_checks"
        const val STATE_PREPARING = "preparing"
        const val STATE_FINALIZING_PRIMARY = "finalizing_primary"
        const val STATE_FINALIZING_BACKUP = "finalizing_backup"
        const val STATE_FINISHED = "finished"
    }
}
