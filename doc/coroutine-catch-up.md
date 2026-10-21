# coroutine-catch-up.md — what coroutineDesign is missing from rxTransactionDesign

**Prompt to paste in the coroutineDesign thread:**

> Read `doc/CLAUDE.md` and `doc/coroutine-catch-up.md` (both on branch `rxTransactionDesign`).
> `coroutineDesign` carries rxTransactionDesign up to `0bee281`; bring it up to `c70ce09` in
> Kotlin — steps C1–C4, one commit each, in order. Before each step show me what coroutineDesign
> has today and what changes. Don't redo anything already there; don't touch the EMV flow.

---

## Where each branch is

- `coroutineDesign` (`c4faffa`): the app in Kotlin + coroutines. Its lifecycle matches
  rxTransactionDesign `13e2b02` + `0bee281`: `suspend fun startTransaction`, one cancel flag
  with PENDING / claimed, engine running flag, end guard, `SharedFlow` steps in the engine.
- `rxTransactionDesign` (`c70ce09`): three commits later.
  - `8ffbc5e`: the lifecycle moved into `PosTerminal`, with no Completable / suspend wrapper.
  - `40f080d`: CANCELLING, one lock, SafeObserver.
  - `c70ce09`: one cancel check, retry loop.
- The source of truth is rxTransactionDesign's Java files named in each step. Read them, and
  write the Kotlin form. `doc/kotlin-rx-transaction.md` is **stale** (it describes the
  discarded Observable `ae25378`): ignore it.

## C1 — the transaction belongs to PosTerminal (`8ffbc5e`)

| rxTransactionDesign (Java) | coroutineDesign (Kotlin) should have |
|---|---|
| `startTransaction(config): Transaction` — CAS `current` on the caller's thread, busy → `TerminalBusyException`, `transactionExecutor.execute { process(t) }` | a plain `fun startTransaction(config): Transaction` (not `suspend`): the caller gets the handle at once; no coroutine owns the lifecycle |
| `Transaction.end: AtomicReference<TransactionStepEvent>` — null = running, first end / cancel's ERROR = ended | same; remove PENDING / `claimed` / `cancelled` / `EmvEngine.running` |
| `process` `finally`: no-outcome end → free `current` → publish the end | same order |
| transaction steps owned by `PosTerminal`, published only while running; `EmvEngine(behavior, authorizer, publish, running, cancelled)` with `begin()` returning nothing | same: the engine keeps `emvSteps` + trace + retry only |
| `MainViewModel` keeps the `Transaction`, cancels it; busy caught synchronously | same, in `viewModelScope`; `SearchCardFragment` collects busy **after** the steps |

## C2 — CANCELLING, one lock, a subscriber can't stop the steps (`40f080d`)

- New `TransactionStep.CANCELLING` (not an end). `Transaction.cancel()`: CAS `end` → emit
  CANCELLING → `cancelCardSearch()`.
- One lock around every emit, the cancel's [CAS + CANCELLING], and the finally's [end + free +
  emit]. CANCELLING never comes after the end. One atomic can't order an emit on two threads.
- Kotlin: steps = `MutableSharedFlow<TransactionStepEvent>(replay = 0, extraBufferCapacity = 64)`
  + `tryEmit` under the lock; if `tryEmit` returns false, log it (rule 15), never drop silently.
  **Not** `StateFlow`: it conflates, and APPROVED then COMPLETED can arrive as COMPLETED only.
- No SafeObserver is needed: a collector that throws fails its own coroutine, not the emitter.
  Check that this is really so in your code (collect in `viewModelScope` / `lifecycleScope`, never
  on the transaction thread).

## C3 — one cancel check (`c70ce09`, part 1)

- The engine's running gate in `notifyEmvStep` is the one check at a step boundary. It returns
  whether the step was published; `goToStep` returns whether the step ran.
- Remove: the cancel checks in `goToStep`, in the EMV step observer/dispatch, in `prepare`
  (`return engine.isRunning()`), at the top of every PAX runner (11), in `retryWithMode`, and in
  Fake / Ingenico `onStartOnlineProcess`.
- `advanceContactStage` / `advanceContactlessStage`: `if (!goToStep(next)) finish…Stage()`, so
  the readers are still closed when the next step didn't run.
- Keep: `authorizeOnline`'s guard (payment safety); `finish…Stage` skipping the result, with a
  trace note "cancelled — the card's result is not reported"; the app-select retry loop's check.

## C4 — retry is a turn of the loop (`c70ce09`, part 2)

- `process`: emit TRANSACTION_STARTED **once**, then
  `while (config != null && t.isRunning()) config = attempt(t, config)`; `attempt` = prepare +
  search + `engine.consumePendingRetry()`; a retry of a transaction that is over is dropped with a
  trace note.
- `handleCardFound` no longer starts a search; `AbstractEmvBehavior.prepare` no longer emits
  TRANSACTION_STARTED.

## Tests (port one to one, `runTest` where useful)

`PosTerminalTransactionLifecycleTest` (busy refused without queueing; cancelled before it ran
still frees; end published once free — start a sale from inside the end callback; ends once
whatever its code reports after; no-outcome end; cancel twice / after the end does nothing;
nothing published outside a transaction; CANCELLING then ERROR; a cancel racing the end never
says CANCELLING after it), `PosTerminalRetryLoopTest`, `PosTerminalConcurrentStepsTest`,
`EmvEngineEndTest`, `TestSession`. Core today: 79 tests. Name in each commit the test that fails
when its guard is removed.

## Kotlin traps (from CLAUDE.md §6)

- `StateFlow` conflates steps. Use `SharedFlow` with no replay.
- No `flowOn` / `withContext(txDispatcher)` around the busy check: that queues a second sale.
- A coroutine cancelled before it starts never runs. That's why the lifecycle uses
  `executor.execute`, not `launch`.
- Never `runInterruptible` or `CancellationException` inside kernel calls. Kernel callbacks stay
  plain, non-`suspend`.
- `askStep` / the step dispatch stay synchronous on the transaction thread.
