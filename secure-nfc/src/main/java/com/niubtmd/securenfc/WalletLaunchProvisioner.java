package com.niubtmd.securenfc;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

/** Migrates routing metadata without replacing an issued identity or touching recovery bytes. */
public final class WalletLaunchProvisioner {
    private WalletLaunchProvisioner() {}

    public static void provision(CardIo io, String url, UUID newId, long now) throws Exception {
        byte[] launchFile = Arrays.copyOf(WalletLaunchNdef.encode(url), Ntag424Dna.NDEF_FILE_SIZE);
        byte[] before = io.readPrivateFile();
        if (before.length != Ntag424Dna.RECOVERY_FILE_SIZE) throw new IOException("Incomplete private file");
        SecureCardIdentityStore.Record record = SecureCardIdentityStore.decode(Arrays.copyOfRange(
            before, SecureCardIdentityStore.OFFSET, SecureCardIdentityStore.OFFSET + SecureCardIdentityStore.ENCODED_BYTES));
        SecureCardPayload.CardIdentity identity;
        if (record != null) {
            // A previous attempt may have stopped before publishing NDEF. Keep its protected identity.
            identity = new SecureCardPayload.CardIdentity(record.getCardInstanceId(), record.getKeyVersion(),
                record.getIssuedAt(), url);
        } else {
            byte[] legacy = io.readLegacyIdentity(); // transport/auth failures must never assign a new identity
            if (allZero(legacy) && allZero(before)) {
                identity = new SecureCardPayload.CardIdentity(newId, 1, now, url);
            } else {
                SecureCardPayload.CardIdentity old = SecureCardPayload.decodeV2(legacy);
                identity = new SecureCardPayload.CardIdentity(old.getCardInstanceId(), old.getKeyVersion(),
                    old.getIssuedAt(), url);
            }
        }
        byte[] privateRecord = SecureCardIdentityStore.encode(identity);
        byte[] expectedPrivate = before.clone();
        System.arraycopy(privateRecord, 0, expectedPrivate, SecureCardIdentityStore.OFFSET, privateRecord.length);

        // Keep File 02 unreadable while replacing every byte, including the old private payload tail.
        io.setLaunchPublic(false);
        io.writeIdentity(privateRecord);
        requireEqual(expectedPrivate, io.readPrivateFile());
        io.writeLockedLaunchFile(launchFile);
        requireEqual(launchFile, io.readLockedLaunchFile());
        // Check all recovery bytes before exposing the new public marker.
        requireEqual(expectedPrivate, io.readPrivateFile());
        io.setLaunchPublic(true);
        requireEqual(launchFile, io.readPublicLaunchFile());
    }

    private static boolean allZero(byte[] data) {
        if (data == null || data.length == 0) return false;
        for (byte value : data) if (value != 0) return false;
        return true;
    }

    private static void requireEqual(byte[] expected, byte[] actual) throws IOException {
        if (!Arrays.equals(expected, actual)) throw new IOException("VERIFY_FAILED");
    }

    /** Each operation uses its own selected application/authentication session. */
    public interface CardIo {
        byte[] readPrivateFile() throws Exception;
        byte[] readLegacyIdentity() throws Exception;
        void setLaunchPublic(boolean enabled) throws Exception;
        void writeIdentity(byte[] data) throws Exception;
        void writeLockedLaunchFile(byte[] data) throws Exception;
        byte[] readLockedLaunchFile() throws Exception;
        byte[] readPublicLaunchFile() throws Exception;
    }
}
