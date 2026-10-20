package com.emvenhance.core.terminal

/**
 * A transaction start refused: the previous transaction hasn't finished with the reader yet
 * (usually a cancelled search still stopping). Nothing of the refused one runs — see
 * [PosTerminal.startTransaction].
 */
class TerminalBusyException :
    IllegalStateException("POS terminal is busy: the previous transaction hasn't finished")
