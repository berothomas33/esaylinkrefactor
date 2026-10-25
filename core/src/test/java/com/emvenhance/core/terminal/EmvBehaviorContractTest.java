package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

/**
 * {@link EmvBehavior} is what the engine drives — the lifecycle, nothing else. The EMV step
 * methods and {@code readIccTag} belong to {@link AbstractEmvBehavior}, protected, so a step runs
 * only through {@code goToStep}.
 */
public class EmvBehaviorContractTest {

    @Test
    public void theEngineSeesOnlyTheLifecycle() {
        Set<String> names = new TreeSet<>();
        for (Method method : EmvBehavior.class.getMethods()) {
            names.add(method.getName());
        }

        assertEquals(new TreeSet<>(Arrays.asList("attach", "dispose", "engine", "prepare", "start")), names);
    }

    @Test
    public void everyStepMethodAndReadIccTagIsProtected() {
        int found = 0;
        for (Method method : AbstractEmvBehavior.class.getDeclaredMethods()) {
            String name = method.getName();
            if (name.startsWith("on") || name.equals("readIccTag")) {
                found++;
                assertTrue(name + " is not protected", Modifier.isProtected(method.getModifiers()));
            }
        }

        assertEquals("17 step methods + readIccTag", 18, found);
    }
}
