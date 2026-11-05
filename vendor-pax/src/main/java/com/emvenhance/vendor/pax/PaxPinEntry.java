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

final class PaxPinEntry {

    private static final String TAG = "PaxPinEntry";

    private static final int ONLINE_PIN_KEY_LENGTH_BYTES = 16;

    private final PinBypassSettings pinBypass;
    private final Supplier<String> hostPublicKey;

    PaxPinEntry(PinBypassSettings pinBypass, Supplier<String> hostPublicKey) {
        this.pinBypass = pinBypass;
        this.hostPublicKey = hostPublicKey;
    }

    boolean bypassAllowed(PinRequest request) {
        return request.supportPinBypass && (request.online
                ? pinBypass.isOnlinePinBypassEnabled()
                : pinBypass.isOfflinePinBypassEnabled());
    }

    boolean onlineBypassEnabled() {
        return pinBypass.isOnlinePinBypassEnabled();
    }

    int enter(PinRequest request, PaxKernelSession tx, Supplier<String> pan) {
        boolean isOnlinePin = request.online;
        boolean supportPINByPass = request.supportPinBypass;
        int leftTimes = request.leftTimes;
        PinService pinService = new PinService();

        if (!isOnlinePin) {
            clearOnlinePin(tx);
            if (tx.activePinPad != null) {
                tx.activePinPad.dismiss();
            }
            tx.activePinPad = new PaxPinPad("Enter Offline PIN (" + leftTimes + " left)");
            tx.activePinPad.showForOfflinePin();
            pinService.setInputPinListener(tx.activePinPad);
            EmvDeviceImpl.getInstance().setPinCallback(tx.activePinPad);
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        boolean bypassAllowed = supportPINByPass && pinBypass.isOnlinePinBypassEnabled();
        tx.lastOnlinePinBlock = null;
        ensureOnlinePinKey(tx);

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
                clearOnlinePin(tx);
                return bypassAllowed ? EmvConstant.ContactCallbackStatus.NO_PASSWORD
                        : EmvConstant.ContactCallbackStatus.USER_CANCEL;
            }
            tx.lastOnlinePinBlock = pinBlock;
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        } catch (PinException e) {
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

    private static final String NO_PIN_INPUT =
            String.valueOf(EPedDevException.PED_ERR_NO_PIN_INPUT.getErrCodeFromBasement());

    String offlinePinLengths() {
        String lengths = "4,5,6,7,8,9,10,11,12";
        return (pinBypass.isOfflinePinBypassEnabled() ? "0," + lengths : lengths) + "\0";
    }

    static void clearOnlinePin(PaxKernelSession tx) {
        tx.lastOnlinePinBlock = null;
        tx.lastOnlinePinKeyEncrypted = null;
    }

    /**
     * One online PIN key per attempt, made before its first online request: the key goes with
     * every exchange (the host needs it for a Single Tap PIN that comes later, with no exchange
     * of its own), and a PIN block entered after that is made under the same key.
     */
    void ensureOnlinePinKey(PaxKernelSession tx) {
        if (tx.lastOnlinePinKeyEncrypted == null) {
            provisionOnlinePinKey(tx);
        }
    }

    private void provisionOnlinePinKey(PaxKernelSession tx) {
        tx.lastOnlinePinKeyEncrypted = null;

        byte[] pinKey = new byte[ONLINE_PIN_KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(pinKey);

        IPed ped = PedHelper.getPed();
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
