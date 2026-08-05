package com.emvenhance.core.card;

/**
 * Card entry method selected by {@link PosTerminal#searchCard}.
 *
 * <p>After the terminal reports one of these, {@link EmvBehavior#start} runs the matching
 * transaction flow.
 */
public enum EntryMethod {
    CHIP,
    CONTACTLESS,
    MAGSTRIPE,
    MANUAL
}
