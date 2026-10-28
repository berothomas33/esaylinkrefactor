package com.emvenhance.vendor.fake;

import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.HostDefaults;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.terminal.PosTerminal;

/**
 * Demo terminal, so the UI works without POS hardware: a host that always approves and a log
 * printer. The demo card search is {@link FakeEmvBehavior}'s.
 */
public class FakeTerminal extends PosTerminal {

    public FakeTerminal() {
        this(HostDefaults.approveAlways(), HostDefaults.logPrinter("FakePrinter"));
    }

    private FakeTerminal(CommunicationBehavior communication, PrinterBehavior printer) {
        super(new FakeEmvBehavior(), communication, printer);
    }
}
