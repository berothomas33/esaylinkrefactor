package com.emvenhance.core.host;

import androidx.annotation.Nullable;

/**
 * Encrypts what the terminal keeps at rest (the host's public key, sacs, challenge, service
 * account), as the old app did with its POS master key. PAX: an AES key in the PED at index 0x20
 * ({@code PaxMasterKey}). The network module only sees this interface.
 */
public interface MasterKey {

    EncryptorMessage encryptDataByMasterKey(String plainData);

    /** The plain text, or {@code null} when it cannot be decrypted. */
    @Nullable
    String decryptDataByMasterKey(EncryptorMessage message);

    /** The old app's EncryptorMessage: the encrypted hex and how many '@' were added. */
    final class EncryptorMessage {
        public final String encrypted;
        public final int addedPadding;

        public EncryptorMessage(String encrypted, int addedPadding) {
            this.encrypted = encrypted;
            this.addedPadding = addedPadding;
        }
    }
}
