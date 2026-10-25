# CLAUDE.md — EmvEnhance / EasyLink (PAX EMV POS, Android)

Project memory for Claude. Read it before touching code. When a fact here changes, update this
file in the same commit. Never put credentials, tokens, keys, PANs, track data or serial
numbers in it. Kotlin migration plan: `claudekotlin.md`. Full flow reference (architecture,
EmvEngine/EmvStep/onXxx, contact, contactless, EMV params, UI → kernel callback):
`doc/REFERENCE.md`. Everything missing / broken / risky, ranked: `doc/BACKLOG.md`.

---

## 0. What this project is

An Android card-acceptance app ("EasyLink") for **PAX A920** terminals, talking to our own
acquiring host in Egypt (currency EGP/818). It refactors PAX's EDC demo into a clean,
vendor-neutral design: the UI and the transaction logic know nothing about PAX; PAX (and later
Ingenico) plug in as a Gradle flavor.

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
- `PosTerminal` (core) — the only API the UI uses: start / cancel a transaction and its steps
  (for the UI), card search, host, printer; one transaction at a time on one thread.
- `EmvEngine` (core) — the EMV steps bus (for the behavior and the APDU trace), the trace, the
  retry request. No EMV logic, no transaction state.
- `AbstractEmvBehavior` (core) — the 16 EMV step hooks a vendor implements, `goToStep`,
  `askStep`, field 55, `authorizeOnline`.
- `PaxEmvBehavior` (app/src/pax) — PAX's implementation: drives `ContactProcess` /
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
   `PosTerminal.Transaction.end` (one `AtomicReference`, compare-and-set); transaction steps =
   `PosTerminal.transactionSteps`; EMV steps = `EmvEngine.emvSteps`. A class that needs another's
   state gets a narrow read (e.g. `EmvEngine.isRunning()` / `isCancelled()`, a
   `BooleanSupplier`), never its own copy.
   Prefer removing redundant code over adding abstractions.
10. **Separation of responsibilities.** `PosTerminal`: the transaction's whole life — thread,
    busy, cancel, its steps and its end — plus card search, host, printer. `EmvEngine`: the EMV
    steps stream, the APDU trace, the retry request; it reads the transaction's state through
    narrow hooks and hands its transaction steps to `PosTerminal`.
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
    "pick the first". Open violations: BACKLOG 0.4, 1.1.
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
    same commit.

## 3. Where things are

| What | File |
|---|---|
| Transaction thread, busy, cancel, host timeout, printing | `core/.../terminal/PosTerminal.java` |
| Step bus, drop-after-end, APDU trace | `core/.../engine/EmvEngine.java`, `core/.../util/ApduTrace.java` |
| Step hooks, `goToStep`, `askStep`, field 55 | `core/.../terminal/AbstractEmvBehavior.java`, `core/.../card/Field55.java` |
| PAX flow (contact / contactless runners, callbacks, PIN, online) | `app/src/pax/.../vendor/pax/PaxEmvBehavior.java` |
| Card search (polling, ICC retry) | `app/src/pax/.../vendor/pax/PaxTerminal.java` |
| Contact kernel wrapper + callback adapter | `emvlib/dpas/.../contact/ContactProcess.java` |
| Contactless dispatcher, reselect | `emvlib/.../contactless/ClssProcess.java` |
| Per-kernel stages | `emvlib/.../contactless/Clss{PayPass,PayWave,EFT,...}Process.java`, base `ClssKernelProcess` |
| Parameter cache, DB → kernel conversion, terminal config | `bizentity/.../configservice/impl/EmvParamService.java` |
| XML parsing | `bizentity/.../configservice/xml/{EmvXmlParamParser,ClssXmlParamParser,XmlDomUtils}.java` |
| Download apply / delete / result | `bizentity/.../configservice/impl/EmvParamUpdater.java`, `EmvParamUpdateResult.java` |
| EMV Params screen (download, Downloaded XML, cached details) | `app/src/pax/.../ui/EmvParamActivity.java`, `EmvParamListFragment.java`, `ParamDump.java` |
| Host envelope / sale / onboarding | `network/...` (`HostCommunicationBehavior`, `SaleCommunicationBehavior`, `OnboardingClient`) |
| Seeds | `bizentity/src/main/assets/{acquirer,config,...}.json` |

## 4. Branches (as of 2026-10-20)

All start from `4a74277`. Keep shared fixes in sync where they apply.

| Branch | Commits | Dispatch | Status |
|---|---|---|---|
| `Finalvision` | 8 | direct | end-of-transaction, contracts, 9F03 fix. Safest. Device-tested (form) |
| `rxDispatchEmvSteps` | 12 | Rx observer | same + Rx dispatch. Device-tested (form) |
| `rxSetTransactionData` | 29 | Rx | everything marked **[rxSet]** below. **Not device-tested** |
| `coroutineDesign` | rxSet + 7 | Kotlin | the whole app in Kotlin + coroutines, **built on `ae25378`** (the Observable detour) — lacks rxTx's lifecycle in `PosTerminal` (one owner, ends once). Not device-tested |
| `rxTransactionDesign` | rxSet + 8 | Rx (UI only) | rxSet + the transaction owned by `PosTerminal`, no Rx in its lifecycle (**[rxTx]** below). **Not device-tested**. |

Mainline not chosen; the plan is `rxSetTransactionData` once it passes the form. Old:
`add-rsa-pin-key-injection`, many stale `claude/*` branches — don't merge blindly.

## 5. Build & test

- AGP 8.2.2, Gradle 8.2, Java 17, compileSdk 34, minSdk 24. Java + RxJava 3 (Kotlin: planned).
- Flavors: vendor `pax|ingenico|fake` × environment `prod|envtest|uat|uatr3|bank|pos_as_atm|dss`
  (`BuildConfig.baseUrl`) × production `dev|staging|live` → e.g. `paxEnvtestDevDebug`.
- `~/.gradle/gradle.properties` (never committed): `DATABASE_PWD`, `MTOKEN_TEMP`.
- He builds in Android Studio (Windows), runs on a PAX A920 (Vysor).
- **Unit tests** (`src/test/java`, JUnit 4, run on the PC JVM, not in the APK):
  `./gradlew :core:testDebugUnitTest`, `:network:…`, `:bizentity:…`, or the green arrow in
  Android Studio. Core (rxSet): 65; rxTx: 77 — `AskStepTest`, `EmvStepObserverTest`,
  `EmvBehaviorContractTest`, `TransactionStepTest`, `Field55Test`, `OldAppSaleRequestTest`,
  `PosTerminal*Test`. Bizentity: XML parser tests on the host's real sections
  (`clss_param_paypass.xml`, `clss_param_paywave.xml`), `PaywaveAidFloorLimitsTest`.
- **Cloud container: no Android SDK → no Gradle.** Tests run with javac + JUnit + stub jars;
  vendor code is checked by comparing the javac error set to a baseline (emvlib ≈ 2558 known
  errors) plus a mutation check that the harness sees the file.
- Logcat: `EmvLog` (steps), `DeviceImplNeptune` (APDU, debug), `HostComm`, `SaleComm`,
  `ClssPayWaveProcess` (9F35 / 9F33 / TTQ / limits), `ClssXmlParamParser`, `EmvParamUpdater`.
- GreenDAO `SCHEMA_VERSION = 1`, no migrations: a schema change needs **uninstall + install**
  (rxSet added `paywave_aid.TERMINAL_CAPABILITY`). DAOs are hand-maintained (no generator).

## 6. Architecture

- Modules: `app` (vendor source sets), `core` (zero vendor imports), `network`, PAX stack
  (`emvflow emvbase emvlib emvlib:base emvlib:dpas emvservice poslib bizlib bizentity
  commonutils`). Vendors are flavors with their own `TerminalFactory`. WMRouter can't run on
  AGP 8 → `new X()`.
- **`PosTerminal`** owns the transaction's whole life [rxTx]. One transaction thread (a plain
  single-thread `Executor`, never interrupted), one `AtomicReference<Transaction> current`
  (busy = non-empty). `startTransaction` / `acceptCard`: `current.compareAndSet` on the caller's
  thread — busy → `TerminalBusyException` at once, never queued — then
  `transactionExecutor.execute(process)`; returns the `Transaction` (the handle a screen
  cancels). `Transaction.end` (`AtomicReference<TransactionStepEvent>`) is its whole state:
  `null` = running; the first ERROR/COMPLETED, or the cancel's ERROR, = ended; one
  compare-and-set, so it ends exactly once and a second cancel / a cancel after the end does
  nothing. `cancel()` = that CAS, then CANCELLING at once (a non-end step), then
  `cancelCardSearch()`; the EMV winds down at its next check.
  `process` always runs (even cancelled before it started) and its `finally` is the one place the
  end is published — after `current` is freed, so COMPLETED/ERROR means "the terminal is free"; a
  search with no outcome ends as "Transaction ended without an outcome". Steps:
  `PosTerminal.transactionSteps` (plain `PublishSubject`, no replay, never completes), published
  only while the current transaction runs. Every publish holds one lock (the subject): no two
  `onNext` at once, and a cancel's CANCELLING can never land after the end — one atomic can't
  order a publish across two threads. Each subscriber is a `SafeObserver`: one that throws is
  cut off alone; the stream and the transaction thread carry on. Print: its own single thread, receipts in order. [rxSet] had
  `scheduleDirect` + `boolean`; an rxTx detour had a `Completable` (`13e2b02`, `0bee281`) —
  gone: it was a second "ended" signal next to the steps.
- **`EmvEngine`**: `emvSteps` (PublishSubject, not serialized, no `observeOn`; dropped once the
  transaction ended), `ApduTrace`, retry request. No transaction state of its own: it reads
  `isRunning()` / `isCancelled()` and hands `notifyXxx` to `PosTerminal` (constructor hooks).
- **Contracts**: `EmvBehavior` = `prepare/start` (+ `dispose`; [rxTx] no `cancel`). `AbstractEmvBehavior`:
  16 `protected abstract onXxx`, optional `onSearchCard`, `readIccTag`, `goToStep`, [rxSet]
  `askStep(step, detail, failClosed)` / `answerStep`. `TransactionStep.isEnd()` = "ended".
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
  (`needsHostWithoutArqc`, REFERENCE §4.4); an OFFLINE_APPROVED outcome at the end = Online
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

- **Cache**: `EmvParamService.getCachedEmvParam()` — loaded from the DB once, used by every
  transaction ("Use cached EMV param"); reloaded on the first transaction after a download or
  onboarding (`terminal_initialized` flag) and after a restart (empty cache). Correct design;
  don't read the DB per transaction.
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
- **Debug tools** [rxSet]: "Downloaded XML" button (the files as the host sent them); tap a
  row in a category → that entry in the kernel's cache, every field (`ParamDump`).

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

- Device test of rxSet (§10).
- Bugs: #10 See Phone, #11 tap again; PayPass try-again / See Phone OK → offline decline;
  try-again has no cap; TAC Denial read as 8121 (contact); `resetParam` calls
  `EMVGetMCKParam`; "Terminal initialization failed" twice ([rxTx]: fixed by the end guard);
  UI shows "HTTP 403" not the server text; demo TID/MCC; `emvVerifyPINfailed`/`certVerify` don't call `setCallBackResult`.
- Parameters: `deleteAll` + `insert` not in one transaction (a failed insert leaves the table
  empty → `runInTx`); a bad number parses as 0 silently; CAPKs not validated (expiry,
  checksum, duplicates); no AID/CAPK check before a transaction; no automatic update.
- Reversal/ack, refund AAC at host, withdraw/deposit bodies, remove the hardcoded mToken, gate
  secret logging before live, Ingenico, manual PAN, `ConfigInit` place, pick the mainline.
- ~~Re-evaluate `startTransaction()` and the `Completable` design~~ — done on
  `rxTransactionDesign`: the lifecycle and the transaction steps live in `PosTerminal`, no Rx in
  the lifecycle (§6). Not device-tested.
  - Last change (`40f080d`) — goal: a cancel the UI can see at once without breaking "the end
    means the terminal is free". `CANCELLING` (a non-end step) is published the moment
    `cancel()` wins; ERROR "Transaction cancelled" still comes from `process`'s `finally`. One
    lock (the subject) orders the two across threads; each subscriber is a `SafeObserver`, so
    one that throws can't stop the steps or the transaction thread.
- Kotlin + coroutines migration: plan in `claudekotlin.md`; in progress on `coroutineDesign`.
- Everything else found by the 2026-10-20 scan (PCI, fail-open host response, Back, DB
  encryption, receipt, tests…): `doc/BACKLOG.md`.

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
