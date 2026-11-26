package com.emvenhance.vendor.pax;

import com.emvenhance.emvflow.device.EmvDeviceImpl;
import com.emvenhance.settings.PinBypassSettings;
import com.emvenhance.vendor.pax.PaxKernelSession.PinRequest;
import com.pax.bizlib.ped.PedHelper;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IPed;
import com.pax.dal.exceptions.EPedDevException;
import com.pax.dal.exceptions.PedDevException;
import com.pax.emvbase.constant.EmvConstant;
import com.pax.emvservice.emv.pin.PinService;
import com.pax.emvservice.export.exceptions.PinException;
import java.util.function.Supplier;

final class PaxPinEntry {

    private static final String TAG = "PaxPinEntry";

    private final PinBypassSettings pinBypass;

    PaxPinEntry(PinBypassSettings pinBypass) {
        this.pinBypass = pinBypass;
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

    /** The online PIN block is made under the RKI TPK (AES_TPK 4), injected at onboarding. */
    static void clearOnlinePin(PaxKernelSession tx) {
        tx.lastOnlinePinBlock = null;
    }
}
