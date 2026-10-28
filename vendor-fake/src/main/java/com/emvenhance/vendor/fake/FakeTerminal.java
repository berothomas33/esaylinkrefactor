package com.emvenhance.vendor.fake;

import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.HostDefaults;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.terminal.PosTerminal;

public class FakeTerminal extends PosTerminal {

    public FakeTerminal() {
        this(HostDefaults.approveAlways(), HostDefaults.logPrinter("FakePrinter"));
    }

    private FakeTerminal(CommunicationBehavior communication, PrinterBehavior printer) {
        super(new FakeEmvBehavior(), communication, printer);
    }
}
