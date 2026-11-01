package com.emvenhance.core.terminal;

public final class TerminalBusyException extends IllegalStateException {

    public TerminalBusyException() {
        super("POS terminal is busy: the previous transaction hasn't finished");
    }
}
