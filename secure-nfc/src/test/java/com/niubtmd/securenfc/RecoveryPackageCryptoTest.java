package com.niubtmd.securenfc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;
import org.junit.Test;

public class RecoveryPackageCryptoTest {
    private static final String ROOT_HASH =
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void allSharePairsDecryptAuthenticatedPayload() throws Exception {
        byte[] plaintext = "TESTNET opaque wallet recovery material".getBytes(StandardCharsets.UTF_8);
        RecoveryPackageCrypto.CreatedPackage created = RecoveryPackageCrypto.create(
            plaintext,
            UUID.fromString("fd9b7406-8e99-47d9-96cb-03f138090f7d"),
            ROOT_HASH,
            new SecureRandom()
        );

        assertArrayEquals(plaintext, RecoveryPackageCrypto.decrypt(created, created.getCustomerShare(), created.getCardShare()));
        assertArrayEquals(plaintext, RecoveryPackageCrypto.decrypt(created, created.getCustomerShare(), created.getServerShare()));
        assertArrayEquals(plaintext, RecoveryPackageCrypto.decrypt(created, created.getCardShare(), created.getServerShare()));
        assertNotEquals(created.getCustomerShareFingerprint(), created.getCardShareFingerprint());
        assertEquals(created.getPayloadSha256(), RecoveryPackageCrypto.fingerprint(created.getCiphertext()));
    }

    @Test
    public void rejectsCiphertextTampering() throws Exception {
        RecoveryPackageCrypto.CreatedPackage created = RecoveryPackageCrypto.create(
            new byte[]{1, 2, 3}, UUID.randomUUID(), ROOT_HASH, new SecureRandom()
        );
        byte[] damaged = created.getCiphertext();
        damaged[0] ^= 1;
        RecoveryPackageCrypto.EncryptedPackage packageWithDamage = new RecoveryPackageCrypto.EncryptedPackage(
            created.getRecoverySetId(), created.getWalletId(), created.getRootPublicKeyHash(),
            damaged, created.getNonce(), created.getAad()
        );
        try {
            RecoveryPackageCrypto.decrypt(packageWithDamage, created.getCustomerShare(), created.getCardShare());
            fail("Tampered ciphertext should be rejected");
        } catch (GeneralSecurityException expected) {
            // Authenticated encryption must reject any change.
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsContextSubstitution() {
        UUID recoverySetId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        new RecoveryPackageCrypto.EncryptedPackage(
            recoverySetId,
            walletId,
            ROOT_HASH,
            new byte[17],
            new byte[12],
            RecoveryPackageCrypto.payloadAad(recoverySetId, UUID.randomUUID(), ROOT_HASH)
        );
    }
}
