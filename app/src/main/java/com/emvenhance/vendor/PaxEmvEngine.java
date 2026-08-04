package com.emvenhance.vendor;

import com.emvenhance.core.EmvEngine;

/**
 * PAX EMV engine — owns overridable EMV lifecycle hooks for the PAX vendor.
 *
 * <p>SDK adaptation lives in {@link PaxEmvBehavior}. Card search lives in {@link PaxTerminal}.
 */
public class PaxEmvEngine extends EmvEngine {
    // Vendors override onSearchCard / onCardDetected / onOnlineProcessing / … as needed.
}
