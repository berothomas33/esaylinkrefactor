package com.emvenhance.core.terminal

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Collects a flow on the calling thread (`Dispatchers.Unconfined`, started undispatched — like
 * `viewModelScope`'s `Main.immediate` on the main thread) and records what it got: the test's
 * version of Rx's `TestObserver`. [cancel] is the screen going away.
 */
internal class FlowRecorder<T>(flow: Flow<T>) {

    val values: MutableList<T> = CopyOnWriteArrayList()

    @Volatile
    var error: Throwable? = null
        private set

    @Volatile
    var completed = false
        private set

    private val done = CountDownLatch(1)

    private val job = GlobalScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try {
            flow.collect { values.add(it) }
            completed = true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            error = t
        } finally {
            done.countDown()
        }
    }

    fun cancel() {
        job.cancel()
    }

    /** Waits for the flow to complete or fail; `false` on timeout. */
    fun await(seconds: Long): Boolean = done.await(seconds, TimeUnit.SECONDS)

    fun assertError(type: Class<out Throwable>) = apply {
        assertTrue("expected $type, got $error", type.isInstance(error))
    }

    fun assertNoErrors() = apply {
        assertNull("got $error", error)
    }

    fun assertComplete() = apply {
        assertTrue("not complete", completed)
    }

    fun assertNotComplete() = apply {
        assertTrue("completed", !completed)
    }

    fun assertNoValues() = apply {
        assertTrue("got $values", values.isEmpty())
    }

    fun assertValueCount(count: Int) = apply {
        assertEquals("got $values", count, values.size)
    }
}

internal fun <T> Flow<T>.record(): FlowRecorder<T> = FlowRecorder(this)
