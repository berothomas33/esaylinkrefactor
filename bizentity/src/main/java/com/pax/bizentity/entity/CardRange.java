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
 * card range table
 */
@Entity(nameInDb = "card_range")
public class CardRange implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ID_FIELD_NAME = "card_id";
    public static final String NAME_FIELD_NAME = "card_name";
    private static final String ISSUER_NAME = "issuer_name";
    public static final String RANGE_LOW_FIELD_NAME = "card_range_low";
    public static final String RANGE_HIGH_FIELD_NAME = "card_range_high";
    public static final String LENGTH_FIELD_NAME = "card_length";

    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    private Long id;

    @Property(nameInDb = NAME_FIELD_NAME)
    private String name;

    @Property(nameInDb = ISSUER_NAME)
    private String issuerName;

    @Property(nameInDb = RANGE_LOW_FIELD_NAME)
    @NotNull
    @Unique
    private String panRangeLow;

    @Property(nameInDb = RANGE_HIGH_FIELD_NAME)
    @NotNull
    @Unique
    private String panRangeHigh;

    @Property(nameInDb = LENGTH_FIELD_NAME)
    private int panLength;

    // Plain reference (no join): load the issuer by id when needed.
    private long issuerId;

    public CardRange() {
    }

    @Generated(hash = 1881461910)
    public CardRange(Long id, String name, String issuerName, @NotNull String panRangeLow, @NotNull String panRangeHigh, int panLength,
            long issuerId) {
        this.id = id;
        this.name = name;
        this.issuerName = issuerName;
        this.panRangeLow = panRangeLow;
        this.panRangeHigh = panRangeHigh;
        this.panLength = panLength;
        this.issuerId = issuerId;
    }

    public void update(@NonNull CardRange cardRange) {
        name = cardRange.getName();
        panLength = cardRange.getPanLength();
        issuerName = cardRange.getIssuerName();
        issuerId = cardRange.getIssuerId();
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public void setIssuerName(String issuerName) {
        this.issuerName = issuerName;
    }

    public String getPanRangeLow() {
        return this.panRangeLow;
    }

    public void setPanRangeLow(String panRangeLow) {
        this.panRangeLow = panRangeLow;
    }

    public String getPanRangeHigh() {
        return this.panRangeHigh;
    }

    public void setPanRangeHigh(String panRangeHigh) {
        this.panRangeHigh = panRangeHigh;
    }

    public int getPanLength() {
        return this.panLength;
    }

    public void setPanLength(int panLength) {
        this.panLength = panLength;
    }

    public long getIssuerId() {
        return this.issuerId;
    }

    public void setIssuerId(long issuerId) {
        this.issuerId = issuerId;
    }
}
