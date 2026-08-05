package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TLVs collected during a transaction, keyed by EMV tag (e.g. {@code "5A"}, {@code "9F02"}).
 *
 * <p>Values are hex strings so this stays vendor-neutral — each vendor converts from its own
 * kernel representation on the way in.
 */
public final class EmvTagStore {

    private final Map<String, String> tags = new LinkedHashMap<>();

    public synchronized void put(@NonNull String tag, @Nullable String hexValue) {
        if (hexValue != null) {
            tags.put(tag.toUpperCase(), hexValue);
        }
    }

    @Nullable
    public synchronized String get(@NonNull String tag) {
        return tags.get(tag.toUpperCase());
    }

    public synchronized boolean has(@NonNull String tag) {
        return tags.containsKey(tag.toUpperCase());
    }

    @NonNull
    public synchronized Map<String, String> asMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(tags));
    }

    public synchronized void clear() {
        tags.clear();
    }
}
