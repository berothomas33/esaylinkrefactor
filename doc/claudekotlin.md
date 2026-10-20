# claudekotlin.md — Java + RxJava + Threads → Kotlin + Coroutines

Plan for moving EmvEnhance (PAX EMV POS) to Kotlin with coroutines. Read `CLAUDE.md` first:
every rule there still applies (Egyptian Arabic, do only what was asked, one experiment = one
commit, the kernel's order wins, fail closed, never put credentials or card data in a file).

This file is the plan; each phase is its own branch, its own commits, and its own device test.
On `rxTransactionDesign` (the transaction as an `Observable`): `doc/kotlin-rx-transaction.md`
maps that design to Kotlin Flow and lists the traps it found in §3 below.

---

## 0. Ground rules for the migration

1. **Behavior does not change while converting.** A conversion commit translates; it does not
   fix, rename or redesign. Fixes go in their own commit, before or after.
2. **Two commits per file group**: (a) "Convert X to Kotlin" — Android Studio's
   *Code → Convert Java File to Kotlin* plus whatever makes it compile; (b) "Idiomatic X" —
   nullability, `val`, data classes, `when`. Reviewing (a) is a line-by-line diff check.
3. **One module per branch**, merged before the next starts. Java and Kotlin live together
   the whole time (full interop); there is no "big bang".
4. **The EMV thread model is fixed** (see §2). Coroutines change how we *start* and *cancel*
   work and how the UI *observes* it — never the order or the thread of kernel calls.
5. **Tests before and after.** Every module keeps its JUnit tests green through the
   conversion; the core tests are converted last in each phase (they prove the conversion).
6. **Device test after every phase** with the test form (`report_easylink.xlsx`), new column
   per phase branch. A phase isn't merged until the form's Pass rows still pass.
7. **Don't convert PAX's own code** (see §4) unless there is a reason beyond "it's Java".

---

## 1. What we have (2026-10-20, branch `rxSetTransactionData`)

| Module | Java files / lines | Tests | Rx / threads | Convert? |
|---|---|---|---|---|
| `core` | 29 / 3.2k | 13 | Rx subjects, transaction + print schedulers, Atomics | **Yes — heart of the design** |
| `network` | 45 / 3.4k | 3 | Retrofit Rx adapter, `Single`, `blockingGet` | **Yes — easiest win** |
| `app/src/main` | 9 / 0.9k | 0 | `MainViewModel` Rx + LiveData | **Yes** |
| `app/src/pax` | 15 / 4.4k | 0 | `PaxEmvBehavior`, `PaxTerminal`, dialogs, PIN pad | **Yes, last and carefully** |
| `app/src/fake`, `ingenico` | 10 / 0.8k | 0 | — | Yes, with core |
| `emvflow`, `emvservice`, `bizlib` | 17 / 2.3k | 0 | a few threads | Optional, late |
| `emvbase`, `emvlib*`, `poslib`, `commonutils` | 132 / 23k | 3 | — | **No** (PAX SDK wrappers) |
| `bizentity` (GreenDAO) | 100 / 21k | 5 | — | **No** (GreenDAO needs Java entities) |

Toolchain today: AGP 8.2.2, Gradle 8.2, Java 17, RxJava 3.1.8, Retrofit 2.9.0, OkHttp 4.9.1,
GreenDAO 3.3.0, no Kotlin.

---

## 2. The threading rules that must survive (read before touching core)

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

## 3. Mapping — what each Rx / thread construct becomes

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

## 4. What not to convert (and why)

- **`emvbase`, `emvlib`, `emvlib:base`, `emvlib:dpas`, `poslib`, `commonutils`**: PAX's SDK
  wrappers around certified native kernels (JNI). Converting them changes nothing for the
  user and risks byte/sign bugs in the exact code that talks to the card. Kotlin calls them
  as they are.
- **`bizentity`**: GreenDAO generates Java DAOs from Java entities; it doesn't support Kotlin
  entities. Keep it Java, or replace GreenDAO with Room as its own project later (schema,
  migration of stored parameters) — not part of this migration.
- `emvflow`, `emvservice`, `bizlib`: small; convert only if a later phase touches them.

---

## 5. Phases

Each phase: its own branch from the mainline, conversion commit(s), idiomatic commit(s),
tests green, device test, merge.

### Phase 0 — Gradle setup (no code converted)
- Add the Kotlin Android plugin and coroutines, pinned (AGP 8.2.2 works with Kotlin 1.9.x):
  - `org.jetbrains.kotlin.android` **1.9.22**, `jvmTarget = "17"`
  - `kotlinx-coroutines-core` / `-android` **1.7.3**, `kotlinx-coroutines-test` **1.7.3**
  - `androidx.lifecycle:lifecycle-runtime-ktx` / `-viewmodel-ktx` **2.6.2**
- Apply the plugin to `core`, `network`, `app` only.
- Build every flavor (`paxEnvtestDevDebug`, fake, ingenico); run all unit tests.
- **Done when:** the app builds and behaves exactly as before; nothing is Kotlin yet.

### Phase 1 — `network` (Retrofit → suspend)
- Convert models first (→ `data class`, keep `@SerializedName`).
- Convert API interfaces to `suspend fun`; drop the Rx call adapter.
- `HostApiConnection`, `OnboardingClient`, `EmvParamDownloadClient`,
  `SaleCommunicationBehavior`, `RetrofitCommunicationBehavior` → suspend.
- `CommunicationBehavior` (in `core`) gets `suspend fun authorize`; `PosTerminal.authorize`
  keeps its blocking signature for the kernel thread: `runBlocking { withTimeout(…) }`.
- Crypto (`HostEnvelope`, RSA/AES): convert last, byte for byte; keep the existing tests.
- **Device test:** sale chip + contactless, refund, host timeout (form rows 1, 6, 8, 16),
  onboarding, parameter download (row 22).

### Phase 2 — `core` (the engine)
- Order: `event/*` (enums, events) → `card/*` (`Field55`, `TransactionConfig`) →
  `util/ApduTrace` → `engine/EmvEngine` → `terminal/AbstractEmvBehavior` → `PosTerminal`.
- `EmvEngine`: `transactionSteps` → `StateFlow`; `emvSteps` for observers → `SharedFlow`;
  dispatch to the behavior is a direct call (§2.2). Keep `isEnd()` / drop-after-end.
- `AbstractEmvBehavior`: keep `goToStep` / `askStep` / `answerStep` synchronous; the 16
  `protected abstract` step methods stay (Java vendors still extend it during the migration).
- `PosTerminal`: coroutine scope on one thread; `startTransaction` returns `Boolean` (busy);
  `cancelTransaction` sets the flag, stops the search, cancels the engine.
- Convert the 13 core tests last; add `kotlinx-coroutines-test` tests for: busy while a
  cancelled search stops, one thread for every transaction, ordered printing, no step after
  the end, nested `askStep`.
- **Device test:** the whole form.

### Phase 3 — `app/src/main` (UI)
- `MainViewModel` → `viewModelScope` + `StateFlow`; Activities collect with
  `repeatOnLifecycle(STARTED)`. Rotation (row 18) must not start or cancel a sale.
- **Device test:** rows 13, 14, 17, 18 + one sale.

### Phase 4 — `app/src/fake` and `app/src/ingenico`
- Small; proves the converted core contract with a second vendor. Fake vendor flow tests.

### Phase 5 — `app/src/pax` (largest risk, last)
- Order: `PaxPrinter`, `PaxPinPad`, `AppSelectDialog`, `PaxEmvParamUpdateService`,
  `EmvParamActivity` / fragments, `PaxTerminal`, then `PaxEmvBehavior` **in pieces**
  (helpers → contact runners → contactless runners → callbacks).
- Kernel callbacks (`IContactCallback`, `IContactlessCallback`, `EMVCallback` adapters) stay
  plain, non-suspending functions on the transaction thread.
- **Device test:** the whole form, every row.

### Phase 6 (optional) — `emvflow`, `emvservice`, `bizlib`; Room instead of GreenDAO
- Separate decisions, not part of "done".

---

## 6. Kotlin traps specific to EMV code

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

## 7. How to work in the other chat

Start the chat with:

> Read `CLAUDE.md` and `claudekotlin.md`. We are on Phase N. Phase N only: convert
> <module/files> to Kotlin, conversion commit first, no behavior change. Stop and show me the
> diff summary before the idiomatic commit.

Per phase deliverables:
1. Branch `kotlin/phase-N-<module>` from the mainline.
2. Commit "Convert <X> to Kotlin" (mechanical), then "Idiomatic <X>".
3. All unit tests green (`./gradlew test` per module).
4. A test-form column for the branch, filled on the device.
5. `CLAUDE.md` updated (module now Kotlin, any rule that changed).

## 8. Open decisions before Phase 0
- Which branch is the mainline to start from (`rxSetTransactionData` after its device test?).
- Kotlin 1.9.22 with AGP 8.2.2, or upgrade AGP first (separate change).
- Room instead of GreenDAO: now, later, or never.
