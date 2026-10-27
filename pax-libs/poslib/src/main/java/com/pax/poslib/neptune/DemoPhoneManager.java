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
 * Date                  Author	                 Action
 * 20190108  	         xieYb                   Create
 * ===========================================================================================
 */

package com.pax.poslib.neptune;

import android.telephony.PhoneStateListener;
import com.pax.dal.IPhoneManager;
import com.pax.dal.entity.OperatorInfo;
import com.pax.dal.exceptions.PhoneDevException;
import java.util.List;

/**
 * neptune IPhoneManager
 */
class DemoPhoneManager implements IPhoneManager {
    @Override
    public int[] getSubId(int i) throws PhoneDevException {
        return new int[0];
    }

    @Override
    public void listenPhoneState(PhoneStateListener phoneStateListener, int i, int i1) throws PhoneDevException {
        //do nothing
    }

    @Override
    public void setDefaultDataSubId(int i) throws PhoneDevException {
        //do nothing
    }

    @Override
    public boolean setPreferredNetworkType(int i) throws PhoneDevException {
        return false;
    }

    @Override
    public boolean isAirplaneModeEnable() throws PhoneDevException {
        return false;
    }

    @Override
    public void enableAirplaneMode(boolean b) throws PhoneDevException {
        // do nothing
    }

    @Override
    public List<OperatorInfo> getCellNetworkScanResults(int i) throws PhoneDevException {
        return null;
    }

    @Override
    public boolean getDataRoamingEnabled(int i) throws PhoneDevException {
        return false;
    }

    @Override
    public void setDataRoamingEnabled(int i, boolean b) throws PhoneDevException {
        //do nothing
    }

    @Override
    public boolean setNetworkSelectionModeManual(int i, OperatorInfo operatorInfo)
            throws PhoneDevException {
        return false;
    }

    @Override
    public boolean isSimCardLock(int i) throws PhoneDevException {
        return false;
    }

    @Override
    public boolean isDualSim() throws PhoneDevException {
        return false;
    }

    @Override
    public boolean isVSimDataInUse() throws PhoneDevException {
        return false;
    }

    @Override
    public void turnOnVSimData() throws PhoneDevException {
        // do nothing
    }

    @Override
    public void turnOffVSimData() throws PhoneDevException {
        // do nothing
    }
}
