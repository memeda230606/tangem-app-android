package com.niubtmd.nfcwriter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.IsoDep;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.IDN;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.niubtmd.securenfc.Hex;
import com.niubtmd.securenfc.Ntag424Dna;
import com.niubtmd.securenfc.SecureCardKeys;
import com.niubtmd.securenfc.SecureCardPayload;

public final class MainActivity extends Activity implements NfcAdapter.ReaderCallback {

    private static final String LOG_TAG = "SecureNfcWriter";
    private static final String DEFAULT_TARGET_URL = "https://xn--6qqv7i2xdt95b.com/download";
    private static final String PREFERENCES_NAME = "nfc_writer";
    private static final String PREFERENCE_TARGET_URL = "target_url";
    private static final int READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A
        | NfcAdapter.FLAG_READER_NFC_B
        | NfcAdapter.FLAG_READER_NFC_F
        | NfcAdapter.FLAG_READER_NFC_V;

    private NfcAdapter nfcAdapter;
    private EditText urlInput;
    private TextView statusIcon;
    private TextView statusTitle;
    private TextView statusMessage;
    private TextView cardId;
    private ProgressBar progress;
    private Button actionButton;
    private Button inspectButton;
    private Button logButton;
    private final AtomicBoolean writeInProgress = new AtomicBoolean(false);
    private volatile boolean writeArmed;
    private volatile boolean inspectArmed;
    private volatile String targetUrl = DEFAULT_TARGET_URL;
    private volatile UUID pendingCardInstanceId;
    private boolean logPromptVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DiagnosticLogger.initialize(this);
        DiagnosticLogger.installCrashHandler(this);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            view.setPadding(0, top, 0, bottom);
            return insets;
        });

        urlInput = findViewById(R.id.urlInput);
        statusIcon = findViewById(R.id.statusIcon);
        statusTitle = findViewById(R.id.statusTitle);
        statusMessage = findViewById(R.id.statusMessage);
        cardId = findViewById(R.id.cardId);
        progress = findViewById(R.id.progress);
        actionButton = findViewById(R.id.actionButton);
        inspectButton = findViewById(R.id.inspectButton);
        logButton = findViewById(R.id.logButton);

        String savedUrl = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(PREFERENCE_TARGET_URL, DEFAULT_TARGET_URL);
        urlInput.setText(savedUrl);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        DiagnosticLogger.logNfcState(nfcAdapter);
        actionButton.setOnClickListener(view -> runUiAction("ARM_WRITE", this::armWriter));
        inspectButton.setOnClickListener(view -> runUiAction("ARM_INSPECT", this::armInspector));
        logButton.setOnClickListener(view -> shareDiagnostics());

        if (nfcAdapter == null) {
            showFailure(getString(R.string.nfc_unavailable), null);
            actionButton.setEnabled(false);
            inspectButton.setEnabled(false);
        }

        if (DiagnosticLogger.hasPendingCrash(this)) {
            findViewById(R.id.root).post(() -> {
                DiagnosticLogger.clearPendingCrash(this);
                offerDiagnosticExport(
                    getString(R.string.previous_crash_title),
                    getString(R.string.previous_crash_message)
                );
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        DiagnosticLogger.info("ACTIVITY_RESUME", "writeArmed=" + writeArmed + " inspectArmed=" + inspectArmed);
        if (writeArmed || inspectArmed) {
            enableReaderMode();
        }
    }

    @Override
    protected void onPause() {
        DiagnosticLogger.info("ACTIVITY_PAUSE", "writeArmed=" + writeArmed + " inspectArmed=" + inspectArmed);
        disableReaderMode();
        super.onPause();
    }

    private void armWriter() {
        DiagnosticLogger.info("WRITE_REQUESTED", "urlLength=" + urlInput.getText().length());
        String normalizedUrl = normalizeUrl(urlInput.getText().toString());
        if (normalizedUrl == null) {
            DiagnosticLogger.warning("URL_REJECTED", "invalid target URL");
            urlInput.setError(getString(R.string.invalid_url));
            showFailure(getString(R.string.invalid_url), null);
            return;
        }
        if (nfcAdapter == null) {
            showFailure(getString(R.string.nfc_unavailable), null);
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            DiagnosticLogger.warning("WRITE_BLOCKED", "NFC disabled");
            showFailure(getString(R.string.nfc_disabled), null);
            openNfcSettings();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.secure_confirm_title)
            .setMessage(R.string.secure_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.secure_confirm_action, (dialog, which) -> armWriterConfirmed(normalizedUrl))
            .show();
    }

    private void armWriterConfirmed(String normalizedUrl) {
        DiagnosticLogger.info("WRITE_ARMED", "target URL validated; content omitted");
        targetUrl = normalizedUrl;
        pendingCardInstanceId = UUID.randomUUID();
        urlInput.setText(normalizedUrl);
        urlInput.setSelection(normalizedUrl.length());
        urlInput.setEnabled(false);
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit()
            .putString(PREFERENCE_TARGET_URL, normalizedUrl)
            .apply();
        hideKeyboard();

        writeArmed = true;
        inspectArmed = false;
        writeInProgress.set(false);
        cardId.setVisibility(View.GONE);
        statusIcon.setVisibility(View.VISIBLE);
        statusIcon.setText(R.string.nfc_mark);
        statusIcon.setTextColor(getColor(R.color.accent));
        progress.setVisibility(View.GONE);
        statusTitle.setText(R.string.waiting_title);
        statusMessage.setText(R.string.waiting_message);
        actionButton.setEnabled(false);
        inspectButton.setEnabled(false);
        actionButton.setText(R.string.waiting_title);
        enableReaderMode();
    }

    private void armInspector() {
        DiagnosticLogger.info("INSPECTION_REQUESTED", "-");
        if (nfcAdapter == null) {
            showFailure(getString(R.string.nfc_unavailable), null);
            return;
        }
        if (!nfcAdapter.isEnabled()) {
            DiagnosticLogger.warning("INSPECTION_BLOCKED", "NFC disabled");
            showFailure(getString(R.string.nfc_disabled), null);
            openNfcSettings();
            return;
        }

        writeArmed = false;
        inspectArmed = true;
        writeInProgress.set(false);
        hideKeyboard();
        cardId.setVisibility(View.GONE);
        statusIcon.setVisibility(View.VISIBLE);
        statusIcon.setText(R.string.nfc_mark);
        statusIcon.setTextColor(getColor(R.color.accent));
        progress.setVisibility(View.GONE);
        statusTitle.setText(R.string.inspect_waiting_title);
        statusMessage.setText(R.string.inspect_waiting_message);
        actionButton.setEnabled(false);
        inspectButton.setEnabled(false);
        enableReaderMode();
    }

    private void enableReaderMode() {
        if (nfcAdapter != null && nfcAdapter.isEnabled()) {
            try {
                nfcAdapter.enableReaderMode(this, this, READER_FLAGS, null);
                DiagnosticLogger.info("READER_MODE_ENABLED", "flags=" + READER_FLAGS);
            } catch (RuntimeException error) {
                DiagnosticLogger.error("READER_MODE_ENABLE_FAILED", error);
                writeArmed = false;
                inspectArmed = false;
                writeInProgress.set(false);
                showFailure(getString(R.string.reader_mode_failed), null);
                offerDiagnosticExport(getString(R.string.error_log_title), getString(R.string.error_log_message));
            }
        }
    }

    private void disableReaderMode() {
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableReaderMode(this);
                DiagnosticLogger.info("READER_MODE_DISABLED", "-");
            } catch (RuntimeException error) {
                DiagnosticLogger.error("READER_MODE_DISABLE_FAILED", error);
            }
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        if ((!writeArmed && !inspectArmed) || !writeInProgress.compareAndSet(false, true)) {
            DiagnosticLogger.warning("TAG_IGNORED", "operation not armed or already running");
            return;
        }

        final String uid = toHex(tag.getId());
        DiagnosticLogger.info("TAG_DISCOVERED", "uid=" + maskUid(uid)
            + " technologies=" + Arrays.toString(tag.getTechList())
            + " operation=" + (inspectArmed ? "inspect" : "write"));
        if (inspectArmed) {
            inspectArmed = false;
            runOnUiThread(() -> showWriting(uid));
            final String result = inspectSecurityCapabilities(tag);
            runOnUiThread(() -> {
                disableReaderMode();
                showInspectionResult(result, uid);
            });
            return;
        }

        runOnUiThread(() -> showWriting(uid));

        try {
            personalizeWriteAndVerify(tag, targetUrl, pendingCardInstanceId);
            DiagnosticLogger.info("WRITE_COMPLETED", "uid=" + maskUid(uid));
            writeArmed = false;
            runOnUiThread(() -> {
                disableReaderMode();
                showSuccess(uid);
            });
        } catch (TagLostException error) {
            Log.e(LOG_TAG, "Card was removed during secure write", error);
            DiagnosticLogger.error("WRITE_TAG_LOST", error);
            writeArmed = false;
            writeInProgress.set(false);
            runOnUiThread(() -> {
                disableReaderMode();
                showFailure("卡片移开得太早，请贴紧后重试。", uid);
                offerDiagnosticExport(getString(R.string.error_log_title), getString(R.string.error_log_message));
            });
        } catch (Exception error) {
            Log.e(LOG_TAG, "Secure write failed", error);
            DiagnosticLogger.error("WRITE_FAILED", error);
            writeArmed = false;
            writeInProgress.set(false);
            runOnUiThread(() -> {
                disableReaderMode();
                showFailure(toFriendlyMessage(error), uid);
                offerDiagnosticExport(getString(R.string.error_log_title), getString(R.string.error_log_message));
            });
        }
    }

    private String inspectSecurityCapabilities(Tag tag) {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            DiagnosticLogger.warning("INSPECTION_UNSUPPORTED", "IsoDep unavailable");
            return "不支持 ISO-DEP 安全通信，只能使用普通 NDEF，不能实现真正的加密读写保护。";
        }

        try {
            isoDep.connect();
            isoDep.setTimeout(3_000);
            byte[] version = readChainedNativeResponse(isoDep, (byte) 0x60);
            DiagnosticLogger.info("INSPECTION_VERSION_READ", "responseBytes=" + version.length);
            if (version.length < 7 || (version[0] & 0xFF) != 0x04) {
                return "支持 ISO-DEP，但不是已识别的 NXP 安全卡。芯片响应：" + toCompactHex(version);
            }

            int hardwareType = version[1] & 0xFF;
            int hardwareSubtype = version[2] & 0xFF;
            if (hardwareType == 0x04 && hardwareSubtype == 0x02) {
                return "支持完全加密：检测为 NTAG 424 DNA，可使用 AES-128 身份认证、加密通信和密钥控制读写。";
            }
            if (hardwareType == 0x01) {
                return "支持完全加密：检测为 MIFARE DESFire 系列，可使用芯片密钥认证和加密文件读写。";
            }
            return "检测到 NXP ISO-DEP 安全芯片，但型号需要进一步确认。芯片响应：" + toCompactHex(version);
        } catch (Exception error) {
            DiagnosticLogger.error("INSPECTION_FAILED", error);
            return "卡片支持 ISO-DEP，但安全型号读取失败。可能不是 DESFire/NTAG 424，或卡片已设置访问限制。";
        } finally {
            try {
                isoDep.close();
            } catch (IOException ignored) {
                // Inspection is read-only; the result is already known.
            }
        }
    }

    private static byte[] readChainedNativeResponse(IsoDep isoDep, byte command) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte currentCommand = command;

        for (int frame = 0; frame < 8; frame++) {
            byte[] response = isoDep.transceive(new byte[]{(byte) 0x90, currentCommand, 0x00, 0x00, 0x00});
            if (response.length < 2 || (response[response.length - 2] & 0xFF) != 0x91) {
                throw new IOException("UNEXPECTED_RESPONSE");
            }

            data.write(response, 0, response.length - 2);
            int status = response[response.length - 1] & 0xFF;
            if (status == 0x00) {
                return data.toByteArray();
            }
            if (status != 0xAF) {
                throw new IOException("CARD_STATUS_" + status);
            }
            currentCommand = (byte) 0xAF;
        }

        throw new IOException("TOO_MANY_FRAMES");
    }

    private static String toCompactHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.US, "%02X", value & 0xFF));
        }
        return result.toString();
    }

    private void personalizeWriteAndVerify(Tag tag, String expectedUrl, UUID requestedCardInstanceId) throws Exception {
        byte[] uid = tag.getId();
        if (uid == null || uid.length != 7) throw new IOException("UNSUPPORTED_CARD");

        DiagnosticLogger.info("WRITE_STAGE", "derive card keys uid=" + maskUid(toHex(uid)));

        byte[] adminRoot = Hex.decode(BuildConfig.SECURE_CARD_ADMIN_ROOT);
        byte[] readRoot = Hex.decode(BuildConfig.SECURE_CARD_READ_ROOT);
        byte[] writeRoot = Hex.decode(BuildConfig.SECURE_CARD_WRITE_ROOT);
        byte[][] keys = new byte[5][];
        for (int keyNumber = 0; keyNumber < keys.length; keyNumber++) {
            byte[] root = SecureCardKeys.rootForKey(keyNumber, adminRoot, readRoot, writeRoot);
            keys[keyNumber] = SecureCardKeys.derive(root, uid, keyNumber);
        }

        try {
            DiagnosticLogger.info("WRITE_STAGE", "initialize security settings");
            withCard(tag, card -> {
                if (!card.isNtag424Dna()) throw new IOException("UNSUPPORTED_CARD");
                Ntag424Dna.Session admin = card.authenticate(SecureCardKeys.ADMIN_KEY, Ntag424Dna.DEFAULT_KEY);
                for (int keyNumber = 1; keyNumber < keys.length; keyNumber++) {
                    if (card.getKeyVersion(admin, keyNumber) == 0) {
                        card.changeKey(admin, keyNumber, Ntag424Dna.DEFAULT_KEY, keys[keyNumber], 1);
                    }
                }
                card.changeFileSettings(
                    admin,
                    Ntag424Dna.NDEF_FILE,
                    Ntag424Dna.COMMUNICATION_FULL,
                    Ntag424Dna.NDEF_SECURE_ACCESS_RIGHTS
                );
                card.changeFileSettings(
                    admin,
                    Ntag424Dna.RECOVERY_FILE,
                    Ntag424Dna.COMMUNICATION_FULL,
                    Ntag424Dna.RECOVERY_SECURE_ACCESS_RIGHTS
                );
                card.changeKey(admin, SecureCardKeys.ADMIN_KEY, Ntag424Dna.DEFAULT_KEY, keys[0], 1);
                return null;
            });
        } catch (Ntag424Dna.CardException error) {
            if (error.getStatus() != 0xAE) throw error;
            DiagnosticLogger.info("WRITE_STAGE", "card already initialized; verify admin authentication");
            withCard(tag, card -> {
                Ntag424Dna.Session admin = card.authenticate(SecureCardKeys.ADMIN_KEY, keys[0]);
                card.changeFileSettings(
                    admin,
                    Ntag424Dna.RECOVERY_FILE,
                    Ntag424Dna.COMMUNICATION_FULL,
                    Ntag424Dna.RECOVERY_SECURE_ACCESS_RIGHTS
                );
                return null;
            });
        }

        UUID cardInstanceId = requestedCardInstanceId;
        try {
            SecureCardPayload.CardIdentity existing = withCard(tag, card -> {
                Ntag424Dna.Session reader = card.authenticate(SecureCardKeys.READ_KEY, keys[1]);
                return SecureCardPayload.decodeV2(card.readFull(reader, Ntag424Dna.NDEF_FILE));
            });
            cardInstanceId = existing.getCardInstanceId();
            DiagnosticLogger.info("WRITE_STAGE", "reuse existing v2 card identity");
        } catch (Exception ignored) {
            DiagnosticLogger.info("WRITE_STAGE", "assign new v2 card identity");
        }

        byte[] payload = SecureCardPayload.encodeV2(
            cardInstanceId,
            1,
            System.currentTimeMillis() / 1000L,
            expectedUrl
        );
        DiagnosticLogger.info("WRITE_STAGE", "write encrypted payload bytes=" + payload.length);
        withCard(tag, card -> {
            Ntag424Dna.Session writer = card.authenticate(SecureCardKeys.WRITE_KEY, keys[2]);
            card.writeFull(writer, Ntag424Dna.NDEF_FILE, payload);
            return null;
        });
        DiagnosticLogger.info("WRITE_STAGE", "read back and verify encrypted payload");
        SecureCardPayload.CardIdentity verifiedIdentity = withCard(tag, card -> {
            Ntag424Dna.Session reader = card.authenticate(SecureCardKeys.READ_KEY, keys[1]);
            return SecureCardPayload.decodeV2(card.readFull(reader, Ntag424Dna.NDEF_FILE));
        });
        if (!expectedUrl.equals(verifiedIdentity.getTargetUrl()) ||
            !cardInstanceId.equals(verifiedIdentity.getCardInstanceId()) ||
            verifiedIdentity.getKeyVersion() != 1) {
            throw new IOException("VERIFY_FAILED");
        }
        DiagnosticLogger.info("WRITE_STAGE", "verification passed");
    }

    private static <T> T withCard(Tag tag, CardOperation<T> operation) throws Exception {
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) throw new IOException("UNSUPPORTED_CARD");
        try {
            isoDep.connect();
            isoDep.setTimeout(5_000);
            Ntag424Dna card = new Ntag424Dna(isoDep::transceive);
            card.selectNdefApplication();
            return operation.run(card);
        } finally {
            try {
                isoDep.close();
            } catch (IOException ignored) {
                // The command result has already been determined.
            }
        }
    }

    private interface CardOperation<T> {
        T run(Ntag424Dna card) throws Exception;
    }

    private void showWriting(String uid) {
        statusIcon.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        statusTitle.setText(R.string.writing_title);
        statusMessage.setText(R.string.writing_message);
        showCardId(uid);
    }

    private void showSuccess(String uid) {
        progress.setVisibility(View.GONE);
        statusIcon.setVisibility(View.VISIBLE);
        statusIcon.setText(R.string.success_mark);
        statusIcon.setTextColor(getColor(R.color.success));
        statusTitle.setText(R.string.success_title);
        statusMessage.setText(R.string.success_message);
        showCardId(uid);
        urlInput.setEnabled(true);
        actionButton.setEnabled(true);
        inspectButton.setEnabled(true);
        actionButton.setText(R.string.write_another);
    }

    private void showInspectionResult(String message, String uid) {
        progress.setVisibility(View.GONE);
        statusIcon.setVisibility(View.VISIBLE);
        statusIcon.setText(R.string.success_mark);
        statusIcon.setTextColor(getColor(R.color.success));
        statusTitle.setText(R.string.inspect_success_title);
        statusMessage.setText(message);
        showCardId(uid);
        urlInput.setEnabled(true);
        actionButton.setEnabled(true);
        inspectButton.setEnabled(true);
        actionButton.setText(R.string.start_write);
        writeInProgress.set(false);
    }

    private void showFailure(String message, String uid) {
        progress.setVisibility(View.GONE);
        statusIcon.setVisibility(View.VISIBLE);
        statusIcon.setText(R.string.error_mark);
        statusIcon.setTextColor(getColor(R.color.error));
        statusTitle.setText(R.string.failed_title);
        statusMessage.setText(message);
        showCardId(uid);
        urlInput.setEnabled(true);
        actionButton.setEnabled(nfcAdapter != null);
        inspectButton.setEnabled(nfcAdapter != null);
        actionButton.setText(R.string.retry);
    }

    private void showCardId(String uid) {
        if (uid == null || uid.isEmpty()) {
            cardId.setVisibility(View.GONE);
        } else {
            cardId.setText(getString(R.string.card_id, uid));
            cardId.setVisibility(View.VISIBLE);
        }
    }

    private void runUiAction(String event, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException error) {
            DiagnosticLogger.error(event + "_FAILED", error);
            showFailure(getString(R.string.unexpected_error), null);
            offerDiagnosticExport(getString(R.string.error_log_title), getString(R.string.error_log_message));
        }
    }

    private void openNfcSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
            DiagnosticLogger.info("OPEN_NFC_SETTINGS", "specific NFC settings");
        } catch (ActivityNotFoundException firstError) {
            DiagnosticLogger.warning("OPEN_NFC_SETTINGS_FALLBACK", firstError.getClass().getSimpleName());
            try {
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            } catch (RuntimeException secondError) {
                DiagnosticLogger.error("OPEN_NFC_SETTINGS_FAILED", secondError);
                offerDiagnosticExport(getString(R.string.error_log_title), getString(R.string.error_log_message));
            }
        } catch (RuntimeException error) {
            DiagnosticLogger.error("OPEN_NFC_SETTINGS_FAILED", error);
            offerDiagnosticExport(getString(R.string.error_log_title), getString(R.string.error_log_message));
        }
    }

    private void offerDiagnosticExport(String title, String message) {
        if (logPromptVisible || isFinishing() || isDestroyed()) {
            return;
        }
        logPromptVisible = true;
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.export_later, (ignored, which) -> logPromptVisible = false)
            .setPositiveButton(R.string.export_log, (ignored, which) -> {
                logPromptVisible = false;
                shareDiagnostics();
            })
            .create();
        dialog.setOnCancelListener(ignored -> logPromptVisible = false);
        dialog.show();
    }

    private void shareDiagnostics() {
        try {
            DiagnosticLogger.info("LOG_EXPORT_REQUESTED", "-");
            File logFile = DiagnosticLogger.createExportFile(this);
            Uri contentUri = DiagnosticLogProvider.getUri(logFile, getPackageName() + ".provider");
            Intent shareIntent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, contentUri)
                .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_share_subject))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setClipData(ClipData.newRawUri(getString(R.string.log_share_subject), contentUri));
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_log_chooser)));
            DiagnosticLogger.info("LOG_EXPORT_OPENED", "file=" + logFile.getName());
        } catch (IOException | RuntimeException error) {
            DiagnosticLogger.error("LOG_EXPORT_FAILED", error);
            new AlertDialog.Builder(this)
                .setTitle(R.string.export_failed_title)
                .setMessage(R.string.export_failed_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        }
    }

    private String toFriendlyMessage(Exception error) {
        String code = error.getMessage();
        if ("VERIFY_FAILED".equals(code)) {
            return "加密写入后的回读内容不一致，请重新贴卡尝试。";
        }
        if ("UNSUPPORTED_CARD".equals(code)) {
            return "只支持 NTAG 424 DNA 安全卡。";
        }
        if (error instanceof Ntag424Dna.CardException cardError) {
            if (cardError.getStatus() == 0xAE) {
                return "卡片密钥与本工具不匹配，可能已由其他系统初始化。";
            }
            if (cardError.getStatus() == 0x9D) {
                return "卡片拒绝当前访问权限，请确认卡片未被其他系统锁定。";
            }
        }
        return "安全写入没有完成，请保持卡片贴紧手机后重试。";
    }

    private String normalizeUrl(String input) {
        if (input == null) {
            return null;
        }

        String value = input.trim();
        if (value.isEmpty() || value.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        if (!value.matches("(?i)^https?://.*")) {
            value = "https://" + value;
        }

        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.isEmpty()) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.US);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }
            if (uri.getEncodedUserInfo() != null) {
                return null;
            }

            String asciiHost = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US);
            String authority = uri.getPort() == -1 ? asciiHost : asciiHost + ":" + uri.getPort();
            return uri.buildUpon()
                .scheme(scheme)
                .encodedAuthority(authority)
                .build()
                .toString();
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        urlInput.clearFocus();
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "未知";
        }
        StringBuilder result = new StringBuilder(bytes.length * 3 - 1);
        for (int index = 0; index < bytes.length; index++) {
            if (index > 0) {
                result.append(':');
            }
            result.append(String.format(Locale.US, "%02X", bytes[index] & 0xFF));
        }
        return result.toString();
    }

    private static String maskUid(String uid) {
        if (uid == null || uid.length() < 5) {
            return "unknown";
        }
        return "**:**:**:**:" + uid.substring(Math.max(0, uid.length() - 8));
    }
}
