package com.emvenhance.vendor.pax;

import com.pax.configservice.impl.EmvParamService;
import com.pax.emvlib.dpas.contact.ContactProcess;
import com.pax.emvlib.process.contactless.ClssProcess;
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
 *
 * <p>{@code contactless} is {@link ClssProcess} directly too — no {@code ContactlessService}
 * layer in between. It stays {@code final} (unlike {@code contact}) because {@link ClssProcess}
 * is itself a process-lifetime singleton ({@code ClssProcess.getInstance()}) with a private
 * constructor — {@code ContactlessService} never held its own instance either, it always called
 * {@code ClssProcess.getInstance()} directly, so there's no "fresh per attempt" behavior to
 * replicate here.
 */
public final class PaxKernel {

    public final EmvParamService params = new EmvParamService();
    public ContactProcess contact = new ContactProcess();
    public final ClssProcess contactless = ClssProcess.getInstance();
    public final MagCardService mag = new MagCardService();
}
