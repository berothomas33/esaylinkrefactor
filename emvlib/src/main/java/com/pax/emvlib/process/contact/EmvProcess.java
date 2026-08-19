/*
 *  ===========================================================================================
 *  = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *     This software is supplied under the terms of a license agreement or nondisclosure
 *     agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *     disclosed except in accordance with the terms in that agreement.
 *          Copyright (C) 2020 -? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 *  Description: // Detail description about the function of this module,
 *               // interfaces with the other modules, and dependencies.
 *  Revision History:
 *  Date	               Author	                   Action
 *  2020/05/26 	         Qinny Zhou           	      Create
 *  ===========================================================================================
 */

package com.pax.emvlib.process.contact;

import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.process.EmvBase;
import com.pax.emvbase.process.contact.IContactCallback;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvlib.base.contact.BaseContactProcess;
import com.pax.emvlib.dpas.contact.ContactProcess;

/**
 * Constructs {@link ContactProcess} directly instead of resolving it through WMRouter's
 * {@code Router.getService(BaseContactProcess.class, EmvKernelConst.EMV)} — that lookup can
 * never succeed on this project's AGP version. WMRouter's own Gradle plugin can't run here (see
 * the note in the root build.gradle: it needs the legacy Transform API, removed in AGP 8.0+), so
 * the per-module {@code @RouterService} registrations {@code annotationProcessor} generates are
 * never combined into the registry {@code ServiceLoader.lazyInit()} looks for — the lookup
 * returns null on every call, permanently, regardless of timing. This matches
 * {@code PaxKernel}'s own "direct composition instead of WMRouter" pattern one layer up.
 *
 * <p>{@code emvlib}'s only contact kernel dependency is {@code emvlib:dpas} (see
 * emvlib/build.gradle) — {@code emvlib:dpas2}'s {@code ContactProcess} is a second
 * {@code @RouterService} registered under the same key but isn't linked into this build at all,
 * so it was never reachable via Router either way.
 */
public class EmvProcess extends EmvBase {

    private final BaseContactProcess contactProcess = new ContactProcess();

    private EmvProcess() {
    }

    private static class Holder {
        private static final EmvProcess INSTANCE = new EmvProcess();
    }

    public static EmvProcess getInstance() {
        return Holder.INSTANCE;
    }

    public void registerEmvProcessListener(IContactCallback emvTransProcessListener) {
        contactProcess.registerEmvProcessListener(emvTransProcessListener);
    }

    @Override
    public int preTransProcess(EmvProcessParam emvProcessParam) {
        return contactProcess.preTransProcess(emvProcessParam);
    }

    public TransResult selectApplication() {
        return contactProcess.selectApplication();
    }

    public TransResult readApplicationData() {
        return contactProcess.readApplicationData();
    }

    public TransResult cardAuthentication() {
        return contactProcess.cardAuthentication();
    }

    @Override
    public TransResult startTransProcess() {
        return contactProcess.startTransProcess();
    }

    @Override
    public TransResult completeTransProcess(IssuerRspData issuerRspData) {
        return contactProcess.completeTransProcess(issuerRspData);
    }

    @Override
    public byte[] getTlv(int tag) {
        return contactProcess.getTlv(tag);
    }

    /**
     * Sets value on specific tag
     *
     * @param tag   emv tag
     * @param value tag value
     */
    @Override
    public void setTlv(int tag, byte[] value) {
        contactProcess.setTlv(tag, value);
    }
}
