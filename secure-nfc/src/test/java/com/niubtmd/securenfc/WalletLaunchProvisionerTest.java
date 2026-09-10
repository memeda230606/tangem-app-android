package com.niubtmd.securenfc;

import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;

public class WalletLaunchProvisionerTest {
    private static final String URL = "https://example.com/download";
    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test public void launchNdefHasExactMimeAndWalletPackageWithoutIdentity() {
        byte[] file = WalletLaunchNdef.encode(URL);
        assertEquals(URL, WalletLaunchNdef.decodeTargetUrl(Arrays.copyOf(file, 256)));
        assertEquals(0x92, file[2] & 0xff);
        String publicData = new String(file, StandardCharsets.UTF_8);
        assertTrue(publicData.contains(WalletLaunchNdef.MIME_TYPE));
        assertTrue(publicData.contains("android.com:pkg"));
        assertTrue(publicData.contains(WalletLaunchNdef.WALLET_PACKAGE));
        assertFalse(publicData.contains(ID.toString()));
        assertFalse(publicData.contains("NBSI"));
    }

    @Test public void rejectsAnotherApplicationAndTruncatedOrOversizedNdef() {
        byte[] file = WalletLaunchNdef.encode(URL);
        byte[] wrongPackage = file.clone();
        wrongPackage[wrongPackage.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> WalletLaunchNdef.decodeTargetUrl(wrongPackage));
        for (int size = 0; size < file.length; size++) {
            byte[] truncated = Arrays.copyOf(file, size);
            assertThrows(IllegalArgumentException.class, () -> WalletLaunchNdef.decodeTargetUrl(truncated));
        }
        assertThrows(IllegalArgumentException.class, () -> WalletLaunchNdef.encode(URL + "a".repeat(180)));
        assertThrows(IllegalArgumentException.class, () -> WalletLaunchNdef.encode("intent://other-app"));
    }

    @Test public void protectedRecordBindsUrlAndRejectsCorruptReservedBytes() throws Exception {
        byte[] bytes = SecureCardIdentityStore.encode(new SecureCardPayload.CardIdentity(ID, 1, 123, URL));
        assertEquals(62, bytes.length);
        assertTrue(SecureCardIdentityStore.OFFSET >= 63);
        assertTrue(SecureCardIdentityStore.OFFSET + bytes.length <= 128);
        SecureCardIdentityStore.Record record = SecureCardIdentityStore.decode(bytes);
        assertEquals(ID, record.verifyUrl(URL).getCardInstanceId());
        assertThrows(GeneralSecurityException.class, () -> record.verifyUrl(URL + "/changed"));
        assertNull(SecureCardIdentityStore.decode(new byte[62]));
        bytes[0] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> SecureCardIdentityStore.decode(bytes));
    }

    @Test public void migrationPreservesIdentityIssuedAtAndEveryRecoveryByte() throws Exception {
        MemoryCard card = legacyCard();
        byte[] before = card.privateFile.clone();
        WalletLaunchProvisioner.provision(card, URL, UUID.randomUUID(), 999);
        assertTrue(card.publicRead);
        assertArrayEquals(Arrays.copyOf(before, 64), Arrays.copyOf(card.privateFile, 64));
        assertArrayEquals(Arrays.copyOfRange(before, 126, 128), Arrays.copyOfRange(card.privateFile, 126, 128));
        SecureCardPayload.CardIdentity identity = stored(card).verifyUrl(URL);
        assertEquals(ID, identity.getCardInstanceId());
        assertEquals(2, identity.getKeyVersion());
        assertEquals(123, identity.getIssuedAt());
        byte[] launch = WalletLaunchNdef.encode(URL);
        assertArrayEquals(Arrays.copyOf(launch, 256), card.launchFile); // no old private tail exposed
    }

    @Test public void freshCardAndReissueKeepOneIdentity() throws Exception {
        MemoryCard card = new MemoryCard();
        WalletLaunchProvisioner.provision(card, URL, ID, 123);
        WalletLaunchProvisioner.provision(card, URL + "/new", UUID.randomUUID(), 999);
        assertEquals(ID, stored(card).verifyUrl(URL + "/new").getCardInstanceId());
        assertEquals(123, stored(card).getIssuedAt());
    }

    @Test public void failedLegacyReadDoesNotWriteOrReidentifyCard() {
        MemoryCard card = legacyCard();
        card.failRead = true;
        assertThrows(IOException.class, () -> WalletLaunchProvisioner.provision(card, URL, ID, 123));
        assertEquals(0, card.mutations);
    }

    @Test public void corruptIdentityAndUnknownPayloadFailBeforeAnyWrites() {
        MemoryCard card = legacyCard();
        card.privateFile[64] = 1;
        assertThrows(IllegalArgumentException.class, () -> WalletLaunchProvisioner.provision(card, URL, ID, 123));
        assertEquals(0, card.mutations);
        card.privateFile[64] = 0;
        card.launchFile[0] = 1;
        assertThrows(IllegalArgumentException.class, () -> WalletLaunchProvisioner.provision(card, URL, ID, 123));
        assertEquals(0, card.mutations);
    }

    @Test public void failedWriteStaysPrivateAndRetryPreservesIdentity() throws Exception {
        MemoryCard card = legacyCard();
        card.failWrite = true;
        assertThrows(IOException.class, () -> WalletLaunchProvisioner.provision(card, URL, ID, 999));
        assertFalse(card.publicRead);
        card.failWrite = false;
        WalletLaunchProvisioner.provision(card, URL, UUID.randomUUID(), 1000);
        assertTrue(card.publicRead);
        assertEquals(ID, stored(card).getCardInstanceId());
    }

    @Test public void verificationFailureNeverPublishesPrivateTail() {
        MemoryCard card = legacyCard();
        card.corruptWrite = true;
        assertThrows(IOException.class, () -> WalletLaunchProvisioner.provision(card, URL, ID, 123));
        assertFalse(card.publicRead);
    }

    private static SecureCardIdentityStore.Record stored(MemoryCard card) {
        return SecureCardIdentityStore.decode(Arrays.copyOfRange(card.privateFile, 64, 126));
    }

    private static MemoryCard legacyCard() {
        MemoryCard card = new MemoryCard();
        Arrays.fill(card.launchFile, (byte) 0x7f); // ensure old private tail is scrubbed
        byte[] legacy = SecureCardPayload.encodeV2(ID, 2, 123, URL);
        System.arraycopy(legacy, 0, card.launchFile, 0, legacy.length);
        Arrays.fill(card.privateFile, 0, 63, (byte) 0x42); // recovery data sentinel
        card.privateFile[63] = 0x23;
        card.privateFile[126] = 0x24;
        return card;
    }

    private static final class MemoryCard implements WalletLaunchProvisioner.CardIo {
        byte[] launchFile = new byte[256];
        byte[] privateFile = new byte[128];
        boolean publicRead, failRead, failWrite, corruptWrite;
        int mutations;
        public byte[] readPrivateFile() { return privateFile.clone(); }
        public byte[] readLegacyIdentity() throws IOException {
            if (failRead) throw new IOException("Tag lost");
            return Arrays.copyOf(launchFile, SecureCardPayload.MAX_ENCODED_BYTES);
        }
        public void setLaunchPublic(boolean enabled) { mutations++; publicRead = enabled; }
        public void writeIdentity(byte[] bytes) { mutations++; System.arraycopy(bytes, 0, privateFile, 64, bytes.length); }
        public void writeLockedLaunchFile(byte[] bytes) throws IOException {
            assertFalse(publicRead);
            mutations++;
            if (failWrite) throw new IOException("Tag lost");
            launchFile = bytes.clone();
            if (corruptWrite) launchFile[255] ^= 1;
        }
        public byte[] readLockedLaunchFile() { assertFalse(publicRead); return launchFile.clone(); }
        public byte[] readPublicLaunchFile() { assertTrue(publicRead); return launchFile.clone(); }
    }
}
