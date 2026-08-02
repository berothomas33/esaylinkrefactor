/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2021/06/18                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

import androidx.annotation.NonNull;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.param.EmvTransParam;
import com.pax.emvbase.param.common.Config;
import java.util.Arrays;
import java.util.List;

/**
 * Contactless Kernel Param
 */
public abstract class BaseParam<T extends BaseAid> {

    protected final String TAG = this.getClass().getSimpleName();


    //////////////////////////////////////////////////////////////////////////////////////
    // Common param //////////////////////////////////////////////////////////////////////

    /**
     * Selected AID
     *
     * @see #loadSelectedAid(byte[] finalSelectData)
     */
    protected T selectedAid;
    /**
     * AID List
     */
    protected List<T> aidList;

    /**
     * Application version bytes
     *
     * @see BaseAid#getVersion()
     */
    protected byte[] appVersion;
    /**
     * Acquirer ID bytes
     *
     * @see BaseAid#getAcquirerId()
     */
    protected byte[] acquirerId;

    /**
     * Terminal type
     *
     * @see BaseAid#getTermType()
     */
    protected byte termType;


    //// From Config
    /**
     * Conversion ratio
     *
     * @see Config#getConversionRatio()
     */
    protected long referCurrCon;
    /**
     * Transaction reference currency code bytes
     *
     * @see Config#getTransReferenceCurrencyCode()
     */
    protected byte[] referCurrCode;
    /**
     * Merchant ID bytes
     *
     * @see Config#getMerchantId()
     */
    protected byte[] merchantId;
    /**
     * Country code bytes
     *
     * @see Config#getTerminalCountryCode()
     */
    protected byte[] countryCode;
    /**
     * Merchant category code bytes
     *
     * @see Config#getMerchantCategoryCode()
     */
    protected byte[] merchantCategoryCode;
    /**
     * Merchant name location bytes
     *
     * @see Config#getMerchantNameAndLocationBytes()
     */
    protected byte[] merchantNameLocation;


    //// From EmvTransParam / Clss_TransParam
    /**
     * Amount bytes
     *
     * @see EmvTransParam#getAmountBytes()
     */
    protected byte[] amount;
    /**
     * Other amount bytes
     *
     * @see EmvTransParam#getAmountOtherBytes()
     */
    protected byte[] otherAmount;
    /**
     * Transaction date bytes
     *
     * @see EmvTransParam#getTransDate()
     */
    protected byte[] transDate;
    /**
     * Get transaction time bytes
     *
     * @see EmvTransParam#getTransTime()
     */
    protected byte[] transTime;
    /**
     * Transaction type byte code
     * <p>
     *      This byte value is taken from the first two digits of the <code>procCode</code> field
     *      of the <code>ETransType</code> class.
     *      <ul>
     *          <li>Sale: <code>00</code></li>
     *          <li>Void: <code>02</code></li>
     *          <li>Refund: <code>20</code></li>
     *          <li>Adjust: <code>00</code></li>
     *          <li>PreAuth: <code>30</code></li>
     *          <li>Offline: <code>00</code></li>
     *      </ul>
     * </p>
     * @see EmvTransParam#getTransType()
     */
    protected byte transType;
    /**
     * Transaction currency code bytes
     *
     * @see EmvTransParam#getTransCurrencyCode()
     */
    protected byte[] transCurrCode;
    /**
     * Transaction currency exponent
     *
     * @see EmvTransParam#getTransCurrencyExponent()
     */
    protected byte transCurrExp;


    //////////////////////////////////////////////////////////////////////////////////////
    // Other method //////////////////////////////////////////////////////////////////////

    /**
     * Set param from AID
     *
     * @param aid AID Param
     * @return this param object
     */
    @NonNull
    protected abstract BaseParam<T> loadFromAid(T aid);

    /**
     * Set param from Terminal Config
     *
     * @param config Config param
     * @return this param object
     */
    @NonNull
    public abstract BaseParam<T> loadFromConfig(Config config);

    /**
     * Set param from EmvTransParam
     *
     * @param param EmvTransParam param
     * @return this param object
     */
    @NonNull
    public abstract BaseParam<T> loadFromEmvTransParam(EmvTransParam param);

    /**
     * Set selected AID
     *
     * @param finalSelectData finalSelectData, output from the
     *      <code>ClssEntryApi.Clss_GetFinalSelectData_Entry(ByteArray finalSelectData)</code>
     *      method
     * @return this param
     * @throws IllegalArgumentException <code>finalSelectData</code> is empty
     */
    public final BaseParam<T> loadSelectedAid(byte[] finalSelectData) throws IllegalArgumentException {
        if (aidList == null || aidList.isEmpty()) {
            LogUtils.e(TAG, "AidList is empty!!!");
            throw new IllegalArgumentException("AidList is empty!!!");
        }
        for (T aid : aidList) {
            byte[] appId = aid.getAid();
            boolean selected;
            if (aid.getSelFlag() == 0) {
                int len = appId.length;
                int selLen = finalSelectData[0];
                selected = selLen >= len
                        && Arrays.equals(appId, Arrays.copyOfRange(finalSelectData, 1, len + 1));
            } else {
                selected = Arrays.equals(appId,
                        Arrays.copyOfRange(finalSelectData, 1, finalSelectData[0] + 1));
            }
            if (selected) {
                selectedAid = aid;
                LogUtils.d(TAG, "Selected Aid: " + ConvertUtils.bcd2Str(aid.getAid()));
                return loadFromAid(aid);
            }
        }
        LogUtils.e(TAG, "No Selected AID!!!");
        return this;
    }

    //////////////////////////////////////////////////////////////////////////////////////
    // Common param special getter ///////////////////////////////////////////////////////

    /**
     * Get transaction type bytes
     *
     * @return New byte array created using <code>transType</code>. This method is equivalent to
     *      <code>new byte[]{param.getTransType()}</code>.
     * @see #transType
     */
    public final byte[] getTransTypeBytes() {
        return new byte[]{transType};
    }

    /**
     * Get terminal type bytes
     *
     * @return New byte array created using <code>termType</code>. This method is equivalent to
     *      <code>new byte[]{param.getTermType()}</code>.
     * @see #termType
     */
    public final byte[] getTermTypeBytes() {
        return new byte[]{termType};
    }

    /**
     * Get transaction currency exponent bytes
     *
     * @return New byte array created using <code>transCurrExp</code>. This method is equivalent to
     *      <code>new byte[]{param.getTransCurrExp()}</code>
     * @see #transCurrExp
     */
    public final byte[] getTransCurrExpBytes() {
        return new byte[]{transCurrExp};
    }


    //////////////////////////////////////////////////////////////////////////////////////
    // Common param default getter ///////////////////////////////////////////////////////

    /**
     * Get selected AID object
     *
     * @return Selected AID object
     * @see #selectedAid
     */
    public final T getSelectedAid() {
        return selectedAid;
    }

    /**
     * Get all AID object list of this contactless kernel
     *
     * @return AID object list
     * @see #aidList
     */
    public final List<T> getAidList() {
        return aidList;
    }

    /**
     * Set all AID object list of this contactless kernel
     *
     * @param aidList AID object list
     * @see #aidList
     */
    public final void setAidList(List<T> aidList) {
        this.aidList = aidList;
    }

    /**
     * Get application version
     *
     * @return Application version bytes
     * @see #appVersion
     */
    public final byte[] getAppVersion() {
        return appVersion;
    }

    /**
     * Get acquirer id
     *
     * @return Acquirer id bytes
     * @see #acquirerId
     */
    public final byte[] getAcquirerId() {
        return acquirerId;
    }

    /**
     * Get merchant category code bytes
     *
     * @return Merchant category code bytes
     * @see #merchantCategoryCode
     */
    public final byte[] getMerchantCategoryCode() {
        return merchantCategoryCode;
    }

    /**
     * Get merchant name location bytes
     *
     * @return Merchant name location bytes
     * @see #merchantNameLocation
     */
    public final byte[] getMerchantNameLocation() {
        return merchantNameLocation;
    }

    /**
     * Get amount bytes
     *
     * @return Amount bytes
     * @see #amount
     */
    public final byte[] getAmount() {
        return amount;
    }

    /**
     * Get other amount bytes
     *
     * @return Other amount bytes
     * @see #otherAmount
     */
    public final byte[] getOtherAmount() {
        return otherAmount;
    }

    /**
     * Get conversion ratio
     *
     * @return Conversion ratio
     * @see #referCurrCon
     */
    public final long getReferCurrCon() {
        return referCurrCon;
    }

    /**
     * Get transaction reference currency code bytes
     *
     * @return Transaction reference currency code bytes
     * @see #referCurrCode
     */
    public final byte[] getReferCurrCode() {
        return referCurrCode;
    }

    /**
     * Get merchant ID bytes
     *
     * @return Merchant ID bytes
     * @see #merchantId
     */
    public final byte[] getMerchantId() {
        return merchantId;
    }

    /**
     * Get country code bytes
     *
     * @return Country code bytes
     * @see #countryCode
     */
    public final byte[] getCountryCode() {
        return countryCode;
    }

    /**
     * Get transaction date bytes
     *
     * @return Transaction date bytes
     * @see #transDate
     */
    public final byte[] getTransDate() {
        return transDate;
    }

    /**
     * Get transaction time bytes
     *
     * @return Transaction time bytes
     * @see #transTime
     */
    public final byte[] getTransTime() {
        return transTime;
    }

    /**
     * Get transaction currency code bytes
     *
     * @return Transaction currency code bytes
     * @see #transCurrCode
     */
    public final byte[] getTransCurrCode() {
        return transCurrCode;
    }

    /**
     * Get transaction type
     *
     * @return Transaction type byte code
     * @see #transType
     */
    public final byte getTransType() {
        return transType;
    }

    /**
     * Get terminal type
     *
     * @return Terminal type
     * @see #termType
     */
    public final byte getTermType() {
        return termType;
    }

    /**
     * Get transaction currency exponent
     *
     * @return Transaction currency exponent
     * @see #transCurrExp
     */
    public final byte getTransCurrExp() {
        return transCurrExp;
    }
}
