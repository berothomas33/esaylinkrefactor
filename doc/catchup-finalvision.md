# catchup-finalvision.md — bring Finalvision to where transactionOwnsSteps is

**Prompt to paste in the Finalvision thread:**

> Read `doc/CLAUDE.md`, `doc/handoff-2026-10-23.md` and `doc/catchup-finalvision.md` (all on branch `transactionOwnsSteps`;
> fetch it). Work on `Finalvision` only. Goal: Finalvision reaches the same stage as
> `transactionOwnsSteps` — every fix and every simplification — in Finalvision's own style:
> direct EMV step dispatch, no Rx in the transaction lifecycle (thread + one Atomic), Rx only
> where a screen observes. Do the stages in order (A → B → P → S → T), skipping what `git log Finalvision` shows already done, one commit per step, commit
> titles as given. For each step: read the source commit body and diff, show me what Finalvision
> has today and what will change (native call order before/after for EMV steps), then wait for
> my go. Port the idea, not the text. Never reorder native calls, never drop payment-safety code
> (torn log, the host guard, fail-closed answers) without asking. Run the core tests after each
> step; say plainly what you couldn't build or test.

---

## Where things are

- **Finalvision** (`fbc1ebc` then; head `cdd056f` on 22/10 — check `git log`): device-tested base + Phase A steps A1–A6 done (thread + Atomic
  lifecycle, `Transaction.end`, CANCELLING, end in `finally`, steps owned by `PosTerminal`, engine
  without transaction state). Direct dispatch; no `askStep`; no rxSet EMV work.
- **transactionOwnsSteps** (source of truth): rxSet EMV work + rxTx lifecycle + today's changes
  (P1–P5) + phase 1 (`step1`) + phase 2 (`step2`).
- Java modules shared by every branch (`emvlib`, `bizentity`, `network`, `emvbase`…): where
  Finalvision's file equals the source's parent, the source diff applies as is; otherwise port
  by hand. Check with `git diff <source>^ Finalvision -- <file>` first.

## Stage A — finish the lifecycle (A7–A9; read the source commits' bodies)

| Step | Source | What |
|---|---|---|
| A7 | `c70ce09` (part 1) | One cancel check at the step boundary: `goToStep` returns whether the step ran; remove the cancel checks in `goToStep`, the dispatch, `prepare`, every PAX runner, `retryWithMode`, Fake/Ingenico `onStartOnlineProcess`; `advance*Stage`: `if (!goToStep(next)) finish*Stage()`; `finish*Stage` skips the result after a cancel with a trace note. Keep `authorizeOnline`'s guard |
| A8 | `c70ce09` (part 2) | Retry = a turn of `process`'s loop; TRANSACTION_STARTED published once by `PosTerminal` |
| A9 | `8ffbc5e` (app part) | `MainViewModel` keeps the `Transaction`, busy caught synchronously; `SearchCardFragment` busy observer after the steps observer |

## Stage B — rxSet's EMV work (decided: needed, phase 1 depends on it)

Keep direct dispatch: a step method runs inside `goToStep`, synchronously, so `askStep` works
the same. One commit per source commit, in this order; a device check after each 🔶.

| Step | Source | What |
|---|---|---|
| B1 | `8bb4d38` | SET_TRANSACTION_DATA gives the granular contactless kernels their data |
| B2 | `bc659c5` | Granular stages: CLSS_RESELECT_APP selects the next candidate |
| B3 🔶 | `1297352` | PayWave: each stage in its own step, in the kernel's order (PR then ODA) |
| B4 | `1c19af9` | PayPass: CAPK loaded in OFFLINE_DATA_AUTHENTICATION |
| B5 | `4d810fa` | `askStep` / `answerStep` |
| B6 🔶 | `9b8c3b3` | Application selection runs in its step |
| B7 🔶 | `b9bd58f` | PIN entry runs in its step |
| B8 🔶 | `922a991` | Contact: SET_TRANSACTION_DATA in its own step after READ |
| B9 | `ceb267e` | The outcome is reported in TRANSACTION_COMPLETION |
| B10 🔶 | `39d49ca` | Online and 2nd GENERATE AC in START_ONLINE_PROCESS / ISSUER_AUTHENTICATION |
| B11 🔶 | `9f14418` | PayWave 2nd tap: issuer auth and scripts in their steps |
| B12 | `ee161fb` | EMV param download: a contactless kernel the package doesn't bring is deleted |
| B13 | `3144589` | EMV params screen: last downloaded XML (debug) |
| B14 | `bb08980` | PayWave params: TERMINALCAPABILITIES for every Visa AID; contactless floor limit |
| B15 | `2a362cf` | EMV params screen: tap a row → the kernel cache's entry |

Skip `2362df8` / `6c280f0` (rxSet's lifecycle — replaced by stage A) and the doc commits.

## Stage P — the 2026-10-22 changes (read each commit's body)

| Step | Source | What |
|---|---|---|
| P1 | `62d889f` | Superseded by S2 (one cache owner) — skip, or do and let S2 replace it |
| P2 | `9fb2d4d` | The `Transaction` holds its state (`end`), its own steps (`steps()`, a per-transaction `ReplaySubject` — Rx only for the screen), CANCELLED end step; no terminal-wide stream; `MainViewModel` one field; `MainActivity` buttons only |
| P3 | `01dc911` | `VendorApplication` per vendor; `EmvEnhanceApp` imports nothing of PAX |
| P4 | `bab0eed` `805c325` `9eed173` `835b8c3` | `:vendor-pax` / `:vendor-ingenico` / `:vendor-fake` modules; app drops `commonutils` |
| P5 | `fa50258` | Every screen in `app/src/main`; vendor data through `PosTerminal` (`serialNumber`, `requiredPermissions`, `countries`/`setCountry`, `emvParams` → `EmvParams`); `VendorDebugPanel` and `app/src/pax` gone; `PinBypassSettings` → `:network` |

## Stage S — the fix phases

**S1 = `ddd8dc1` (`step1`, payment safety / hangs)** — read its body; in short:
`ContactProcess` callbacks always answer (`certVerify`, `emvVerifyPINfailed`, try/catch in
`emvWaitAppSel`/`emvGetHolderPwd`/`emvSetParam`/`emvUnknowTLVData`); `resetParam` Set not Get;
app-select never "picks the first" (no answer → USER_CANCEL, closes on cancel), `askStep` gives
the fail-closed value after the end; PIN pad waits bounded, one offline pad on screen; tap-again
cap in `tryAgain()`, See Phone / Try Again with EMV_OK stop before the host; host answer without
`responseCode` = online failed; contactless online PIN not entered = ERROR, not "Offline Denied".
`ContactProcess` and `SaleResponse` / `SaleCommunicationBehavior` are shared Java — check and
apply the diff.

**S2 = `03b1cde` (`step2`, EMV parameters)** — almost all in `bizentity` (shared Java):
CAPK / revocation / AID lists never null; each section replaced in one DB transaction; the parser
skips a CAPK / revocation without RID/KeyID and logs bad numbers; one cache owner
(`EmvParamService` static cache + `invalidate()`; the `terminal_initialized` pref removed); one
rule for both files (absent = deleted; contactless needs the package's contact file); download /
seed refused while busy (`PosTerminal.isBusy()`); "Insert EMV param" debug only, `ConfigInit`
synchronous; a package with neither file is failed; `PaxEmvParamUpdateService` removed.
Item 12 (gate on host params) is **not** wanted: the terminal runs on the JSON seed until a
download.

## Stage T — after step2, up to `80f4ca9` (read each commit's body; `doc/handoff-2026-10-23.md` §1–§2 has the result)

| Step | Source | What (keep direct dispatch: T5–T7 change who reports and who owns the engine, not how a step runs) |
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

`git diff transactionOwnsSteps Finalvision` shows only Finalvision's own choices (direct
dispatch, its lifecycle style) and doc differences. Device form: Finalvision column, rows 1–24
plus Cancel (CANCELLING → CANCELLED), rotation after APPROVED, BUSY, Retry, PayPass See Phone,
tap too short ×4, PIN bypass, EMV params download / browse, onboarding, country, `fakeDebug`
builds.
