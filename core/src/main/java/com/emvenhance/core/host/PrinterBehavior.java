package com.emvenhance.core.host;

import io.reactivex.rxjava3.core.Completable;
import java.util.List;

/**
 * Receipt printer port. Owned by {@link com.emvenhance.core.terminal.PosTerminal}.
 */
public interface PrinterBehavior {

    Completable print(List<String> lines);
}
