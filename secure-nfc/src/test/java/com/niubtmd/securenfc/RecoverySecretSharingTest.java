package com.niubtmd.securenfc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

public class RecoverySecretSharingTest {
    private final SecureRandom random = new SecureRandom();

    @Test
    public void everyPairRestoresRecoveryWrappingKey() {
        byte[] secret = new byte[RecoverySecretSharing.SECRET_BYTES];
        random.nextBytes(secret);
        List<RecoverySecretSharing.Share> shares = RecoverySecretSharing.split(secret, UUID.randomUUID(), random);

        assertArrayEquals(secret, RecoverySecretSharing.combine(shares.get(0), shares.get(1)));
        assertArrayEquals(secret, RecoverySecretSharing.combine(shares.get(0), shares.get(2)));
        assertArrayEquals(secret, RecoverySecretSharing.combine(shares.get(1), shares.get(2)));
        for (RecoverySecretSharing.Share share : shares) {
            assertFalse(Arrays.equals(secret, share.getValue()));
        }
    }

    @Test
    public void recoveryCodeRoundTripsAndRejectsTampering() {
        byte[] secret = new byte[RecoverySecretSharing.SECRET_BYTES];
        random.nextBytes(secret);
        List<RecoverySecretSharing.Share> shares = RecoverySecretSharing.split(secret, UUID.randomUUID(), random);
        RecoverySecretSharing.Share original = shares.get(0);
        String encoded = original.encodeRecoveryCode();
        RecoverySecretSharing.Share decoded = RecoverySecretSharing.Share.decodeRecoveryCode(encoded);

        assertArrayEquals(secret, RecoverySecretSharing.combine(
            decoded,
            shares.get(1)
        ));
        String tampered = encoded.substring(0, encoded.length() - 1) + (encoded.endsWith("0") ? "1" : "0");
        assertThrows(IllegalArgumentException.class, () -> RecoverySecretSharing.Share.decodeRecoveryCode(tampered));
    }

    @Test
    public void incompatibleSharesAreRejected() {
        byte[] secret = new byte[RecoverySecretSharing.SECRET_BYTES];
        random.nextBytes(secret);
        RecoverySecretSharing.Share first = RecoverySecretSharing.split(secret, UUID.randomUUID(), random).get(0);
        RecoverySecretSharing.Share otherSet = RecoverySecretSharing.split(secret, UUID.randomUUID(), random).get(1);

        assertThrows(IllegalArgumentException.class, () -> RecoverySecretSharing.combine(first, first));
        assertThrows(IllegalArgumentException.class, () -> RecoverySecretSharing.combine(first, otherSet));
    }

    @Test
    public void protectedCardRecordRoundTripsAndRejectsTampering() {
        byte[] secret = new byte[RecoverySecretSharing.SECRET_BYTES];
        random.nextBytes(secret);
        RecoverySecretSharing.Share cardShare = RecoverySecretSharing.split(secret, UUID.randomUUID(), random).get(1);
        byte[] encoded = RecoveryCardRecord.encode(cardShare);
        RecoverySecretSharing.Share decoded = RecoveryCardRecord.decode(encoded);

        assertArrayEquals(cardShare.getValue(), decoded.getValue());
        encoded[20] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> RecoveryCardRecord.decode(encoded));
    }
}
