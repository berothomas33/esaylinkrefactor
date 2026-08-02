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
 * 20210429 	        xieYb                  Create
 * ===========================================================================================
 *
 */

package com.pax.configservice.export;

import com.pax.bizentity.entity.CapkParamBean;
import com.pax.bizentity.entity.EmvAid;
import com.pax.bizentity.entity.clss.amex.AmexParamBean;
import com.pax.bizentity.entity.clss.dpas.DpasParamBean;
import com.pax.bizentity.entity.clss.eft.EFTParamBean;
import com.pax.bizentity.entity.clss.jcb.JcbParamBean;
import com.pax.bizentity.entity.clss.mir.MirParamBean;
import com.pax.bizentity.entity.clss.paypass.PayPassParamBean;
import com.pax.bizentity.entity.clss.paywave.PayWaveParamBean;
import com.pax.bizentity.entity.clss.pboc.PBOCParamBean;
import com.pax.bizentity.entity.clss.pure.PureParamBean;
import com.pax.bizentity.entity.clss.rupay.RupayParamBean;
import com.pax.emvbase.param.EmvProcessParam;
import java.util.List;

public interface IEmvParamService {
    /**
     * insert EmvCapk
     * @param emvCapkList EmvCapk list
     * @return insert result
     */
    boolean insertEmvCapk(CapkParamBean emvCapkList);

    /**
     * remove EmvCapk
     */
    void removeEmvCapk();

    /**
     * insert terminal config
     * @return insert result
     */
    boolean insertTerminalConfig();

    /**
     * remove terminal config
     */
    void removeTerminalConfig();

    /**
     * insert EmvAid
     * @param emvAidList EmvAid list
     * @return insert result
     */
    boolean insertEmvAid(List<EmvAid> emvAidList);

    /**
     * remove EmvAid
     */
    void removeEmvAid();

    /**
     * insert amex param
     * @param amexParam amex param
     * @return insert result
     */
    boolean insertAmexParam(AmexParamBean amexParam);

    /**
     * remove amex param
     */
    void removeAmexParam();

    /**
     * insert paypass param
     * @param payPassParam paypass param
     * @return insert result
     */
    boolean insertPaypassParam(PayPassParamBean payPassParam);

    /**
     * remove paypass param
     */
    void removePaypassParam();

    /**
     * insert payWave ParamBean
     * @param payWaveParamBean payWave ParamBean
     * @return insert result
     */
    boolean insertPaywaveParam(PayWaveParamBean payWaveParamBean);

    /**
     * remove paywave param
     */
    void removePaywaveParam();

    /**
     * insert dpas param
     * @param dpasParamBean daps param
     * @return insert result
     */
    boolean insertDpasParam(DpasParamBean dpasParamBean);

    /**
     * remove dpas param
     */
    void removeDpasParam();

    /**
     * insert eft param
     * @param eftParamBean eft param
     * @return insert result
     */
    boolean insertEFTParam(EFTParamBean eftParamBean);

    /**
     * remove eft param
     */
    void removeEFTParam();

    /**
     * insert jcb param
     * @param jcbParamBean jcb param
     * @return insert result
     */
    boolean insertJcbParam(JcbParamBean jcbParamBean);

    /**
     * remove jcb param
     */
    void removeJcbParam();

    /**
     * insert mir param
     * @param mirParamBean mir param
     * @return insert result
     */
    boolean insertMirParam(MirParamBean mirParamBean);

    /**
     * remove mir param
     */
    void removeMirParam();

    /**
     * insert pboc param
     * @param pbocParamBean pboc param
     * @return insert result
     */
    boolean insertPBOCParam(PBOCParamBean pbocParamBean);

    /**
     * remove pboc param
     */
    void removePBOCParam();

    /**
     * insert pure param
     * @param pureParamBean pure param
     * @return insert result
     */
    boolean insertPureParam(PureParamBean pureParamBean);

    /**
     * remove pure param
     */
    void removePureParam();

    /**
     * insert rupay param
     * @param rupayParamBean rupay param
     * @return insert result
     */
    boolean insertRupayParam(RupayParamBean rupayParamBean);

    /**
     * remove rupay param
     */
    void removeRupayParam();

    /**
     * Gets cached emv param
     * @return cached emv param
     */
    EmvProcessParam getCachedEmvParam();

    /**
     * Invalid cached emv param
     */
    void invalidCachedEmvParam();
}
