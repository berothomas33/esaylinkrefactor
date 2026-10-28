/**
 * The POS machine: {@code PosTerminal} (and its {@code Transaction}), and the vendor seam it drives —
 * {@code EmvBehavior} / {@code AbstractEmvBehavior}, kept here because the behavior reports to the
 * transaction through package-private calls only the terminal may make.
 */
package com.emvenhance.core.terminal;
