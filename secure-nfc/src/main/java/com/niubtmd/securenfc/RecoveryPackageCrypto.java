package com.niubtmd.securenfc;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts an opaque wallet recovery payload with a random wrapping key and splits only that key.
 * Callers must never send the plaintext payload or customer/card shares to the service.
 */
public final class RecoveryPackageCrypto {
    public static final int PROTOCOL_VERSION = 1;
    private static final int NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private RecoveryPackageCrypto() {}

    public static CreatedPackage create(
        byte[] recoveryPayload,
        UUID walletId,
        String rootPublicKeyHash,
        SecureRandom random
    ) throws GeneralSecurityException {
        if (recoveryPayload == null || recoveryPayload.length == 0) {
            throw new IllegalArgumentException("Recovery payload is required");
        }
        if (walletId == null) throw new IllegalArgumentException("Wallet id is required");
        validateFingerprint(rootPublicKeyHash, "Root public key hash");
        if (random == null) throw new IllegalArgumentException("Secure random is required");

        UUID recoverySetId = UUID.randomUUID();
        byte[] wrappingKey = new byte[RecoverySecretSharing.SECRET_BYTES];
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(wrappingKey);
        random.nextBytes(nonce);
        byte[] aad = payloadAad(recoverySetId, walletId, rootPublicKeyHash);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(wrappingKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            cipher.updateAAD(aad);
            byte[] ciphertext = cipher.doFinal(recoveryPayload);
            List<RecoverySecretSharing.Share> shares = RecoverySecretSharing.split(wrappingKey, recoverySetId, random);
            return new CreatedPackage(
                recoverySetId,
                walletId,
                rootPublicKeyHash.toLowerCase(java.util.Locale.US),
                ciphertext,
                nonce,
                aad,
                shares.get(0),
                shares.get(1),
                shares.get(2)
            );
        } finally {
            Arrays.fill(wrappingKey, (byte) 0);
        }
    }

    public static byte[] decrypt(EncryptedPackage encrypted, RecoverySecretSharing.Share first,
        RecoverySecretSharing.Share second) throws GeneralSecurityException {
        if (encrypted == null) throw new IllegalArgumentException("Encrypted package is required");
        byte[] wrappingKey = RecoverySecretSharing.combine(first, second);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(wrappingKey, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, encrypted.nonce)
            );
            cipher.updateAAD(encrypted.aad);
            return cipher.doFinal(encrypted.ciphertext);
        } catch (AEADBadTagException error) {
            throw new GeneralSecurityException("Recovery package authentication failed", error);
        } finally {
            Arrays.fill(wrappingKey, (byte) 0);
        }
    }

    public static byte[] payloadAad(UUID recoverySetId, UUID walletId, String rootPublicKeyHash) {
        if (recoverySetId == null || walletId == null) throw new IllegalArgumentException("Recovery context is required");
        validateFingerprint(rootPublicKeyHash, "Root public key hash");
        return String.join(
            "\n",
            "TANGEM_L0_RECOVERY_PAYLOAD_V1",
            recoverySetId.toString(),
            walletId.toString(),
            rootPublicKeyHash.toLowerCase(java.util.Locale.US)
        ).getBytes(StandardCharsets.UTF_8);
    }

    public static String fingerprint(byte[] value) {
        if (value == null) throw new IllegalArgumentException("Value is required");
        try {
            return Hex.encode(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void validateFingerprint(String value, String label) {
        if (value == null || !value.matches("(?i)^[a-f0-9]{64}$")) {
            throw new IllegalArgumentException(label + " must be a SHA-256 hex value");
        }
    }

    public static class EncryptedPackage {
        private final UUID recoverySetId;
        private final UUID walletId;
        private final String rootPublicKeyHash;
        private final byte[] ciphertext;
        private final byte[] nonce;
        private final byte[] aad;

        public EncryptedPackage(UUID recoverySetId, UUID walletId, String rootPublicKeyHash,
            byte[] ciphertext, byte[] nonce, byte[] aad) {
            if (recoverySetId == null || walletId == null) throw new IllegalArgumentException("Recovery context is required");
            validateFingerprint(rootPublicKeyHash, "Root public key hash");
            if (ciphertext == null || ciphertext.length < 17) throw new IllegalArgumentException("Ciphertext is invalid");
            if (nonce == null || nonce.length != NONCE_BYTES) throw new IllegalArgumentException("Nonce is invalid");
            byte[] expectedAad = payloadAad(recoverySetId, walletId, rootPublicKeyHash);
            if (aad == null || !MessageDigest.isEqual(expectedAad, aad)) {
                throw new IllegalArgumentException("Recovery package context is invalid");
            }
            this.recoverySetId = recoverySetId;
            this.walletId = walletId;
            this.rootPublicKeyHash = rootPublicKeyHash.toLowerCase(java.util.Locale.US);
            this.ciphertext = Arrays.copyOf(ciphertext, ciphertext.length);
            this.nonce = Arrays.copyOf(nonce, nonce.length);
            this.aad = Arrays.copyOf(aad, aad.length);
        }

        public UUID getRecoverySetId() { return recoverySetId; }
        public UUID getWalletId() { return walletId; }
        public String getRootPublicKeyHash() { return rootPublicKeyHash; }
        public byte[] getCiphertext() { return Arrays.copyOf(ciphertext, ciphertext.length); }
        public byte[] getNonce() { return Arrays.copyOf(nonce, nonce.length); }
        public byte[] getAad() { return Arrays.copyOf(aad, aad.length); }
        public String getPayloadSha256() { return fingerprint(ciphertext); }
    }

    public static final class CreatedPackage extends EncryptedPackage {
        private final RecoverySecretSharing.Share customerShare;
        private final RecoverySecretSharing.Share cardShare;
        private final RecoverySecretSharing.Share serverShare;

        private CreatedPackage(UUID recoverySetId, UUID walletId, String rootPublicKeyHash,
            byte[] ciphertext, byte[] nonce, byte[] aad, RecoverySecretSharing.Share customerShare,
            RecoverySecretSharing.Share cardShare, RecoverySecretSharing.Share serverShare) {
            super(recoverySetId, walletId, rootPublicKeyHash, ciphertext, nonce, aad);
            this.customerShare = customerShare;
            this.cardShare = cardShare;
            this.serverShare = serverShare;
        }

        public RecoverySecretSharing.Share getCustomerShare() { return copy(customerShare); }
        public RecoverySecretSharing.Share getCardShare() { return copy(cardShare); }
        public RecoverySecretSharing.Share getServerShare() { return copy(serverShare); }
        public String getCustomerRecoveryCode() { return customerShare.encodeRecoveryCode(); }
        public String getCustomerShareFingerprint() { return fingerprint(customerShare.getValue()); }
        public String getCardShareFingerprint() { return fingerprint(cardShare.getValue()); }
        public String getServerShareFingerprint() { return fingerprint(serverShare.getValue()); }

        private static RecoverySecretSharing.Share copy(RecoverySecretSharing.Share share) {
            return new RecoverySecretSharing.Share(share.getRecoverySetId(), share.getIndex(), share.getValue());
        }
    }
}
