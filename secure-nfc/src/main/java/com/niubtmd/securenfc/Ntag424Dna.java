package com.niubtmd.securenfc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Minimal NTAG 424 DNA AES secure-messaging implementation based on NXP AN12196. */
public final class Ntag424Dna {
    public static final byte NDEF_FILE = 0x02;
    public static final byte COMMUNICATION_FULL = 0x03;
    public static final byte[] NDEF_SECURE_ACCESS_RIGHTS = new byte[]{(byte) 0xF0, 0x12};
    public static final byte[] DEFAULT_KEY = new byte[16];

    private static final byte STATUS_PREFIX = (byte) 0x91;
    private static final byte STATUS_OK = 0x00;
    private static final byte STATUS_ADDITIONAL_FRAME = (byte) 0xAF;
    private static final int MAC_LENGTH = 8;

    private final Transceiver transceiver;
    private final RandomSource randomSource;

    public Ntag424Dna(Transceiver transceiver) {
        this(transceiver, bytes -> new SecureRandom().nextBytes(bytes));
    }

    Ntag424Dna(Transceiver transceiver, RandomSource randomSource) {
        this.transceiver = transceiver;
        this.randomSource = randomSource;
    }

    /** Selects the NFC Forum NDEF application before using native application commands. */
    public void selectNdefApplication() throws IOException {
        byte[] response = transceiver.transceive(
            Hex.decode("00A4040C07D276000085010100")
        );
        if (response == null || response.length != 2 ||
            response[0] != (byte) 0x90 || response[1] != 0x00) {
            throw new IOException("Selecting the NDEF application failed");
        }
    }

    public boolean isNtag424Dna() throws IOException {
        byte[] version = readChained((byte) 0x60);
        return version.length >= 14 && (version[0] & 0xFF) == 0x04 &&
            (version[1] & 0xFF) == 0x04 && (version[7] & 0xFF) == 0x04 &&
            (version[8] & 0xFF) == 0x04 && (version[9] & 0xFF) == 0x02;
    }

    public Session authenticate(int keyNumber, byte[] key) throws IOException, GeneralSecurityException {
        validateKey(key);
        byte[] first = transceiver.transceive(wrap((byte) 0x71, new byte[]{(byte) keyNumber, 0x00}));
        Response firstResponse = parseResponse(first);
        if (firstResponse.status != STATUS_ADDITIONAL_FRAME || firstResponse.data.length != 16) {
            throw new CardException("Authentication part 1 failed", firstResponse.status);
        }

        byte[] rndB = aesCbc(key, new byte[16], firstResponse.data, Cipher.DECRYPT_MODE);
        byte[] rndA = new byte[16];
        randomSource.nextBytes(rndA);
        byte[] challenge = concat(rndA, rotateLeft(rndB));
        byte[] encryptedChallenge = aesCbc(key, new byte[16], challenge, Cipher.ENCRYPT_MODE);

        Response secondResponse = parseResponse(
            transceiver.transceive(wrap((byte) 0xAF, encryptedChallenge))
        );
        if (secondResponse.status != STATUS_OK || secondResponse.data.length != 32) {
            throw new CardException("Authentication part 2 failed", secondResponse.status);
        }
        byte[] result = aesCbc(key, new byte[16], secondResponse.data, Cipher.DECRYPT_MODE);
        byte[] transactionId = Arrays.copyOfRange(result, 0, 4);
        byte[] rotatedRndA = Arrays.copyOfRange(result, 4, 20);
        if (!Arrays.equals(rndA, rotateRight(rotatedRndA))) {
            throw new GeneralSecurityException("Card authentication proof mismatch");
        }

        byte[] context = new byte[26];
        System.arraycopy(rndA, 0, context, 0, 2);
        for (int index = 0; index < 6; index++) {
            context[2 + index] = (byte) (rndA[2 + index] ^ rndB[index]);
        }
        System.arraycopy(rndB, 6, context, 8, 10);
        System.arraycopy(rndA, 8, context, 18, 8);
        byte[] encVector = concat(new byte[]{(byte) 0xA5, 0x5A, 0x00, 0x01, 0x00, (byte) 0x80}, context);
        byte[] macVector = concat(new byte[]{0x5A, (byte) 0xA5, 0x00, 0x01, 0x00, (byte) 0x80}, context);
        return new Session(
            keyNumber,
            transactionId,
            AesCmac.compute(key, encVector),
            AesCmac.compute(key, macVector)
        );
    }

    public void writePlain(byte fileNumber, byte[] data) throws IOException {
        if (data.length == 0 || data.length > 240) throw new IllegalArgumentException("Invalid write length");
        byte[] header = fileHeader(fileNumber, 0, data.length);
        Response response = parseResponse(transceiver.transceive(wrap((byte) 0x8D, concat(header, data))));
        if (response.status != STATUS_OK) throw new CardException("Plain write failed", response.status);
    }

    public byte[] readFull(Session session, byte fileNumber) throws IOException, GeneralSecurityException {
        // A zero length requests the entire 256-byte NDEF file. With Full secure messaging,
        // the encrypted response and MAC exceed the card's short-Le 256-byte response limit.
        return secureCommand(
            session,
            (byte) 0xAD,
            fileHeader(fileNumber, 0, SecureCardPayload.MAX_ENCODED_BYTES),
            new byte[0],
            true,
            true
        );
    }

    public void writeFull(Session session, byte fileNumber, byte[] data)
        throws IOException, GeneralSecurityException {
        if (data.length == 0 || data.length > 220) throw new IllegalArgumentException("Invalid write length");
        secureCommand(session, (byte) 0x8D, fileHeader(fileNumber, 0, data.length), data, true, false);
    }

    public int getKeyVersion(Session session, int keyNumber) throws IOException, GeneralSecurityException {
        byte[] response = secureCommand(
            session,
            (byte) 0x64,
            new byte[]{(byte) keyNumber},
            new byte[0],
            false,
            true
        );
        if (response.length != 1) throw new IOException("Invalid key version response");
        return response[0] & 0xFF;
    }

    public void changeFileSettings(Session session, byte fileNumber, byte fileOption, byte[] accessRights)
        throws IOException, GeneralSecurityException {
        if (accessRights.length != 2) throw new IllegalArgumentException("Access rights must be two bytes");
        secureCommand(
            session,
            (byte) 0x5F,
            new byte[]{fileNumber},
            new byte[]{fileOption, accessRights[0], accessRights[1]},
            true,
            false
        );
    }

    public void changeKey(Session session, int keyNumber, byte[] oldKey, byte[] newKey, int newVersion)
        throws IOException, GeneralSecurityException {
        validateKey(oldKey);
        validateKey(newKey);
        ByteArrayOutputStream keyData = new ByteArrayOutputStream();
        if (keyNumber == session.authenticatedKeyNumber) {
            keyData.write(newKey, 0, newKey.length);
            keyData.write(newVersion);
        } else {
            byte[] difference = new byte[16];
            for (int index = 0; index < difference.length; index++) {
                difference[index] = (byte) (oldKey[index] ^ newKey[index]);
            }
            keyData.write(difference, 0, difference.length);
            keyData.write(newVersion);
            byte[] checksum = crc32LittleEndian(newKey);
            keyData.write(checksum, 0, checksum.length);
        }
        secureCommand(
            session,
            (byte) 0xC4,
            new byte[]{(byte) keyNumber},
            keyData.toByteArray(),
            true,
            false
        );
        if (keyNumber == session.authenticatedKeyNumber) session.valid = false;
    }

    private byte[] secureCommand(
        Session session,
        byte command,
        byte[] header,
        byte[] commandData,
        boolean encryptCommandData,
        boolean responseDataExpected
    ) throws IOException, GeneralSecurityException {
        if (!session.valid) throw new IllegalStateException("Authentication session is no longer valid");
        byte[] protectedData = commandData;
        if (encryptCommandData && commandData.length > 0) {
            protectedData = aesCbc(
                session.encKey,
                commandIv(session, false, session.commandCounter),
                pad(commandData),
                Cipher.ENCRYPT_MODE
            );
        }

        byte[] macInput = concat(
            new byte[]{command},
            littleEndian2(session.commandCounter),
            session.transactionId,
            header,
            protectedData
        );
        byte[] commandMac = truncateMac(AesCmac.compute(session.macKey, macInput));
        Response response = parseResponse(
            transceiver.transceive(wrap(command, concat(header, protectedData, commandMac)))
        );
        if (response.status != STATUS_OK) throw new CardException("Secure command failed", response.status);

        session.commandCounter++;
        if (response.data.length == 0) {
            if (responseDataExpected) throw new IOException("Missing protected response");
            return new byte[0];
        }
        if (response.data.length < MAC_LENGTH) throw new IOException("Protected response is too short");
        byte[] responsePayload = Arrays.copyOf(response.data, response.data.length - MAC_LENGTH);
        byte[] responseMac = Arrays.copyOfRange(response.data, response.data.length - MAC_LENGTH, response.data.length);
        byte[] expectedMac = truncateMac(AesCmac.compute(
            session.macKey,
            concat(
                new byte[]{response.status},
                littleEndian2(session.commandCounter),
                session.transactionId,
                responsePayload
            )
        ));
        if (!constantTimeEquals(responseMac, expectedMac)) {
            throw new GeneralSecurityException("Card response MAC mismatch");
        }
        if (!responseDataExpected) return new byte[0];
        if (encryptCommandData) {
            if (responsePayload.length == 0 || responsePayload.length % 16 != 0) {
                throw new IOException("Invalid encrypted response length");
            }
            return unpad(aesCbc(
                session.encKey,
                commandIv(session, true, session.commandCounter),
                responsePayload,
                Cipher.DECRYPT_MODE
            ));
        }
        return responsePayload;
    }

    private byte[] readChained(byte command) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte next = command;
        for (int frame = 0; frame < 8; frame++) {
            Response response = parseResponse(transceiver.transceive(wrap(next, new byte[0])));
            result.write(response.data, 0, response.data.length);
            if (response.status == STATUS_OK) return result.toByteArray();
            if (response.status != STATUS_ADDITIONAL_FRAME) {
                throw new CardException("GetVersion failed", response.status);
            }
            next = (byte) 0xAF;
        }
        throw new IOException("Too many chained response frames");
    }

    private static byte[] commandIv(Session session, boolean response, int counter)
        throws GeneralSecurityException {
        byte[] input = concat(
            response ? new byte[]{0x5A, (byte) 0xA5} : new byte[]{(byte) 0xA5, 0x5A},
            session.transactionId,
            littleEndian2(counter),
            new byte[8]
        );
        return aesEcb(session.encKey, input, Cipher.ENCRYPT_MODE);
    }

    private static byte[] wrap(byte command, byte[] data) {
        if (data.length > 255) throw new IllegalArgumentException("APDU data is too long");
        if (data.length == 0) {
            return new byte[]{(byte) 0x90, command, 0x00, 0x00, 0x00};
        }
        byte[] result = new byte[6 + data.length];
        result[0] = (byte) 0x90;
        result[1] = command;
        result[2] = 0x00;
        result[3] = 0x00;
        result[4] = (byte) data.length;
        System.arraycopy(data, 0, result, 5, data.length);
        result[result.length - 1] = 0x00;
        return result;
    }

    private static Response parseResponse(byte[] response) throws IOException {
        if (response == null || response.length < 2 || response[response.length - 2] != STATUS_PREFIX) {
            throw new IOException("Unexpected card response");
        }
        return new Response(
            Arrays.copyOf(response, response.length - 2),
            response[response.length - 1]
        );
    }

    private static byte[] fileHeader(byte fileNumber, int offset, int length) {
        return concat(new byte[]{fileNumber}, littleEndian3(offset), littleEndian3(length));
    }

    private static byte[] pad(byte[] input) {
        int length = ((input.length + 1 + 15) / 16) * 16;
        byte[] result = Arrays.copyOf(input, length);
        result[input.length] = (byte) 0x80;
        return result;
    }

    private static byte[] unpad(byte[] input) throws GeneralSecurityException {
        int marker = input.length - 1;
        while (marker >= 0 && input[marker] == 0x00) marker--;
        if (marker < 0 || input[marker] != (byte) 0x80) {
            throw new GeneralSecurityException("Invalid ISO 7816-4 padding");
        }
        return Arrays.copyOf(input, marker);
    }

    private static byte[] truncateMac(byte[] fullMac) {
        byte[] result = new byte[MAC_LENGTH];
        for (int index = 0; index < MAC_LENGTH; index++) result[index] = fullMac[index * 2 + 1];
        return result;
    }

    private static byte[] aesCbc(byte[] key, byte[] iv, byte[] data, int mode)
        throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(data);
    }

    private static byte[] aesEcb(byte[] key, byte[] data, int mode) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(data);
    }

    private static byte[] rotateLeft(byte[] value) {
        byte[] result = Arrays.copyOf(value, value.length);
        System.arraycopy(value, 1, result, 0, value.length - 1);
        result[result.length - 1] = value[0];
        return result;
    }

    private static byte[] rotateRight(byte[] value) {
        byte[] result = Arrays.copyOf(value, value.length);
        System.arraycopy(value, 0, result, 1, value.length - 1);
        result[0] = value[value.length - 1];
        return result;
    }

    private static byte[] crc32LittleEndian(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        long value = crc.getValue() ^ 0xFFFF_FFFFL;
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16), (byte) (value >>> 24)};
    }

    private static byte[] littleEndian2(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8)};
    }

    private static byte[] littleEndian3(int value) {
        return new byte[]{(byte) value, (byte) (value >>> 8), (byte) (value >>> 16)};
    }

    private static byte[] concat(byte[]... values) {
        int length = 0;
        for (byte[] value : values) length += value.length;
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private static boolean constantTimeEquals(byte[] left, byte[] right) {
        if (left.length != right.length) return false;
        int difference = 0;
        for (int index = 0; index < left.length; index++) difference |= left[index] ^ right[index];
        return difference == 0;
    }

    private static void validateKey(byte[] key) {
        if (key == null || key.length != 16) throw new IllegalArgumentException("AES key must be 16 bytes");
    }

    public interface Transceiver {
        byte[] transceive(byte[] command) throws IOException;
    }

    interface RandomSource {
        void nextBytes(byte[] bytes);
    }

    public static final class Session {
        private final int authenticatedKeyNumber;
        private final byte[] transactionId;
        private final byte[] encKey;
        private final byte[] macKey;
        private int commandCounter;
        private boolean valid = true;

        private Session(int authenticatedKeyNumber, byte[] transactionId, byte[] encKey, byte[] macKey) {
            this.authenticatedKeyNumber = authenticatedKeyNumber;
            this.transactionId = transactionId;
            this.encKey = encKey;
            this.macKey = macKey;
        }

        public int getAuthenticatedKeyNumber() {
            return authenticatedKeyNumber;
        }
    }

    public static final class CardException extends IOException {
        private final int status;

        CardException(String message, byte status) {
            super(message + " (91" + String.format(java.util.Locale.US, "%02X", status & 0xFF) + ")");
            this.status = status & 0xFF;
        }

        public int getStatus() {
            return status;
        }
    }

    private record Response(byte[] data, byte status) {}
}
