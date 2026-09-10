package com.niubtmd.securenfc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/** V3 identity in the unused tail of protected File 03; existing recovery bytes [0, 63) are untouched. */
public final class SecureCardIdentityStore {
    public static final int OFFSET = 64;
    public static final int ENCODED_BYTES = 62;
    private static final byte[] MAGIC = new byte[]{'N', 'B', 'S', 'I'};

    private SecureCardIdentityStore() {}

    public static byte[] encode(SecureCardPayload.CardIdentity identity) {
        SecureCardPayload.encodeV2(identity.getCardInstanceId(), identity.getKeyVersion(),
            identity.getIssuedAt(), identity.getTargetUrl());
        UUID id = identity.getCardInstanceId();
        return ByteBuffer.allocate(ENCODED_BYTES).put(MAGIC).put((byte) 3)
            .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
            .put((byte) identity.getKeyVersion()).putLong(identity.getIssuedAt())
            .put(urlHash(identity.getTargetUrl())).array();
    }

    public static Record decode(byte[] data) {
        if (data == null || data.length != ENCODED_BYTES) throw new IllegalArgumentException("Invalid identity size");
        if (!Arrays.equals(MAGIC, Arrays.copyOf(data, MAGIC.length))) {
            for (byte value : data) {
                if (value != 0) throw new IllegalArgumentException("Unknown protected identity");
            }
            return null; // only an empty reserved region may be treated as a legacy card
        }
        ByteBuffer input = ByteBuffer.wrap(data);
        input.position(MAGIC.length);
        if (input.get() != 3) throw new IllegalArgumentException("Unsupported identity version");
        UUID id = new UUID(input.getLong(), input.getLong());
        int keyVersion = input.get() & 0xff;
        long issuedAt = input.getLong();
        byte[] urlHash = new byte[32];
        input.get(urlHash);
        if (keyVersion == 0 || issuedAt <= 0) throw new IllegalArgumentException("Invalid protected identity");
        return new Record(id, keyVersion, issuedAt, urlHash);
    }

    public static Record readRecord(Ntag424Dna card, Ntag424Dna.Session session)
        throws IOException, GeneralSecurityException {
        return decode(card.readFull(session, Ntag424Dna.RECOVERY_FILE, OFFSET, ENCODED_BYTES));
    }

    /** Caller has authenticated key 1. V2 remains readable until its card is migrated by the writer. */
    public static SecureCardPayload.CardIdentity read(Ntag424Dna card, Ntag424Dna.Session session)
        throws IOException, GeneralSecurityException {
        Record record = readRecord(card, session);
        if (record == null) return SecureCardPayload.decodeV2(card.readFull(session, Ntag424Dna.NDEF_FILE));
        String url = WalletLaunchNdef.decodeTargetUrl(card.readPlain(session, Ntag424Dna.NDEF_FILE, 0, WalletLaunchNdef.MAX_FILE_BYTES));
        return record.verifyUrl(url);
    }

    private static byte[] urlHash(String url) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    public static final class Record {
        private final UUID cardInstanceId;
        private final int keyVersion;
        private final long issuedAt;
        private final byte[] urlHash;

        private Record(UUID id, int keyVersion, long issuedAt, byte[] urlHash) {
            this.cardInstanceId = id;
            this.keyVersion = keyVersion;
            this.issuedAt = issuedAt;
            this.urlHash = urlHash;
        }

        public UUID getCardInstanceId() { return cardInstanceId; }
        public int getKeyVersion() { return keyVersion; }
        public long getIssuedAt() { return issuedAt; }

        public SecureCardPayload.CardIdentity verifyUrl(String url) throws GeneralSecurityException {
            if (!MessageDigest.isEqual(urlHash, urlHash(url))) throw new GeneralSecurityException("Launch URL mismatch");
            return new SecureCardPayload.CardIdentity(cardInstanceId, keyVersion, issuedAt, url);
        }
    }
}
