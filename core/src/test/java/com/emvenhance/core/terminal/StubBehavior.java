package com.emvenhance.core.terminal;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * An {@link EmvBehavior} stub for terminal tests: every call goes to {@code calls}. The engine is
 * the terminal's ({@code PosTerminal#engine}); a stub that needs it reads it from the
 * transaction it is handed ({@link PosTerminal.Transaction#engine()}).
 */
final class StubBehavior {

    private StubBehavior() { }

    static EmvBehavior of(InvocationHandler calls) {
        return (EmvBehavior) Proxy.newProxyInstance(EmvBehavior.class.getClassLoader(),
                new Class<?>[] {EmvBehavior.class}, calls);
    }
}
