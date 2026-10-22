package com.emvenhance.vendor.pax

import com.pax.configservice.impl.EmvParamService
import com.pax.emvlib.dpas.contact.ContactProcess
import com.pax.emvlib.process.contactless.ClssProcess
import com.pax.emvservice.emv.mag.MagCardService

/**
 * Concrete PAX kernel objects owned by the PAX vendor layer.
 *
 * Replaces WMRouter service lookup with direct composition. One instance is created by
 * [PaxTerminal] and shared with [PaxEmvBehavior].
 *
 * **Experiment branch:** [contact] is [ContactProcess] directly — no `EmvContactService` layer in
 * between. It's mutable (a `var`) because `PaxEmvBehavior#prepareKernel` rebuilds it fresh (a new
 * instance) at the start of every transaction, the same "fresh per attempt" responsibility
 * `EmvContactService` used to own internally.
 *
 * [contactless] is [ClssProcess] directly too — no `ContactlessService` layer in between. It
 * stays a `val` (unlike [contact]) because [ClssProcess] is itself a process-lifetime singleton
 * (`ClssProcess.getInstance()`) with a private constructor — `ContactlessService` never held its
 * own instance either, it always called `ClssProcess.getInstance()` directly, so there's no
 * "fresh per attempt" behavior to replicate here.
 */
class PaxKernel {

    @JvmField
    val params = EmvParamService()

    @JvmField
    var contact = ContactProcess()

    @JvmField
    val contactless: ClssProcess = ClssProcess.getInstance()

    @JvmField
    val mag = MagCardService()

    /**
     * Whether this transaction's contact / contactless kernel pre-processing succeeded — set by
     * [PaxEmvBehavior] before every card search, read by [PaxTerminal] so it only listens on
     * readers whose kernel is ready. A tap accepted after contactless pre-processing failed (no
     * contactless AIDs, amount over every contactless limit) could only fail later in
     * application selection.
     */
    @Volatile
    @JvmField
    var contactReady = true

    @Volatile
    @JvmField
    var contactlessReady = true
}
