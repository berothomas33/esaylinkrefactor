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

package com.pax.bizentity.entity.clss.paywave;

import android.text.TextUtils;
import com.pax.bizentity.db.dao.DaoSession;
import com.pax.bizentity.db.dao.PayWaveInterFloorLimitBeanDao;
import com.pax.bizentity.db.dao.PaywaveAidBeanDao;
import com.pax.commonlib.utils.ConvertUtils;
import java.io.Serializable;
import java.util.List;
import org.greenrobot.greendao.DaoException;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.ToMany;
import org.greenrobot.greendao.annotation.Transient;

@Entity(nameInDb = "paywave_aid")
public class PaywaveAidBean implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final String ID_FIELD_NAME = "paywaveAidId";
    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    private Long id;

    private String appName;
    @Transient
    private byte[] appNameBytes;
    private String aid;
    @Transient
    private byte[] aidBytes;
    // Flag indicates whether the AID supports partial name selection or not.
    // 1: Does Not Support.
    // 0: Supports
    private byte selFlag;
    /**
     * application version
     */
    private String version;
    @Transient
    private byte[] versionBytes;
    private String terminalType;
    @Transient
    private byte terminalTypeByte;
    private String ttq;
    @Transient
    private byte[] ttqBytes;
    private byte crypto17Flag;
    private byte statusCheckFlag;
    private byte amountZeroNoAllowed;
    private String securityCapability;
    @Transient
    private byte[] securityCapabilityBytes;
    private byte domesticOnly;
    private byte enDDAVerNo;
    @ToMany(referencedJoinProperty = ID_FIELD_NAME)
    private List<PayWaveInterFloorLimitBean> interWareFloorLimit;
    /** Used to resolve relations */
    @Generated(hash = 2040040024)
    private transient DaoSession daoSession;
    /** Used for active entity operations. */
    @Generated(hash = 1296453004)
    private transient PaywaveAidBeanDao myDao;

    @Generated(hash = 32203421)
    public PaywaveAidBean(Long id, String appName, String aid, byte selFlag, String version,
            String terminalType, String ttq, byte crypto17Flag, byte statusCheckFlag,
            byte amountZeroNoAllowed, String securityCapability, byte domesticOnly, byte enDDAVerNo) {
        this.id = id;
        this.appName = appName;
        this.aid = aid;
        this.selFlag = selFlag;
        this.version = version;
        this.terminalType = terminalType;
        this.ttq = ttq;
        this.crypto17Flag = crypto17Flag;
        this.statusCheckFlag = statusCheckFlag;
        this.amountZeroNoAllowed = amountZeroNoAllowed;
        this.securityCapability = securityCapability;
        this.domesticOnly = domesticOnly;
        this.enDDAVerNo = enDDAVerNo;
    }

    @Generated(hash = 1155075677)
    public PaywaveAidBean() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public byte[] getAppNameBytes() {
        return ConvertUtils.strToBcdPaddingLeft(appName);
    }

    public void setAppNameBytes(byte[] appNameBytes) {
        this.appNameBytes = appNameBytes;
    }

    public String getAid() {
        return aid;
    }

    public void setAid(String aid) {
        this.aid = aid;
    }

    public byte[] getAidBytes() {
        return ConvertUtils.strToBcdPaddingRight(aid);
    }

    public void setAidBytes(byte[] aidBytes) {
        this.aidBytes = aidBytes;
    }

    public byte getSelFlag() {
        return selFlag;
    }

    public void setSelFlag(byte selFlag) {
        this.selFlag = selFlag;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public byte[] getVersionBytes() {
        return ConvertUtils.strToBcdPaddingRight(version);
    }

    public void setVersionBytes(byte[] versionBytes) {
        this.versionBytes = versionBytes;
    }

    public String getTerminalType() {
        return terminalType;
    }

    public void setTerminalType(String terminalType) {
        this.terminalType = terminalType;
    }

    public byte getTerminalTypeByte() {
        if (!TextUtils.isEmpty(terminalType)){
            return ConvertUtils.strToBcdPaddingLeft(terminalType)[0];
        }else {
            return (byte) -1;
        }
    }
    public void setTerminalTypeByte(byte terminalTypeByte) {
        this.terminalTypeByte = terminalTypeByte;
    }

    public String getTtq() {
        return ttq;
    }

    public void setTtq(String ttq) {
        this.ttq = ttq;
    }

    public byte[] getTtqBytes() {
        return ConvertUtils.strToBcdPaddingLeft(ttq);
    }

    public void setTtqBytes(byte[] ttqBytes) {
        this.ttqBytes = ttqBytes;
    }

    public byte getCrypto17Flag() {
        return crypto17Flag;
    }

    public void setCrypto17Flag(byte crypto17Flag) {
        this.crypto17Flag = crypto17Flag;
    }

    public byte getStatusCheckFlag() {
        return statusCheckFlag;
    }

    public void setStatusCheckFlag(byte statusCheckFlag) {
        this.statusCheckFlag = statusCheckFlag;
    }

    public byte getAmountZeroNoAllowed() {
        return amountZeroNoAllowed;
    }

    public void setAmountZeroNoAllowed(byte amountZeroNoAllowed) {
        this.amountZeroNoAllowed = amountZeroNoAllowed;
    }

    public String getSecurityCapability() {
        return securityCapability;
    }

    public void setSecurityCapability(String securityCapability) {
        this.securityCapability = securityCapability;
    }

    public byte[] getSecurityCapabilityBytes() {
        return ConvertUtils.strToBcdPaddingLeft(securityCapability);
    }

    public void setSecurityCapabilityBytes(byte[] securityCapabilityBytes) {
        this.securityCapabilityBytes = securityCapabilityBytes;
    }

    public byte getDomesticOnly() {
        return domesticOnly;
    }

    public void setDomesticOnly(byte domesticOnly) {
        this.domesticOnly = domesticOnly;
    }

    public byte getEnDDAVerNo() {
        return enDDAVerNo;
    }

    public void setEnDDAVerNo(byte enDDAVerNo) {
        this.enDDAVerNo = enDDAVerNo;
    }

    public void setInterWareFloorLimit(List<PayWaveInterFloorLimitBean> interWareFloorLimit) {
        this.interWareFloorLimit = interWareFloorLimit;
    }

    /**
     * To-many relationship, resolved on first access (and after reset).
     * Changes to to-many relations are not persisted, make changes to the target entity.
     */
    @Generated(hash = 1825627674)
    public List<PayWaveInterFloorLimitBean> getInterWareFloorLimit() {
        if (interWareFloorLimit == null) {
            final DaoSession daoSession = this.daoSession;
            if (daoSession == null) {
                throw new DaoException("Entity is detached from DAO context");
            }
            PayWaveInterFloorLimitBeanDao targetDao = daoSession
                    .getPayWaveInterFloorLimitBeanDao();
            List<PayWaveInterFloorLimitBean> interWareFloorLimitNew = targetDao
                    ._queryPaywaveAidBean_InterWareFloorLimit(id);
            synchronized (this) {
                if (interWareFloorLimit == null) {
                    interWareFloorLimit = interWareFloorLimitNew;
                }
            }
        }
        return interWareFloorLimit;
    }

    /** Resets a to-many relationship, making the next get call to query for a fresh result. */
    @Generated(hash = 684653369)
    public synchronized void resetInterWareFloorLimit() {
        interWareFloorLimit = null;
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#delete(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 128553479)
    public void delete() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.delete(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#refresh(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 1942392019)
    public void refresh() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.refresh(this);
    }

    /**
     * Convenient call for {@link org.greenrobot.greendao.AbstractDao#update(Object)}.
     * Entity must attached to an entity context.
     */
    @Generated(hash = 713229351)
    public void update() {
        if (myDao == null) {
            throw new DaoException("Entity is detached from DAO context");
        }
        myDao.update(this);
    }

    /** called by internal mechanisms, do not call yourself. */
    @Generated(hash = 65894659)
    public void __setDaoSession(DaoSession daoSession) {
        this.daoSession = daoSession;
        myDao = daoSession != null ? daoSession.getPaywaveAidBeanDao() : null;
    }

}
