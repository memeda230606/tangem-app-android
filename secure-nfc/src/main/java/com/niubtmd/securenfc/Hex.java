package com.niubtmd.securenfc;

public final class Hex {
    private Hex() {}

    public static byte[] decode(String value) {
        if (value == null || (value.length() & 1) != 0) {
            throw new IllegalArgumentException("Invalid hex string");
        }
        byte[] result = new byte[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            int high = Character.digit(value.charAt(index * 2), 16);
            int low = Character.digit(value.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex string");
            }
            result[index] = (byte) ((high << 4) | low);
        }
        return result;
    }

    public static String encode(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format(java.util.Locale.US, "%02X", item & 0xFF));
        }
        return result.toString();
    }
}
