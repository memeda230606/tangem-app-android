package com.niubtmd.securenfc;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

final class AesCmac {
    private static final int BLOCK_SIZE = 16;
    private static final byte RB = (byte) 0x87;

    private AesCmac() {}

    static byte[] compute(byte[] key, byte[] message) throws GeneralSecurityException {
        byte[] zero = new byte[BLOCK_SIZE];
        byte[] l = encryptBlock(key, zero);
        byte[] k1 = doubleBlock(l);
        byte[] k2 = doubleBlock(k1);

        int blocks = Math.max(1, (message.length + BLOCK_SIZE - 1) / BLOCK_SIZE);
        boolean complete = message.length > 0 && message.length % BLOCK_SIZE == 0;
        byte[] last = new byte[BLOCK_SIZE];
        int lastOffset = (blocks - 1) * BLOCK_SIZE;
        int remaining = message.length - lastOffset;
        if (remaining > 0) {
            System.arraycopy(message, lastOffset, last, 0, remaining);
        }
        if (complete) {
            xorInPlace(last, k1);
        } else {
            last[remaining] = (byte) 0x80;
            xorInPlace(last, k2);
        }

        byte[] state = new byte[BLOCK_SIZE];
        for (int block = 0; block < blocks - 1; block++) {
            byte[] current = Arrays.copyOfRange(message, block * BLOCK_SIZE, (block + 1) * BLOCK_SIZE);
            xorInPlace(current, state);
            state = encryptBlock(key, current);
        }
        xorInPlace(last, state);
        return encryptBlock(key, last);
    }

    private static byte[] encryptBlock(byte[] key, byte[] data) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(data);
    }

    private static byte[] doubleBlock(byte[] input) {
        byte[] result = new byte[input.length];
        int carry = 0;
        for (int index = input.length - 1; index >= 0; index--) {
            int value = input[index] & 0xFF;
            result[index] = (byte) ((value << 1) | carry);
            carry = (value >>> 7) & 1;
        }
        if ((input[0] & 0x80) != 0) {
            result[result.length - 1] ^= RB;
        }
        return result;
    }

    private static void xorInPlace(byte[] target, byte[] value) {
        for (int index = 0; index < target.length; index++) {
            target[index] ^= value[index];
        }
    }
}
