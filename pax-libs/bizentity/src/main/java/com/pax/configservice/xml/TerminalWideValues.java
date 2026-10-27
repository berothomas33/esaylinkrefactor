package com.pax.configservice.xml;

import androidx.annotation.Nullable;
import com.pax.bizentity.entity.EmvAid;
import java.util.ArrayList;
import java.util.List;

/**
 * Terminal-wide EMV values from the contact parameters (each contact {@link EmvAid} carries its
 * ICS profile from {@code emv_param.emv}): Terminal Type (9F35), Additional Terminal
 * Capabilities (9F40) and Security Capability (Terminal Capabilities 9F33 byte 3). The
 * contactless XML carries these per scheme only for some schemes (PAYPASSCONFIGURATION,
 * EXPRESSPAYCONFIGURATION) — PayWave has none — so {@link ClssXmlParamParser} takes them from
 * here when a scheme's own block doesn't set them.
 *
 * <p>Lookup for a contactless AID: the contact AID with the same AID, else the same RID (first
 * 5 bytes — same scheme, same ICS profile), else any contact AID — the value is terminal-wide.
 */
public final class TerminalWideValues {

    private static final int RID_HEX_LENGTH = 10;

    private final List<EmvAid> contactAids;

    public TerminalWideValues(@Nullable List<EmvAid> contactAids) {
        this.contactAids = contactAids != null ? contactAids : new ArrayList<>();
    }

    /** Terminal Type (9F35), e.g. "22". */
    @Nullable
    public String terminalType(@Nullable String aid) {
        for (EmvAid contact : candidates(aid)) {
            if (!blank(contact.getTerminalType())) {
                return contact.getTerminalType();
            }
        }
        return null;
    }

    /** Additional Terminal Capabilities (9F40), e.g. "FF80F0A001". */
    @Nullable
    public String additionalCapability(@Nullable String aid) {
        for (EmvAid contact : candidates(aid)) {
            if (!blank(contact.getTerminalAdditionalCapability())) {
                return contact.getTerminalAdditionalCapability();
            }
        }
        return null;
    }

    /** Security Capability — byte 3 of Terminal Capabilities (9F33), e.g. "C8". */
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

    /** Contact AIDs in lookup order: same AID, then same RID, then all the rest. */
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
