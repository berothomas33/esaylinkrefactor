package com.emvenhance.core.card

/**
 * Card entry method selected by [com.emvenhance.core.terminal.PosTerminal.searchCard].
 *
 * After the terminal reports one of these, [com.emvenhance.core.terminal.EmvBehavior.start]
 * runs the matching transaction flow.
 */
enum class EntryMethod {
    CHIP,
    CONTACTLESS,
    MAGSTRIPE,
    MANUAL,

    /** Chip + contactless + mag — terminal selects the first presented. */
    ANY,
}
