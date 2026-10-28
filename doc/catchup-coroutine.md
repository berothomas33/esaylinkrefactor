# catchup-coroutine.md — bring `coroutineDesign` to `transactionOwnsSteps-oct27`

Compared on 2026-10-28: **`coroutineDesign` @ `870eb83`** (its last commit, 22/10) against
**`transactionOwnsSteps-oct27` @ `9a590d4`** (everything done so far). Both grew from `ae25378`
(20/10): `coroutineDesign` has 21 commits of its own, `-oct27` has 73.

**Prompt to paste in the coroutineDesign thread:**

> Read `doc/CLAUDE.md`, `doc/REFERENCE.md` and `doc/catchup-coroutine.md` on branch
> `transactionOwnsSteps-oct27` (fetch it). Work on `coroutineDesign` only. Goal: coroutineDesign
> reaches the same stage as `transactionOwnsSteps-oct27` — every fix, every simplification, every
> removal — in Kotlin + coroutines, no Rx. Do the steps in order (S → T → U → V), one commit per
> step, titles as given. For each step: read the source commit body and diff, show me what
> coroutineDesign has today and what will change, then wait for my go. Port the idea, not the
> text; Java modules (`emvlib`, `bizentity`, `emvbase`…) stay Java and take the source diff as is
> when the file matches. Never reorder native calls, never drop payment-safety code without
> asking. Keep kernel callbacks plain (never `suspend`), steps never in a `StateFlow`. Run the
> core tests after each step (Kotlin harness: kotlinc + coroutines-test); say plainly what you
> couldn't build or test.

---

## 1. Where each branch is

| | `coroutineDesign` @ `870eb83` | `transactionOwnsSteps-oct27` @ `9a590d4` |
|---|---|---|
| Language | Kotlin (core, network, app, vendors); PAX SDK modules Java | Java + RxJava 3 |
| Transaction start | `transactionExecutor.execute { process(transaction) }` (a loop) | `Transaction.begin()`: its own steps drive it — `TRANSACTION_STARTED` → `onTransactionStarted`, `NEXT_ATTEMPT` → `onNextAttempt`, then `onEnd` (`2e4fbb4`) |
| Transaction steps | `MutableSharedFlow(replay = Int.MAX_VALUE)` per transaction, `end` = `AtomicReference` | `ReplaySubject` per transaction (also the lock), `volatile end` written under the lock |
| Who ends a transaction | the EMV (`finishApproved` publishes APPROVED + COMPLETED) | the `Transaction` (`emvFinished`, `28d79cb`); the EMV only reports |
| EMV dispatch | plain listener (`setEmvStepHandler`) | `EmvStepObserver` on `emvSteps` (synchronous `PublishSubject`) |
| Engine | lives in `PosTerminal`, passed around | kept only in `AbstractEmvBehavior` (`attach` once, `engine()`); `EmvBehavior` = `prepare` / `start` / `dispose` |
| Host after the end | nothing (no ack, no reversal) | acknowledgement / reversal; `notSent` failures not reversed; errors name their step |
| PAX modules | at the repo root (`:emvlib`, `:bizentity`…) | `pax-libs/sdk/*` and `pax-libs/ours/*` (`:pax-libs:sdk:emvlib`…) |
| Payment-safety fixes (step1), params fixes (step2), UI (step3) | not ported | in |
| Unused code | still there (`RetrofitCommunicationBehavior`, `onCardRemoved`, …) | removed |
| Device-tested | no | `19cc019` (chip, tap again); nothing after it |

## 2. Already on both (skip these)

| `-oct27` commit | Ported to `coroutineDesign` as |
|---|---|
| `13e2b02` + `0bee281` (one steps stream, one cancel flag) | `8e6208b`, `61a36d2` |
| `8ffbc5e` (PosTerminal owns the lifecycle) | `254a0cf` (C1) |
| `40f080d` (CANCELLING at once, one lock) | `3e222d8` (C2) |
| `c70ce09` (one cancel check; retry = a turn of the loop) | `2ef5b1c` (C3) + `096f17e` (C4) |
| `9fb2d4d` (the Transaction holds everything; home screen buttons only) | `227b907` |
| `01dc911` (`VendorApplication`) | `8bf13f4` |
| `bab0eed` `805c325` `9eed173` `835b8c3` (vendor modules) | `0dd5052` `8d83c2a` `68229dd` `7994c71` |
| `fa50258` (every screen in `app/src/main`) | `870eb83` |
| part of `3a2a25a` (snapshot before reading the delivered steps) | `13d94ca` |

Net zero on `-oct27`, skip: `be8836f` + `392936b` (moved, then reverted), `0e12c79` + `85ef5a4`
(app-select change, then reverted), `62d889f` (superseded by S2).

## 3. Still to port, in order

Shared Java modules today (`git diff 870eb83:<m> transactionOwnsSteps-oct27:pax-libs/<sdk|ours>/<m>`):
`bizentity` 8 files, `emvlib` 4 files; `emvbase`, `poslib`, `commonutils`, `bizlib`,
`emvservice`, `emvflow` only their `build.gradle` (module paths). Apply those diffs as is.

### Stage S — the fix phases (22/10)

| Step | Source | What / Kotlin notes |
|---|---|---|
| S1 | `ddd8dc1` (step1) | Payment safety / hangs. Java, apply: `ContactProcess` (every callback answers, `resetParam` → `EMVSetMCKParam`), `SaleResponse` / `SaleCommunicationBehavior` (no `responseCode` = online failed). Kotlin: `askStep` fail-closed after the end; app select: no answer = cancel, never index 0, closes on the end (poll in slices, no coroutine); `PaxPinPad` bounded wait, one offline pad; `tryAgain()` capped; See Phone / Try Again with `EMV_OK` stop before the host; contactless online PIN not entered = ERROR |
| S2 | `03b1cde` (step2) | EMV parameters. `bizentity` Java, apply (static cache + `invalidate()`, per-section `callInTx`, `ConfigInit` synchronous). Kotlin: `PosTerminal.isBusy()`, `PaxEmvParams` (refuse while busy, synchronous seed), `initializeTerminalIfNeeded` reads `EmvParamService.isLoaded()`, Insert EMV param debug only |

### Stage T — 22–23/10

| Step | Source | What / Kotlin notes |
|---|---|---|
| T1 | `2405518` (step3) | Busy keeps the handle, outcome titles, Back = cancel |
| T2 | `ea9c9be` | `ParamDump` merged into `PaxEmvParams` |
| T3 | `5afe77e` + `5b11e45` | `PaxTransactionState` in its own file; `AbstractAppSelectDialog` + PAX subclass; `askStep` one fail-closed branch. Take `5b11e45` with it (`5afe77e` lost `enterPin` / `finishWithOutcome`) |
| T4 | `9a974c6` | `announceIssuerAuthentication` removed |
| T5 | `7d1c0b7` | Shape 4: the engine knows nothing of the transaction; the behavior reports to its Transaction (`publish`, `isRunning`, `isCancelled`); `goToStep` is the one cancel check |
| T6 | `cb7ad3b` + `87fbdf5` | The engine is handed once (`attach`) and kept only in the behavior (`engine()`) |
| T7 | `439a82d` | The Transaction runs itself; `@Volatile var end` written under the lock |

### Stage U — 25/10

| Step | Source | What / Kotlin notes |
|---|---|---|
| U1 | `5755adb` | `bizentity` Java, apply: every parameter reaches the cache again (the `preTransProcess` NPE) + `EmvParamServiceCacheTest` |
| U2 | `66a730c` | EMV Params screen: **Cached** and **DB** shown apart, no fallback between them (`EmvParams.cachedRows`) |
| U3 | `ed64019` | `ContactProcess` comment (Java, apply) |
| U4 | `f576eac` + `b667490` + `19cc019` | The engine field private; `start` doesn't re-subscribe the step observer; `attach` / `engine()` only on `AbstractEmvBehavior`, `EmvBehavior` = `prepare` / `start` / `dispose` (`EmvBehaviorContractTest`) |
| U5 | `196af3b` | `ApduTrace.beginAttempt()` after `RETRY #n`: a retry's first GENERATE AC is the 1st again; label "ISSUER AUTHENTICATION (2nd GENERATE AC)" |
| U6 | `3d5fea1` | Ack / reversal: `PosTerminal.authorize` names each request (`asyncRequestId` on `TransactionConfig`) before sending; `closeOnline` after the end; `orchestration/acknowledgement` / `orchestration/reverse`. Kotlin: `suspend fun acknowledge / reverse` with defaults, sent on their own single-thread scope after the end, `withTimeout(90 s)`, fire-and-forget |
| U7 | `30cd5aa` | Errors name their step; `HostNotReachedException` → `AuthResult.notSent` (never sent, token refresh, any exchange failure, sale/refund 401/403, no connection) → no reversal |

### Stage V — 27–28/10

| Step | Source | What / Kotlin notes |
|---|---|---|
| V1 | `62f5206` + `3622d1d` + `173ebf7` | PAX modules under `pax-libs/sdk` (emvbase, emvlib, emvservice, bizlib, poslib, commonutils) and `pax-libs/ours` (emvflow, bizentity); Gradle paths = folders (`:pax-libs:sdk:poslib`…), 29 `project(...)` lines. Folder moves only (`git mv`) |
| V2 | `28d79cb` | The EMV reports, the Transaction ends: `finish*` → `Transaction.emvFinished(outcome)` sets outcome + end under one lock; a behavior's `publish` drops outcome / end steps (`TransactionEmvFinishedTest`) |
| V3 | `2e4fbb4` | **Design decision first.** The transaction runs from its own steps: `begin()` subscribes a step observer on the transaction thread, publishes `TRANSACTION_STARTED`; `NEXT_ATTEMPT` (new step) for a retry; `onEnd` = the old finally. Kotlin: a collector of the transaction's `steps` launched on the single-thread transaction dispatcher, cancelled in `onEnd`. This goes against the Kotlin rule "the transaction on its executor (never `launch`)" below — Ebram decides how it maps before porting |
| V4 | `3a2a25a` | Tests: wait for the end to reach the list the test reads (`awaitEnd`); copy before `subList` |
| V5 | `5f6e1c9` | One field for the transaction thread (the executor made inside the scheduler / dispatcher) |
| V6 | `c6d1e17` `4c7b757` `708f5f6` `a1ace8a` `9a590d4` | Remove what nothing uses: `PosTerminal.communication()` / `printer()`, `CardSearchListener.onCardRemoved`, `KEY_AMOUNT`, `HostDefaults.declineUntilWired`, `AuthResult`'s asyncRequestId, `EmvEnhanceApp.getOnboarding*`, `RetrofitCommunicationBehavior` + `crypto/purchase` + `Purchase*`, `FakePinPad`, `IngenicoPinPad`. Grep each in the Kotlin code first (bare name and `::name`) |

## 4. Done when

- The Kotlin files do what `transactionOwnsSteps-oct27`'s Java does (same tests, same mutations).
- The shared Java modules are identical:
  `git diff coroutineDesign:pax-libs transactionOwnsSteps-oct27:pax-libs` is empty.
- Device form: a `coroutineDesign` column with the same rows as `transactionOwnsSteps-oct27`.

---

# Kotlin rules (merged from the old claudekotlin.md, 2026-10-20; the plan itself is done on coroutineDesign)

### 2. The threading rules that must survive (read before touching core)

These are proven facts from this project; breaking any of them breaks transactions.

1. **The PAX kernel and every callback run on one transaction thread.** Never the main thread
   (dialogs deadlock), never `Dispatchers.Default` / `IO` (shared pools; a blocked kernel call
   starves them). One dedicated thread for every transaction.
2. **Step dispatch is synchronous and re-entrant.** `goToStep(step)` runs that step's `onXxx`
   *before it returns*; a step method publishes the next step from inside itself; a kernel
   callback calls `askStep` and must get the answer back on the same call stack before it
   calls `setCallBackResult`. Today this works because `emvSteps` is a plain
   `PublishSubject` (not serialized, no `observeOn`).
   - **A `SharedFlow`/`Channel` cannot do this**: `emit` suspends or buffers, collectors run
     later on their own dispatcher. So **dispatch becomes a plain function call** (as on
     `Finalvision`), and Flow is used only for *observing* (UI, trace).
3. **Never interrupt the transaction thread.** A cancelled coroutine must not interrupt a
   native APDU / PIN call. Cancel = a flag + `cancelCardSearch()` + `engine.cancel()`; the
   transaction finishes on its own and frees the terminal. No `runInterruptible` there.
4. **Busy = the transaction hasn't returned** (not "the Job was cancelled"): a cancelled
   search still owns the reader until it stops.
5. **The engine drops steps after the transaction ended** (`TransactionStep.isEnd()`), and the
   end is reported exactly once.
6. **Fail closed**: an unanswered `askStep` → `USER_CANCEL`; no host answer → online failed;
   the card still gets its 2nd GENERATE AC.

---

### 3. Mapping — what each Rx / thread construct becomes

| Today | Kotlin | Notes |
|---|---|---|
| `BehaviorSubject<TransactionStepEvent>` (`transactionSteps`) | `MutableStateFlow` | replays the current state like before; drop the `skip(1)` workarounds where a collector starts fresh |
| `PublishSubject<EmvStepEvent>` (`emvSteps`) — **dispatch** | direct call `dispatchStepMethod(step)` | §2.2 — must stay synchronous |
| `PublishSubject<EmvStepEvent>` — **UI / trace observers** | `MutableSharedFlow(extraBufferCapacity = 64)` + `tryEmit` | observers never block the kernel thread |
| `EmvStepObserver` + `CompositeDisposable` + end watcher | gone | the "stop observing at the end" logic becomes `if (!running) return` |
| `transactionScheduler` (single executor) + `scheduleDirect` | `CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())` + `launch` | keep ONE thread; `current: AtomicReference<Transaction>` stays as the busy flag |
| `Transaction.cancelled: AtomicBoolean` | same, or `Job.isCancelled` read **only** as a flag | never rely on cancellation exceptions inside kernel calls |
| `printScheduler` + `Completable` | `suspend fun print(lines)` + `Mutex` (or a single-thread dispatcher) | receipts in order, off the main thread |
| `CommunicationBehavior.authorize(): Single<AuthResult>` + `timeout().blockingGet()` | `suspend fun authorize(config): AuthResult` + `withTimeout(90_000)` | the transaction thread calls it through `runBlocking { }` — allowed **only** on the dedicated transaction thread, never on main |
| Retrofit `RxJava3CallAdapterFactory` | `suspend` Retrofit functions | Retrofit 2.9 supports them natively |
| `EmvParamActivity` / `OnboardingActivity` `subscribeOn(io()).observeOn(main())` | `lifecycleScope.launch { withContext(Dispatchers.IO) { … } }` | |
| `MainViewModel` `CompositeDisposable` + `LiveData` | `viewModelScope` + `StateFlow`, UI collects with `repeatOnLifecycle` | |
| `ApduTrace.changes(): Observable<Long>` | `StateFlow<Long>` | |
| `new Thread(...)` in fragments / dialogs | `lifecycleScope.launch(Dispatchers.IO)` | |
| `ConditionVariable` / blocking dialogs (`AppSelectDialog.selectBlocking`, PIN pad) | **stay blocking** on the transaction thread, or `CompletableDeferred` + `runBlocking` | the kernel needs the answer on the same stack (§2.2) |

---

### 4. What not to convert (and why)

- **`emvbase`, `emvlib`, `emvlib:base`, `emvlib:dpas`, `poslib`, `commonutils`**: PAX's SDK
  wrappers around certified native kernels (JNI). Converting them changes nothing for the
  user and risks byte/sign bugs in the exact code that talks to the card. Kotlin calls them
  as they are.
- **`bizentity`**: GreenDAO generates Java DAOs from Java entities; it doesn't support Kotlin
  entities. Keep it Java, or replace GreenDAO with Room as its own project later (schema,
  migration of stored parameters) — not part of this migration.
- `emvflow`, `emvservice`, `bizlib`: small; convert only if a later phase touches them.

---

### 6. Kotlin traps specific to EMV code

- **`Byte` is signed.** `0xE0` is an `Int`; write `0xE0.toByte()`. Reading: `b.toInt() and 0xFF`.
  Every hex constant above `0x7F` needs `.toByte()`. Comparisons like `pcii[1] & 0x03` become
  `(pcii[1].toInt() and 0x03)`.
- `byteArrayOf(0xE0.toByte(), 0x68, 0x48)`; hex helpers in one place (`ConvertUtils` stays Java).
- **Platform types**: everything from PAX/Java is `T!`. Decide nullability at the boundary;
  never sprinkle `!!` — a null from the kernel is a fail-closed path, not a crash.
- `when` must stay exhaustive over `EmvStep` / `TransactionStep` (no silent `else`).
- `long` amounts in minor units stay `Long`; BCD n12 via the existing helpers.
- JNI-facing interfaces keep Java signatures; add `@JvmStatic` / `@JvmField` /
  `@JvmOverloads` where Java code still calls converted Kotlin.
- Don't turn kernel callbacks into `suspend` functions — native code can't suspend.
- `data class` `equals` on `ByteArray` compares references — override or don't use it for
  parameter beans.

---
