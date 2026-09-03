package com.niubtmd.securenfc;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

public final class SecureCardKeys {
    public static final int ADMIN_KEY = 0;
    public static final int READ_KEY = 1;
    public static final int WRITE_KEY = 2;

    private static final byte[] SYSTEM_ID = "NBTMD424".getBytes(StandardCharsets.US_ASCII);

    private SecureCardKeys() {}

    public static byte[] derive(byte[] rootKey, byte[] uid, int keyNumber) throws GeneralSecurityException {
        if (rootKey.length != 16) throw new IllegalArgumentException("Root key must be 16 bytes");
        if (uid == null || uid.length != 7) throw new IllegalArgumentException("UID must be 7 bytes");
        if (keyNumber < 0 || keyNumber > 4) throw new IllegalArgumentException("Invalid key number");

        byte[] input = new byte[1 + uid.length + SYSTEM_ID.length + 1];
        input[0] = 0x01;
        System.arraycopy(uid, 0, input, 1, uid.length);
        System.arraycopy(SYSTEM_ID, 0, input, 1 + uid.length, SYSTEM_ID.length);
        input[input.length - 1] = (byte) keyNumber;
        return AesCmac.compute(rootKey, input);
    }

    public static byte[] rootForKey(int keyNumber, byte[] adminRoot, byte[] readRoot, byte[] writeRoot) {
        return switch (keyNumber) {
            case READ_KEY -> readRoot;
            case WRITE_KEY -> writeRoot;
            case ADMIN_KEY, 3, 4 -> adminRoot;
            default -> throw new IllegalArgumentException("Invalid key number");
        };
    }
}
