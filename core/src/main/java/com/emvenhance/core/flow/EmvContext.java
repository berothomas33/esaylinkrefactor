package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;

/**
 * All mutable state for exactly one transaction.
 *
 * <p>Because state lives here rather than in fields, {@link EmvStepBehavior} implementations
 * are stateless singletons — thread-safe, reusable across transactions, and testable without
 * constructing a terminal or a kernel.
 */
public interface EmvContext {

    @NonNull
    TransactionConfig config();

    /** The card selected by {@code PosTerminal.searchCard}, or {@code null} before search. */
    @Nullable
    CardPresence card();

    void setCard(@NonNull CardPresence card);

    /** TLVs read from the card during this transaction. */
    @NonNull
    EmvTagStore tags();

    /** Host, printer and PIN access. */
    @NonNull
    ServiceGateway services();

    @NonNull
    CancellationSignal cancellation();

    /** Typed scratch space shared between steps. */
    <T> void put(@NonNull ContextKey<T> key, @NonNull T value);

    @Nullable
    <T> T get(@NonNull ContextKey<T> key);

    @NonNull
    <T> T getOrDefault(@NonNull ContextKey<T> key, @NonNull T fallback);
}
