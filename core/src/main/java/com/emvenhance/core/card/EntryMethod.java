package com.emvenhance.core.card;

/**
 * Card entry method selected by the vendor's card search ({@code onSearchCard}).
 *
 * <p>After the terminal reports one of these, {@link EmvBehavior#start} runs the matching
 * transaction flow.
 */
public enum EntryMethod {
    CHIP,
    CONTACTLESS,
    MAGSTRIPE,
    MANUAL,
    /** Chip + contactless + mag — terminal selects the first presented. */
    ANY
}
