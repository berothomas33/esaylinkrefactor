package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import java.util.HashMap;
import java.util.Map;

/** Default {@link EmvContext} — one instance per transaction, created by the terminal. */
public final class TransactionContext implements EmvContext {

    private final TransactionConfig config;
    private final ServiceGateway services;
    private final CancellationSignal cancellation;
    private final EmvTagStore tags = new EmvTagStore();
    private final Map<ContextKey<?>, Object> attributes = new HashMap<>();

    @Nullable
    private volatile CardPresence card;

    public TransactionContext(@NonNull TransactionConfig config,
            @NonNull ServiceGateway services,
            @NonNull CancellationSignal cancellation) {
        this.config = config;
        this.services = services;
        this.cancellation = cancellation;
    }

    @NonNull
    @Override
    public TransactionConfig config() {
        return config;
    }

    @Nullable
    @Override
    public CardPresence card() {
        return card;
    }

    @Override
    public void setCard(@NonNull CardPresence card) {
        this.card = card;
    }

    @NonNull
    @Override
    public EmvTagStore tags() {
        return tags;
    }

    @NonNull
    @Override
    public ServiceGateway services() {
        return services;
    }

    @NonNull
    @Override
    public CancellationSignal cancellation() {
        return cancellation;
    }

    @Override
    public synchronized <T> void put(@NonNull ContextKey<T> key, @NonNull T value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public synchronized <T> T get(@NonNull ContextKey<T> key) {
        return (T) attributes.get(key);
    }

    @NonNull
    @Override
    public synchronized <T> T getOrDefault(@NonNull ContextKey<T> key, @NonNull T fallback) {
        T value = get(key);
        return value != null ? value : fallback;
    }
}
