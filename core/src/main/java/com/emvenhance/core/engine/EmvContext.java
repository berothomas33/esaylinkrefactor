package com.emvenhance.core.engine;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.host.AuthResult;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable per-transaction blackboard shared by all step behaviors.
 * Never holds Android {@code Context} or UI references.
 */
public final class EmvContext {

    public static final String KEY_CANDIDATES = "candidates";
    public static final String KEY_SELECTED_AID = "selectedAid";
    public static final String KEY_ODA_METHOD = "odaMethod";
    public static final String KEY_CVM_METHOD = "cvmMethod";
    public static final String KEY_ONLINE_REQUIRED = "onlineRequired";
    public static final String KEY_OFFLINE_APPROVED = "offlineApproved";
    public static final String KEY_APPROVED = "approved";
    public static final String KEY_RESULT_MESSAGE = "resultMessage";
    public static final String KEY_PAN = "pan";
    public static final String KEY_ISSUER = "issuer";
    public static final String KEY_CARDHOLDER = "cardholder";
    public static final String KEY_EMIT_ISSUER_STEPS = "emitIssuerSteps";
    public static final String KEY_PIN_ONLINE = "pinOnline";
    public static final String KEY_PIN_BYPASS = "pinBypass";
    public static final String KEY_PIN_TRIES = "pinTries";

    @Nullable
    private TransactionConfig config;
    @Nullable
    private CardPresence card;
    @Nullable
    private EmvStep currentStep;
    @Nullable
    private AuthResult hostResult;
    private boolean cancelled;
    private int retryCount;

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Map<String, byte[]> tlv = new ConcurrentHashMap<>();

    public void reset(TransactionConfig config) {
        this.config = config;
        this.card = null;
        this.currentStep = EmvStep.TERMINAL_INITIALIZATION;
        this.hostResult = null;
        this.cancelled = false;
        this.retryCount = 0;
        this.attributes.clear();
        this.tlv.clear();
    }

    @Nullable
    public TransactionConfig getConfig() {
        return config;
    }

    public void setConfig(TransactionConfig config) {
        this.config = config;
    }

    @Nullable
    public CardPresence getCard() {
        return card;
    }

    public void setCard(CardPresence card) {
        this.card = card;
    }

    @Nullable
    public EntryMethod getEntryMethod() {
        return card != null ? card.getEntryMethod() : null;
    }

    @Nullable
    public EmvStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(EmvStep currentStep) {
        this.currentStep = currentStep;
    }

    @Nullable
    public AuthResult getHostResult() {
        return hostResult;
    }

    public void setHostResult(@Nullable AuthResult hostResult) {
        this.hostResult = hostResult;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        retryCount++;
    }

    public void resetRetryCount() {
        retryCount = 0;
    }

    public void put(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T get(String key) {
        return (T) attributes.get(key);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object v = attributes.get(key);
        return v instanceof Boolean ? (Boolean) v : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        Object v = attributes.get(key);
        return v instanceof Number ? ((Number) v).intValue() : defaultValue;
    }

    @Nullable
    public String getString(String key) {
        Object v = attributes.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getCandidates() {
        Object v = attributes.get(KEY_CANDIDATES);
        if (v instanceof List) {
            return (List<String>) v;
        }
        return Collections.emptyList();
    }

    public void putTlv(String tag, byte[] value) {
        if (value == null) {
            tlv.remove(tag);
        } else {
            tlv.put(tag, value);
        }
    }

    @Nullable
    public byte[] getTlv(String tag) {
        return tlv.get(tag);
    }

    public Map<String, byte[]> tlvSnapshot() {
        return Collections.unmodifiableMap(new HashMap<>(tlv));
    }
}
