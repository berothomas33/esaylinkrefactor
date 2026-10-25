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
