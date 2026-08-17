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

package com.pax.emvservice.emv.manual;

import com.pax.bizlib.card.PanUtils;
import com.pax.emvservice.export.constant.EmvServiceConstant;
import com.pax.emvservice.export.manual.IManualCardService;
import com.sankuai.waimai.router.annotation.RouterService;

@RouterService(interfaces = IManualCardService.class,key = EmvServiceConstant.EMVSERVICE_MANUAL_CARD)
public class ManualCardService implements IManualCardService {
    /**
     * Gets pan, the result is ciphertext in p2pe mode
     *
     * @param cardNo manual cardNo
     * @return pan
     */
    @Override
    public String getPan(String cardNo) {
        return cardNo;
    }

    /**
     * Gets masked pan
     *
     * @param cardNo  manual cardNo
     * @param pattern masked pattern
     * @return masked pan
     */
    @Override
    public String getMaskedPan(String cardNo, String pattern) {
        return PanUtils.maskCardNo(cardNo,pattern);
    }

    /**
     * Gets pan block
     * @param cardNo  manual cardNo
     * @return pan block
     */
    @Override
    public String getPanBlock(String cardNo) {
        return PanUtils.getPanBlock(getPan(cardNo), PanUtils.X9_8_WITH_PAN);
    }
}
