# CLAUDE.md — EmvEnhance / EasyLink (PAX EMV POS, Android)

Project memory for Claude. Read it before touching code. When a fact here changes, update this
file in the same commit. Never put credentials, tokens, keys, PANs, track data or serial
numbers in it. Kotlin migration plan: `claudekotlin.md`. Full flow reference (architecture,
EmvEngine/EmvStep/onXxx, contact, contactless, EMV params, UI → kernel callback):
`doc/REFERENCE.md`.

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
- `PosTerminal` (core) — the only API the UI uses: start / cancel a transaction, card search,
  host, printer; one transaction at a time on one thread.
- `EmvEngine` (core) — event bus: transaction steps (for the UI) and EMV steps (for the
  behavior and the APDU trace). No EMV logic.
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
- Other agents (Cursor, other sessions) push to this repo: **fetch first**.
- His Excel test form: fill only the cells he asks for; never change his own edits.

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
| `rxTransactionDesign` | rxSet + 2 | Rx | rxSet + the transaction as a `Completable` (**[rxTx]** below). **Not device-tested**. `doc/kotlin-rx-transaction.md` still describes the earlier `Observable` form (`ae25378`) |

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
  Android Studio. Core (rxSet): 65; rxTx: 68 — `AskStepTest`, `EmvStepObserverTest`,
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
- **`PosTerminal`** [rxSet]: one transaction thread
  (`Schedulers.from(Executors.newSingleThreadExecutor())`, `scheduleDirect`), one
  `AtomicReference<Transaction> current` (busy = non-empty, freed when `run()` returns),
  cancel = the transaction's `AtomicBoolean`. Never dispose/interrupt the transaction thread.
  Print: its own single thread, receipts in order.
- **[rxTx] the transaction is a `Completable`**: `startTransaction` / `acceptCard` —
  subscribe = start (`Completable.defer`: busy check on the subscribing thread,
  `onError(TerminalBusyException)`, never queued; then `fromAction(process)` +
  `subscribeOn(transactionScheduler)`, no `scheduleDirect`), `onComplete` = `process` freed the
  terminal, `dispose()` = `doOnDispose(cancel)` for this one only. `Transaction.claimed`
  (one Atomic): a dispose before `process` began frees the terminal itself (the dispose dropped
  the task); otherwise cancel only while `current == this`. Steps: one stream only, the
  engine's `transactionSteps` (`MainViewModel` `skip(1)` + IDLE on each start).
  `cancelTransaction()` is gone; `MainViewModel.getTerminalBusy()`.
- **`EmvEngine`**: `transactionSteps` (BehaviorSubject → `skip(1)`), `emvSteps`
  (PublishSubject, not serialized, no `observeOn`), `ApduTrace`. Drops steps after the end.
- **Contracts**: `EmvBehavior` = `prepare/start/cancel` (+ `dispose`). `AbstractEmvBehavior`:
  16 `protected abstract onXxx`, optional `onSearchCard`, `readIccTag`, `goToStep`, [rxSet]
  `askStep(step, detail, failClosed)` / `answerStep`. `TransactionStep.isEnd()` = "ended".
- **Threading**: kernel + callbacks on the transaction thread only. `askStep` works because
  the hook runs before `goToStep` returns — keep dispatch synchronous.
- **Rx facts (proven)**: PublishSubject is synchronous and re-entrant; `takeUntil` drops
  re-entrant `onNext`; a throwing observer sticks a `SerializedSubject`;
  `subscribe(Consumer)` errors crash.

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
- **Field 55 = old app byte for byte** (`Field55Test`): `5F2A 5F34 82 84 95 9A 9C 9F02 9F03
  9F09 9F10 9F21 9F1A 9F26 9F27 9F33 9F34 9F35 9F36 9F37 9F41 9F42 9F1E` + `9F09 9F33 9F34
  9F35 9F41` + `9F53 9F63 9F66`. Never add `9F49 97 9F40 9F1B 9F15 9F1C`.
- Online-only: `forceOnline=1`; kernel TC / offline approval = Online Failed; a refund always
  goes online. [rxSet] a cancel before the host step sends nothing; the card still gets its
  2nd GAC.
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
  `EMVGetMCKParam`; "Terminal initialization failed" twice; UI shows "HTTP 403" not the server
  text; demo TID/MCC; `emvVerifyPINfailed`/`certVerify` don't call `setCallBackResult`.
- Parameters: `deleteAll` + `insert` not in one transaction (a failed insert leaves the table
  empty → `runInTx`); a bad number parses as 0 silently; CAPKs not validated (expiry,
  checksum, duplicates); no AID/CAPK check before a transaction; no automatic update.
- Reversal/ack, refund AAC at host, withdraw/deposit bodies, remove the hardcoded mToken, gate
  secret logging before live, Ingenico, manual PAN, `ConfigInit` place, pick the mainline.
- Kotlin + coroutines migration: plan in `claudekotlin.md`, not started.

## 12. Never again

- Don't guess EMV tags/protocol — check PAX source/specs or a real host file; flag assumptions.
- Grep the bare name **and** `Class::method` before deleting "dead" code.
- Never silently delete payment-safety code (torn log) — ask.
- No bulk `sed` renames in Java. Don't dismiss native-lib / permission errors.
- Don't move uncommitted work between branches with stash without saying so.
