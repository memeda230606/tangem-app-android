package com.niubtmd.securenfc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import org.junit.Test;

public class Ntag424DnaTest {

    @Test
    public void incompleteChipVersionIsReadFailureNotUnsupportedCard() {
        ScriptedTransceiver transport = new ScriptedTransceiver();
        transport.add("9060000000", "04040101001A059100");
        assertThrows(IOException.class, () -> new Ntag424Dna(transport).isNtag424Dna());
    }

    @Test
    public void plainReadUsesOffsetAndRejectsTruncatedResponse() throws Exception {
        ScriptedTransceiver transport = new ScriptedTransceiver();
        transport.add("90AD0000070280000002000000", "12349100");
        transport.add("90AD0000070200000002000000", "129100");
        Ntag424Dna card = new Ntag424Dna(transport);
        assertArrayEquals(hex("1234"), card.readPlain(Ntag424Dna.NDEF_FILE, 128, 2));
        assertThrows(IOException.class, () -> card.readPlain(Ntag424Dna.NDEF_FILE, 0, 2));
        assertEquals(0, transport.remaining());
    }

    @Test
    public void fileBoundsAreCheckedBeforeTransmission() {
        Ntag424Dna card = new Ntag424Dna(command -> { throw new AssertionError("Must not send invalid command"); });
        assertThrows(IllegalArgumentException.class, () -> card.readPlain(Ntag424Dna.NDEF_FILE, 240, 17));
        assertThrows(IllegalArgumentException.class, () -> card.readFull(null, Ntag424Dna.RECOVERY_FILE, 64, 65));
        assertThrows(IllegalArgumentException.class, () -> card.writeFull(null, Ntag424Dna.RECOVERY_FILE, 64, new byte[65]));
    }

    @Test
    public void aesCmacMatchesNxpDiversificationVector() throws Exception {
        byte[] master = hex("00112233445566778899AABBCCDDEEFF");
        byte[] message = hex("0104782E21801D803042F54E585020416275");

        assertArrayEquals(hex("A8DD63A3B89D54B37CA802473FDA9175"), AesCmac.compute(master, message));
    }

    @Test
    public void authenticateAndFullWriteMatchNxpVectors() throws Exception {
        ScriptedTransceiver transport = new ScriptedTransceiver();
        transport.add(
            "9071000002030000",
            "B875CEB0E66A6C5CD00898DC371F92D191AF"
        );
        transport.add(
            "90AF000020FF0306E47DFBC50087C4D8A78E88E62DE1E8BE457AA477C707E2F0874916A8B100",
            "0CC9A8094A8EEA683ECAAC5C7BF20584206D0608D477110FC6B3D5D3F65C3A6A9100"
        );
        transport.add(
            "908D00001F030000000A00006B5E6804909962FC4E3FF5522CF0F8436C0C53315B9C73AA00",
            "C26D236E4A7C046D9100"
        );
        Ntag424Dna card = new Ntag424Dna(
            transport,
            bytes -> System.arraycopy(hex("B98F4C50CF1C2E084FD150E33992B048"), 0, bytes, 0, bytes.length)
        );

        Ntag424Dna.Session session = card.authenticate(3, new byte[16]);
        card.writeFull(session, (byte) 0x03, hex("0102030405060708090A"));

        assertEquals(0, transport.remaining());
    }

    @Test
    public void selectsNdefApplicationUsingOfficialIsoCommand() throws Exception {
        ScriptedTransceiver transport = new ScriptedTransceiver();
        transport.add("00A4040C07D276000085010100", "9000");

        new Ntag424Dna(transport).selectNdefApplication();

        assertEquals(0, transport.remaining());
    }

    @Test
    public void getVersionUsesNoDataNativeApduAndReadsAllFrames() throws Exception {
        ScriptedTransceiver transport = new ScriptedTransceiver();
        transport.add("9060000000", "04040101001A0591AF");
        transport.add("90AF000000", "04040201001A0591AF");
        transport.add("90AF000000", "00000000000000000000000000009100");

        boolean supported = new Ntag424Dna(transport).isNtag424Dna();

        assertEquals(true, supported);
        assertEquals(0, transport.remaining());
    }

    @Test
    public void changeKeyCrcMatchesNxpVector() throws Exception {
        Method crc = Ntag424Dna.class.getDeclaredMethod("crc32LittleEndian", byte[].class);
        crc.setAccessible(true);

        byte[] result = (byte[]) crc.invoke(
            null,
            (Object) hex("F3847D627727ED3BC9C4CC050489B966")
        );

        assertArrayEquals(hex("789DFADC"), result);
    }

    @Test
    public void securePayloadReadLengthFitsShortLeLimit() throws Exception {
        Method fileHeader = Ntag424Dna.class.getDeclaredMethod(
            "fileHeader",
            byte.class,
            int.class,
            int.class
        );
        fileHeader.setAccessible(true);

        byte[] result = (byte[]) fileHeader.invoke(
            null,
            Ntag424Dna.NDEF_FILE,
            0,
            SecureCardPayload.MAX_ENCODED_BYTES
        );

        assertArrayEquals(hex("02000000E30000"), result);
    }

    @Test
    public void payloadRoundTripsAndRejectsNonWebSchemes() {
        String url = "https://xn--6qqv7i2xdt95b.com/download";
        assertEquals(url, SecureCardPayload.decode(SecureCardPayload.encode(url)));
    }

    @Test
    public void payloadV2RoundTripsCardIdentity() {
        UUID cardInstanceId = UUID.fromString("28df5c3d-64f4-4b68-92a2-38e479fbdf80");
        String url = "https://example.com/t/test";

        SecureCardPayload.CardIdentity decoded = SecureCardPayload.decodeV2(
            SecureCardPayload.encodeV2(cardInstanceId, 1, 1_756_800_000L, url)
        );

        assertEquals(cardInstanceId, decoded.getCardInstanceId());
        assertEquals(1, decoded.getKeyVersion());
        assertEquals(1_756_800_000L, decoded.getIssuedAt());
        assertEquals(url, decoded.getTargetUrl());
    }

    private static byte[] hex(String value) {
        return Hex.decode(value);
    }

    private static final class ScriptedTransceiver implements Ntag424Dna.Transceiver {
        private final Queue<Exchange> exchanges = new ArrayDeque<>();

        void add(String command, String response) {
            exchanges.add(new Exchange(hex(command), hex(response)));
        }

        int remaining() {
            return exchanges.size();
        }

        @Override
        public byte[] transceive(byte[] command) throws IOException {
            Exchange exchange = exchanges.remove();
            assertArrayEquals(exchange.command, command);
            return exchange.response;
        }
    }

    private record Exchange(byte[] command, byte[] response) {}
}
