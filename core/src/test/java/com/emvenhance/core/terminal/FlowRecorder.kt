package com.emvenhance.core.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

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

/** Waits until `count` recorded steps match `predicate`; `false` on timeout. */
internal fun <T> FlowRecorder<T>.await(count: Int = 1, seconds: Long = 5, predicate: (T) -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
    while (values.count(predicate) < count) {
        if (System.nanoTime() > deadline) return false
        Thread.sleep(5)
    }
    return true
}
