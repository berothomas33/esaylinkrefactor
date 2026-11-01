package com.emvenhance.vendor.pax;

import com.emvenhance.emvflow.device.EmvDeviceImpl;
import com.emvenhance.network.crypto.RsaPublicKeyEncryptor;
import com.emvenhance.settings.PinBypassSettings;
import com.emvenhance.vendor.pax.PaxKernelSession.PinRequest;
import com.pax.bizlib.ped.PedHelper;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IPed;
import com.pax.dal.entity.EAesCheckMode;
import com.pax.dal.entity.EPedKeyType;
import com.pax.dal.exceptions.EPedDevException;
import com.pax.dal.exceptions.PedDevException;
import com.pax.emvbase.constant.EmvConstant;
import com.pax.emvservice.emv.pin.PinService;
import com.pax.emvservice.export.exceptions.PinException;
import com.pax.poslib.utils.PosDeviceUtils;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.function.Supplier;

/**
 * The PIN the kernel asks for ({@code PaxEmvBehavior.onCardHolderPwd}), collected by the PIN
 * step's method: the PIN pad, the online PIN key and its RSA wrap for the host, the PIN bypass
 * rules. What the cardholder enters ends in the attempt's {@link PaxKernelSession} (the PIN block,
 * the wrapped key, the offline pad on screen); the step itself — CARDHOLDER_VERIFIED, askStep,
 * the answer — stays in {@code PaxEmvBehavior}.
 *
 * <p>The host's public key is handed in ({@link #hostPublicKey}, from onboarding, by
 * {@code PaxTerminal}): the EMV side never reads the host's state itself.
 */
final class PaxPinEntry {

    private static final String TAG = "PaxPinEntry";

    /**
     * AES-128 key length — see {@link #provisionOnlinePinKey} ({@code EPedKeyType.AES_TPK}, not
     * the 3DES-family plain {@code TPK}).
     */
    private static final int ONLINE_PIN_KEY_LENGTH_BYTES = 16;

    private final PinBypassSettings pinBypass;
    /** The host's RSA public key from onboarding, or {@code null} before it completed. */
    private final Supplier<String> hostPublicKey;

    PaxPinEntry(PinBypassSettings pinBypass, Supplier<String> hostPublicKey) {
        this.pinBypass = pinBypass;
        this.hostPublicKey = hostPublicKey;
    }

    /** Whether the cardholder may skip this PIN: the card allows it and so does the terminal. */
    boolean bypassAllowed(PinRequest request) {
        return request.supportPinBypass && (request.online
                ? pinBypass.isOnlinePinBypassEnabled()
                : pinBypass.isOfflinePinBypassEnabled());
    }

    /** The terminal's online PIN bypass setting (contactless passes it as the card's own). */
    boolean onlineBypassEnabled() {
        return pinBypass.isOnlinePinBypassEnabled();
    }

    /**
     * Collects the PIN the kernel asked for, and answers it: offline — the secure pad shown, the
     * kernel verifies it with the card; online — a fresh PIN key, then the PIN block for the
     * host, kept in {@code tx}. {@code pan} is read only when the online PIN block needs it.
     */
    int enter(PinRequest request, PaxKernelSession tx, Supplier<String> pan) {
        boolean isOnlinePin = request.online;
        boolean supportPINByPass = request.supportPinBypass;
        int leftTimes = request.leftTimes;
        PinService pinService = new PinService();

        if (!isOnlinePin) {
            // Offline PIN never produces a PIN block for the host — nothing goes in encPinBlock.
            clearOnlinePin(tx);
            // Offline PIN is the kernel's job, not ours: once we say CONTACT_OK, it calls
            // EmvDeviceImpl#pedVerifyPlainPin / #pedVerifyCipherPin (the native IDevice
            // callback) directly, which is already fully wired to IPed#verifyPlainPin /
            // #verifyCipherPin — the real secure PIN pad, real verification against the ICC.
            // What's missing without the two lines below is only the on-screen feedback: PAX's
            // own demo (OfflinePinTask) always wires setInputPinListener + EmvDeviceImpl's
            // pinCallback and shows a dialog before returning CONTACT_OK, fire-and-forget —
            // the dialog is dismissed by whichever of PinCallback's terminal callbacks
            // EmvDeviceImpl invokes (onCancel/onTimeout/onError), or defensively in
            // finishContactStage() if none of those fire.
            if (tx.activePinPad != null) {
                tx.activePinPad.dismiss(); // the kernel asks again (wrong PIN): one pad on screen
            }
            tx.activePinPad = new PaxPinPad("Enter Offline PIN (" + leftTimes + " left)");
            tx.activePinPad.showForOfflinePin();
            pinService.setInputPinListener(tx.activePinPad);
            EmvDeviceImpl.getInstance().setPinCallback(tx.activePinPad);
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        // Online PIN never touches the card, so nothing in the native kernel collects it for
        // us — show the secure pad ourselves, matching PAX's own demo (OnlinePinTask): a dialog
        // shown and waited on via ConditionVariable before the blocking collect call, so the
        // key-event listener always has a live dialog to update. Uses PinService directly
        // (same object for both the listener and the actual collection) instead of duplicating
        // its already-correct getPinBlock(keyIndex, pinLenCsv, panBytes, mode, timeoutMs) call.
        //
        // Bypass needs the terminal setting, and for contact the selected AID too
        // (supportPINByPass); contactless passes the setting itself.
        boolean bypassAllowed = supportPINByPass && pinBypass.isOnlinePinBypassEnabled();
        tx.lastOnlinePinBlock = null;
        provisionOnlinePinKey(tx);

        PaxPinPad pad = new PaxPinPad(bypassAllowed
                ? "Enter Online PIN (Enter to skip)" : "Enter Online PIN");
        tx.activePinPad = pad;
        pad.showForOnlinePin();
        pinService.setInputPinListener(pad);
        try {
            IPed ped = PedHelper.getPed();
            ped.setIntervalTime(1, 1);
            String cardPan = pan.get();
            byte[] pinBlock = pinService.getEncryptedPinData(cardPan != null ? cardPan : "",
                    bypassAllowed, false);
            if (pinBlock == null || pinBlock.length == 0) {
                // Enter with no digits: the PIN pad only allows it when bypass is allowed.
                clearOnlinePin(tx);
                return bypassAllowed ? EmvConstant.ContactCallbackStatus.NO_PASSWORD
                        : EmvConstant.ContactCallbackStatus.USER_CANCEL;
            }
            tx.lastOnlinePinBlock = pinBlock;
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        } catch (PinException e) {
            // No PIN block, so no PIN key either — the host must not expect one.
            clearOnlinePin(tx);
            if (bypassAllowed && NO_PIN_INPUT.equals(e.getErrCode())) {
                return EmvConstant.ContactCallbackStatus.NO_PASSWORD;
            }
            LogUtils.e(TAG, "online PIN entry failed", e);
            return EmvConstant.ContactCallbackStatus.USER_CANCEL;
        } catch (PedDevException e) {
            LogUtils.e(TAG, "online PIN entry failed", e);
            clearOnlinePin(tx);
            return EmvConstant.ContactCallbackStatus.USER_CANCEL;
        } finally {
            pinService.setInputPinListener(null);
            pad.dismiss();
        }
    }

    /** {@code PinException#getErrCode} when the PIN pad's Enter was pressed with no digits. */
    private static final String NO_PIN_INPUT =
            String.valueOf(EPedDevException.PED_ERR_NO_PIN_INPUT.getErrCodeFromBasement());

    /**
     * PIN lengths the PIN pad accepts for offline PIN (the contact kernel's PCI mode). A {@code 0}
     * lets Enter with no digits through, which the kernel treats as a PIN bypass — only when
     * offline PIN bypass is enabled.
     */
    String offlinePinLengths() {
        String lengths = "4,5,6,7,8,9,10,11,12";
        return (pinBypass.isOfflinePinBypassEnabled() ? "0," + lengths : lengths) + "\0";
    }

    /** Forgets this transaction's online PIN block and PIN key (none collected / not online). */
    static void clearOnlinePin(PaxKernelSession tx) {
        tx.lastOnlinePinBlock = null;
        tx.lastOnlinePinKeyEncrypted = null;
    }

    /**
     * Generates a fresh random online PIN key (PEK) for this transaction, writes it to
     * {@link PosDeviceUtils#INDEX_AES_PEK} as an **AES** key so {@code getPinBlock} produces an
     * AES-encrypted PIN block (confirmed against the server team — it decrypts the PIN block with
     * AES, not 3DES), and RSA-wraps the same key value with the host's public key (from
     * onboarding, handed in by {@code PaxTerminal}: {@link #hostPublicKey}) so the host can
     * independently recover it. Runs every online-PIN transaction, not just once — a fresh key
     * per transaction, not a reused one, matching the old project's {@code preparePekKey()} being
     * called at the start of every sale.
     *
     * <p>Ported from the old project's {@code PaxUtils#writePekAES} — {@code EPedKeyType.AES_TPK}
     * (a distinct key type from the plain, 3DES-family {@code EPedKeyType.TPK}) written via
     * {@code IPed#writeAesKey}, erasing whatever's at that index first, wrapped under
     * {@code EPedKeyType.TLK} index 0 (not {@code TMK} — the AES key hierarchy uses a different
     * wrapping key type than the 3DES one). The key type written here must match the PIN-block
     * mode {@code PinService#getEncryptedPinData} reads with: the 3DES
     * {@code EPinBlockMode.ISO9564_0} call reads the 3DES {@code TPK} slot, not this
     * {@code AES_TPK}, so it uses the AES ISO 9564 format 4 mode (see {@code PinService}'s
     * {@code PIN_BLOCK_MODE_ISO9564_4_AES}).
     *
     * <p>That assumes a TLK is already present at index 0, true on PAX SDK demo/dev units out of
     * the box; a real deployment provisions its own TLK (see the onboarding online/TMK-provisioning
     * cycle, currently dormant — {@code OnboardingClient#runOnlineCycle} — which would need an AES
     * counterpart if it's ever turned on for this key hierarchy too). If the write fails (no TLK)
     * or no public key has been onboarded yet, this logs and leaves
     * {@link PaxKernelSession#lastOnlinePinKeyEncrypted} unset — online PIN entry itself still proceeds (matching
     * this method's predecessor's behavior), it just won't have a key the host can decrypt with.
     */
    private void provisionOnlinePinKey(PaxKernelSession tx) {
        tx.lastOnlinePinKeyEncrypted = null;

        byte[] pinKey = new byte[ONLINE_PIN_KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(pinKey);

        IPed ped = PedHelper.getPed();
        // Clear the slot before every PIN entry so nothing stale can be used. Both PIN key types
        // at this index are erased; an empty slot throws, which isn't a reason to skip the write.
        eraseKeyQuietly(ped, EPedKeyType.AES_TPK, PosDeviceUtils.INDEX_AES_PEK);
        eraseKeyQuietly(ped, EPedKeyType.TPK, PosDeviceUtils.INDEX_AES_PEK);
        try {
            ped.writeAesKey(EPedKeyType.TLK.getPedkeyType(), (byte) 0,
                    EPedKeyType.AES_TPK.getPedkeyType(), PosDeviceUtils.INDEX_AES_PEK, pinKey,
                    EAesCheckMode.KCV_NONE, null);
        } catch (PedDevException e) {
            LogUtils.e(TAG, "Online PIN key write failed — no TLK at index 0? Online PIN will "
                    + "keep failing 'Key does not exist' until a real TLK is injected.", e);
            return;
        }

        String hostPublicKey = this.hostPublicKey.get();
        if (hostPublicKey == null) {
            LogUtils.w(TAG, "No host public key on file (onboarding not completed?) — online PIN "
                    + "key written to the PED, but the host has no way to decrypt with it.");
            return;
        }
        try {
            tx.lastOnlinePinKeyEncrypted = RsaPublicKeyEncryptor.encryptToHex(hostPublicKey, pinKey);
        } catch (GeneralSecurityException e) {
            LogUtils.e(TAG, "Failed to RSA-encrypt the online PIN key for the host", e);
        }
    }

    private static void eraseKeyQuietly(IPed ped, EPedKeyType keyType, byte index) {
        try {
            ped.eraseKey(keyType.getPedkeyType(), index);
        } catch (PedDevException e) {
            LogUtils.i(TAG, "No " + keyType + " to erase at index " + index + ": " + e.getErrMsg());
        }
    }

}
