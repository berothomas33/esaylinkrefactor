package com.pax.bizentity.entity;

import androidx.annotation.NonNull;
import java.io.Serializable;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;
import org.greenrobot.greendao.annotation.Unique;

@Entity(nameInDb = "issuer")
public class Issuer implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String ID_FIELD_NAME = "issuer_id";
    public static final String NAME_FIELD_NAME = "issuer_name";

    @Id(autoincrement = true)
    @Property(nameInDb = ID_FIELD_NAME)
    private Long id;

    @Property(nameInDb = NAME_FIELD_NAME)
    @Unique
    private String name;

    private long floorLimit;

    private float adjustPercent;

    private String panMaskPattern;

    private boolean isEnableAdjust = true;

    private boolean isEnableOffline = true;

    private boolean isAllowExpiry = true;

    private boolean isAllowManualPan = true;

    private boolean isAllowCheckExpiry = false;

    private boolean isAllowPrint = true;

    private boolean isAllowCheckPanMod10 = true;

    private boolean isRequirePIN = true;

    private boolean isRequireMaskExpiry = true;

    public Issuer() {
    }

    public Issuer(String name) {
        this.setName(name);
    }

    public Issuer(Long id, String name) {
        this.setId(id);
        this.setName(name);
    }

    @Generated(hash = 1280116958)
    public Issuer(Long id, String name, long floorLimit, float adjustPercent, String panMaskPattern,
                  boolean isEnableAdjust, boolean isEnableOffline, boolean isAllowExpiry, boolean isAllowManualPan,
                  boolean isAllowCheckExpiry, boolean isAllowPrint, boolean isAllowCheckPanMod10, boolean isRequirePIN,
                  boolean isRequireMaskExpiry) {
        this.id = id;
        this.name = name;
        this.floorLimit = floorLimit;
        this.adjustPercent = adjustPercent;
        this.panMaskPattern = panMaskPattern;
        this.isEnableAdjust = isEnableAdjust;
        this.isEnableOffline = isEnableOffline;
        this.isAllowExpiry = isAllowExpiry;
        this.isAllowManualPan = isAllowManualPan;
        this.isAllowCheckExpiry = isAllowCheckExpiry;
        this.isAllowPrint = isAllowPrint;
        this.isAllowCheckPanMod10 = isAllowCheckPanMod10;
        this.isRequirePIN = isRequirePIN;
        this.isRequireMaskExpiry = isRequireMaskExpiry;
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

    public long getFloorLimit() {
        return floorLimit;
    }

    public void setFloorLimit(long floorLimit) {
        this.floorLimit = floorLimit;
    }

    public float getAdjustPercent() {
        return adjustPercent;
    }

    public void setAdjustPercent(float adjustPercent) {
        this.adjustPercent = adjustPercent;
    }

    public String getPanMaskPattern() {
        return panMaskPattern;
    }

    public void setPanMaskPattern(String panMaskPattern) {
        this.panMaskPattern = panMaskPattern;
    }

    public boolean isEnableAdjust() {
        return isEnableAdjust;
    }

    public boolean isEnableOffline() {
        return isEnableOffline;
    }

    public boolean isAllowExpiry() {
        return isAllowExpiry;
    }

    public boolean isAllowManualPan() {
        return isAllowManualPan;
    }

    public void setAllowManualPan(boolean allowManualPan) {
        isAllowManualPan = allowManualPan;
    }

    public boolean isAllowCheckExpiry() {
        return isAllowCheckExpiry;
    }

    public boolean isAllowPrint() {
        return isAllowPrint;
    }

    public boolean isAllowCheckPanMod10() {
        return isAllowCheckPanMod10;
    }

    public boolean isRequirePIN() {
        return isRequirePIN;
    }

    public boolean isRequireMaskExpiry() {
        return isRequireMaskExpiry;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && getClass() == obj.getClass() && ((Issuer) obj).getName().equals(getName());
    }

    @Override
    public int hashCode() {
        return 17 * id.hashCode() + name.hashCode();
    }

    public void update(@NonNull Issuer issuer) {
        adjustPercent = issuer.getAdjustPercent();
        floorLimit = issuer.getFloorLimit();
        panMaskPattern = issuer.getPanMaskPattern();
        isAllowCheckExpiry = issuer.isAllowCheckExpiry;
        isAllowCheckPanMod10 = issuer.isAllowCheckPanMod10();
        isAllowExpiry = issuer.isAllowExpiry();
        isAllowManualPan = issuer.isAllowManualPan();
        isAllowPrint = issuer.isAllowPrint();
        isEnableAdjust = issuer.isEnableAdjust();
        isEnableOffline = issuer.isEnableOffline();
        isRequireMaskExpiry = issuer.isRequireMaskExpiry();
        isRequirePIN = issuer.isRequirePIN();
    }

    public boolean getIsEnableAdjust() {
        return this.isEnableAdjust;
    }

    public void setIsEnableAdjust(boolean isEnableAdjust) {
        this.isEnableAdjust = isEnableAdjust;
    }

    public boolean getIsEnableOffline() {
        return this.isEnableOffline;
    }

    public void setIsEnableOffline(boolean isEnableOffline) {
        this.isEnableOffline = isEnableOffline;
    }

    public boolean getIsAllowExpiry() {
        return this.isAllowExpiry;
    }

    public void setIsAllowExpiry(boolean isAllowExpiry) {
        this.isAllowExpiry = isAllowExpiry;
    }

    public boolean getIsAllowManualPan() {
        return this.isAllowManualPan;
    }

    public void setIsAllowManualPan(boolean isAllowManualPan) {
        this.isAllowManualPan = isAllowManualPan;
    }

    public boolean getIsAllowCheckExpiry() {
        return this.isAllowCheckExpiry;
    }

    public void setIsAllowCheckExpiry(boolean isAllowCheckExpiry) {
        this.isAllowCheckExpiry = isAllowCheckExpiry;
    }

    public boolean getIsAllowPrint() {
        return this.isAllowPrint;
    }

    public void setIsAllowPrint(boolean isAllowPrint) {
        this.isAllowPrint = isAllowPrint;
    }

    public boolean getIsAllowCheckPanMod10() {
        return this.isAllowCheckPanMod10;
    }

    public void setIsAllowCheckPanMod10(boolean isAllowCheckPanMod10) {
        this.isAllowCheckPanMod10 = isAllowCheckPanMod10;
    }

    public boolean getIsRequirePIN() {
        return this.isRequirePIN;
    }

    public void setIsRequirePIN(boolean isRequirePIN) {
        this.isRequirePIN = isRequirePIN;
    }

    public boolean getIsRequireMaskExpiry() {
        return this.isRequireMaskExpiry;
    }

    public void setIsRequireMaskExpiry(boolean isRequireMaskExpiry) {
        this.isRequireMaskExpiry = isRequireMaskExpiry;
    }
}
