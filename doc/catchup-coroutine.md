# catchup-coroutine.md — bring coroutineDesign to where transactionOwnsSteps is

**Prompt to paste in the coroutineDesign thread:**

> Read `doc/CLAUDE.md`, `doc/handoff-2026-10-23.md` and `doc/catchup-coroutine.md` (all on branch `transactionOwnsSteps`;
> fetch it). Work on `coroutineDesign` only. Goal: coroutineDesign reaches the same stage as
> `transactionOwnsSteps` — every fix and every simplification — in Kotlin + coroutines, no Rx.
> Do the steps in order (P → S → T), one commit per step, commit titles as given. For each step: read
> the source commit body and diff, show me what coroutineDesign has today and what will change,
> then wait for my go. Port the idea, not the text; Java modules (`emvlib`, `bizentity`,
> `emvbase`…) stay Java and take the source diff as is when the file matches. Never reorder native
> calls, never drop payment-safety code without asking. Keep kernel callbacks plain (never
> `suspend`), the transaction on its executor (never `launch`), steps never in a `StateFlow`. Run
> the core tests after each step (Kotlin harness: kotlinc + coroutines-test); say plainly what you
> couldn't build or test.

---

## Where things are

- **coroutineDesign** (`096f17e`): Kotlin app on rxSet's EMV work, with rxTransactionDesign's
  lifecycle up to `c70ce09` (C1–C4: `Transaction.end`, CANCELLING + one lock, one cancel check,
  retry loop). Steps are a `MutableSharedFlow` in `PosTerminal`; EMV dispatch is a plain listener
  (`setEmvStepHandler`).
- **transactionOwnsSteps** (source of truth) = that + today's changes (P1–P5) + phase 1 (`step1`)
  + phase 2 (`step2`).
- Java modules: check `git diff <source>^ coroutineDesign -- <file>`; if empty, apply the
  source's diff to that file as is.

## Stage P — the 2026-10-22 changes (read each commit's body)

| Step | Source | Kotlin notes |
|---|---|---|
| P1 | `62d889f` | Superseded by S2 (one cache owner) — skip, or do and let S2 replace it |
| P2 | `9fb2d4d` | `Transaction` holds `end` (`AtomicReference`, the state) and its own steps: a per-transaction flow that **replays every step** to a late collector (`MutableSharedFlow(replay = Int.MAX_VALUE)` or a list + flow) — never `StateFlow`. `TransactionStep.CANCELLED` (end). Remove the terminal-wide `transactionSteps()`. `MainViewModel`: one `StateFlow<Transaction?>` (null after a refused, busy start); the fragment collects `transaction.steps()` in `repeatOnLifecycle`. `MainActivity`: buttons only |
| P3 | `01dc911` | `VendorApplication` per vendor (PAX: extends `BaseApplication`); `EmvEnhanceApp` imports nothing of PAX |
| P4 | `bab0eed` `805c325` `9eed173` `835b8c3` | Vendor modules with the Kotlin Android plugin; `vendor-pax`: `api core`, `api commonutils`, the rest `implementation`, `buildConfig true`; app: `pax/ingenico/fakeImplementation`, drop `commonutils` |
| P5 | `fa50258` | Screens in `app/src/main`; `PosTerminal`: `serialNumber()`, `requiredPermissions()`, `countries()`/`setCountry()`, `emvParams()`; core `EmvParams` (`download()` a `suspend fun`, no Rx); `PaxEmvParams` + `ParamDump` in vendor-pax; `VendorDebugPanel` gone; `PinBypassSettings` → `:network` |

## Stage S — the fix phases

**S1 = `ddd8dc1` (`step1`, payment safety / hangs).** Shared Java, apply the diff:
`ContactProcess` (callbacks always answer, try/catch, `resetParam` Set), `SaleResponse` /
`SaleCommunicationBehavior` (no `responseCode` = online failed). Port to Kotlin:
`AbstractEmvBehavior.askStep` (fail-closed after the end), `AppSelectDialog` (no answer instead of
index 0, closes on the end — poll in slices, no coroutine), `PaxPinPad` (bounded wait, one
offline pad), `PaxEmvBehavior` (cap in `tryAgain()`, See Phone / Try Again with EMV_OK stop
before the host, contactless online PIN not entered = ERROR).

**S2 = `03b1cde` (`step2`, EMV parameters).** `bizentity` is shared Java — apply the diff
(`EmvParamService` static cache + `invalidate()`, `EmvParamUpdater` per-section `callInTx` and
one rule, parsers, `ConfigInit` synchronous). Port to Kotlin: `OnboardingState` (remove
`terminal_initialized`), `PosTerminal.isBusy()`, `PaxEmvParams` (refuse while busy, download
inline, synchronous seed), `PaxTerminal` (first-start seed on its own thread), `PaxEmvBehavior`
(`initializeTerminalIfNeeded` reads `EmvParamService.isLoaded()`), `MainActivity` (Insert EMV
param debug only, busy message). Item 12 (gate on host params) is **not** wanted.

## Stage T — after step2, up to `80f4ca9` (read each commit's body; `doc/handoff-2026-10-23.md` §1–§2 has the result)

| Step | Source | What (Kotlin: keep callbacks plain, the transaction on its executor; `@Volatile var end`) |
|---|---|---|
| T1 | `2405518` | step3: UI layers (busy keeps the handle, outcome titles, Back = cancel) |
| T2 | `ea9c9be` | `ParamDump` merged into `PaxEmvParams` |
| T3 | `5afe77e` + `5b11e45` | `PaxTransactionState` in its own file; `AbstractAppSelectDialog` + PAX subclass; `askStep` one fail-closed branch. Take `5b11e45` with it: `5afe77e` lost `enterPin` / `finishWithOutcome` |
| T4 | `9a974c6` | `announceIssuerAuthentication` removed |
| T5 | `7d1c0b7` | Shape 4: `EmvEngine(authorizer)` without Consumer / suppliers; the behavior reports to the Transaction (`publish`, `isRunning`, `isCancelled`); `goToStep` is the one cancel check |
| T6 | `cb7ad3b` + `87fbdf5` | The engine is handed once (`attach`) and kept only in the behavior (`engine()`); no `engine` parameter on the step methods |
| T7 | `439a82d` | The Transaction runs itself (`transaction::run`); `volatile end` under the lock; `isOver()` / `end(event)` / the publish try/catch removed |
| T8 | `0e12c79` | Ebram's small move in `PaxEmvBehavior` |

## Done when

The Kotlin files do what transactionOwnsSteps' Java does; the shared Java modules are identical
(`git diff transactionOwnsSteps coroutineDesign -- emvlib bizentity` empty). Device form:
coroutineDesign column, same rows as transactionOwnsSteps.

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
