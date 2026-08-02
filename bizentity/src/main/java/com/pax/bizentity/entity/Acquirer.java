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
 * 20210508 	        xieYb                  Create
 * ===========================================================================================
 *
 */
package com.pax.bizentity.entity;

import androidx.annotation.NonNull;
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Unique;

/**
 * acquirer table
 */
@Entity(nameInDb = "acquirer")
public class Acquirer implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ID_FIELD_NAME = "acquirer_id";
    public static final String NAME_FIELD_NAME = "acquirer_name";
    public static final String SSL_TYPE_FIELD_NAME = "ssl_type";
    public static final String ENABLE_KEYIN = "key_in";
    public static final String ENABLE_QR = "qr";
    /**
     * id
     */
    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    private Long id;

    /**
     * name
     */
    @Property(nameInDb = NAME_FIELD_NAME)
    @Unique
    private String name;

    @NotNull
    private String nii;

    @NotNull
    private String terminalId;

    @NotNull
    private String merchantId;

    private int currBatchNo;

    private String ip;

    private int port;

    private String url;

    private String ipBak1;

    private short portBak1;

    private String ipBak2;

    private short portBak2;

    private int tcpTimeOut;

    private int wirelessTimeOut;

    @Property(nameInDb = SSL_TYPE_FIELD_NAME)
    private String sslType = "NO SSL";

    @Property(nameInDb = ENABLE_KEYIN)
    private boolean isEnableKeyIn = true;

    @Property(nameInDb = ENABLE_QR)
    private boolean isEnableQR = true;

    public Acquirer() {
    }

    public Acquirer(String name) {
        this.setName(name);
    }

    public Acquirer(Long id, String acquirerName) {
        this.setId(id);
        this.setName(acquirerName);
    }

    @Generated(hash = 1462023442)
    public Acquirer(Long id, String name, @NotNull String nii, @NotNull String terminalId,
            @NotNull String merchantId, int currBatchNo, String ip, int port, String url,
            String ipBak1, short portBak1, String ipBak2, short portBak2, int tcpTimeOut,
            int wirelessTimeOut, String sslType, boolean isEnableKeyIn, boolean isEnableQR) {
        this.id = id;
        this.name = name;
        this.nii = nii;
        this.terminalId = terminalId;
        this.merchantId = merchantId;
        this.currBatchNo = currBatchNo;
        this.ip = ip;
        this.port = port;
        this.url = url;
        this.ipBak1 = ipBak1;
        this.portBak1 = portBak1;
        this.ipBak2 = ipBak2;
        this.portBak2 = portBak2;
        this.tcpTimeOut = tcpTimeOut;
        this.wirelessTimeOut = wirelessTimeOut;
        this.sslType = sslType;
        this.isEnableKeyIn = isEnableKeyIn;
        this.isEnableQR = isEnableQR;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNii() {
        return nii;
    }

    public void setNii(String nii) {
        this.nii = nii;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public int getCurrBatchNo() {
        return currBatchNo;
    }

    public void setCurrBatchNo(int currBatchNo) {
        this.currBatchNo = currBatchNo;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getIpBak1() {
        return ipBak1;
    }

    public void setIpBak1(String ipBak1) {
        this.ipBak1 = ipBak1;
    }

    public short getPortBak1() {
        return portBak1;
    }

    public void setPortBak1(short portBak1) {
        this.portBak1 = portBak1;
    }

    public String getIpBak2() {
        return ipBak2;
    }

    public void setIpBak2(String ipBak2) {
        this.ipBak2 = ipBak2;
    }

    public short getPortBak2() {
        return portBak2;
    }

    public void setPortBak2(short portBak2) {
        this.portBak2 = portBak2;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getTcpTimeOut() {
        return tcpTimeOut;
    }

    public void setTcpTimeOut(int tcpTimeOut) {
        this.tcpTimeOut = tcpTimeOut;
    }

    public int getWirelessTimeOut() {
        return wirelessTimeOut;
    }

    public void setWirelessTimeOut(int wirelessTimeOut) {
        this.wirelessTimeOut = wirelessTimeOut;
    }

    public String getSslType() {
        return sslType;
    }

    public void setSslType(String sslType) {
        this.sslType = sslType;
    }

    public boolean isEnableKeyIn() {
        return isEnableKeyIn;
    }

    public void setEnableKeyIn(boolean enableKeyIn) {
        isEnableKeyIn = enableKeyIn;
    }

    public boolean isEnableQR() {
        return isEnableQR;
    }

    public void setEnableQR(boolean enableQR) {
        isEnableQR = enableQR;
    }

    /**
     * copy constructor
     * update data with specified acquirer
     *
     * @param acquirer the specified acquirer to be copied
     * @return true if update successfully or false otherwise
     */
    public boolean update(@NonNull Acquirer acquirer) {
        if (null == acquirer) {
            return false;
        }
        nii = acquirer.getNii();
        merchantId = acquirer.getMerchantId();
        terminalId = acquirer.getTerminalId();
        currBatchNo = acquirer.getCurrBatchNo();
        sslType = acquirer.getSslType();
        port = acquirer.getPort();
        tcpTimeOut = acquirer.getTcpTimeOut();
        wirelessTimeOut = acquirer.getWirelessTimeOut();
        isEnableKeyIn = acquirer.isEnableKeyIn();
        isEnableQR = acquirer.isEnableQR();

        //optional data
        String ip = acquirer.getIp();
        if (ip != null && !ip.isEmpty()) {
            this.ip = ip;
        }

        String ipBak = acquirer.getIpBak1();
        if (ipBak != null && !ipBak.isEmpty()) {
            ipBak1 = ipBak;
        }
        ipBak = acquirer.getIpBak2();
        if (ipBak != null && !ipBak.isEmpty()) {
            ipBak2 = ipBak;
        }
        short portBak = acquirer.getPortBak1();
        if (portBak != 0) {
            portBak1 = portBak;
        }
        portBak = acquirer.getPortBak2();
        if (portBak != 0) {
            portBak2 = portBak;
        }

        String url = acquirer.getUrl();
        if (url != null && !url.isEmpty()) {
            this.url = url;
        }
        return true;
    }

    public boolean getIsEnableKeyIn() {
        return this.isEnableKeyIn;
    }

    public void setIsEnableKeyIn(boolean isEnableKeyIn) {
        this.isEnableKeyIn = isEnableKeyIn;
    }

    public boolean getIsEnableQR() {
        return this.isEnableQR;
    }

    public void setIsEnableQR(boolean isEnableQR) {
        this.isEnableQR = isEnableQR;
    }
}
