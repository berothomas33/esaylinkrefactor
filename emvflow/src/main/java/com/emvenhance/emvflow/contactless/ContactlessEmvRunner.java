package com.emvenhance.emvflow.contactless;

import androidx.annotation.NonNull;
import com.emvenhance.core.EmvBehavior;
import com.emvenhance.core.EmvStep;
import com.emvenhance.emvflow.EmvFlowRuntime;
import com.emvenhance.emvflow.EmvStepProgress;
import com.emvenhance.emvflow.device.EmvDeviceImpl;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.entity.EPiccType;
import com.pax.emvservice.export.EmvServiceConstant;
import com.pax.emvservice.export.IEmvContactlessService;
import com.pax.jemv.device.DeviceManager;
import com.sankuai.waimai.router.Router;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Drives the PAX contactless kernel on RxJava's IO scheduler.
 *
 * <p>The kernel call is blocking and runs to completion; there is no cancellation hook.
 */
public class ContactlessEmvRunner {
    private static final String TAG = "ContactlessEmvRunner";

    public void start(@NonNull EmvBehavior.Callback callback) {
        Completable.fromAction(() -> run(callback))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> LogUtils.d(TAG, "contactless flow finished"),
                        t -> callback.onFailed("Unexpected error", t.getMessage()));
    }

    private void run(@NonNull EmvBehavior.Callback callback) {
        EmvStepProgress progress = new EmvStepProgress(callback);
        IEmvContactlessService emv = null;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            emv = Router.getService(
                    IEmvContactlessService.class, EmvServiceConstant.EMVSERVICE_CONTACTLESS);
            if (emv == null) {
                callback.onFailed("EMV service missing",
                        "Router returned null IEmvContactlessService");
                return;
            }
            progress.advanceTo(EmvStep.SEARCH_CARD, "card tapped");
            LogUtils.d(TAG, "============ Start Contactless EMV ============");
            int ret = emv.startTransProcess(new SimpleContactlessCallback(emv, callback, progress));
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } finally {
            closeReaders();
            checkResult(emv, callback, progress);
        }
    }

    private void checkResult(IEmvContactlessService emv, @NonNull EmvBehavior.Callback callback,
            @NonNull EmvStepProgress progress) {
        if (emv == null) {
            return;
        }
        try {
            emv.checkClsResult(new SimpleContactlessResultListener(callback, progress));
        } catch (Exception e) {
            LogUtils.e(TAG, "checkClsResult error", e);
        }
    }

    private void closeReaders() {
        try {
            if (EmvFlowRuntime.getDal() != null) {
                EmvFlowRuntime.getDal().getMag().close();
                EmvFlowRuntime.getDal().getIcc().close((byte) 0);
                EmvFlowRuntime.getDal().getPicc(EPiccType.INTERNAL).close();
                EmvFlowRuntime.getDal().getPicc(EPiccType.EXTERNAL).close();
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "close readers failed", e);
        }
    }
}
