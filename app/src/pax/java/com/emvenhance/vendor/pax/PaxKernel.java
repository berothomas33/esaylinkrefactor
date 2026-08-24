package com.emvenhance.vendor.pax;

import com.pax.configservice.impl.EmvParamService;
import com.pax.emvlib.dpas.contact.ContactProcess;
import com.pax.emvservice.emv.contactless.ContactlessService;
import com.pax.emvservice.emv.mag.MagCardService;

/**
 * Concrete PAX kernel objects owned by the PAX vendor layer.
 *
 * <p>Replaces WMRouter service lookup with direct composition. One instance is created by
 * {@link PaxTerminal} and shared with {@link PaxEmvBehavior}.
 *
 * <p><b>Experiment branch:</b> {@code contact} is {@link ContactProcess} directly — no
 * {@code EmvContactService} layer in between. It's mutable (not {@code final}) because
 * {@link PaxEmvBehavior#prepareKernel} rebuilds it fresh (a new instance) at the start of every
 * transaction, the same "fresh per attempt" responsibility {@code EmvContactService} used to
 * own internally.
 */
public final class PaxKernel {

    public final EmvParamService params = new EmvParamService();
    public ContactProcess contact = new ContactProcess();
    public final ContactlessService contactless = new ContactlessService();
    public final MagCardService mag = new MagCardService();
}
