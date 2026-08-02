package com.emvenhance.vendor;

import com.emvenhance.core.EmvBehavior;
import com.emvenhance.emvflow.EmvPreProcessFacade;
import com.emvenhance.emvflow.contact.ContactEmvRunner;
import com.emvenhance.emvflow.contactless.ContactlessEmvRunner;
import com.pax.bizentity.entity.SearchMode;
import com.pax.commonlib.utils.LogUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Real PAX kernel, driven through the :emvflow runners.
 *
 * <p>The runners report EMV steps straight to {@link Callback}, so there is nothing to
 * translate here.
 */
public class PaxEmvBehavior implements EmvBehavior {

    private static final String TAG = "PaxEmvBehavior";

    @Override
    public boolean prepare(String procCode, long amountMinor, boolean contact,
            boolean contactless) {
        byte requested = 0;
        if (contact) {
            requested |= SearchMode.INSERT;
        }
        if (contactless) {
            requested |= SearchMode.INTERNAL_WAVE;
        }
        try {
            byte adjusted = new EmvPreProcessFacade(
                    procCode, amountMinor, timestamp(), 0, requested).start();
            if (adjusted == 0) {
                LogUtils.e(TAG, "preTransProcess disabled every search mode");
                return false;
            }
            return true;
        } catch (Exception e) {
            LogUtils.e(TAG, "preTransProcess failed", e);
            return false;
        }
    }

    @Override
    public void startContact(Callback callback) {
        new ContactEmvRunner().start(callback);
    }

    @Override
    public void startContactless(Callback callback) {
        new ContactlessEmvRunner().start(callback);
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
    }
}
