package com.niubtmd.nfcwriter;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DiagnosticLogger {

    private static final String LOG_TAG = "NfcWriterDiagnostics";
    private static final String LOG_DIRECTORY = "diagnostics";
    private static final String LOG_FILE = "nfc-writer.log";
    private static final String PREVIOUS_LOG_FILE = "nfc-writer.previous.log";
    private static final String CRASH_MARKER_FILE = "pending-crash";
    private static final long MAX_LOG_BYTES = 512L * 1024L;
    private static final Object LOCK = new Object();

    private static File logDirectory;

    private DiagnosticLogger() {
    }

    static void initialize(Context context) {
        synchronized (LOCK) {
            logDirectory = new File(context.getFilesDir(), LOG_DIRECTORY);
            if (!logDirectory.exists() && !logDirectory.mkdirs()) {
                Log.e(LOG_TAG, "Unable to create diagnostics directory");
            }
        }
        info("APP_START", "version=" + BuildConfig.VERSION_NAME
            + " code=" + BuildConfig.VERSION_CODE
            + " sdk=" + Build.VERSION.SDK_INT
            + " device=" + safe(Build.MANUFACTURER) + "/" + safe(Build.MODEL));
    }

    static void installCrashHandler(Context context) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        if (previous instanceof CrashHandler) {
            return;
        }
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context.getApplicationContext(), previous));
    }

    static void logNfcState(NfcAdapter adapter) {
        if (adapter == null) {
            info("NFC_STATE", "adapter=unavailable");
            return;
        }
        boolean enabled;
        try {
            enabled = adapter.isEnabled();
        } catch (RuntimeException error) {
            error("NFC_STATE_FAILED", error);
            return;
        }
        info("NFC_STATE", "adapter=available enabled=" + enabled);
    }

    static void info(String event, String detail) {
        write("INFO", event, detail, null);
    }

    static void warning(String event, String detail) {
        write("WARN", event, detail, null);
    }

    static void error(String event, Throwable error) {
        write("ERROR", event, error == null ? "unknown" : safe(error.getMessage()), error);
    }

    static boolean hasPendingCrash(Context context) {
        return new File(new File(context.getFilesDir(), LOG_DIRECTORY), CRASH_MARKER_FILE).exists();
    }

    static void clearPendingCrash(Context context) {
        File marker = new File(new File(context.getFilesDir(), LOG_DIRECTORY), CRASH_MARKER_FILE);
        if (marker.exists() && !marker.delete()) {
            warning("CRASH_MARKER_CLEAR_FAILED", marker.getName());
        }
    }

    static File createExportFile(Context context) throws IOException {
        synchronized (LOCK) {
            File exportDirectory = new File(context.getCacheDir(), "diagnostic-exports");
            if (!exportDirectory.exists() && !exportDirectory.mkdirs()) {
                throw new IOException("无法创建日志导出目录");
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File exportFile = new File(exportDirectory, "nfc-writer-" + timestamp + ".log");
            try (FileOutputStream output = new FileOutputStream(exportFile);
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                writer.println("NFC Writer diagnostic report");
                writer.println("App: " + BuildConfig.APPLICATION_ID + " " + BuildConfig.VERSION_NAME
                    + " (" + BuildConfig.VERSION_CODE + ")");
                writer.println("Device: " + safe(Build.MANUFACTURER) + " " + safe(Build.MODEL)
                    + ", Android API " + Build.VERSION.SDK_INT);
                writer.println("Generated: " + now());
                writer.println("Security: AES keys and target URL contents are intentionally excluded.");
                writer.println();
                appendFile(writer, new File(logDirectory(context), PREVIOUS_LOG_FILE));
                appendFile(writer, new File(logDirectory(context), LOG_FILE));
                if (writer.checkError()) {
                    throw new IOException("写入导出日志失败");
                }
            }
            return exportFile;
        }
    }

    private static void appendFile(PrintWriter writer, File source) throws IOException {
        if (!source.exists()) {
            return;
        }
        writer.println("===== " + source.getName() + " =====");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.InputStreamReader(new java.io.FileInputStream(source), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.println(line);
            }
        }
        writer.println();
    }

    private static void write(String level, String event, String detail, Throwable throwable) {
        String line = now() + " " + level + " [" + Thread.currentThread().getName() + "] "
            + safe(event) + " - " + safe(detail);
        if ("ERROR".equals(level)) {
            Log.e(LOG_TAG, line, throwable);
        } else {
            Log.i(LOG_TAG, line);
        }

        synchronized (LOCK) {
            if (logDirectory == null) {
                return;
            }
            try {
                rotateIfNeeded();
                File current = new File(logDirectory, LOG_FILE);
                try (FileOutputStream output = new FileOutputStream(current, true);
                     BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                    writer.write(line);
                    writer.newLine();
                    if (throwable != null) {
                        PrintWriter printWriter = new PrintWriter(writer);
                        throwable.printStackTrace(printWriter);
                        printWriter.flush();
                    }
                }
            } catch (IOException fileError) {
                Log.e(LOG_TAG, "Unable to persist diagnostics", fileError);
            }
        }
    }

    private static void rotateIfNeeded() {
        File current = new File(logDirectory, LOG_FILE);
        if (!current.exists() || current.length() < MAX_LOG_BYTES) {
            return;
        }
        File previous = new File(logDirectory, PREVIOUS_LOG_FILE);
        if (previous.exists() && !previous.delete()) {
            Log.w(LOG_TAG, "Unable to delete previous diagnostics");
        }
        if (!current.renameTo(previous)) {
            Log.w(LOG_TAG, "Unable to rotate diagnostics");
        }
    }

    private static File logDirectory(Context context) {
        if (logDirectory == null) {
            logDirectory = new File(context.getFilesDir(), LOG_DIRECTORY);
        }
        return logDirectory;
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
    }

    private static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    private static final class CrashHandler implements Thread.UncaughtExceptionHandler {
        private final Context context;
        private final Thread.UncaughtExceptionHandler previous;

        private CrashHandler(Context context, Thread.UncaughtExceptionHandler previous) {
            this.context = context;
            this.previous = previous;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            error("UNCAUGHT_EXCEPTION", throwable);
            synchronized (LOCK) {
                try {
                    File marker = new File(logDirectory(context), CRASH_MARKER_FILE);
                    if (!marker.exists() && !marker.createNewFile()) {
                        Log.e(LOG_TAG, "Unable to create crash marker");
                    }
                } catch (IOException markerError) {
                    Log.e(LOG_TAG, "Unable to persist crash marker", markerError);
                }
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        }
    }
}
