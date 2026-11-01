package com.pax.bizentity.entity;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Unique;

@Entity(nameInDb = "card_bin_black")
public class CardBinBlack {
    public static final String ID_FIELD_NAME = "id";
    public static final String BIN_FIELD_NAME = "card_bin";

    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    protected Long id;
    @Property(nameInDb = BIN_FIELD_NAME)
    @Unique
    protected String bin;
    protected int cardNoLen;
    @Generated(hash = 1079076009)
    public CardBinBlack(Long id, String bin, int cardNoLen) {
        this.id = id;
        this.bin = bin;
        this.cardNoLen = cardNoLen;
    }
    @Generated(hash = 1478329469)
    public CardBinBlack() {
    }
    public Long getId() {
        return this.id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getBin() {
        return this.bin;
    }
    public void setBin(String bin) {
        this.bin = bin;
    }
    public int getCardNoLen() {
        return this.cardNoLen;
    }
    public void setCardNoLen(int cardNoLen) {
        this.cardNoLen = cardNoLen;
    }
}
