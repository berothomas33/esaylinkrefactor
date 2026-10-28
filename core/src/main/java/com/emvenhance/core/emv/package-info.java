/**
 * The EMV layer every vendor shares: the EMV steps bus ({@code EmvEngine}, {@code EmvStep},
 * {@code EmvStepEvent}), what the card gives the host ({@code EmvTransactionResult},
 * {@code Field55}) and the APDU trace. The vendor seam itself ({@code AbstractEmvBehavior}) stays in
 * {@code terminal}, next to the {@code PosTerminal.Transaction} it reports to.
 */
package com.emvenhance.core.emv;
