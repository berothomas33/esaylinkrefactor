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

import androidx.annotation.NonNull;
import com.pax.bizlib.card.PanUtils;
import com.pax.bizlib.card.TrackUtils;
import com.pax.dal.entity.TrackData;
import com.pax.poslib.utils.PosDeviceUtils;

public class MagCardService {
    TrackData trackData;

    /**
     * read mag data
     */
    public void magRead() {
        trackData = PosDeviceUtils.getTrackData();
    }

    /**
     * Gets track1 data
     *
     * @return track1 data
     */
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
    public String getPanBlock() {
        return PanUtils.getPanBlock(getPan(), PanUtils.X9_8_WITH_PAN);
    }

    /**
     * Gets expire Date
     *
     * @return expire Date
     */
    public String getExpireDate() {
        return TrackUtils.getExpDate(getTrack2());
    }
}
