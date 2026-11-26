package com.emvenhance.network.crypto;

import androidx.annotation.Nullable;
import com.emvenhance.core.host.TerminalKeys;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * The RKI envelope: AES-CBC with a key that stays in the PED (TDK for sales, APP_TDK for the
 * onboarding request), as card acceptance's {@code EncryptorHelper.encryptDataTdk /
 * decryptDataTdk}: a random 16-byte IV in front, PKCS#7 padding, all hex.
 */
public final class PedEnvelopeCrypto {

    private static final int BLOCK = 16;

    /** Set once at start-up by the vendor (PAX: {@code PaxTerminalKeys}); null = no PED keys. */
    @Nullable
    private static TerminalKeys terminalKeys;

    private PedEnvelopeCrypto() {
    }

    public static void useKeys(@Nullable TerminalKeys keys) {
        terminalKeys = keys;
    }

    /** The vendor's PED keys; fails closed when the vendor has none. */
    public static TerminalKeys keys() {
        if (terminalKeys == null) {
            throw new IllegalStateException("No PED keys on this terminal — RKI needs the vendor's TerminalKeys");
        }
        return terminalKeys;
    }

    public static String encrypt(TerminalKeys.Key key, String plainText) {
        byte[] iv = new byte[BLOCK];
        new SecureRandom().nextBytes(iv);
        byte[] cipherText = keys().encrypt(key, iv, pad(plainText.getBytes(StandardCharsets.UTF_8)));
        return Hex.encode(iv) + Hex.encode(cipherText);
    }

    public static String decrypt(TerminalKeys.Key key, String hexEnvelope) {
        if (hexEnvelope.length() < 2 * 2 * BLOCK || hexEnvelope.length() % (2 * BLOCK) != 0) {
            throw new IllegalArgumentException("Not an IV + AES envelope: " + hexEnvelope.length() + " hex chars");
        }
        byte[] iv = Hex.decode(hexEnvelope.substring(0, 2 * BLOCK));
        byte[] cipherText = Hex.decode(hexEnvelope.substring(2 * BLOCK));
        return new String(unpad(keys().decrypt(key, iv, cipherText)), StandardCharsets.UTF_8);
    }

    static byte[] pad(byte[] data) {
        int padding = BLOCK - data.length % BLOCK;
        byte[] out = Arrays.copyOf(data, data.length + padding);
        Arrays.fill(out, data.length, out.length, (byte) padding);
        return out;
    }

    static byte[] unpad(byte[] data) {
        int padding = data.length == 0 ? 0 : data[data.length - 1] & 0xFF;
        if (padding < 1 || padding > BLOCK || padding > data.length) {
            throw new IllegalArgumentException("Bad PKCS#7 padding — wrong key?");
        }
        for (int i = data.length - padding; i < data.length; i++) {
            if ((data[i] & 0xFF) != padding) {
                throw new IllegalArgumentException("Bad PKCS#7 padding — wrong key?");
            }
        }
        return Arrays.copyOf(data, data.length - padding);
    }
}
