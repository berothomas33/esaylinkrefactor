/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2019-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date	                Author	               Action
 * 20210707 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.emvservice.emv.version;

import com.pax.emvservice.export.constant.EmvServiceConstant;
import com.pax.emvservice.export.version.IEmvVersionService;
import com.pax.jemv.amex.api.ClssAmexApi;
import com.pax.jemv.clcommon.ByteArray;
import com.pax.jemv.dpas.api.ClssDPASApi;
import com.pax.jemv.eftpos.api.ClssEFTPOSApi;
import com.pax.jemv.emv.api.EMVApi;
import com.pax.jemv.entrypoint.api.ClssEntryApi;
import com.pax.jemv.jcb.api.ClssJCBApi;
import com.pax.jemv.mir.api.ClssMIRApi;
import com.pax.jemv.paypass.api.ClssPassApi;
import com.pax.jemv.paywave.api.ClssWaveApi;
import com.pax.jemv.pure.api.ClssPUREApi;
import com.pax.jemv.qpboc.api.ClssPbocApi;
import com.pax.jemv.rupay.api.ClssRuPayApi;
import com.sankuai.waimai.router.annotation.RouterService;

import static com.pax.jemv.clcommon.RetCode.EMV_OK;

@RouterService(interfaces = IEmvVersionService.class,key = EmvServiceConstant.EMVSERVICE_EMV_VERSION)
public class EmvVersionService implements IEmvVersionService {
    /**
     * Query the version number of Emv Kernel
     *
     * @return version number
     */
    @Override
    public String getEmvVersion() {
        ByteArray version = new ByteArray();
        int ret = EMVApi.EMVReadVerInfo(version);
        if (ret ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of Entry Kernel.
     *
     * @return version number
     */
    @Override
    public String getEntryVersion() {
        ByteArray version = new ByteArray();
        int i = ClssEntryApi.Clss_ReadVerInfo_Entry(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of PayPass kernel.
     *
     * @return version number
     */
    @Override
    public String getPayPassVersion() {
        ByteArray version = new ByteArray();
        int i = ClssPassApi.Clss_ReadVerInfo_MC(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of PayWave kernel.
     *
     * @return version number
     */
    @Override
    public String getPayWaveVersion() {
        ByteArray version = new ByteArray();
        int i = ClssWaveApi.Clss_ReadVerInfo_Wave(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of Rupay kernel.
     *
     * @return version number
     */
    @Override
    public String getRupayVersion() {
        ByteArray version = new ByteArray();
        int i = ClssRuPayApi.Clss_ReadVerInfo_RuPay(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of PBOC kernel.
     *
     * @return version number
     */
    @Override
    public String getPbocVersion() {
        ByteArray version = new ByteArray();
        int i = ClssPbocApi.Clss_ReadVerInfo_Pboc(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of Jcb kernel.
     *
     * @return version number
     */
    @Override
    public String getJcbVersion() {
        ByteArray version = new ByteArray();
        int i = ClssJCBApi.Clss_ReadVerInfo_JCB(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of Amex kernel.
     *
     * @return version number
     */
    @Override
    public String getAmexVersion() {
        ByteArray version = new ByteArray();
        int i = ClssAmexApi.Clss_ReadVerInfo_AE(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of Dpas kernel.
     *
     * @return version number
     */
    @Override
    public String getDpasVersion() {
        ByteArray version = new ByteArray();
        int i = ClssDPASApi.Clss_ReadVerInfo_DPAS(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of Mir kernel.
     *
     * @return version number
     */
    @Override
    public String getMIRVersion() {
        ByteArray version = new ByteArray();
        int i = ClssMIRApi.Clss_ReadVerInfo_MIR(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of Pure kernel.
     *
     * @return version number
     */
    @Override
    public String getPureVersion() {
        ByteArray version = new ByteArray();
        int i = ClssPUREApi.Clss_ReadVerInfo_PURE(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }

    /**
     * Query the version number of EFT kernel.
     *
     * @return version number
     */
    @Override
    public String getEFTVersion() {
        ByteArray version = new ByteArray();
        int i = ClssEFTPOSApi.Clss_ReadVerInfo_EFT(version);
        if (i ==EMV_OK){
            return new String(version.data);
        }
        return "";
    }
}
