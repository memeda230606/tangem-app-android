package com.niubtmd.securenfc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * L0 TESTNET 2-of-3 Shamir sharing for a random 256-bit recovery wrapping key.
 * This class never accepts a mnemonic or a blockchain private key directly.
 */
public final class RecoverySecretSharing {
    public static final int SECRET_BYTES = 32;
    private static final int SHARE_COUNT = 3;
    private static final String CODE_PREFIX = "NBRC1";

    private RecoverySecretSharing() {}

    public static List<Share> split(byte[] recoveryWrappingKey, UUID recoverySetId, SecureRandom random) {
        if (recoveryWrappingKey == null || recoveryWrappingKey.length != SECRET_BYTES) {
            throw new IllegalArgumentException("Recovery wrapping key must be 32 bytes");
        }
        if (recoverySetId == null) throw new IllegalArgumentException("Recovery set id is required");
        if (random == null) throw new IllegalArgumentException("Secure random is required");

        byte[][] values = new byte[SHARE_COUNT][SECRET_BYTES];
        byte[] coefficientBytes = new byte[1];
        for (int offset = 0; offset < SECRET_BYTES; offset++) {
            // A uniform coefficient, including zero, preserves perfect one-share secrecy.
            random.nextBytes(coefficientBytes);
            int coefficient = coefficientBytes[0] & 0xFF;
            int secretByte = recoveryWrappingKey[offset] & 0xFF;
            for (int shareIndex = 1; shareIndex <= SHARE_COUNT; shareIndex++) {
                values[shareIndex - 1][offset] = (byte) (secretByte ^ multiply(coefficient, shareIndex));
            }
        }

        return Arrays.asList(
            new Share(recoverySetId, 1, values[0]),
            new Share(recoverySetId, 2, values[1]),
            new Share(recoverySetId, 3, values[2])
        );
    }

    public static byte[] combine(Share first, Share second) {
        requireCompatible(first, second);
        byte[] secret = new byte[SECRET_BYTES];
        int x1 = first.index;
        int x2 = second.index;
        int denominator = x1 ^ x2;
        for (int offset = 0; offset < SECRET_BYTES; offset++) {
            int y1 = first.value[offset] & 0xFF;
            int y2 = second.value[offset] & 0xFF;
            int firstTerm = divide(multiply(y1, x2), denominator);
            int secondTerm = divide(multiply(y2, x1), denominator);
            secret[offset] = (byte) (firstTerm ^ secondTerm);
        }
        return secret;
    }

    private static void requireCompatible(Share first, Share second) {
        if (first == null || second == null) throw new IllegalArgumentException("Two shares are required");
        if (!first.recoverySetId.equals(second.recoverySetId)) {
            throw new IllegalArgumentException("Shares belong to different recovery sets");
        }
        if (first.index == second.index) throw new IllegalArgumentException("Shares must be distinct");
    }

    private static int multiply(int left, int right) {
        int product = 0;
        int a = left & 0xFF;
        int b = right & 0xFF;
        for (int bit = 0; bit < 8; bit++) {
            if ((b & 1) != 0) product ^= a;
            boolean highBit = (a & 0x80) != 0;
            a = (a << 1) & 0xFF;
            if (highBit) a ^= 0x1B;
            b >>>= 1;
        }
        return product;
    }

    private static int divide(int numerator, int denominator) {
        if (denominator == 0) throw new IllegalArgumentException("Division by zero in GF(256)");
        return multiply(numerator, inverse(denominator));
    }

    private static int inverse(int value) {
        int result = 1;
        int base = value & 0xFF;
        int exponent = 254;
        while (exponent > 0) {
            if ((exponent & 1) != 0) result = multiply(result, base);
            base = multiply(base, base);
            exponent >>>= 1;
        }
        return result;
    }

    private static byte[] checksum(String value) {
        try {
            return Arrays.copyOf(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)),
                4
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    public static final class Share {
        private final UUID recoverySetId;
        private final int index;
        private final byte[] value;

        public Share(UUID recoverySetId, int index, byte[] value) {
            if (recoverySetId == null) throw new IllegalArgumentException("Recovery set id is required");
            if (index < 1 || index > SHARE_COUNT) throw new IllegalArgumentException("Invalid share index");
            if (value == null || value.length != SECRET_BYTES) {
                throw new IllegalArgumentException("Share must be 32 bytes");
            }
            this.recoverySetId = recoverySetId;
            this.index = index;
            this.value = Arrays.copyOf(value, value.length);
        }

        public UUID getRecoverySetId() { return recoverySetId; }
        public int getIndex() { return index; }
        public byte[] getValue() { return Arrays.copyOf(value, value.length); }

        public String encodeRecoveryCode() {
            String body = CODE_PREFIX + "-" + recoverySetId.toString().replace("-", "").toUpperCase(Locale.US) +
                "-" + index + "-" +
                Hex.encode(value).toUpperCase(Locale.US);
            return body + "-" + Hex.encode(checksum(body)).toUpperCase(Locale.US);
        }

        public static Share decodeRecoveryCode(String encoded) {
            if (encoded == null) throw new IllegalArgumentException("Recovery code is required");
            String normalized = encoded.trim().toUpperCase(Locale.US);
            String[] parts = normalized.split("-");
            if (parts.length != 5 || !CODE_PREFIX.equals(parts[0])) {
                throw new IllegalArgumentException("Invalid recovery code format");
            }
            String body = String.join("-", parts[0], parts[1], parts[2], parts[3]);
            if (!MessageDigest.isEqual(checksum(body), Hex.decode(parts[4]))) {
                throw new IllegalArgumentException("Recovery code checksum mismatch");
            }
            if (parts[1].length() != 32) throw new IllegalArgumentException("Invalid recovery set id");
            UUID recoverySetId = UUID.fromString(
                parts[1].substring(0, 8) + "-" + parts[1].substring(8, 12) + "-" +
                    parts[1].substring(12, 16) + "-" + parts[1].substring(16, 20) + "-" +
                    parts[1].substring(20)
            );
            int index;
            try {
                index = Integer.parseInt(parts[2]);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Invalid share index", error);
            }
            return new Share(recoverySetId, index, Hex.decode(parts[3]));
        }
    }
}
