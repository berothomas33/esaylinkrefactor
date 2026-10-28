package com.emvenhance.vendor.ingenico;

import android.util.Log;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.HostDefaults;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.terminal.PosTerminal;

public class IngenicoTerminal extends PosTerminal {

    private static final String TAG = "IngenicoTerminal";

    public IngenicoTerminal() {
        this(HostDefaults.approveAlways(), HostDefaults.logPrinter(TAG));
    }

    private IngenicoTerminal(CommunicationBehavior communication, PrinterBehavior printer) {
        super(new IngenicoEmvBehavior(), communication, printer);
    }

    @Override
    protected void initializeVendor() {
        Log.w(TAG, "Ingenico SDK not attached — stub card search");
    }
}
