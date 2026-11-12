# EasyLink — code reference

> Written from the code of branch **`transactionStepObserver`** (2026-11-12, `abfff24`). Every name here exists in the code.
> The drawings: `doc/EasyLink-EMV-Flows.html` / `.pdf` (and https://claude.ai/artifact/S69zGozrJmfoGCVe3VkAnh). This file is the short reference: where things are, who owns them, the rules.
> ⚠ = not fixed yet; the number in brackets is its `doc/BACKLOG.md` item. Last full device test at `19cc019` (chip, tap again); later work is device-tested only where it says so.
> If the code changed and this file did not, the code is right.

## Contents

1. [Where everything is](#1-where-everything-is)
2. [Who owns what](#2-who-owns-what)
3. [The sale and its steps](#3-the-sale-and-its-steps)
4. [EMV steps](#4-emv-steps)
5. [The way to the bank](#5-the-way-to-the-bank)
6. [EMV params](#6-emv-params)
7. [Threads](#7-threads)
8. [When you change something](#8-when-you-change-something)
9. [The difference with paxEmvBehaviorSplit](#9-the-difference-with-paxemvbehaviorsplit)

---

## 1. Where everything is

| What | Where |
|---|---|
| The terminal, the sale, its steps, cancel, the safe end | `core/.../terminal/PosTerminal.java` (+ `Transaction`) |
| The shared EMV: attempts, `goToStep`, `ask`, `askTheBank`, the 16 `onXxx` hooks | `core/.../terminal/AbstractEmvBehavior.java` |
| The EMV's way to the bank | `core/.../terminal/BankRequest.java` |
| The EMV step stream (banner, dispatch) and the APDU trace | `core/.../emv/EmvEngine.java`, `ApduTrace.java` |
| Field 55 | `core/.../emv/Field55.java` (+ `field55()` in each vendor) |
| The card | `core/.../card/CardPresence.java` (`isEmv()` / `isNonEmv()`) |
| PAX: the steps, the kernel callbacks, the way to the bank | `vendor-pax/.../PaxEmvBehavior.java` |
| PAX: search, PIN, the bank's answer in PAX shape, readers, card data, setup, one attempt's notes | `PaxCardSearch`, `PaxPinEntry`, `PaxHostAnswer`, `PaxReaders`, `PaxCardData`, `PaxSetup`, `PaxKernelSession` |
| The contact kernel and its callbacks | `vendor-pax/libs/sdk/emvlib/dpas/.../contact/ContactProcess.java` (`EmvCallBackListener`) |
| The contactless kernels | `vendor-pax/libs/sdk/emvlib/.../contactless/ClssProcess.java` + `Clss*Process` (DPAS: `emvlib/dpas`) |
| The bank | `network/.../SaleCommunicationBehavior.java`, `HostApiClient.java` |
| Params (cache, XML, download) | `vendor-pax/libs/ours/bizentity/.../EmvParamService.java`, `EmvParamUpdater.java` |

## 2. Who owns what

Each piece of state has one owner and one copy:

| State | Owner |
|---|---|
| The terminal is busy | `PosTerminal.current` |
| The sale's state (running / ended / cancelled) | Its steps: `Transaction.decision()` (no field) |
| The sale's steps | `Transaction.steps` (`ReplaySubject`, also the lock; completes after the end) |
| The EMV steps | `EmvEngine.emvSteps` (one for the terminal, never completes) |
| Retry | `AbstractEmvBehavior` (`requestRetry` / `retryCount()`) |
| What an asked hook needs from the kernel callback (PAX) | `PaxKernelSession`: `tx.pinRequest`, `tx.candidates` (written by the callback right before `ask`) |
| The bank's answer, for the EMV | `AbstractEmvBehavior.bankAnswer()` (cleared at each attempt and at the end of `run`) |
| The bank's answer, for the safe end | `Transaction` → `OnlineRequest.answer` |
| What one attempt knows (PAX) | `PaxKernelSession` (new at each attempt) |

- The EMV (`AbstractEmvBehavior` + vendor) knows nothing of threads, the busy state or the bank; it has a `BankRequest` only.
- `PosTerminal` holds no EMV logic.
- The screen holds no sale state and knows no vendor.

## 3. The sale and its steps

Every step enters through one method, `publish` (it drops what comes after the decision, checked before the lock and again under it). `TransactionStepObserver` then runs the step's case in `dispatchTransactionStep`:

| Step | Method | Does |
|---|---|---|
| `TRANSACTION_STARTED` | `onTransactionStarted` | Moves the sale to the transaction thread (`runTransaction`) |
| `CANCELLING` | `onCancelling` | Stops the card search |
| `APPROVED` | `closeLast(ACKNOWLEDGING)` | If the bank approved the last request: acknowledge it (`closeAtHost`) |
| `DECLINED` | `closeLast(REVERSING)` | If the bank approved the last request and the card declined: reverse it |
| `ENDING` | `onEnding` | Reverse every request with no `ACKNOWLEDGING` / `REVERSING` for its id, if the bank may have approved it (after a cancel, `FAILED`, or an earlier attempt). Then free the terminal, deliver the end, complete the steps |
| Any other | — | A report for the screen: `ONLINE_*`, `CARD_*`, `SINGLE_TAP_PIN_REQUIRED`, `FAILED`, `ACKNOWLEDGING` / `REVERSING` / `CLOSING_COMPLETED`, `COMPLETED` / `ERROR` / `CANCELLED` |

**Order:** `TRANSACTION_STARTED → WAITING_FOR_CARD → CARD_DETECTED → … → ONLINE_REQUIRED → ONLINE_PROCESSING → ONLINE_COMPLETED → APPROVED | DECLINED → (ACKNOWLEDGING | REVERSING → CLOSING_COMPLETED) → ENDING → (REVERSING → CLOSING_COMPLETED)… → COMPLETED`, or `FAILED → ENDING → … → ERROR`, or `CANCELLING → ENDING → … → CANCELLED`.

**The state is the steps.** The first decision step delivered is the state. `decision()` looks through the stored steps for the first `CANCELLING`, `APPROVED`, `DECLINED` or `FAILED` (`TransactionStep.isDecision()`). `isRunning()` = no decision; `isCancelled()` = the decision is `CANCELLING`. The first decision wins; later ones are dropped.

| Decision | From | The end delivered last (`endOfDecision`) |
|---|---|---|
| `CANCELLING` | `cancel()` | `CANCELLED` |
| `APPROVED` / `DECLINED` | `finishApproved` / `finishDeclined` → `publish` (the step is forced there) | `COMPLETED` |
| `FAILED` + message | `finishError` / `fail(message)`, or the `finally` ("Transaction ended without an outcome") | `ERROR` with the same message |

- **Why `FAILED`:** `ERROR` is the end the screen shows, and the terminal stays busy until the closings finish. A failure needs a step at the moment it happens; the end comes later.
- **The end:** the `finally` of `runTransaction` makes sure there is a decision and publishes `ENDING`. The terminal stays busy until every closing is done; then `current = null`, then the end is delivered.
- **Nothing stays attached after the end** (`213762c`):
  - `onEnding` delivers the end, then `steps.onComplete()`: every subscriber is released without disposing. The steps stay readable (`decision()`, and a late subscriber gets them all, then the completion).
  - `AbstractEmvBehavior.run` ends with `forgetTransaction()` in a `finally`: the EMV dispatcher is disposed and `transaction`, `activeConfig` (it carries field 55), `activeCard`, `cardSearch`, `bankAnswer` are cleared. Vendors read these fields inside `run` only.
  - `emvSteps` never completes: it belongs to the terminal, and completing it would swallow the next sale's `goToStep`. The screen listens to it per sale, until the sale ends (`MainViewModel.emvStepsOfTrx`, `takeUntil`).
- **Cancel:** `cancel()` publishes `CANCELLING`. The EMV stops at its next `goToStep`. The thread is never interrupted.
- **A vendor only reports:** `AbstractEmvBehavior.publish` drops any decision or end step.
- **Retry:** the EMV asks for it (`requestRetry`); `run` then starts a new attempt from `TERMINAL_INITIALIZATION`, in the same sale.
- ⚠ The cases run while the lock is held, including the bank calls of the closings. So `publish` checks the decision before taking the lock, and `cancel()` never waits for a closing. Steps that may wait for the bank or a person stay reports, with no case.

## 4. EMV steps

**Rules.** Nobody calls an `onXxx` hook directly; `goToStep` does. `goToStep` drops the step and returns `false` when the sale has ended or was cancelled (with a "step dropped" trace line). The dispatch is synchronous on the transaction thread (no `observeOn`, no serialize). A callback that waits for a person calls `ask(step, detail, failClosed)`: the step is shown, then its hook asks the person and **returns** the result, which is the kernel's answer (`failClosed` when the sale ended before or meanwhile).

| # | EmvStep | Contact (PAX) | Contactless granular | Contactless atomic | Swipe / Manual |
|---|---|---|---|---|---|
| 0 | TERMINAL_INITIALIZATION | New `PaxKernelSession` + `prepareKernel`, then `searchCard()` | = | = | = |
| 1 | SEARCH_CARD | `PaxCardSearch.search` | = | = | = |
| 2 | APPLICATION_SELECTION | `runContactAppSelect` (`EMVAppSelect`) | `runContactlessAppSelect` | = | — |
| 3 / 4 | WAIT / FINAL_APPLICATION_SELECTION | Asked from inside 2: `emvWaitAppSel` → `onWaitAppSelect` → `ask`; the hook returns `chooseApplication(tx.candidates)` | = | = | — |
| 5 | READ_APPLICATION_DATA | `runContactReadAppData` (`EMVReadAppData`) | `runContactlessReadAppData` | From `onReadCardOk`, after 6 (no work) | `CARD_READ` → 13 |
| 6 | SET_TRANSACTION_DATA | `runContactSetTransactionData` (9C) | `runContactlessSetTransactionData` | From `onReadCardOk`, before 5 (no work) | — |
| 7 | OFFLINE_DATA_AUTHENTICATION | `runContactCardAuth` (`EMVCardAuth`) | `runContactlessCardAuth` | — | — |
| 8 | PROCESS_RESTRICTIONS | `runContactStartTransaction` (`EMVStartTrans` = PR + CVM + TRM + TAA + 1st GAC); then 13 if the card goes online, else the stage ends | `runContactlessProcessRestrictions` → `runContactlessStartTransaction` (the same) | The one call does it all, then the same | — |
| 9 | CARDHOLDER_VERIFICATION | Online PIN, asked from inside 8 (the PIN circle below); the hook returns `enterPin(tx.pinRequest)` | Online PIN, called after the kernel returns | = | — |
| 10 | OFFLINE_PIN_VERIFICATION | Offline PIN, asked from inside 8; the hook returns `enterPin(tx.pinRequest)` | — | — | — |
| 11 / 12 | TERMINAL_RISK_MANAGEMENT / ACTION_ANALYSIS | Not published (inside `EMVStartTrans`) | = | = | — |
| 13 | ONLINE_AUTHORIZATION | `askTheBank()` (first line of the hook, every card), then `runContactOnline`: always 14 (the 2nd GAC) | `runContactlessOnline`: 14 if the bank approved, else the end; `pinRequired` → `runSingleTap` → 13 again (§5) | = | `askTheBank()`, then `finishNonEmv`: the result is `bankAnswer()` |
| 14 | ISSUER_AUTHENTICATION | `runContactIssuerAuthentication`: `EMVCompleteTrans` (ARPC + scripts + 2nd GAC), then the stage ends | `runContactlessIssuerAuthentication`: Visa: the second tap + `Clss_IssuerAuth_Wave`, then the end | = | — |
| 15 | SCRIPT_PROCESSING | Not published (the scripts run inside 14) | Visa second tap only: `Clss_IssScriptProc_Wave` | — | — |

- **Each step does its own work, then hands over** (`advanceContactStage` / `advanceContactlessStage`). The end of the stage (`finishContactStage` / `finishContactlessStage`: close the reader, read the result) runs in the last step that works, or where the path stops. If the sale has already ended, the result is not read twice.
- **A step is published only where the kernel does its work** (rules 1 and 11). Two labels were removed: `SCRIPT_PROCESSING` at every approval (`cf04104`, it ran no script) and `CARDHOLDER_VERIFICATION` before an offline PIN (`93a798a`, its hook did nothing).
- **No `TRANSACTION_COMPLETION`:** no vendor did kernel work there, so it is gone (16 hooks). The outcome is reported once: `completeApproved` / `completeDeclined` → `finishApproved` / `finishDeclined`, or `finishError`.
- **The kernel's order wins, not the numbers** (PayWave: PR, then ODA; atomic kernels announce 5 and 6 when the kernel says they happened).
- Every hook is `protected abstract`: a vendor that forgets one does not compile. `onTerminalInitialization` must end with `searchCard()`.

### The PIN circle (`ask`)

During a native call the kernel can stop and ask a question through a callback. The callback asks the step that owns the question with `ask`; that step's hook runs **inside** the step that called the kernel and returns the answer; the kernel carries on from where it stopped:

```
① step 8 (PROCESS_RESTRICTIONS) calls the kernel: EMVStartTrans — the thread waits inside it
② the CVM needs a PIN → EMVCallback.cEMVGetHolderPwd (JNI) → ContactProcess.emvGetHolderPwd(tryFlag, remainCnt, pinData)
③ ContactProcess guards: online and offline mixed → EMV_DATA_ERR; pinData[0] ≠ 0 → the kernel's status (1 = timeout)
④ → PaxEmvBehavior.onCardHolderPwd: tx.pinRequest = …, then ask(9 online | 10 offline)
⑤ → ask: goToStep shows the step (false → USER_CANCEL, no PIN pad), then runs its hook:
     onCardholderVerification / onOfflinePinVerification → enterPin(tx.pinRequest) → PIN pad → returns the result
⑥ ← ask returns it (USER_CANCEL if the sale was cancelled while the pad was open)
⑦ ← setCallBackResult(result): result = …, cv.open() → cEMVGetHolderPwd's cv.block() returns → the kernel carries on with the CVM
⑧ offline PIN wrong → emvVerifyPINfailed → EMV_OK → the kernel asks again: back to ② (one try less)
⑨ EMVStartTrans returns (TRM → TAA → 1st GAC) → step 8 hands over to 13 (ARQC) or ends
```

- `ask` is three checks: the step shown? → the hook's result → the sale still running? The hooks of steps 3 / 4 / 9 / 10 return `int` and are run by `ask` only (the dispatch switch skips them, so a hook never runs twice). Tests: `AskStepTest` (cancel before → no question; cancel while open → `failClosed`; the step shown before the hook).
- A hook only gets `(config, card)`, so the callback leaves the question's data in the session first (`tx.pinRequest`, `tx.candidates`).
- A hook that throws goes out through `ask`: `ContactProcess` catches it and answers `USER_CANCEL`.
- Application selection is the same circle: `EMVAppSelect` → `emvWaitAppSel` → `onWaitAppSelect` → `ask(3 / 4)` → the hook returns the dialog's index, or the only application.
- Contactless has no PIN callback: the kernel returns first, then our code calls `onCardHolderPwd(true, …)` itself (CVM = online PIN, and Single Tap).

**How the kernel waits** (`EMVCallback` in PAX's `EMV_v105.jar`, read with `javap`): the native kernel does not call our listener directly; it calls a Java method of `EMVCallback`, and that method waits for `setCallBackResult`:

```java
// EMVCallback (PAX, decompiled)
public int cEMVGetHolderPwd(int tryFlag, int remainCnt, byte[] pinData) {
    result = -14;                                   // default, before any answer
    if (listener != null) {
        cv = new ConditionVariable();
        listener.emvGetHolderPwd(tryFlag, remainCnt, pinData);  // ContactProcess
        cv.block();                                 // waits for setCallBackResult
    }
    return result;                                  // back to native EMVStartTrans
}
public void setCallBackResult(int value) { result = value; cv.open(); }
```

- What waits is `cv.block()` inside `EMVCallback`. We call `setCallBackResult` on the same thread before the listener returns, so `block()` returns at once.
- Without `setCallBackResult`, `cv.block()` waits forever: the terminal hangs. So every `ContactProcess` callback calls it on every path.
- A listener that throws never reaches `cv.block()`; the exception goes back into the native call. So `ContactProcess` catches `Throwable` and answers `EMV_USER_CANCEL`.
- The `ConditionVariable` lets an answer come from another thread (PAX's design); we answer on the same thread.
- The default `-14` (no listener) is not named in the jar; its meaning is not assumed.

**The kernel's callbacks** (`ContactProcess.EmvCallBackListener`, new at each attempt in `preTransProcess`, so its counters start fresh):

| Callback | The kernel calls it | What we do |
|---|---|---|
| `emvWaitAppSel` | In `EMVAppSelect`, to choose / confirm the application | Asks a person: `onWaitAppSelect` → `ask(3 / 4)` |
| `emvGetHolderPwd` | In `EMVStartTrans`, when the CVM needs a PIN | Asks a person: the circle above |
| `emvVerifyPINfailed` | The offline PIN was wrong | `EMV_OK`; the kernel asks again or goes on |
| `emvVerifyPINOK` | The offline PIN was right | Log only |
| `emvSetParam` | After application selection, before GPO | Data: `resetParam(AID)` (9F33 / 9F40 / 9F35, PIN bypass) |
| `emvInputAmount` | The kernel needs the amount | Data: amount and other amount |
| `emvUnknowTLVData` | The kernel needs a tag it does not have | Data: 9A, 9F1E, 9F21, 9F37, FF01; any other → −1 |
| `certVerify` | PBOC cardholder ID check | `EMV_DATA_ERR` (not supported, fail closed) |
| `emvAdviceProc` | Advice | Log only |
| `cRFU2` | Reserved | Returns 0 |

## 5. The way to the bank

```java
// AbstractEmvBehavior — the EMV asks the bank
protected AuthResult askTheBank()          // field 55 + card data → bankRequest.send(config); keeps the answer and returns it
protected final AuthResult bankAnswer()    // reads it, never asks again
// PosTerminal — attach(this::sendToBank), once, in its constructor
public AuthResult sendToBank(config)       // names the request, records it, ONLINE_*, communication.connectToHost(sent)
// SaleCommunicationBehavior — connectToHost: token → exchange → sale / refund (plain steps)
```

- **Why `attach`:** `PosTerminal` needs the behavior in its constructor, and the behavior needs `sendToBank`, which exists only after. So it is wired once, right after (setter injection).
- **`askTheBank` always keeps its answer**, even when it returns early (no field 55 → `failed`; cancelled → declined "17"), so the vendor reads the real reason.
- **`startOnlineProcess()` and `confirmCard()`** are only in PAX's interfaces: neither the kernel nor our code calls them. Going online is the `ONLINE_AUTHORIZATION` step. The card-read work (9F1C, `CARD_READ`) is in `onReadCardOk`, which the kernel calls after the read and before it decides.
- **Only an ARQC goes to the bank** (a refund like a sale). The exception: a TC (`RESULT_OFFLINE_APPROVED`) on this online-only terminal is sent without an ARQC, and the bank's answer is the result. An AAC is a decline.

**The safe end** (`case APPROVED` / `case DECLINED` for the last request, `ENDING` for the rest):

| Answer | Sent |
|---|---|
| Approved, the decision is APPROVED, the last request | Acknowledgement (`orchestration/acknowledgement`) |
| Approved with any other end, or an earlier request | Reversal (`orchestration/reverse`) |
| `failed` (sent, no usable answer) | Reversal |
| `notSent`, declined or `pinRequired` | Nothing |

**`notSent`** is decided by the network only (`answerOf`): any failure before sending, `Token refresh: …`, `Exchange: …`, and `Sale:` with no connection or HTTP 401 / 403. Anything else after the sale was sent is `failed`.

| Timeout | Value |
|---|---|
| Each payment call: `callTimeout` (`HostApiClient.createForPayments`), synchronous adapter | 25 s |
| `PosTerminal.sendToBank` (safety net) | 90 s |
| `closeAtHost` (safety net) | 60 s |

- ⚠ **The 90 s and 60 s nets do not cut the wait:** `connectToHost` / `acknowledge` / `reverse` are `fromCallable` with no `subscribeOn`, so they run on the transaction thread and `timeout` returns only when the call does (a 500 ms timeout waited 3124 ms). The real bound is `callTimeout`. An answer after the timeout is thrown away as `failed` → reversal (safe for the money). The fix (`subscribeOn(io)`) needs a decision.
- ⚠ An acknowledgement / reversal is sent once, with no stored journal (0.15). ⚠ Not tried with the bank; that they need the `mToken` is an assumption.

**The online PIN key (RSA):** an online PIN goes to the bank as a PIN block made inside the PED under a fresh AES key; the bank gets that key wrapped with its own RSA public key.

```
① ONLINE_AUTHORIZATION → askTheBank → addOnlineRequestData → PaxPinEntry.ensureOnlinePinKey(tx)  (one key per attempt)
② provisionOnlinePinKey: pinKey = 16 random bytes (SecureRandom, AES-128)
③ PED: erase the old AES_TPK / TPK at INDEX_AES_PEK; writeAesKey(TLK index 0 → AES_TPK at INDEX_AES_PEK, pinKey)
④ RSA/ECB/PKCS1Padding(host public key, pinKey) → hex → tx.lastOnlinePinKeyEncrypted
⑤ step 9: PinService.getEncryptedPinData(PAN) → the PED builds the PIN block under AES_TPK (ISO 9564 format 4) → tx.lastOnlinePinBlock
⑥ request: withOnlinePinKeyEncrypted · withOnlinePinBlock
     exchange → GeneralRequest.forExchange(…, pinKeyToSend)   the wrapped key, with every exchange
     sale     → GeneralRequest.forSale(…, pinBlockToSend)     the PIN block, only when there is one
⑦ the bank: RSA private key → pinKey → decrypts the PIN block
```

- The key goes with every exchange, PIN or not: a Single Tap PIN comes later, in a resend with no exchange of its own.
- The clear key never leaves the terminal unwrapped; the PIN block is built by the PED, not by our code.
- ⚠ The TLK must be injected at index 0, or the key write fails and every online PIN fails (`Key does not exist`); TLK injection is not wired yet.
- ⚠ No host public key (onboarding not completed): the key is written but not sent; the bank cannot read the PIN.
- Not device-tested on this branch (form rows 4, 5, 7, 9).

**Mastercard Single Tap** (`35ce685`, contactless only): a Mastercard sale under the CVM limit goes without a PIN, and the bank may answer RC 90. No second tap:

```
ONLINE_AUTHORIZATION → askTheBank() (no PIN) → network: RC 90 + contactless + Mastercard AID + sale + no PIN block
  → AuthResult.pinRequired (the network keeps id, TEK, headers for one resend)
runContactlessOnline → runSingleTap → SINGLE_TAP_PIN_REQUIRED (report)
  → onCardHolderPwd(online, no bypass) → CARDHOLDER_VERIFICATION → PIN pad
  → ONLINE_AUTHORIZATION again → askTheBank (+ PIN block)
  → PosTerminal.sendToBank: the same OnlineRequest + asyncRequestId (pinRequired()), its answer replaced
  → network: same id + TEK, no token / exchange, singleTap "ST"
  → the answer as any other: approved → ISSUER_AUTHENTICATION → APPROVED → ACK; declined → DECLINED
```

- `pinRequired` is neither approved nor failed: never reversed. The resend is the same request, so it is closed once.
- **No loop, three guards:** network: RC 90 with a PIN is a plain decline. Core: one request, one closing. PAX: the PIN once per attempt (`tx.singleTapPinEntered`); a second request → DECLINED.
- The PIN key goes with every exchange (`pinKeyToSend`), one key per attempt (`ensureOnlinePinKey`).
- ⚠ Open (4.11): RC 90 as HTTP 4xx becomes `failed` + a reversal and Single Tap never starts; the resend's posEntryMode is 071 (an assumption). ⚠ Not tried on a device or with the bank.

## 6. EMV params

- **The database (GreenDAO) is the source:** filled from JSON once (`ConfigInit`), then from the host file on Download (`EmvParamUpdater.applyFromZip`).
- **The cache** (`EmvParamService`) is a copy in memory: any write empties it (`invalidate()`), the first sale fills it (`getCachedEmvParam` → `loadFromDatabase`, 13 params).
- **The kernel keeps nothing:** the params are loaded into it at every attempt (`preTransProcess`).
- Contact: what the host file does not bring keeps its value. Contactless: the host file is the whole truth; a kernel it does not bring is deleted.
- TID / MID / MCC / merchant name do not come from the host file (`OTHERCONFIGURATION` is skipped on purpose).
- ⚠ CAPKs are not checked (expiry, checksum).
- A schema change: the DAO is edited by hand + uninstall on the device.

## 7. Threads

| Thread | Runs | Never |
|---|---|---|
| main | The screen, `acceptCard` (the busy check), `cancel()`, the dialogs (app choice, PIN), the banner (`emvSteps` with `observeOn(main)`, per sale until its end) | Any blocking work |
| transaction (`transactionScheduler`) | The whole sale: init, search, EMV, the bank (`blockingGet`), the closings | Interrupted; more than one sale |
| print | `printReceipt`, in order | — |
| `Schedulers.io()` | Params download, onboarding | — |

The two executors are not shut down in `dispose()`; fine today, as there is one `PosTerminal` for the app's whole life.

The kernel and every callback run on the transaction thread only. It waits for main during the app-choice and PIN dialogs, so a dialog must close at every end. ⚠ A cancel does not interrupt the online PIN entry (1.2).

## 8. When you change something

1. **The kernel's order first**, not the enum's. A step shows where the kernel really works.
2. **The work goes in `onXxx`**; a callback that needs a person calls `ask`, and the asked hook returns the answer.
3. **Fail closed:** no answer = `USER_CANCEL` / online failed / decline; never an offline approval.
4. **Contact ≠ contactless:** one runner each.
5. **`goToStep`, never a direct call**, and no `observeOn` inside the behavior.
6. **One owner per piece of state** (§2); no second flag.
7. **A new sale step with work:** a case in `dispatchTransactionStep`. If it may wait for the bank or a person before the end is decided, make it a report.
8. **The PAX SDK** (`emvbase`, `emvlib*`, `poslib` …) is touched only for a clear EMV reason.
9. **Tests:** this branch carries code only. The tests live outside it (`transactionStepObserver-tests.zip`, sent with these docs) and run with javac + JUnit; then the device with the `report_easylink.xlsx` rows.

## 9. The difference with paxEmvBehaviorSplit

The older branch (`paxEmvBehaviorSplit` / `-tests`) works the same way, with:

| Here (`transactionStepObserver`) | There |
|---|---|
| `askTheBank()` / `bankAnswer()` | `authorizeOnline()` / `tx.lastAuth` in each vendor |
| The bank and the issuer are two steps after `PROCESS_RESTRICTIONS`; the card-read work in `onReadCardOk` | Both inside the `PROCESS_RESTRICTIONS` hook; `confirmCard` after the 1st GAC |
| `BankRequest.send` (its own file), `PosTerminal.sendToBank` | `AbstractEmvBehavior.Authorizer.authorize`, `PosTerminal.authorize` |
| `CommunicationBehavior.connectToHost` | `CommunicationBehavior.authorize` |
| `ONLINE_AUTHORIZATION` / `onOnlineAuthorization` | `START_ONLINE_PROCESS` / `onStartOnlineProcess` |
| `TransactionStepObserver` as the one way in, `ENDING` with a closings loop, the state from the steps (`decision()`, `FAILED`), no `TRANSACTION_COMPLETION` | The switch called from several places, `onEnd` with a loop, `CLOSED`, an `end` field |
| `TERMINAL_INITIALIZATION` starts the search | `run` starts the search with an `if` |
| `field55()` abstract, `Field55.TagReader` | `readIccTag` abstract, `IntFunction` |
| `CardPresence.isEmv()` / `isNonEmv()` | `AbstractEmvBehavior.isNonEmv(card)` |
| The issuer step builds the PAX answer from `bankAnswer()`; no second announcement | `tx.issuerResponse`; a second announcement in `onlineDenied` / `onlineCardDenied` |
| The network as plain steps, `callTimeout` 25 s, synchronous adapter, `closeAtHost` 60 s | An Rx chain (`flatMap`), no `callTimeout`, `closeAtHost` 15 s |
