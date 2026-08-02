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
 * 2021/06/17                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.emvbase.param.clss;

public abstract class BaseAid {
    /**
     * Application name
     */
    private byte[] appName;

    /**
     * AID
     */
    private byte[] aid;

    /**
     * Flag indicates whether the AID supports partial name selection or not
     * <ul>
     *     <li>1 - Does Not Support</li>
     *     <li>0 - Supports</li>
     * </ul>
     */
    private byte selFlag;
    /**
     * Reader CVM required limit
     */
    private long cvmLimit;
    /**
     * Reader CVM required limit check or not
     * <ul>
     *     <li>0: Deactivated</li>
     *     <li>1: Active and exist</li>
     *     <li>2: Active but not exist</li>
     * </ul>
     */
    private byte cvmLimitFlag;
    /**
     * Reader contactless transaction limit (No On-device CVM)
     */
    private long transLimit;
    /**
     * Reader contactless transaction limit (No On-device CVM) check or not
     * <ul>
     *     <li>0: Deactivated</li>
     *     <li>1: Active and exist</li>
     *     <li>2: Active but not exist</li>
     * </ul>
     */
    private byte transLimitFlag;
    /**
     * Reader contactless floor limit
     */
    private long floorLimit;
    /**
     * Reader contactless floor limit check or not
     * <ul>
     *     <li>0: Deactivated</li>
     *     <li>1: Active and exist</li>
     *     <li>2: Active but not exist</li>
     * </ul>
     */
    private byte floorLimitFlag;

    /**
     * Acquirer Identifier
     */
    private byte[] acquirerId;
    /**
     * AID Version
     */
    private byte[] version;

    /**
     * Terminal Type
     * <p>
     *     Operational Control Provided By:
     *     <ul>
     *         <li>1? - Financial Institution</li>
     *         <li>2? - Merchant</li>
     *         <li>3? - Cardholder</li>
     *     </ul>
     * </p>
     * <p>
     *     Environment:
     *     <ul>
     *         <li>?1 - Attended, Online only</li>
     *         <li>?2 - Attended, Offline with online capability</li>
     *         <li>?3 - Attended, Offline only</li>
     *         <li>?4 - Unattended, Online only</li>
     *         <li>?5 - Unattended, Offline with online capability</li>
     *         <li>?6 - Unattended, Offline only</li>
     *     </ul>
     * </p>
     * <p>For example, '22' means operational control provided by 'Merchant', environment is
     *     attended, offline with online capability.</p>
     * <p>31, 32, 33 do not exist.</p>
     */
    private byte termType;


    //////////////////////////////////////////////////////////////////////////////////////
    // Getter and setter /////////////////////////////////////////////////////////////////

    /**
     * Get application name bytes.
     *
     * @return Application name bytes
     * @see #appName
     */
    public final byte[] getAppName() {
        return appName;
    }

    /**
     * Set application name bytes.
     *
     * @param appName Application name bytes
     * @see #appName
     */
    public final void setAppName(byte[] appName) {
        this.appName = appName;
    }

    /**
     * Get Application AID.
     *
     * @return AID Bytes. This value is converted from the AID field in the json configuration
     * file of each contactless kernel
     */
    public byte[] getAid() {
        return aid;
    }

    /**
     * Set Application AID.
     *
     * @param aid AID Bytes
     */
    public void setAid(byte[] aid) {
        this.aid = aid;
    }

    /**
     * Get whether the AID supports partial name selection or not.
     *
     * @return <ul>
     *     <li>1: Does Not Support</li>
     *     <li>0: Supports</li>
     * </ul>
     */
    public byte getSelFlag() {
        return selFlag;
    }

    /**
     * Set whether the AID supports partial name selection or not.
     *
     * @param selFlag <ul>
     *     <li>1: Does Not Support</li>
     *     <li>0: Supports</li>
     * </ul>
     */
    public void setSelFlag(byte selFlag) {
        this.selFlag = selFlag;
    }

    /**
     * <p>Get reader CVM required limit.</p>
     * <p>
     *     This method should only be called when the <code>long</code> value of
     *     <code>cvmLimit</code> needs to be read. Please <b>DO NOT</b> get this value in the
     *     contactless kernel process and then perform type conversion, such as converting from
     *     <code>long</code> type to <code>byte</code> array. Doing so will increase the time
     *     consumption of the contactless kernel process.
     * </p>
     * <p>
     *      If you need to read other types of the value in the contactless kernel, the correct
     *      approach is to declare a corresponding type of field in the Aid class and parameter
     *      class of the contactless kernel, convert the data in the preprocessing stage of the
     *      contactless process and set it to Aid class and parameter class.
     * </p>
     *
     * @return CVM required limit value
     * @see #cvmLimit
     */
    public long getCvmLimit() {
        return cvmLimit;
    }

    /**
     * Set reader CVM required limit.
     *
     * @param cvmLimit CVM required limit value
     * @see #cvmLimit
     */
    public void setCvmLimit(long cvmLimit) {
        this.cvmLimit = cvmLimit;
    }

    /**
     * <p>Get reader contactless transaction limit (No On-device CVM).</p>
     * <p>
     *     This method should only be called when the <code>long</code> value of
     *     <code>transLimit</code> needs to be read. Please <b>DO NOT</b> get this value in the
     *     contactless kernel process and then perform type conversion, such as converting from
     *     <code>long</code> type to <code>byte</code> array. Doing so will increase the time
     *     consumption of the contactless kernel process.
     * </p>
     * <p>
     *      If you need to read other types of the value in the contactless kernel, the correct
     *      approach is to declare a corresponding type of field in the Aid class and parameter
     *      class of the contactless kernel, convert the data in the preprocessing stage of the
     *      contactless process and set it to Aid class and parameter class.
     * </p>
     *
     * @return transaction limit value
     * @see #transLimit
     */
    public long getTransLimit() {
        return transLimit;
    }

    /**
     * Set reader contactless transaction limit (No On-device CVM).
     *
     * @param transLimit transaction limit value
     * @see #transLimit
     */
    public void setTransLimit(long transLimit) {
        this.transLimit = transLimit;
    }

    /**
     * <p>Get reader contactless floor limit.</p>
     * <p>
     *     This method should only be called when the <code>long</code> value of
     *     <code>floorLimit</code> needs to be read. Please <b>DO NOT</b> get this value in the
     *     contactless kernel process and then perform type conversion, such as converting from
     *     <code>long</code> type to <code>byte</code> array. Doing so will increase the time
     *     consumption of the contactless kernel process.
     * </p>
     * <p>
     *      If you need to read other types of the value in the contactless kernel, the correct
     *      approach is to declare a corresponding type of field in the Aid class and parameter
     *      class of the contactless kernel, convert the data in the preprocessing stage of the
     *      contactless process and set it to Aid class and parameter class.
     * </p>
     *
     * @return floor limit value
     * @see #floorLimit
     */
    public long getFloorLimit() {
        return floorLimit;
    }

    /**
     * Set reader contactless floor limit.
     *
     * @param floorLimit floor limit value
     * @see #floorLimit
     */
    public void setFloorLimit(long floorLimit) {
        this.floorLimit = floorLimit;
    }

    /**
     * Get reader contactless floor limit check or not.
     *
     * @return Reader contactless floor limit check or not
     * @see #floorLimitFlag
     */
    public byte getFloorLimitFlag() {
        return floorLimitFlag;
    }

    /**
     * Set reader contactless floor limit check or not.
     *
     * @param floorLimitFlag Reader contactless floor limit check or not
     * @see #floorLimitFlag
     */
    public void setFloorLimitFlag(byte floorLimitFlag) {
        this.floorLimitFlag = floorLimitFlag;
    }

    /**
     * Get reader contactless transaction limit (No On-device CVM) check or not.
     *
     * @return Reader contactless transaction limit (No On-device CVM) check or not
     * @see #transLimitFlag
     */
    public byte getTransLimitFlag() {
        return transLimitFlag;
    }

    /**
     * Set reader contactless transaction limit (No On-device CVM) check or not.
     *
     * @param transLimitFlag Reader contactless transaction limit (No On-device CVM) check or not
     * @see #transLimitFlag
     */
    public void setTransLimitFlag(byte transLimitFlag) {
        this.transLimitFlag = transLimitFlag;
    }

    /**
     * Get reader CVM required limit check or not.
     *
     * @return Reader CVM required limit check or not
     * @see #cvmLimitFlag
     */
    public byte getCvmLimitFlag() {
        return cvmLimitFlag;
    }

    /**
     * Set reader CVM required limit check or not.
     *
     * @param cvmLimitFlag Reader CVM required limit check or not
     * @see #cvmLimitFlag
     */
    public void setCvmLimitFlag(byte cvmLimitFlag) {
        this.cvmLimitFlag = cvmLimitFlag;
    }

    /**
     * Get acquirer identifier.
     *
     * @return Acquirer ID
     */
    public byte[] getAcquirerId() {
        return acquirerId;
    }

    /**
     * Set acquirer identifier.
     *
     * @param acquirerId Acquirer ID
     */
    public void setAcquirerId(byte[] acquirerId) {
        this.acquirerId = acquirerId;
    }

    /**
     * Get application version bytes.
     *
     * @return Application version bytes
     */
    public byte[] getVersion() {
        return version;
    }

    /**
     * Set application version bytes.
     *
     * @param version Application version bytes
     */
    public void setVersion(byte[] version) {
        this.version = version;
    }

    /**
     * Get terminal type.
     *
     * @return Terminal type
     * @see #termType
     */
    public byte getTermType() {
        return termType;
    }

    /**
     * Set terminal type.
     *
     * @param termType Terminal type
     * @see #termType
     */
    public void setTermType(byte termType) {
        this.termType = termType;
    }
}
