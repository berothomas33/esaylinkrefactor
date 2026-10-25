package com.emvenhance.core.terminal;

import com.emvenhance.core.engine.EmvEngine;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An {@link EmvBehavior} stub for terminal tests: keeps the engine {@code attach} gives it and
 * returns it from {@code engine()}, as every behavior does; every other call goes to
 * {@code calls}.
 */
final class StubBehavior {

    private StubBehavior() { }

    static EmvBehavior of(InvocationHandler calls) {
        AtomicReference<EmvEngine> engine = new AtomicReference<>();
        return (EmvBehavior) Proxy.newProxyInstance(EmvBehavior.class.getClassLoader(),
                new Class<?>[] {EmvBehavior.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "attach":
                            engine.set((EmvEngine) args[0]);
                            return null;
                        case "engine":
                            return engine.get();
                        default:
                            return calls.invoke(proxy, method, args);
                    }
                });
    }
}
