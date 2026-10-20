package com.emvenhance.core.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Collects a flow on the publishing thread (`Dispatchers.Unconfined`, started undispatched) and
 * records every value — the test's version of an Rx `subscribe(list::add)`: a step is in
 * [values] before the publish that emitted it returns.
 */
internal class FlowRecorder<T>(scope: CoroutineScope, flow: Flow<T>) {

    val values: MutableList<T> = CopyOnWriteArrayList()

    private val job = scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        flow.collect { values.add(it) }
    }

    fun cancel() {
        job.cancel()
    }
}

internal fun <T> Flow<T>.recordIn(scope: CoroutineScope): FlowRecorder<T> = FlowRecorder(scope, this)

/**
 * One call of a suspending transaction start, launched on the calling thread
 * (`Dispatchers.Unconfined`, undispatched — like `viewModelScope`'s `Main.immediate` on the main
 * thread): the test's version of Rx's `TestObserver<Void>`. [cancel] is the screen going away.
 */
internal class TestSale(scope: CoroutineScope, start: suspend () -> Unit) {

    @Volatile
    var error: Throwable? = null
        private set

    @Volatile
    var completed = false
        private set

    private val done = CountDownLatch(1)

    private val job = scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try {
            start()
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

    /** Waits for the start to return or fail; `false` on timeout. */
    fun await(seconds: Long): Boolean = done.await(seconds, TimeUnit.SECONDS)

    fun assertError(type: Class<out Throwable>) = apply {
        assertTrue("expected $type, got $error", type.isInstance(error))
    }

    fun assertNoErrors() = apply {
        assertTrue("got $error", error == null)
    }

    fun assertComplete() = apply {
        assertTrue("not complete", completed)
    }

    fun assertNotComplete() = apply {
        assertTrue("completed", !completed)
    }
}
