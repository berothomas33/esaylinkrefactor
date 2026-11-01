package com.pax.configservice.xml;

import androidx.annotation.Nullable;
import com.pax.bizentity.entity.EmvAid;
import java.util.ArrayList;
import java.util.List;

public final class TerminalWideValues {

    private static final int RID_HEX_LENGTH = 10;

    private final List<EmvAid> contactAids;

    public TerminalWideValues(@Nullable List<EmvAid> contactAids) {
        this.contactAids = contactAids != null ? contactAids : new ArrayList<>();
    }

    @Nullable
    public String terminalType(@Nullable String aid) {
        for (EmvAid contact : candidates(aid)) {
            if (!blank(contact.getTerminalType())) {
                return contact.getTerminalType();
            }
        }
        return null;
    }

    @Nullable
    public String additionalCapability(@Nullable String aid) {
        for (EmvAid contact : candidates(aid)) {
            if (!blank(contact.getTerminalAdditionalCapability())) {
                return contact.getTerminalAdditionalCapability();
            }
        }
        return null;
    }

    @Nullable
    public String securityCapability(@Nullable String aid) {
        for (EmvAid contact : candidates(aid)) {
            String capability = contact.getTerminalCapability();
            if (capability != null && capability.trim().length() == 6) {
                return capability.trim().substring(4, 6);
            }
        }
        return null;
    }

    private List<EmvAid> candidates(@Nullable String aid) {
        String target = aid == null ? "" : aid.trim().toUpperCase();
        String rid = target.length() >= RID_HEX_LENGTH ? target.substring(0, RID_HEX_LENGTH) : target;
        List<EmvAid> sameAid = new ArrayList<>();
        List<EmvAid> sameRid = new ArrayList<>();
        List<EmvAid> rest = new ArrayList<>();
        for (EmvAid contact : contactAids) {
            String other = contact.getAid() == null ? "" : contact.getAid().trim().toUpperCase();
            if (!target.isEmpty() && other.equals(target)) {
                sameAid.add(contact);
            } else if (!rid.isEmpty() && other.startsWith(rid)) {
                sameRid.add(contact);
            } else {
                rest.add(contact);
            }
        }
        sameAid.addAll(sameRid);
        sameAid.addAll(rest);
        return sameAid;
    }

    private static boolean blank(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }
}
