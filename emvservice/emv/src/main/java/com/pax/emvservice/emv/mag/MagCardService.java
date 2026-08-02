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

package com.pax.emvservice.emv.mag;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.pax.bizentity.db.helper.GreendaoHelper;
import com.pax.bizentity.entity.CardRange;
import com.pax.bizentity.entity.Issuer;
import com.pax.bizlib.card.PanUtils;
import com.pax.bizlib.card.TrackUtils;
import com.pax.dal.entity.TrackData;
import com.pax.emvservice.export.EmvServiceConstant;
import com.pax.emvservice.export.IMagCardService;
import com.pax.poslib.utils.PosDeviceUtils;
import com.sankuai.waimai.router.annotation.RouterService;

@RouterService(interfaces = IMagCardService.class,key = EmvServiceConstant.EMVSERVICE_MAG_CARD,singleton = true)
public class MagCardService implements IMagCardService {
    TrackData trackData;

    /**
     * read mag data
     */
    @Override
    public void magRead() {
        trackData = PosDeviceUtils.getTrackData();
    }

    /**
     * Gets track1 data
     *
     * @return track1 data
     */
    @Override
    public String getTrack1() {
        if (trackData == null){
            trackData = PosDeviceUtils.getTrackData();
        }
        return trackData != null ? trackData.getTrack1() : null;
    }

    /**
     * Gets track2 data
     *
     * @return track2 data
     */
    @Override
    public String getTrack2() {
        if (trackData == null){
            trackData = PosDeviceUtils.getTrackData();
        }
        return trackData != null ? trackData.getTrack2() : null;
    }

    /**
     * Gets track3 data
     *
     * @return track3 data
     */
    @Override
    public String getTrack3() {
        if (trackData == null){
            trackData = PosDeviceUtils.getTrackData();
        }
        return trackData != null ? trackData.getTrack3() : null;
    }

    /**
     * Gets pan, the result is ciphertext in p2pe mode
     *
     * @return pan
     */
    @Override
    public String getPan() {
        if (trackData == null){
            trackData = PosDeviceUtils.getTrackData();
        }
        String track2 = trackData != null ? trackData.getTrack2() : null;
        return TrackUtils.getPan(track2);
    }

    /**
     * Gets pan block
     *
     * @return pan block
     */
    @NonNull
    @Override
    public String getPanBlock() {
        return PanUtils.getPanBlock(getPan(), PanUtils.X9_8_WITH_PAN);
    }

    /**
     * Gets masked pan
     *
     * @param pattern masked pattern
     * @return masked pan
     */
    @Override
    public String getMaskedPan(String pattern) {
        return PanUtils.maskCardNo(getPan(),pattern);
    }

    /**
     * Gets expire Date
     *
     * @return expire Date
     */
    @Override
    public String getExpireDate() {
        return TrackUtils.getExpDate(getTrack2());
    }

    /**
     * Gets cardholder name
     *
     * @return cardholder name
     */
    @Override
    public String getCardholderName() {
        return TrackUtils.getHolderName(getTrack1());
    }

    /**
     * Gets Issuer by pan(use maskedPan in p2pe mode)
     *
     * @return Issuer
     */
    @Override
    public Issuer getMatchedIssuerByPan() {
        CardRange cardRange = GreendaoHelper.getCardRangeHelper().findCardRange(getPan());
        if (cardRange == null) {
            return null;
        } else {
            return cardRange.getIssuer();
        }
    }

    /**
     * check is valid pan
     *
     * @return result
     */
    @Override
    public boolean isValidPan() {
        String pan = getPan();
        return PanUtils.isValidPan(pan);
    }

    /**
     * Gets service code
     *
     * @return service code
     */
    @Override
    public String getServiceCode() {
        String track2 = getTrack2();
        if (TextUtils.isEmpty(track2)){
            return "";
        }
        int idx = track2.indexOf('=');
        if (idx == -1) {
            return "";
        } else {
            return track2.substring(idx + 5, idx + 8);
        }
    }
}
