# CLAUDE.md — EmvEnhance / EasyLink (PAX EMV POS, Android)

Project memory for Claude. Read it before touching code. When a fact here changes, update this
file in the same commit. Never put credentials, tokens, keys, PANs, track data or serial
numbers in it. Kotlin: `catchup-coroutine.md`. Full flow reference (architecture,
EmvEngine/EmvStep/onXxx, contact, contactless, EMV params, UI → kernel callback):
`doc/REFERENCE.md`. Everything missing / broken / risky, ranked: `doc/BACKLOG.md`.

---

## 0. What this project is

An Android card-acceptance app ("EasyLink") for **PAX A920** terminals, talking to our own
acquiring host in Egypt (currency EGP/818). It refactors PAX's EDC demo into a clean,
vendor-neutral design: the UI and the transaction logic know nothing about PAX; PAX (and later
Ingenico) plug in as a vendor module, picked by a Gradle flavor (`doc/VENDOR.md`).

**The terminal's life:**
1. **Install / first start** — `ConfigInit` seeds the database from JSON assets (`acquirer.json`,
   `config.json`, AIDs, CAPKs, kernels) once (`emv_config_initialized`).
2. **Onboarding** — the terminal registers with the host: keys, access token, PIN-bypass
   switches (`OnboardingActivity`, `OnboardingState`).
3. **Parameter download** — EMV Params screen → Download: a zip with `emv_param.emv` (contact)
   and `clss_param.clss` (contactless) XML, parsed into GreenDAO (§8).
4. **Terminal initialization** — first transaction after onboarding / a download loads every
   parameter from the database into the kernel's in-memory cache.
5. **Sale / refund** — amount → card search (insert / tap / swipe / manual) → EMV with the PAX
   kernel → online to the host (always: online-only terminal) → 2nd GENERATE AC → result →
   receipt.

**Who does what (one sentence each):**
- `PosTerminal` (core) — the only API the UI uses: start / cancel a transaction
  (`PosTerminal.Transaction`: its steps, cancel and end), card search, host, printer; one
  transaction at a time on one thread.
- `EmvEngine` (core) — the EMV steps bus (the dispatch, the banner, the APDU trace), the trace,
  the retry request, the way to the host. No EMV logic, no transaction state.
- `AbstractEmvBehavior` (core) — the 16 EMV step hooks a vendor implements, `goToStep`,
  `askStep`, field 55, `authorizeOnline`; keeps the engine (`attach` / `engine()`).
- `PaxEmvBehavior` (`vendor-pax`) — PAX's implementation: drives `ContactProcess` /
  `ClssProcess` one native call per step.
- `ContactProcess`, `Clss*Process` (emvlib) — PAX's wrappers over the certified native kernels.
- `network` — host envelope (RSA/AES), Retrofit, onboarding, parameter download.
- `bizentity` — GreenDAO entities/DAOs, `EmvParamService` (DB ↔ kernel params, cache),
  XML parsers, `EmvParamUpdater`.

---

## 1. How to work with Ebram

- He writes Egyptian Arabic + English, short and fast. **Answer in Egyptian Arabic**, calm and
  short. Code, commits and javadoc stay in English.
- **Do only what was asked.** A question is not a request to change code. Quoting your text
  back is not approval. When unsure, offer numbered options ("do 1 and 2 only").
- **Before any rollback / delete / reset: say your understanding in one line, then wait.**
- Architecture work is design-first: when he says "draw" / "explain", no code yet. A drawing
  (artifact page / diagram) helps him decide.
- One experiment = one commit, with a long body; for EMV changes show the native call order
  before / after.
- Give a recommendation, not a survey. Full analysis before saying "fixed". Say plainly what is
  not device-tested.
- He asks "is this safe / does this break anything?" — answer with the concrete call path.
- "Logs still wrong" → suspect a stale build first: logcat tag, `git log -1`, Clean + Rebuild.
- Test environment: don't lecture about tokens in logs.
- His Excel test form: fill only the cells he asks for; never change his own edits.
- **Docs once a day, not per commit.** Don't touch `doc/CLAUDE.md` / `doc/REFERENCE.md` with
  every code commit (tokens). A scheduled task reminds him daily at ~5 PM Cairo; then, if he
  says yes, both docs are brought up to date in one commit. The commit bodies are the record
  until then. Exception: he asks for a doc change now.

## 2. His design rules (apply them unasked)

1. **Each EMV step's hook does that step's real kernel work.** No step that is only a label
   around work done elsewhere. If the SDK has a separate native call, it goes in its step.
2. **The kernel's order wins.** Steps follow what the kernel really does, even when numbers
   come out of order (contactless 2 → 6 → 5; Visa … → PR → ODA). Never reorder native calls.
3. **Kernel callbacks are thin adapters.** A callback that needs the cardholder (app
   selection, PIN) asks its step (`askStep`), the hook does the UI. Data callbacks
   (`emvInputAmount`, `emvUnknowTLVData`, `emvSetParam`) stay in `ContactProcess`. The adapter
   always calls `setCallBackResult`.
4. **Fail closed.** No answer → `USER_CANCEL` / online failed; never a hang, never approved.
5. **Simple first.** No Rx where a plain call works, no lock/volatile when one Atomic does it,
   no single-implementor interfaces, no facades.
6. **Contact and contactless stay separate** — their own run* methods; no shared helper that
   merges the two flows.
7. **Parameters come from the host only** (XML → GreenDAO). What the package doesn't bring is
   deleted, not kept from the JSON seed. Terminal identity (TID, MID, MCC, merchant name)
   does **not** come from the XML.
8. Keep `EmvStep` numbers (EMV Book 3 order). A new step = a new abstract hook in every vendor
   — avoid.

*Added 2026-10-20 from the rxTx / cleanup session. Apply them unasked, like 1–8.*

9. **One responsibility, one owner, one source of truth.** Check existing ownership before
   adding anything: before adding a flag, `Atomic*`, `Boolean`, subject, stream, list or
   guard, grep for the one that exists and use it. No duplicated state, flags, `Atomic*`,
   subjects, streams or guards — two of the same kind is a design bug (e.g. cancellation has
   one owner, never two `cancelled` flags; found twice already: two `cancelled` flags; two
   `TransactionStepEvent` streams). Owners today:
   busy = `PosTerminal.current`; a transaction's whole state (running / ended / cancelled) =
   `PosTerminal.Transaction.end` (`volatile`, written only under the steps lock); transaction
   steps = `Transaction.steps` (one `ReplaySubject` per transaction); EMV steps =
   `EmvEngine.emvSteps`; the engine = `AbstractEmvBehavior.engine` (private, written once by
   `attach`). A class that needs another's state gets a narrow read (e.g. the behavior asks its
   `Transaction`: `isRunning()` / `isCancelled()`), never its own copy.
   Prefer removing redundant code over adding abstractions.
10. **Separation of responsibilities.** `PosTerminal`: the transaction's whole life — thread,
    busy, cancel, its steps and its end — plus card search, host, printer. `EmvEngine`: the EMV
    steps stream, the APDU trace, the retry request; no transaction state (whether a step may
    still run is asked of the `Transaction` before it is published).
    `AbstractEmvBehavior` + vendor: EMV steps and kernel work, nothing about threads, busy or
    the host. UI: observes and asks, owns no transaction state. When a change makes a class do
    another's job, move the job, don't bridge it — remove the redundant copy rather than add a
    layer to reconcile two.
11. **A step exists only where the kernel does that work.** Before publishing or moving a step
    (`SET_TRANSACTION_DATA`, `READ_APPLICATION_DATA`, …) check the SDK: which native call runs
    there, in which order (rule 2), once per attempt. Atomic kernels (one native call) announce
    steps from callbacks and leave the hooks empty with a comment; they don't fake a stage.
12. **Kernel callbacks** (`IContactCallback`, `IContactlessCallback`, `EMVCallback` adapters):
    thin, plain (never `suspend`, never Rx), and on every path — including exceptions and
    cancel — they end with `setCallBackResult` and a fail-closed value (`askStep` does this for
    you). Anything that waits for a person has a timeout that answers `USER_CANCEL`, never
    "pick the first". (BACKLOG 0.4 and 1.1 are fixed.)
13. **Rx only where it earns its place, and safely.** The kernel and every callback stay on the
    one transaction thread; Rx never moves them. See the facts in §6 before touching
    `PosTerminal`, `EmvEngine` or `MainViewModel`. Anything about Rx behavior you haven't
    proven in a test is marked *unverified*, not assumed.
14. **The vendor seam is the only layer.** `core` and `app/src/main` must stay
    vendor-independent: they import no vendor class, so the same code runs on any POS. All
    vendor-specific SDK code belongs in its vendor flavor. Adding a vendor must not require
    changing the core transaction flow. A new POS = a flavor with a `TerminalFactory`, a
    `PosTerminal` subclass (`searchCard`, `cancelCardSearch`), an `AbstractEmvBehavior`
    subclass (the `onXxx` hooks, `readIccTag`), and optionally its own `CommunicationBehavior` /
    `PrinterBehavior`. `pax`, `ingenico` and `fake` are the implementors that justify the seam;
    do not add unnecessary layers or abstractions — rule 5 still forbids every other facade or
    single-implementor interface. Keep `fake` compiling and testable: it is the proof that core
    is vendor-free.
15. **Logs say what was decided and why.** Every path that drops, retries, ends or fails closed
    writes one line saying what happened, why, and what was done instead — to the APDU trace
    (`apduTrace.note(tag, text)`; tags `EMV`, `PICC`, `ICC`) and `EmvLog.d` (debug only).
    Examples: `EmvStep dropped, no transaction running`, `no answer for <step> — answering
    <value>`, `RETRY #n`, `online step did not run — online failed`. Never a PAN, track, PIN
    block, key or token (BACKLOG 5.2 lists the ones that still leak).
16. **Before saying "done"** (on top of §1): grep that nothing is duplicated (rule 9);
    each new guard has a test that fails when the guard is removed (mutation check), named in
    the commit; say what is not device-tested. Docs: at the daily 5 PM update (§1), not in the
    same commit — unless he asks for them now.

## 3. Where things are

| What | File |
|---|---|
| Transaction thread, busy, cancel, host timeout, printing | `core/.../terminal/PosTerminal.java` |
| Step bus, drop-after-end, APDU trace | `core/.../engine/EmvEngine.java`, `core/.../util/ApduTrace.java` |
| Step hooks, `goToStep`, `askStep`, field 55 | `core/.../terminal/AbstractEmvBehavior.java`, `core/.../card/Field55.java` |
| PAX flow (contact / contactless runners, callbacks, PIN, online) | `vendor-pax/.../vendor/pax/PaxEmvBehavior.java` |
| Card search (polling, ICC retry) | `vendor-pax/.../vendor/pax/PaxTerminal.java` |
| Contact kernel wrapper + callback adapter | `pax-libs/sdk/emvlib/dpas/.../contact/ContactProcess.java` |
| Contactless dispatcher, reselect | `pax-libs/sdk/emvlib/.../contactless/ClssProcess.java` |
| Per-kernel stages | `pax-libs/sdk/emvlib/.../contactless/Clss{PayPass,PayWave,EFT,...}Process.java`, base `ClssKernelProcess` |
| Parameter cache, DB → kernel conversion, terminal config | `pax-libs/ours/bizentity/.../configservice/impl/EmvParamService.java` |
| XML parsing | `pax-libs/ours/bizentity/.../configservice/xml/{EmvXmlParamParser,ClssXmlParamParser,XmlDomUtils}.java` |
| Download apply / delete / result | `pax-libs/ours/bizentity/.../configservice/impl/EmvParamUpdater.java`, `EmvParamUpdateResult.java` |
| EMV Params screen (Cached / DB, download, Downloaded XML) | `app/src/main/.../ui/EmvParamActivity.java`, `EmvParamListFragment.java`; PAX data: `vendor-pax/.../PaxEmvParams.java` (`core` contract `EmvParams`) |
| Host envelope / sale / onboarding | `network/...` (`HostCommunicationBehavior`, `SaleCommunicationBehavior`, `OnboardingClient`) |
| Seeds | `pax-libs/ours/bizentity/src/main/assets/{acquirer,config,...}.json` |

## 4. Branches (as of 2026-10-25)

- **`transactionOwnsSteps`** — the one working branch (decision 23/10: no ports, no parallel
  design branches). Every commit goes here. Device-tested at `19cc019`: chip sale, tap again.
  Its handoff: `doc/handoff-2026-10-23.md` (decision log §2, risks §4, SDK plan §5).
- **`transactionOwnsSteps-clean`** — a code-only copy of `19cc019` for walkthroughs: no tests,
  no comments, no `doc/` (tokens and `core` bytecode proven identical). Never merge it back;
  remake it from `transactionOwnsSteps` when needed.
- Older (history only, don't merge blindly): `Finalvision` (device-tested 15/10, direct
  dispatch; catch-up in `catchup-finalvision.md`), `coroutineDesign` (Kotlin; built on the
  discarded `ae25378`; `catchup-coroutine.md`), `rxDispatchEmvSteps`, `rxSetTransactionData`,
  `rxTransactionDesign`, `add-rsa-pin-key-injection`, many `claude/*`.

## 5. Build & test

- AGP 8.2.2, Gradle 8.2, Java 17, compileSdk 34, minSdk 24. Java + RxJava 3 (Kotlin: planned).
- Flavors: vendor `pax|ingenico|fake` × environment `prod|envtest|uat|uatr3|bank|pos_as_atm|dss`
  (`BuildConfig.baseUrl`) × production `dev|staging|live` → e.g. `paxEnvtestDevDebug`.
- `~/.gradle/gradle.properties` (never committed): `DATABASE_PWD`, `MTOKEN_TEMP`.
- He builds in Android Studio (Windows), runs on a PAX A920 (Vysor).
- **Unit tests** (`src/test/java`, JUnit 4, run on the PC JVM, not in the APK):
  `./gradlew :core:testDebugUnitTest`, `:network:…`, `:bizentity:…`, or the green arrow in
  Android Studio. Core: 76 — `AskStepTest`, `EmvStepObserverTest`,
  `EmvBehaviorContractTest`, `TransactionStepTest`, `Field55Test`, `PosTerminal*Test` (tests
  drive a behavior as `PosTerminal` does: `TestSession.attempt` = `prepare` + `start`).
  Bizentity: 25 — XML parser tests on the host's real sections (`clss_param_paypass.xml`,
  `clss_param_paywave.xml`), `PaywaveAidFloorLimitsTest`, `EmvParamServiceCacheTest`.
- **Cloud container: no Android SDK → no Gradle.** Tests run with javac + JUnit + stub jars;
  vendor code is checked by comparing the javac error set to a baseline (`vendor-pax` 205
  errors, all missing Android / PAX SDK symbols; fake and ingenico 0) plus a mutation check that
  the harness sees the file. The harness (stubs, jars) lives outside the repo and is rebuilt per
  session.
- Logcat: `EmvLog` (steps), `DeviceImplNeptune` (APDU, debug), `HostComm`, `SaleComm`,
  `ClssPayWaveProcess` (9F35 / 9F33 / TTQ / limits), `ClssXmlParamParser`, `EmvParamUpdater`.
- GreenDAO `SCHEMA_VERSION = 1`, no migrations: a schema change needs **uninstall + install**
  (rxSet added `paywave_aid.TERMINAL_CAPABILITY`). DAOs are hand-maintained (no generator).

## 6. Architecture

- Modules: `app` (vendor source sets), `core` (zero vendor imports), `network`, PAX stack
  (`emvflow emvbase emvlib emvlib:base emvlib:dpas emvservice poslib bizlib bizentity
  commonutils`) — under `pax-libs/`: `pax-libs/sdk/` = PAX's own SDK code (emvbase, emvlib,
  emvservice, bizlib, poslib, commonutils; touched only for a clear EMV reason), `pax-libs/ours/` =
  PAX-only modules we write and change (emvflow, bizentity). Gradle names stay short (`:poslib`),
  mapped in `settings.gradle`. Only `vendor-pax` depends on them. Vendors are flavors with their own `TerminalFactory`. WMRouter can't run on
  AGP 8 → `new X()`.
- **`PosTerminal`** owns the transaction's whole life. One transaction thread (a plain
  single-thread `Executor`, never interrupted), one `AtomicReference<Transaction> current`
  (busy = non-empty). `startTransaction` / `acceptCard`: `current.compareAndSet` on the caller's
  thread — busy → `TerminalBusyException` at once, never queued — then
  `transactionExecutor.execute(transaction::run)`; returns the `Transaction` (the handle a screen
  cancels and observes). `PosTerminal.authorize` = the host call, 90 s, `ONLINE_*` steps around it.
- **`PosTerminal.Transaction`**: `end` (`volatile`, written only under the lock) is its whole
  state — `null` = running; the first COMPLETED / ERROR, or the cancel's CANCELLED, = ended.
  `steps()`: its own `ReplaySubject` (replays everything to a late subscriber, never completes;
  each subscriber a `SafeObserver`); the subject is also the lock, so a cancel's CANCELLING never
  lands after the end. `cancel()` = CANCELLED as the end + CANCELLING at once +
  `cancelCardSearch()`; the EMV winds down at its next `goToStep`. `run()`: `engine.begin()`,
  TRANSACTION_STARTED, then the attempt loop (`attempt` = `behavior.prepare` → `searchCard` →
  `behavior.start` on the card found; a retry is the next turn); its `finally` frees the
  terminal and then publishes the end, once ("Transaction ended without an outcome" if none).
- **`EmvEngine`**: `emvSteps` (PublishSubject, not serialized, no `observeOn`), `ApduTrace`,
  retry request, `authorize` → `PosTerminal::authorize`. No transaction state. Made in
  `PosTerminal`'s constructor and handed once to the behavior (`attach`); kept only in
  `AbstractEmvBehavior` (private field); everyone reads it through `engine()`.
- **Contracts**: `EmvBehavior` = `prepare` / `start` / `dispose` only (`EmvBehaviorContractTest`);
  `attach` / `engine()` are `AbstractEmvBehavior`'s, so `PosTerminal.behavior` is an
  `AbstractEmvBehavior`. `AbstractEmvBehavior`: 16 `protected abstract onXxx`, optional
  `onSearchCard`, `readIccTag`, `goToStep` (the one cancel check), `askStep(step, detail,
  failClosed)` / `answerStep`, `publish`, `finish*`. `initEmvObserver()` runs in `prepare` only
  (it keeps the dispatch observer the last subscriber). `TransactionStep.isEnd()` = COMPLETED,
  ERROR or CANCELLED.
- **Threading**: kernel + callbacks on the transaction thread only. `askStep` works because
  the hook runs before `goToStep` returns — keep dispatch synchronous.
- **Rx facts (proven by tests or the lifecycle tests)**: PublishSubject is synchronous and
  re-entrant; `takeUntil` drops re-entrant `onNext`; a throwing observer sticks a
  `SerializedSubject`; `subscribe(Consumer)` errors crash.
  - Why the transaction lifecycle has no Rx: `subscribeOn` + dispose before the task starts
    means the task **never runs** (whatever it would have freed stays held); a
    cancellable/`doOnDispose` can fire after `onComplete`; `Completable.defer` was needed just to
    keep the busy check on the caller's thread. A plain executor always runs its task and never
    interrupts it.
  - A `BehaviorSubject` replays to new subscribers; every consumer then needed `skip(1)`.
    A `PublishSubject.toSerialized()` replays nothing (see `EmvEngine` above).
  - In Kotlin (not yet done): `StateFlow` conflates (APPROVED then COMPLETED can arrive as
    COMPLETED only); `flowOn` around the busy check would queue a second sale; `awaitClose`
    also runs after a normal close.

## 7. EMV flow (PAX)

**Contact** [rxSet]: APP_SEL (`EMVAppSelect`; app choice via `askStep`) → READ
(`EMVReadAppData`) → SET (`setTransactionData`: confirm card, 9C) → ODA (`EMVCardAuth`) → PR
(`EMVStartTrans`: PR+CVM+TRM+TAA+1st GAC; PIN via `askStep`) → START_ONLINE (host) →
ISSUER_AUTH (`EMVCompleteTrans`) → COMPLETION (reports the outcome once).

**Contactless**: `ClssKernelProcess#supportsGranularSteps()` — granular: EFT, PayPass, PayWave
[rxSet]; atomic: the other 7 (one call, steps announced from callbacks).
- Granular: APP_SEL → SET → READ → ODA/PR (PayWave: PR then ODA,
  `restrictionsBeforeOfflineDataAuth()`) → remaining call → online → ISSUER_AUTH (2nd tap) →
  SCRIPT (PayWave `Clss_IssScriptProc_Wave`) → COMPLETION.
- PayPass: the ODA stage loads the CAPK; the ODA itself is inside `Clss_TransProc_MC_MChip`.
- `CLSS_RESELECT_APP` from any granular stage → `reselectApplication()` → start over at SET.
- An AAC from `Clss_Proctrans_Wave` makes no further kernel call (`cardDeclined`).

**Facts**
- **Never write 9F03 into the contact kernel** (AUC → cashback → TVR b2b5 → IAC-Denial → AAC
  −11; device-confirmed). `readIccTag` answers 9F03 as zeros. Sale transType `0x00`.
- Online-only: `forceOnline=1`; nothing is approved offline. A kernel TC / offline approval
  after the 1st GAC goes to the host without an ARQC and the host's answer is the result
  (`needsHostWithoutArqc`, REFERENCE §5.5); an OFFLINE_APPROVED outcome at the end = Online
  Failed. A refund always goes online. (Decide if TC-to-host is the rule: BACKLOG 1.6.)
  [rxSet] a cancel before the host step sends nothing; the card still gets its 2nd GAC.
- **Terminal Capabilities**: Visa/contact take one **9F33** (Visa: `TERMINALCAPABILITIES`
  CardDataInput+CVMSupport+OffDataAuth → `aucTmCap`, e.g. E06848). **PayPass takes separate
  tags** — DF8117 / DF8118 (CVM required) / DF8119 (no CVM) / DF811F — the kernel builds 9F33
  per transaction (E060C8 above the CVM limit, E008C8 below). Never merge them.
- Amount limits reach the kernel as **n12 BCD** (`getPaddedNumber(x, 12)`) or as native
  `long` fields (`Clss_PreProcInfo`, `Clss_VisaAidParam`, `EMV_APPLIST`). Exception: RuPay
  floor limit padded to 8 (unused).
- PIN: `getPinBlock` timeout in **ms**, no UI of its own; online PIN = AES TPK under TLK 0,
  ISO 9564 format 4. Close each reader in its own try/catch (ICC#97 otherwise).

## 8. Parameters

- **Where parameters live**: GreenDAO is the source of truth (from the host XML; the JSON seed
  once, at first start, as a fallback). The cache (`EmvParamService.cache`, static, one per
  process) is its in-memory copy and the only thing applied to the kernel, in `preTransProcess`
  on every attempt. The kernel stores nothing.
- **Cache**: `getCachedEmvParam()` — `null` → `loadFromDatabase()` reads every table and
  `toEmvProcessParam` sets all 13 parameters (an empty table = an empty list, never `null`;
  `EmvParamServiceCacheTest`). Any DB write (download, seed) calls `invalidate()`; the next
  transaction reloads (`initializeTerminalIfNeeded` → `isLoaded()`). Don't read the DB per
  transaction.
- **Download** (`EmvParamUpdater.applyFromZip`): contact XML → contact AIDs (ICS profile via
  `CARDSCHEMECONFIGRATION`) + CAPKs; contactless XML → PayPass (`PAYPASSCONFIGURATION` shared
  by all AIDs), PayWave (`TERMINALCAPABILITIES` shared; `INTERWARELIST` per transaction type;
  floor = `ContactlessFloorLimit`), Amex (+DRL). [rxSet] a contactless kernel the package
  doesn't bring is **deleted** (DPAS, EFT, JCB, Mir, PBOC, Pure, RuPay always: no parser). The
  result lists applied / removed / each file's sections; the cache reloads whenever the DB
  changed.
- Not read on purpose: `OTHERCONFIGURATION` (terminal identity stays local); PayWave
  `PROGRAMIDLIST` (DRL, unused) and `TransTypeCapa/TermInput/TermOutput` (mapping unknown).
- **Terminal identity**: TID / MID from `acquirer.json` → `Acquirer` table; MCC, merchant
  name/address, conversion rate from `config.json`. Demo values today (TID 12345678, MCC
  0840); nothing updates them; the host's `SaleResponse` carries the real TID/MID. Open:
  onboarding vs a settings screen.
- **EMV Params screen**: two sources, no fallback between them — **Cached** (the kernel's cache
  as it is, `EmvParams.cachedRows`; never loads it: "The cache is empty — it loads at the first
  transaction") and **DB** (GreenDAO, `rows`). "Downloaded XML" (debug): the files as the host
  sent them.

## 9. Host

`orchestration/exchange` → `orchestration/sale|refund`, fresh AES TEK RSA-wrapped (key is
PKCS#1). `transactionType` "PURCHASE". Encrypted fields uppercase hex, HMAC base64, amounts
major-unit doubles, `accountId` header. mToken expires ~3 days (`403 Token has expired`,
editable on onboarding). 90 s timeout in `PosTerminal.authorize`. Sale request matches the old
app (`OldAppSaleRequestTest`): posEntryMode chip 051 / contactless 071 / swipe 901 / manual
011, cvm 3 offline PIN / 2 online PIN. Acknowledgement ≠ reversal; not sent yet.

## 10. Device test cases (`report_easylink.xlsx`, "Device test form")

Columns: # / Case / Do / Expect / one Pass-Fail-Not run column per branch / Notes. Status
below = Finalvision and rxDispatchEmvSteps (identical, 2026-10-15): 8 Pass, 2 Fail, 14 Not run.
"Look at" = where the proof is.
**`transactionOwnsSteps` @ `19cc019` (2026-10-25)**: chip sale — Pass; #11 tap again — **Pass**
(it was Fail on the old branches). Every other row: not run on this branch yet.

| # | Case | Code path | Look at | Status |
|---|---|---|---|---|
| 1 | Chip, no PIN | contact runners → online → 2nd GAC | each stage once in UI + APDU trace | Not run |
| 2 | Chip offline PIN (MC) | `onCardHolderPwd` offline → kernel verifies | PIN pad; no PIN block in sale request (`SaleComm`) | **Pass** |
| 3 | Offline PIN wrong then right | kernel asks again (`emvGetHolderPwd` twice) | approved | **Pass** |
| 4 | Chip online PIN (MC) | online PIN pad → PEK + PIN block | `pinKey` + PIN block in the request | Not run |
| 5 | Chip online PIN (Visa) | same | same | Not run |
| 6 | Contactless Visa, no CVM | PayWave (rxSet: granular) | one READ in the trace | **Pass** |
| 7 | Contactless Visa, online PIN | `onCardHolderPwd(true…)` from `startContactlessTransProcess` | PIN pad, host result | Not run |
| 8 | Contactless MC, no CVM | PayPass granular | read / ODA / PR once each | **Pass** |
| 9 | Contactless MC, online PIN | same as 7; 9F33 = E060C8 | PIN pad, host result | Not run |
| 10 | See Phone | `seePhone()` retry, `MAX_TAP_RETRIES` | prompt, 2nd tap completes, >3 declines | **Fail** (open bug) |
| 11 | Tap again during search | `markTryAgainOnCommunicationError`, `onSearchRetry` | "Card not read — tap again", same search | **Fail** (open bug) |
| 12 | Visa second tap | ISSUER_AUTH 2nd tap → (rxSet) SCRIPT step | "Tap the same card again" | Not run |
| 13 | Cancel, then new sale | `PosTerminal` busy / cancel | BUSY or starts; no ERROR on the new sale | Not run |
| 14 | Back, then new sale | same | same | Not run |
| 15 | Cancel after host approved | 2nd GAC still runs; nothing for the cancelled sale | new sale uses its own amount | Not run |
| 16 | Host doesn't answer | `PosTerminal.authorize` 90 s | "Online failed: the host didn't answer in time" | Not run |
| 17 | Print twice | print thread, in order | two receipts, not mixed | Not run |
| 18 | Rotate mid-sale | ViewModel survives | no BUSY; sale carries on | Not run |
| 19 | Params loaded once | `initializeTerminalIfNeeded` | "terminal initialization…" only on the 1st sale | **Pass** |
| 20 | AID in the kernel | cache → `EMVAddApp` | SELECT AID → 9000 | **Pass** |
| 21 | CAPK in the kernel | `addCapk` → `EMVCardAuth` | TVR byte 1: no ODA failure | **Pass** |
| 22 | XML download and apply | `EmvParamUpdater` | "Applied …" (rxSet: also Removed / sections) | **Pass** |
| 23 | XML values reach the kernel | cache reload → limits | PIN only above the XML's CVM limit | Not run |
| 24 | Failed download keeps params | download error path | "Failed: …"; old params; sale works | Not run |

Add for **rxSetTransactionData** (new column + rows): contact banner 5 → 6; Visa 2 → 6 → 5 →
PR → ODA; app-select dialog (debug, even one AID); cancel during PIN / host → one ERROR;
refund online; kernel not in the package removed; Downloaded XML; cached details on tap;
PayWave 9F33 = E06848 in field 55; uninstall first (new column).

Add for **rxTransactionDesign** (its own column): rows 13, 14, 15, 18 (cancel / back / cancel
after approve / rotate — this branch changes their path); Retry after a decline; BUSY screen
when a cancelled search is still stopping; `MainActivity` status after a sale.

## 11. Open

- Device form on `transactionOwnsSteps` (§10): everything but the chip sale and #11.
- Next work, in order (handoff §6): Gradle build + device form → the SDK (handoff §5, after
  §5.4 is answered) → release guards (risks 4–7) → payment safety around the host (risks 1–3).
- Payment safety: cancel during the host call ends CANCELLED although the host may have
  approved; no reversal / ack (BACKLOG 0.5); Retry is offered after "the host didn't answer"
  (1.5); APPROVED then CANCELLED if a cancel lands between them.
- Bugs still open: #10 See Phone not device-tested on this branch; the Visa 2nd tap doesn't wait
  for the card (1.4); `clsLastNeedSeePhone` carries over (2.1); TAC Denial read as DF8121 (2.5);
  "HTTP 403" instead of the server text (4.1); demo TID / MCC.
- Parameters: CAPKs not validated (expiry, checksum, duplicates; 76 / 88 seed CAPKs expired); no
  AID / CAPK check before a transaction; no automatic update; a bad number parses as 0.
- Release gate: full PAN / field 55 on screen and receipt, `Level.BODY` logging, a live mToken in
  `HostAppKeys`, `RELEASE` hard-coded `false` (no DB encryption), no minify / signing — BACKLOG
  §5 and its status table.
- Kotlin + coroutines: on `coroutineDesign`; rules and catch-up in `catchup-coroutine.md`.
- Everything else, ranked and re-checked on 25/10: `doc/BACKLOG.md` (status table at the top).

## 12. Never again

- Don't guess EMV tags/protocol — check PAX source/specs or a real host file; flag assumptions.
- Grep the bare name **and** `Class::method` before deleting "dead" code.
- Never silently delete payment-safety code (torn log) — ask.
- No bulk `sed` renames in Java. Don't dismiss native-lib / permission errors.
- Don't move uncommitted work between branches with stash without saying so.
- "I want to get rid of X" means X goes away, not that it gets hidden behind another layer
  (the first `rxTx` commit hid `scheduleDirect` and added a second steps stream). If unclear
  what he wants gone, ask in one line first.
- Don't write a big doc nobody asked for: a prompt is a prompt. A doc about a design that is
  still moving is stale within the hour.
- Before judging a branch, check which commit it is built on (`coroutineDesign` was built on
  the discarded `ae25378`, so it lacked every later fix).
- Don't refactor on top of code that hasn't passed the device form without saying so.
- A subagent's claim is a claim: verify it in the code before it goes into a doc.
