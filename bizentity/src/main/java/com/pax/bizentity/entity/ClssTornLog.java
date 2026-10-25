package com.pax.bizentity.entity;

import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.Property;

@Entity(nameInDb = "clsstornlog")
public class ClssTornLog implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ID_FIELD_NAME = "id";

    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    protected Long id;
    @NotNull
    private String aucPan;
    @NotNull
    private int panLen;
    @NotNull
    private boolean panSeqFlg;
    @NotNull
    private byte panSeq;
    @NotNull
    private String aucTornData;
    @NotNull
    private int tornDataLen;

    public ClssTornLog() {
    }

    @Generated(hash = 2131456756)
    public ClssTornLog(Long id, @NotNull String aucPan, int panLen,
                       boolean panSeqFlg, byte panSeq, @NotNull String aucTornData,
                       int tornDataLen) {
        this.id = id;
        this.aucPan = aucPan;
        this.panLen = panLen;
        this.panSeqFlg = panSeqFlg;
        this.panSeq = panSeq;
        this.aucTornData = aucTornData;
        this.tornDataLen = tornDataLen;
    }

    public String getAucPan() {
        return aucPan;
    }

    public void setAucPan(String aucPan) {
        this.aucPan = aucPan;
    }

    public int getPanLen() {
        return panLen;
    }

    public void setPanLen(int panLen) {
        this.panLen = panLen;
    }

    public boolean getPanSeqFlg() {
        return panSeqFlg;
    }

    public void setPanSeqFlg(boolean panSeqFlg) {
        this.panSeqFlg = panSeqFlg;
    }

    public byte getPanSeq() {
        return panSeq;
    }

    public void setPanSeq(byte panSeq) {
        this.panSeq = panSeq;
    }

    public String getAucTornData() {
        return aucTornData;
    }

    public void setAucTornData(String aucTornData) {
        this.aucTornData = aucTornData;
    }

    public int getTornDataLen() {
        return tornDataLen;
    }

    public void setTornDataLen(int tornDataLen) {
        this.tornDataLen = tornDataLen;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
