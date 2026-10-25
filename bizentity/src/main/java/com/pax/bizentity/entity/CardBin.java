package com.pax.bizentity.entity;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Unique;

@Entity(nameInDb = "card_bin")
public class CardBin {
    public static final String ID_FIELD_NAME = "id";
    public static final String BIN_FIELD_NAME = "card_bin";

    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    protected Long id;
    @Property(nameInDb = BIN_FIELD_NAME)
    @Unique
    protected String bin;
    protected int cardNoLen;

    public CardBin() {
    }

    public CardBin(String bin, int cardNoLen) {
        this.bin = bin;
        this.cardNoLen = cardNoLen;
    }

    @Generated(hash = 1305431416)
    public CardBin(Long id, String bin, int cardNoLen) {
        this.id = id;
        this.bin = bin;
        this.cardNoLen = cardNoLen;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBin() {
        return bin;
    }

    public void setBin(String bin) {
        this.bin = bin;
    }

    public int getCardNoLen() {
        return cardNoLen;
    }

    public void setCardNoLen(int cardNoLen) {
        this.cardNoLen = cardNoLen;
    }

}
