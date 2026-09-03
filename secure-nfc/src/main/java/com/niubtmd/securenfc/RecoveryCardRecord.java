package com.niubtmd.securenfc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/** Binary record stored in NTAG 424 DNA protected File 03. */
public final class RecoveryCardRecord {
    private static final byte[] MAGIC = new byte[]{'N', 'B', 'S', 'R'};
    private static final byte VERSION = 1;
    private static final int CHECKSUM_BYTES = 8;
    public static final int ENCODED_BYTES = 4 + 1 + 16 + 1 + 1 + RecoverySecretSharing.SECRET_BYTES + CHECKSUM_BYTES;

    private RecoveryCardRecord() {}

    public static byte[] encode(RecoverySecretSharing.Share share) {
        if (share == null) throw new IllegalArgumentException("Share is required");
        ByteBuffer body = ByteBuffer.allocate(ENCODED_BYTES - CHECKSUM_BYTES).order(ByteOrder.BIG_ENDIAN);
        UUID id = share.getRecoverySetId();
        body.put(MAGIC)
            .put(VERSION)
            .putLong(id.getMostSignificantBits())
            .putLong(id.getLeastSignificantBits())
            .put((byte) share.getIndex())
            .put((byte) RecoverySecretSharing.SECRET_BYTES)
            .put(share.getValue());
        byte[] bodyBytes = body.array();
        ByteBuffer encoded = ByteBuffer.allocate(ENCODED_BYTES);
        encoded.put(bodyBytes).put(Arrays.copyOf(sha256(bodyBytes), CHECKSUM_BYTES));
        return encoded.array();
    }

    public static RecoverySecretSharing.Share decode(byte[] encoded) {
        if (encoded == null || encoded.length < ENCODED_BYTES) {
            throw new IllegalArgumentException("Invalid recovery card record length");
        }
        byte[] exact = Arrays.copyOf(encoded, ENCODED_BYTES);
        byte[] body = Arrays.copyOf(exact, ENCODED_BYTES - CHECKSUM_BYTES);
        byte[] expected = Arrays.copyOf(sha256(body), CHECKSUM_BYTES);
        byte[] provided = Arrays.copyOfRange(exact, ENCODED_BYTES - CHECKSUM_BYTES, ENCODED_BYTES);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new IllegalArgumentException("Recovery card record checksum mismatch");
        }

        ByteBuffer buffer = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN);
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        if (!Arrays.equals(MAGIC, magic) || buffer.get() != VERSION) {
            throw new IllegalArgumentException("Unsupported recovery card record");
        }
        UUID recoverySetId = new UUID(buffer.getLong(), buffer.getLong());
        int index = buffer.get() & 0xFF;
        int length = buffer.get() & 0xFF;
        if (length != RecoverySecretSharing.SECRET_BYTES) {
            throw new IllegalArgumentException("Invalid recovery share length");
        }
        byte[] value = new byte[length];
        buffer.get(value);
        return new RecoverySecretSharing.Share(recoverySetId, index, value);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
