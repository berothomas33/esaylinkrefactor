package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.param.EmvParams;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.network.SaleCommunicationBehavior;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.settings.PinBypassSettings;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.configservice.impl.EmvParamService;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.poslib.model.ModelInfo;
import java.util.List;

public class PaxTerminal extends PosTerminal {

    private static final String TAG = "PaxTerminal";

    public PaxTerminal() {
        this(new PaxKernel(),
                new SaleCommunicationBehavior(BaseApplication.getAppContext(), ModelInfo.getInstance().getSN()),
                new PaxPrinter());
    }

    private PaxTerminal(PaxKernel kernel,
            CommunicationBehavior communication,
            PrinterBehavior printer) {
        super(new PaxEmvBehavior(kernel, pinEntry()), communication, printer);
    }

    private static PaxPinEntry pinEntry() {
        return new PaxPinEntry(new PinBypassSettings(BaseApplication.getAppContext()),
                () -> new OnboardingState(BaseApplication.getAppContext()).getPublicKey());
    }

    @Nullable
    EmvProcessParam kernelParams() {
        return EmvParamService.loaded();
    }

    @Override
    public String serialNumber() {
        return ModelInfo.getInstance().getSN();
    }

    @Override
    public String[] requiredPermissions() {
        return PaxHardwarePermissions.all();
    }

    @Override
    public List<String> countries() {
        return PaxSetup.countries();
    }

    @Override
    public String setCountry(String country) {
        return PaxSetup.setCountry(country);
    }

    @Override
    public EmvParams emvParams() {
        return new PaxEmvParams(this);
    }

    @Override
    protected void initializeVendor() {
        PaxHardwarePermissions.logGrantState();
        PaxSetup.ensureEmvConfigInitialized();
        LogUtils.i(TAG, "PAX terminal initialized");
    }
}
