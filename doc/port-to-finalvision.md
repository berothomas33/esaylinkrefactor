# port-to-finalvision.md — bring the transaction lifecycle to Finalvision, without Rx

**Prompt to paste in the Finalvision thread:**

> Read `doc/CLAUDE.md` and this file (`doc/port-to-finalvision.md`, on branch
> `rxTransactionDesign`). Work on `Finalvision` only. Do Phase A, one step per commit, in order.
> Before each step: show me what Finalvision has today and what will change. No Rx in the
> transaction lifecycle. Don't touch the EMV flow or native calls. Stop after each step.

---

## Why

`Finalvision` is the safest branch: device-tested, direct step dispatch. Its transaction
lifecycle is still the old one: an Rx scheduler, `volatile current`, a `synchronized` lock, a
`BehaviorSubject` of steps that every screen has to `skip(1)`, and two `cancelled` flags (the
behavior's and the transaction's). `rxTransactionDesign` worked out a simpler lifecycle, with one
owner, a plain thread, one atomic, and an end that is published exactly once. This file is that
design, written for Finalvision, **without Rx anywhere in the lifecycle**. Rx stays only where a
screen observes the steps.

The source of truth is `rxTransactionDesign`'s code at `c70ce09`: `PosTerminal.java`,
`EmvEngine.java`, `AbstractEmvBehavior.java`, `MainViewModel.java`, `SearchCardFragment.java`, and
the tests named below. Read those files; don't copy them blindly. Finalvision's EMV code is older
(no `askStep`, different runners), so apply the **principle** where its code differs.

## Phase A — the lifecycle (do this)

| # | Step | What Finalvision gets | What goes away |
|---|---|---|---|
| A1 | **One thread, plain** | `PosTerminal.transactionExecutor = Executors.newSingleThreadExecutor()`; `startTransaction` → `current.compareAndSet(null, t)` on the caller's thread, busy → `throw TerminalBusyException` at once (never queued), else `transactionExecutor.execute(() -> process(t))`; returns the `Transaction` (the handle a screen cancels) | `Schedulers.from(...)` for the transaction, `subscribeOn`, `volatile current`, `transactionLock` / `synchronized` for start, `boolean` return, `started` / `done` flags |
| A2 | **One state per transaction** | `Transaction.end`: `AtomicReference<TransactionStepEvent>`. `null` = running; the first ERROR/COMPLETED or the cancel's ERROR = ended. `isRunning()`, `isCancelled()` (`end == its cancelled event`), `isOver()` (`!isRunning() \|\| current != this`) | `AbstractEmvBehavior.cancelled`, `EmvBehavior.cancel()`, `EmvEngine.running`, `EmvEngine.cancel()`'s flag |
| A3 | **Cancel = the handle** | `Transaction.cancel()`: `end.compareAndSet(null, cancelled)`, then publish `CANCELLING` (new non-end `TransactionStep`), then `cancelCardSearch()`. A second cancel / a cancel after the end does nothing | `PosTerminal.cancelTransaction()` (global "cancel whatever is current"), `MainViewModel.startedTransaction` |
| A4 | **The end in one place** | `process`'s `finally`: `end.compareAndSet(null, ERROR "Transaction ended without an outcome")`, free `current`, **then** publish the end. COMPLETED/ERROR means "the terminal is free". `process` always runs: cancelled before it started → no search, still frees | the end published from wherever it happened; "Terminal initialization failed" twice; an engine left `running` forever |
| A5 | **Steps owned by PosTerminal** | `PosTerminal.transactionSteps`: a plain `PublishSubject` (no replay), published only while the current transaction runs; every publish holds **one lock** (the subject), so CANCELLING never lands after the end; each subscriber wrapped in `SafeObserver` | `BehaviorSubject` + `skip(1)`, `transactionStepsSerialized`, the engine subscribing to itself, the end watcher in `AbstractEmvBehavior` |
| A6 | **Engine without transaction state** | `EmvEngine(behavior, authorizer, Consumer<TransactionStepEvent> steps, BooleanSupplier running, BooleanSupplier cancelled)`; `begin()` resets the trace + retry only; `notifyEmvStep` returns whether it published (dropped when not running — the one cancel check at a step boundary) | `currentTransactionState()`, `begin()`'s boolean |
| A7 | **One cancel check** | `AbstractEmvBehavior.isCancelled()` → `engine.isCancelled()`; `goToStep` returns whether the step ran; vendor code that holds a reader across steps releases it when `goToStep` returns false | the cancel checks in `goToStep`, in `prepare`, at the top of every runner, in `retryWithMode`. Keep: the host guard in `authorizeOnline`, "result not reported" (with a trace note), any loop *inside* one step |
| A8 | **Retry = next turn** | `process`: `publish(TRANSACTION_STARTED)` once, then `while (config != null && t.isRunning()) config = attempt(t, config);` and `attempt` = prepare, search, `return engine.consumePendingRetry()` | `triggerSearch` called from inside `handleCardFound`, TRANSACTION_STARTED from every `prepare` |
| A9 | **The screen** | `MainViewModel`: keeps the `Transaction`, `cancel()` / `onCleared()` call `transaction.cancel()`; busy caught synchronously → `getTerminalBusy()` Event; IDLE on each start. `SearchCardFragment`: busy observer registered **after** the steps observer; no `started[0]` filter | the replay filter, the async busy |

**Tests to bring** (rxTransactionDesign, `core/src/test`): `PosTerminalTransactionLifecycleTest`,
`PosTerminalRetryLoopTest`, `PosTerminalConcurrentStepsTest`, `EmvEngineEndTest`,
`engine/TestSession` (stands in for PosTerminal when a test drives the engine alone), and the
updated `PosTerminalHostTest` / `PosTerminalApduTraceTest` / `PosTerminalRetryPromptTest`. Each
step's commit names the test that fails when its guard is removed (mutation check).

**Device test after Phase A** (`report_easylink.xlsx`, a new Finalvision column): rows 13, 14, 15,
16, 18, plus Retry after a decline, BUSY while a cancelled search stops, and Cancel during the
host call (CANCELLING at once, ERROR when the host returns).

## Phase B — rxSet's EMV work (only if he decides)

These are not part of the lifecycle. They are what `rxSetTransactionData` added on top of
`rxDispatchEmvSteps` (21 commits: `askStep`, each step doing its kernel work, PayWave granular,
PayPass CAPK in ODA, parameter deletion, debug screens…). Porting them to Finalvision is a
separate decision. If he decides yes: **one commit at a time, in their original order, with a
device test after each risky one**, and the native call order before / after in every commit
body.

## Don't

- Don't bring Rx into the lifecycle (no `Completable`, `defer`, `subscribeOn`, `doOnDispose`).
- Don't reorder native calls or change the EMV flow in Phase A.
- Don't add a second flag / stream / guard for something that already has an owner (rule 9).
- Don't interrupt the transaction thread; a cancel is the state + `cancelCardSearch()`.
- Don't publish the end before `current` is freed.
- No StateFlow-style "latest value only" for steps: APPROVED then COMPLETED must both arrive.
