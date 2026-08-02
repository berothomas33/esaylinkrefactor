package com.emvenhance.emvflow.contact;

import androidx.annotation.NonNull;
import com.emvenhance.core.EmvBehavior;
import com.emvenhance.core.EmvStep;
import com.emvenhance.emvflow.EmvFlowRuntime;
import com.emvenhance.emvflow.EmvStepProgress;
import com.emvenhance.emvflow.device.EmvDeviceImpl;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.entity.EPiccType;
import com.pax.emvservice.export.EmvServiceConstant;
import com.pax.emvservice.export.IEmvContactService;
import com.pax.jemv.device.DeviceManager;
import com.sankuai.waimai.router.Router;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Drives the PAX contact kernel on RxJava's IO scheduler.
 *
 * <p>The kernel call is blocking and runs to completion; there is no cancellation hook.
 */
public class ContactEmvRunner {
    private static final String TAG = "ContactEmvRunner";

    public void start(@NonNull EmvBehavior.Callback callback) {
        Completable.fromAction(() -> run(callback))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> LogUtils.d(TAG, "contact flow finished"),
                        t -> callback.onFailed("Unexpected error", t.getMessage()));
    }

    private void run(@NonNull EmvBehavior.Callback callback) {
        EmvStepProgress progress = new EmvStepProgress(callback);
        IEmvContactService emv = null;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            emv = Router.getService(
                    IEmvContactService.class, EmvServiceConstant.EMVSERVICE_CONTACT);
            if (emv == null) {
                callback.onFailed("EMV service missing", "Router returned null IEmvContactService");
                return;
            }
            progress.advanceTo(EmvStep.SEARCH_CARD, "card present");
            LogUtils.d(TAG, "============ Start Contact EMV ============");
            int ret = emv.startTransProcess(new SimpleContactCallback(emv, callback, progress));
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } finally {
            closeReaders();
            checkResult(emv, callback, progress);
        }
    }

    private void checkResult(IEmvContactService emv, @NonNull EmvBehavior.Callback callback,
            @NonNull EmvStepProgress progress) {
        if (emv == null) {
            return;
        }
        try {
            emv.checkContactResult(new SimpleContactResultListener(callback, progress));
        } catch (Exception e) {
            LogUtils.e(TAG, "checkContactResult error", e);
        }
    }

    private void closeReaders() {
        try {
            if (EmvFlowRuntime.getDal() != null) {
                EmvFlowRuntime.getDal().getMag().close();
                EmvFlowRuntime.getDal().getIcc().close((byte) 0);
                EmvFlowRuntime.getDal().getPicc(EPiccType.INTERNAL).close();
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "close readers failed", e);
        }
    }
}
