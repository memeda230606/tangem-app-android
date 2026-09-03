package com.niubtmd.nfcwriter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class DiagnosticLogProvider extends ContentProvider {

    private static final String EXPORT_DIRECTORY = "diagnostic-exports";

    static Uri getUri(File file, String authority) {
        return new Uri.Builder()
            .scheme("content")
            .authority(authority)
            .appendPath(file.getName())
            .build();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only diagnostics provider");
        }
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only diagnostics provider");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only diagnostics provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only diagnostics provider");
    }

    private File resolve(Uri uri) {
        if (getContext() == null || uri.getLastPathSegment() == null) {
            throw new IllegalArgumentException("Invalid diagnostic log URI");
        }
        String fileName = uri.getLastPathSegment();
        if (!fileName.matches("nfc-writer-[0-9]{8}-[0-9]{6}\\.log")) {
            throw new IllegalArgumentException("Invalid diagnostic log name");
        }

        File directory = new File(getContext().getCacheDir(), EXPORT_DIRECTORY);
        File file = new File(directory, fileName);
        try {
            String directoryPath = directory.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(directoryPath) || !file.isFile()) {
                throw new IllegalArgumentException("Diagnostic log not found");
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Invalid diagnostic log path", error);
        }
        return file;
    }
}
