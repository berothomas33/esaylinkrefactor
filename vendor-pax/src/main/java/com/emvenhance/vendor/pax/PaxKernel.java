package com.emvenhance.vendor.pax;

import com.pax.configservice.impl.EmvParamService;
import com.pax.emvlib.dpas.contact.ContactProcess;
import com.pax.emvlib.process.contactless.ClssProcess;
import com.pax.emvservice.emv.mag.MagCardService;

public final class PaxKernel {

    public final EmvParamService params = new EmvParamService();
    public ContactProcess contact = new ContactProcess();
    public final ClssProcess contactless = ClssProcess.getInstance();
    public final MagCardService mag = new MagCardService();

    public volatile boolean contactReady = true;
    public volatile boolean contactlessReady = true;
}
