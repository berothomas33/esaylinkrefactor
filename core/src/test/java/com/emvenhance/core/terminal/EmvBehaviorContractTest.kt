package com.emvenhance.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.util.TreeSet

/**
 * [EmvBehavior] is what the engine drives — the lifecycle, nothing else. The EMV step methods
 * and `readIccTag` belong to [AbstractEmvBehavior], protected, so a step runs only through
 * `goToStep`.
 */
class EmvBehaviorContractTest {

    @Test
    fun theEngineSeesOnlyTheLifecycle() {
        val names = EmvBehavior::class.java.methods.mapTo(TreeSet()) { it.name }

        assertEquals(TreeSet(listOf("dispose", "prepare", "start")), names)
    }

    @Test
    fun everyStepMethodAndReadIccTagIsProtected() {
        var found = 0
        for (method in AbstractEmvBehavior::class.java.declaredMethods) {
            val name = method.name
            if (name.startsWith("on") || name == "readIccTag") {
                found++
                assertTrue("$name is not protected", Modifier.isProtected(method.modifiers))
            }
        }

        assertEquals("17 step methods + readIccTag", 18, found)
    }
}
