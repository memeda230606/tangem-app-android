package com.niubtmd.securenfc;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Public routing metadata only. Neither this marker nor its URL proves card possession. */
public final class WalletLaunchNdef {
    public static final String MIME_TYPE = "application/vnd.niubtmd.wallet";
    public static final String WALLET_PACKAGE = "com.tangem.wallet.external";
    public static final int MAX_FILE_BYTES = 220;
    private static final String AAR_TYPE = "android.com:pkg";

    private WalletLaunchNdef() {}

    /** NFC Forum Type 4 file: two-byte NLEN, app-specific MIME record, then Android Application Record. */
    public static byte[] encode(String targetUrl) {
        SecureCardPayload.encode(targetUrl); // shared URL validation
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        writeRecord(message, 0x92, MIME_TYPE, targetUrl); // MB, SR, MIME
        writeRecord(message, 0x54, AAR_TYPE, WALLET_PACKAGE); // ME, SR, external
        byte[] ndef = message.toByteArray();
        if (ndef.length + 2 > MAX_FILE_BYTES) throw new IllegalArgumentException("Launch URL is too long");
        return ByteBuffer.allocate(ndef.length + 2).putShort((short) ndef.length).put(ndef).array();
    }

    public static String decodeTargetUrl(byte[] file) {
        if (file == null || file.length < 8) throw new IllegalArgumentException("Missing wallet launch record");
        int length = ((file[0] & 0xff) << 8) | (file[1] & 0xff);
        if (length < 6 || length + 2 > file.length || length + 2 > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Invalid launch record length");
        }
        ByteBuffer message = ByteBuffer.wrap(file, 2, length).slice();
        String url = readRecord(message, 0x92, MIME_TYPE);
        String packageName = readRecord(message, 0x54, AAR_TYPE);
        if (message.hasRemaining() || !WALLET_PACKAGE.equals(packageName)) {
            throw new IllegalArgumentException("Unexpected launch target");
        }
        SecureCardPayload.encode(url);
        return url;
    }

    private static void writeRecord(ByteArrayOutputStream output, int header, String type, String payload) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        if (payloadBytes.length > 255) throw new IllegalArgumentException("Record is too long");
        output.write(header);
        output.write(typeBytes.length);
        output.write(payloadBytes.length);
        output.write(typeBytes, 0, typeBytes.length);
        output.write(payloadBytes, 0, payloadBytes.length);
    }

    private static String readRecord(ByteBuffer input, int header, String type) {
        if (input.remaining() < 3 || (input.get() & 0xff) != header) {
            throw new IllegalArgumentException("Invalid launch record header");
        }
        int typeLength = input.get() & 0xff;
        int payloadLength = input.get() & 0xff;
        if (typeLength + payloadLength > input.remaining()) throw new IllegalArgumentException("Truncated record");
        byte[] typeBytes = new byte[typeLength];
        input.get(typeBytes);
        if (!Arrays.equals(type.getBytes(StandardCharsets.US_ASCII), typeBytes)) {
            throw new IllegalArgumentException("Unexpected launch record type");
        }
        byte[] payload = new byte[payloadLength];
        input.get(payload);
        return new String(payload, StandardCharsets.UTF_8);
    }
}
