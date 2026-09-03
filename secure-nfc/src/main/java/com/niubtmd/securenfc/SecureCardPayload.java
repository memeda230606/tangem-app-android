package com.niubtmd.securenfc;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public final class SecureCardPayload {
    private static final byte[] MAGIC = new byte[]{'N', 'B', 'S', 'C'};
    private static final byte VERSION_V1 = 1;
    private static final byte VERSION_V2 = 2;
    private static final int MAX_URL_BYTES = 220;
    private static final int MAX_V2_URL_BYTES = 188;
    public static final int MAX_ENCODED_BYTES = 4 + 1 + 2 + MAX_URL_BYTES;

    private SecureCardPayload() {}

    public static byte[] encode(String url) {
        validateUrl(url);
        byte[] encodedUrl = url.getBytes(StandardCharsets.UTF_8);
        if (encodedUrl.length == 0 || encodedUrl.length > MAX_URL_BYTES) {
            throw new IllegalArgumentException("URL is too long");
        }
        ByteBuffer result = ByteBuffer.allocate(MAGIC.length + 1 + 2 + encodedUrl.length)
            .order(ByteOrder.BIG_ENDIAN);
        result.put(MAGIC).put(VERSION_V1).putShort((short) encodedUrl.length).put(encodedUrl);
        return result.array();
    }

    public static byte[] encodeV2(UUID cardInstanceId, int keyVersion, long issuedAt, String url) {
        if (cardInstanceId == null) throw new IllegalArgumentException("cardInstanceId is required");
        if (keyVersion < 1 || keyVersion > 255) throw new IllegalArgumentException("Invalid key version");
        if (issuedAt <= 0) throw new IllegalArgumentException("Invalid issuedAt");
        validateUrl(url);
        byte[] encodedUrl = url.getBytes(StandardCharsets.UTF_8);
        if (encodedUrl.length == 0 || encodedUrl.length > MAX_V2_URL_BYTES) {
            throw new IllegalArgumentException("URL is too long");
        }
        ByteBuffer result = ByteBuffer.allocate(4 + 1 + 16 + 1 + 8 + 2 + encodedUrl.length)
            .order(ByteOrder.BIG_ENDIAN);
        result.put(MAGIC)
            .put(VERSION_V2)
            .putLong(cardInstanceId.getMostSignificantBits())
            .putLong(cardInstanceId.getLeastSignificantBits())
            .put((byte) keyVersion)
            .putLong(issuedAt)
            .putShort((short) encodedUrl.length)
            .put(encodedUrl);
        return result.array();
    }

    public static String decode(byte[] data) {
        if (data == null || data.length < 7 || !Arrays.equals(MAGIC, Arrays.copyOf(data, MAGIC.length))) {
            throw new IllegalArgumentException("Unknown secure card payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        buffer.position(MAGIC.length);
        if (buffer.get() != VERSION_V1) throw new IllegalArgumentException("Unsupported payload version");
        int length = buffer.getShort() & 0xFFFF;
        if (length == 0 || length > MAX_URL_BYTES || length > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid URL length");
        }
        byte[] url = new byte[length];
        buffer.get(url);
        String result = new String(url, StandardCharsets.UTF_8);
        validateUrl(result);
        return result;
    }

    public static CardIdentity decodeV2(byte[] data) {
        if (data == null || data.length < 32 || !Arrays.equals(MAGIC, Arrays.copyOf(data, MAGIC.length))) {
            throw new IllegalArgumentException("Unknown secure card payload");
        }
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        buffer.position(MAGIC.length);
        if (buffer.get() != VERSION_V2) throw new IllegalArgumentException("Unsupported payload version");
        UUID cardInstanceId = new UUID(buffer.getLong(), buffer.getLong());
        int keyVersion = buffer.get() & 0xFF;
        long issuedAt = buffer.getLong();
        int length = buffer.getShort() & 0xFFFF;
        if (keyVersion == 0 || issuedAt <= 0 || length == 0 || length > MAX_V2_URL_BYTES || length > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid v2 payload");
        }
        byte[] url = new byte[length];
        buffer.get(url);
        String targetUrl = new String(url, StandardCharsets.UTF_8);
        validateUrl(targetUrl);
        return new CardIdentity(cardInstanceId, keyVersion, issuedAt, targetUrl);
    }

    public static final class CardIdentity {
        private final UUID cardInstanceId;
        private final int keyVersion;
        private final long issuedAt;
        private final String targetUrl;

        CardIdentity(UUID cardInstanceId, int keyVersion, long issuedAt, String targetUrl) {
            this.cardInstanceId = cardInstanceId;
            this.keyVersion = keyVersion;
            this.issuedAt = issuedAt;
            this.targetUrl = targetUrl;
        }

        public UUID getCardInstanceId() { return cardInstanceId; }
        public int getKeyVersion() { return keyVersion; }
        public long getIssuedAt() { return issuedAt; }
        public String getTargetUrl() { return targetUrl; }
    }

    private static void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Invalid URL");
            }
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Invalid URL", error);
        }
    }
}
