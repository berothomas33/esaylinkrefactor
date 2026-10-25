package com.pax.bizentity.entity;

import com.pax.commonlib.utils.ConvertUtils;
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Transient;

@Entity(nameInDb = "capk_revoke")
public class CapkRevokeBean implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id(autoincrement = true)
    private Long id;
    private String rID;
    @Transient
    private byte[] rIDBytes;

    private String keyID;
    @Transient
    private byte keyIDByte;

    private String certificateSN;
    @Transient
    private byte[] certificateSNBytes;

    @Generated(hash = 664269616)
    public CapkRevokeBean(Long id, String rID, String keyID, String certificateSN) {
        this.id = id;
        this.rID = rID;
        this.keyID = keyID;
        this.certificateSN = certificateSN;
    }

    @Generated(hash = 250560995)
    public CapkRevokeBean() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getrID() {
        return rID;
    }

    public void setrID(String rID) {
        this.rID = rID;
    }

    public byte[] getrIDBytes() {
        return ConvertUtils.strToBcdPaddingRight(rID);
    }

    public void setrIDBytes(byte[] rIDBytes) {
        this.rIDBytes = rIDBytes;
    }

    public String getKeyID() {
        return keyID;
    }

    public void setKeyID(String keyID) {
        this.keyID = keyID;
    }

    public byte getKeyIDByte() {
        return ConvertUtils.strToBcdPaddingLeft(keyID)[0];
    }

    public void setKeyIDByte(byte keyIDByte) {
        this.keyIDByte = keyIDByte;
    }

    public String getCertificateSN() {
        return certificateSN;
    }

    public void setCertificateSN(String certificateSN) {
        this.certificateSN = certificateSN;
    }

    public byte[] getCertificateSNBytes() {
        return ConvertUtils.strToBcdPaddingRight(certificateSN);
    }

    public void setCertificateSNBytes(byte[] certificateSNBytes) {
        this.certificateSNBytes = certificateSNBytes;
    }

    public String getRID() {
        return this.rID;
    }

    public void setRID(String rID) {
        this.rID = rID;
    }
}
