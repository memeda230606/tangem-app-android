package com.tangem.tap.features.nfcdemo

import android.app.Activity
import android.nfc.FormatException
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tangem.core.ui.R as CoreUiR
import com.tangem.core.ui.extensions.stringResourceSafe
import com.tangem.core.ui.res.TangemTheme
import com.tangem.tap.domain.sdk.mocks.NfcDemoLogger
import com.tangem.tap.domain.sdk.mocks.MockProvider
import com.tangem.tap.domain.sdk.mocks.NfcDemoTag
import com.tangem.tap.domain.sdk.mocks.NfcDemoTapGate
import com.tangem.wallet.R
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NfcDemoTapOverlay() {
    val state by NfcDemoTapGate.state.collectAsStateWithLifecycle()
    if (state == NfcDemoTapGate.UiState.Idle) return

    NfcDemoReaderModeEffect(
        enabled = state is NfcDemoTapGate.UiState.Waiting || state == NfcDemoTapGate.UiState.Reading,
    )
    BackHandler(enabled = true, onBack = NfcDemoTapGate::cancel)

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = BACKDROP_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 420.dp)
                    .background(
                        color = TangemTheme.colors.background.primary,
                        shape = RoundedCornerShape(28.dp),
                    )
                    .padding(horizontal = 32.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                StatusIcon(state)

                Text(
                    text = titleFor(state),
                    color = TangemTheme.colors.text.primary1,
                    style = TangemTheme.typography.h2,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = descriptionFor(state),
                    color = TangemTheme.colors.text.secondary,
                    style = TangemTheme.typography.body2,
                    textAlign = TextAlign.Center,
                )

                if (state is NfcDemoTapGate.UiState.Waiting) {
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = stringResourceSafe(R.string.nfc_demo_tap_cancel).uppercase(),
                        color = TangemTheme.colors.text.accent,
                        style = TangemTheme.typography.button,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = NfcDemoTapGate::cancel)
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NfcDemoReaderModeEffect(enabled: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(activity, enabled) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (enabled && adapter != null) {
            NfcDemoLogger.event("reader_mode_enabled")
            adapter.enableReaderMode(
                activity,
                { tag ->
                    NfcDemoLogger.event("reader_mode_tag_discovered")
                    if (NfcDemoTapGate.beginPhysicalTagRead()) {
                        coroutineScope.launch {
                            val result = verifyPhysicalTagPresence(tag)
                            NfcDemoTapGate.completePhysicalTagRead(result)
                        }
                    }
                },
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE,
                null,
            )
        }

        onDispose {
            if (enabled && adapter != null) {
                runCatching { adapter.disableReaderMode(activity) }
                NfcDemoLogger.event("reader_mode_disabled")
            }
        }
    }
}

private suspend fun verifyPhysicalTagPresence(tag: Tag): NfcDemoTapGate.Result = withContext(Dispatchers.IO) {
    val ndef = Ndef.get(tag) ?: return@withContext NfcDemoTapGate.Result.RealCard

    try {
        ndef.connect()
        var isDemoTag = false
        repeat(PRESENCE_CHECK_COUNT) { checkIndex ->
            val message = ndef.ndefMessage ?: return@withContext NfcDemoTapGate.Result.TagLost
            if (checkIndex == 0) {
                isDemoTag = message.records
                    .asSequence()
                    .mapNotNull { it.toUri()?.toString() }
                    .firstOrNull(NfcDemoTag::isDemoTag)
                    ?.let(MockProvider::applyNfcDemoTag) == true
            }
            delay(PRESENCE_CHECK_INTERVAL_MILLIS)
        }
        if (isDemoTag) NfcDemoTapGate.Result.Success else NfcDemoTapGate.Result.RealCard
    } catch (_: IOException) {
        NfcDemoTapGate.Result.TagLost
    } catch (_: FormatException) {
        NfcDemoTapGate.Result.TagLost
    } finally {
        runCatching { ndef.close() }
    }
}

@Composable
private fun StatusIcon(state: NfcDemoTapGate.UiState) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .background(TangemTheme.colors.background.secondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            NfcDemoTapGate.UiState.Idle -> Unit
            is NfcDemoTapGate.UiState.Waiting -> Image(
                painter = painterResource(CoreUiR.drawable.ic_tangem_card_24),
                contentDescription = null,
                colorFilter = ColorFilter.tint(TangemTheme.colors.icon.accent),
                modifier = Modifier.size(48.dp),
            )
            NfcDemoTapGate.UiState.Reading -> CircularProgressIndicator(
                color = TangemTheme.colors.icon.accent,
                modifier = Modifier.size(48.dp),
            )
            NfcDemoTapGate.UiState.Success -> Text(
                text = "✓",
                color = SUCCESS_COLOR,
                style = TangemTheme.typography.h1,
            )
            NfcDemoTapGate.UiState.Failure -> Text(
                text = "!",
                color = TangemTheme.colors.text.warning,
                style = TangemTheme.typography.h1,
            )
        }
    }
}

@Composable
private fun titleFor(state: NfcDemoTapGate.UiState): String = when (state) {
    NfcDemoTapGate.UiState.Idle -> ""
    is NfcDemoTapGate.UiState.Waiting -> stringResourceSafe(R.string.nfc_demo_tap_title)
    NfcDemoTapGate.UiState.Reading -> stringResourceSafe(R.string.nfc_demo_tap_reading)
    NfcDemoTapGate.UiState.Success -> stringResourceSafe(R.string.nfc_demo_tap_success)
    NfcDemoTapGate.UiState.Failure -> stringResourceSafe(R.string.nfc_demo_tap_failure)
}

@Composable
private fun descriptionFor(state: NfcDemoTapGate.UiState): String = when (state) {
    NfcDemoTapGate.UiState.Idle -> ""
    is NfcDemoTapGate.UiState.Waiting -> stringResourceSafe(R.string.nfc_demo_tap_description)
    NfcDemoTapGate.UiState.Reading -> stringResourceSafe(R.string.nfc_demo_tap_hold_position)
    NfcDemoTapGate.UiState.Success -> stringResourceSafe(R.string.nfc_demo_tap_success_description)
    NfcDemoTapGate.UiState.Failure -> stringResourceSafe(R.string.nfc_demo_tap_failure_description)
}

private const val BACKDROP_ALPHA = 0.72f
private const val PRESENCE_CHECK_COUNT = 6
private const val PRESENCE_CHECK_INTERVAL_MILLIS = 250L
private val SUCCESS_COLOR = Color(0xFF34C759)
