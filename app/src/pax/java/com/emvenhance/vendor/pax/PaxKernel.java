package com.emvenhance.vendor.pax;

import com.pax.configservice.impl.EmvParamService;
import com.pax.emvservice.emv.contact.EmvContactService;
import com.pax.emvservice.emv.contactless.ContactlessService;
import com.pax.emvservice.emv.mag.MagCardService;

/**
 * Concrete PAX kernel objects owned by the PAX vendor layer.
 *
 * <p>Replaces WMRouter service lookup with direct composition. One instance is created by
 * {@link PaxTerminal} and shared with {@link PaxEmvBehavior}.
 */
public final class PaxKernel {

    public final EmvParamService params = new EmvParamService();
    public final EmvContactService contact = new EmvContactService();
    public final ContactlessService contactless = new ContactlessService();
    public final MagCardService mag = new MagCardService();
}
