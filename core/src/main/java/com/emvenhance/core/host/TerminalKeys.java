package com.emvenhance.core.host;

import androidx.annotation.Nullable;

/**
 * The RKI keys in the terminal's PED, as card acceptance's {@code develop_rki_pilot} keeps them
 * ({@code PaxUtils}, {@code MomknPayScenario}). Each key has one slot; every key but the KEK
 * arrives as a TR-31 key block wrapped by its parent, so the clear key never reaches the app.
 *
 * <pre>
 * KEK      AES_TMK 2   made by the app, written in the clear (pilot only: stands in for RKI)
 * KBPK     AES_TMK 1   TR-31 under the KEK   — the host calls it "MAK"
 * APP_TDK  AES TDK 5   TR-31 under the KBPK  — encrypts the onboarding request
 * TDK      AES TDK 3   TR-31 under the KBPK  — encrypts every sale / refund / reversal
 * TPK      AES_TPK 4   TR-31 under the KBPK  — the online PIN block (ISO 9564 format 4)
 * </pre>
 *
 * The network module only sees this interface. A failed write or calculation throws
 * {@link IllegalStateException}: the caller fails closed.
 */
public interface TerminalKeys {

    enum Key { KEK, KBPK, APP_TDK, TDK, TPK }

    /** Writes the clear KEK into its slot (pilot only). */
    void writeClearKek(byte[] kek);

    /** Writes a TR-31 key block under its parent (KBPK under KEK, the rest under KBPK). */
    void writeKeyBlock(Key key, String tr31Block);

    /** The key's check value as upper-case hex, or {@code null} when the PED can't give one. */
    @Nullable
    String kcv(Key key);

    /** AES-CBC inside the PED with {@code key} (TDK or APP_TDK); {@code data} is block-aligned. */
    byte[] encrypt(Key key, byte[] iv, byte[] data);

    /** AES-CBC inside the PED with {@code key} (TDK or APP_TDK); {@code data} is block-aligned. */
    byte[] decrypt(Key key, byte[] iv, byte[] data);
}
