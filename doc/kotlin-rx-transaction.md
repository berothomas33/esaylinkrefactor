# kotlin-rx-transaction.md — the Rx transaction stream → Kotlin Flow

Companion to `claudekotlin.md`, for the `rxTransactionDesign` branch: how
`PosTerminal.startTransaction(): Observable<TransactionStepEvent>` translates to Kotlin when
Phase 2 (`core`) and Phase 3 (`app/src/main`) of the migration get there. Read `CLAUDE.md` and
`claudekotlin.md` first; their rules (one thread, synchronous dispatch, never interrupt, fail
closed) all still apply. This file changes nothing on its own: it is the plan for that phase.

---

## 1. The design today (Java, `rxTransactionDesign`)

A transaction is a cold stream of its own steps:

| Event | Meaning |
|---|---|
| `subscribe()` | starts it: `current.compareAndSet(null, t)` **on the subscribing thread**, then `scheduleDirect(t::run)` |
| `onNext` | this transaction's steps only (`engine.transactionSteps().skip(1)` forwarded) |
| `onComplete` | `run()` returned **and** freed the terminal (`finally`: stop forwarding → `current = null` → `onComplete`) |
| `onError(TerminalBusyException)` | refused at once; nothing queued |
| `dispose()` | `Transaction.cancel()`: only if `current == t` → flag + `cancelCardSearch()` + `engine.cancel()`; never interrupts |

`run()` is not tied to the subscription: disposed before it began, it still runs, sees
`isOver()`, returns, and frees the terminal. The proofs are in
`PosTerminalTransactionLifecycleTest` (each guard has a test that fails without it).

---

## 2. Mapping

| Java / Rx (this branch) | Kotlin | Why |
|---|---|---|
| `Observable.create { … }` + `source.serialize()` | `callbackFlow { … }` | a `Channel` is thread-safe: steps from the tx thread, a cancel's ERROR from main |
| `emitter.tryOnError(TerminalBusyException())` | `close(TerminalBusyException()); return@callbackFlow` | a closed channel needs no `awaitClose` |
| `engine.transactionSteps().skip(1).subscribe(emitter::onNext)` | `engine.addTransactionStepListener { trySend(it) }` (plain callback) | **synchronous**, no conflation, nothing replayed to skip — see trap 1 and 2 |
| `emitter.setCancellable(transaction::cancel)` | `awaitClose { transaction.cancel() }` | runs on cancel *and* after `close()` — `cancel()` keeps its `current == this` guard |
| `transactionScheduler.scheduleDirect(transaction::run)` | `txExecutor.execute(transaction::run)` | not a child of the flow: it always runs — see trap 4 |
| `finally { steps.dispose(); current = null; emitter.onComplete() }` | `finally { steps.close(); current.compareAndSet(this, null); channel.close() }` | same order: "complete" means "free" |
| `Disposable.dispose()` in the ViewModel | `Job.cancel()` | |
| `CompositeDisposable transactions` | children of `viewModelScope` (or a `MutableList<Job>`) | `onCleared` cancels them by itself |
| `.observeOn(AndroidSchedulers.mainThread())` | collect in `viewModelScope` (`Dispatchers.Main.immediate`) | the `callbackFlow` block then runs on main: busy check on the caller's thread |
| `TestObserver` | `runTest` + `toList()` / `first()` (`kotlinx-coroutines-test`) | |

---

## 3. Sketch (not code yet)

```kotlin
// PosTerminal
private val txExecutor = Executors.newSingleThreadExecutor()
private val current = AtomicReference<Transaction?>(null)

fun startTransaction(config: TransactionConfig): Flow<TransactionStepEvent> = callbackFlow {
    val transaction = Transaction(config, channel)
    // On the collector's thread: refuse at once, never queue behind the transaction thread.
    if (!current.compareAndSet(null, transaction)) {
        close(TerminalBusyException())
        return@callbackFlow
    }
    // A plain synchronous listener: every step, in order, the moment it is published.
    transaction.steps = engine.addTransactionStepListener { trySend(it) }
    // Not tied to this flow: it always runs, so it always frees the terminal.
    txExecutor.execute(transaction::run)
    // Collector cancelled (Cancel, Back, onCleared) — and also after run() closed the channel.
    awaitClose { transaction.cancel() }
}.buffer(Channel.UNLIMITED) // a transaction has a few dozen steps; trySend must never drop one

private inner class Transaction(
    val config: TransactionConfig,
    private val channel: SendChannel<TransactionStepEvent>,
) {
    val cancelled = AtomicBoolean()
    var steps: AutoCloseable = AutoCloseable { }

    fun cancel() {
        if (current.get() !== this) return // done: the reader may be the next one's
        cancelled.set(true)
        cancelCardSearch()
        engine.cancel()
    }

    fun isOver() = cancelled.get() || current.get() !== this

    fun run() { // blocking, on the transaction thread — never suspend here
        try {
            if (isOver() || !engine.begin()) return
            triggerSearch(this, config)
        } catch (t: Throwable) {
            t.printStackTrace()
            if (!isOver()) engine.notifyError(t.message ?: "Transaction failed")
        } finally {
            steps.close()
            current.compareAndSet(this, null)
            channel.close()
        }
    }
}
```

```kotlin
// MainViewModel
private val _transactionStep =
    MutableSharedFlow<TransactionStepEvent>(replay = 1, extraBufferCapacity = 64)
val transactionStep: SharedFlow<TransactionStepEvent> = _transactionStep // NOT a StateFlow: trap 1

private val _terminalBusy = Channel<Unit>(Channel.CONFLATED)
val terminalBusy: Flow<Unit> = _terminalBusy.receiveAsFlow() // one-shot, like Event<Boolean>

private val transactions = mutableListOf<Job>()

fun acceptCard(type: TransactionType, amountMinor: Long, referenceNumber: String?) {
    _transactionStep.tryEmit(TransactionStepEvent.idle())
    transactions += viewModelScope.launch {
        terminal.acceptCard(type, amountMinor, referenceNumber)
            .catch { e ->
                if (e is TerminalBusyException) _terminalBusy.send(Unit)
                else _transactionStep.emit(errorStep(e.message ?: "Transaction failed"))
            }
            .collect { _transactionStep.emit(it) }
    }
}

fun cancel() = transactions.forEach { it.cancel() } // this screen's transactions only
// onCleared(): viewModelScope cancels them itself
```

```kotlin
// SearchCardFragment
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.transactionStep.collect(::render) }
        launch { viewModel.terminalBusy.collect { showBusy(view) } }
    }
}
```

---

## 4. Traps (each one breaks a transaction or the UI)

1. **`StateFlow` conflates.** `APPROVED` and `COMPLETED` are published back to back; a
   `StateFlow` collector can see only `COMPLETED`, and the result panel never shows (it reads
   PAN/TVR/TAC/IAC from `APPROVED`/`DECLINED`). Use a listener for the per-transaction stream
   and `MutableSharedFlow(replay = 1, extraBufferCapacity = 64)` for the UI.
   This affects `claudekotlin.md` §3, row 1 (`transactionSteps` → `MutableStateFlow`).
2. **Engine bookkeeping must stay synchronous.** `EmvEngine` clears `running` on
   `ERROR`/`COMPLETED` from its own subscription, before any other subscriber sees the step —
   that is what drops steps after the end. A `Flow` collector runs later; make it a direct
   call inside `notifyTransactionStep` (like `claudekotlin.md` §2.2 does for `emvSteps`).
   The same goes for `AbstractEmvBehavior`'s end watcher on `transactionSteps`.
3. **No `flowOn(txDispatcher)` on the transaction flow.** It moves the busy check onto the
   transaction thread: two quick taps queue, and the second sale runs later on its own — the
   Kotlin form of the `subscribeOn` trap.
4. **A coroutine can be cancelled before it starts.** `txScope.launch { run() }` from inside
   the flow, cancelled before it runs, never runs: `current` is never freed, the terminal is
   busy forever. Use `txExecutor.execute` (or `launch(start = CoroutineStart.ATOMIC)` on a
   scope that is not the flow's).
5. **`awaitClose` runs after a normal `close()` too.** Without the `current == this` guard in
   `cancel()`, every finished sale would stop the reader and cancel the engine.
6. **`trySend` drops when the buffer is full.** `callbackFlow`'s default buffer is 64 with
   `SUSPEND`; `trySend` from the kernel thread must never wait or fail, hence
   `buffer(Channel.UNLIMITED)` (a transaction's steps are bounded).
7. **Never `runBlocking` on main**, and never make `run()`, the kernel callbacks or `askStep`
   `suspend` — the kernel needs the answer on the same stack (`claudekotlin.md` §2).
8. **While the screen is stopped** a `SharedFlow` with no collector keeps only its replay (the
   latest step) — like `LiveData` today. Fine for this screen; don't build on seeing every step
   while in the background.

---

## 5. Where it lands in the migration

- **Phase 2 (`core`)**: `PosTerminal.startTransaction` → `Flow`; `EmvEngine` gets
  `addTransactionStepListener` and its end bookkeeping as a direct call (traps 1, 2).
  Port the lifecycle tests one to one (`runTest`): busy refused without queueing; dispose
  before `run()` still frees the terminal; complete only once free; no previous steps; dispose
  cancels its own transaction only and stops its stream.
- **Phase 3 (`app/src/main`)**: `MainViewModel` / `SearchCardFragment` / `MainActivity` as in
  §3 (`MainActivity` keeps the terminal-wide status stream).
- Conversion rule still holds: if `rxTransactionDesign` is not the mainline when Phase 2
  starts, convert what the mainline has, and bring this design in as its own change.

## 6. Proposed changes to `claudekotlin.md` (not applied — your call)

- §3 row 1: `transactionSteps` → not `MutableStateFlow`; listener + `SharedFlow` (traps 1, 2).
- §3 row "transactionScheduler": `txExecutor.execute` or `CoroutineStart.ATOMIC` (trap 4).
- §5 Phase 2: "`startTransaction` returns `Boolean`" → returns `Flow<TransactionStepEvent>`
  on this branch; `cancelTransaction` is gone (cancel = cancel the collector).
