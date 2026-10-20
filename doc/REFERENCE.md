# EasyLink / EmvEnhance — مرجع المشروع

> مكتوب من الكود على `rxSetTransactionData` (`2a362cf`). كل اسم method هنا موجود فعلاً.
> لو الكود اتغير والمرجع لأ — الكود هو الصح، وحدّث المرجع.
> ملخص المشروع وقواعد الشغل في `CLAUDE.md`؛ هنا التفاصيل: الـ flows والـ steps.

## الفهرس

1. [الـ Architecture](#1-الـ-architecture)
2. [قلب الحكاية: EmvEngine و EmvStep و onXxx](#2-قلب-الحكاية-emvengine-و-emvstep-و-onxxx)
3. [الـ Transaction flow: من الزرار لحد callback الـ kernel](#3-الـ-transaction-flow-من-الزرار-لحد-callback-الـ-kernel)
4. [Contact flow](#4-contact-flow)
5. [Contactless flow](#5-contactless-flow)
6. [EMV param apply flow](#6-emv-param-apply-flow)
7. [الـ Threads](#7-الـ-threads)
8. [لما تيجي تعدّل](#8-لما-تيجي-تعدل)

---

## 1. الـ Architecture

### 1.1 الطبقات

```text
┌────────────────────────── :app (main) — vendor-agnostic UI ──────────────────────────┐
│ AmountFragment → SearchCardFragment ⇄ MainViewModel (LiveData)                         │
└──────────────────────────────────────────┬────────────────────────────────────────────┘
                                           │ PosTerminal فقط — الـ UI عمرها ما تشوف Pax*
┌────────────────────────── :core — مفيش فيه أي حاجة vendor ────────────────────────────┐
│ PosTerminal (abstract)  ── يملك: card search, CommunicationBehavior, PrinterBehavior    │
│     └── EmvEngine       ── يملك: EmvBehavior, running flag, 2 observables, ApduTrace    │
│           └── EmvBehavior ◄── AbstractEmvBehavior (goToStep / askStep / dispatch)       │
└──────────────────────────────────────────┬────────────────────────────────────────────┘
                                           │ product flavor: pax | ingenico | fake
┌────────────────────────── :app/src/pax — الـ vendor ───────────────────────────────────┐
│ TerminalFactory → PaxTerminal extends PosTerminal   (ICardReaderHelper.polling)         │
│                   PaxEmvBehavior extends AbstractEmvBehavior                            │
│                     implements IContactCallback, IContactlessCallback                   │
│                   PaxKernel { params, contact, contactless, mag }                       │
└──────────────────────────────────────────┬────────────────────────────────────────────┘
┌───────────── PAX SDK modules (متتلمسش إلا لسبب EMV واضح) ────────────────────────────┐
│ :emvlib:dpas   ContactProcess  ──► EMVCallback.EMV*  (native contact kernel)            │
│ :emvlib        ClssProcess ──► ClssKernelProcessFactory ──► ClssPayPassProcess,         │
│                ClssPayWaveProcess, ClssEFTProcess, ClssAEProcess, ClssJcb/Mir/Pboc/     │
│                Pure/RuPay/Dpas  ──► Clss*Api (native contactless kernels)               │
│ :emvlib:base   ClssKernelProcess (الـ framework), BaseContactProcess                    │
│ :emvbase       EmvProcessParam, EmvTransParam, TransResult, IContactCallback, ...       │
│ :emvflow       EmvFlowRuntime (DAL bootstrap), EmvDeviceImpl (IDevice: APDU, PIN, trace)│
│ :emvservice    PinService, MagCardService                                              │
│ :bizentity     GreenDAO tables + EmvParamService + ConfigInit + XML parsers + Updater  │
│ :bizlib :poslib :commonutils   TrackUtils, PedHelper, ModelInfo, ConvertUtils, LogUtils │
└────────────────────────────────────────────────────────────────────────────────────────┘
┌───────────── :network (vendor-agnostic) ───────────────────────────────────────────────┐
│ SaleCommunicationBehavior (exchange → sale/refund, AES envelope)  ← الـ default في PAX  │
│ RetrofitCommunicationBehavior (crypto/purchase) — بديل مش مستخدم                       │
│ OnboardingClient/State, EmvParamDownloadClient, crypto, tlv                            │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 مين بيملك مين

| الكائن | بيتعمل فين | بيملك | بيكلم مين |
|---|---|---|---|
| `EmvEnhanceApp` | Application.onCreate | `PosTerminal` واحد طول عمر الـ process | `TerminalFactory.create` |
| `PaxTerminal` | `TerminalFactory` (بعد `EmvFlowRuntime.init`) | `PaxKernel`، الـ reader، الـ host، الـ printer | `EmvEngine` بس |
| `EmvEngine` | constructor بتاع `PosTerminal` | `PaxEmvBehavior`، `running`، `transactionSteps`، `emvSteps`، `ApduTrace`، retry | يرجع للـ terminal بـ `Authorizer` (= `PosTerminal::authorize`) |
| `PaxEmvBehavior` | constructor بتاع `PaxTerminal` | `TransactionState tx` (جديد كل attempt) | `PaxKernel` + engine |
| `PaxKernel` | `PaxTerminal` | `EmvParamService params` (الـ cache)، `ContactProcess contact` (جديد كل attempt)، `ClssProcess contactless` (singleton)، `MagCardService mag` | الـ native |

### 1.3 قاعدتين معمارية مش بيتكسروا

1. **الـ UI والـ core مش بيعرفوا PAX.** الـ vendor بيتختار بـ flavor (`pax`/`ingenico`/`fake`)، و`TerminalFactory` هو المكان الوحيد اللي فيه اسم `PaxTerminal`.
2. **الـ step methods (`onXxx`) protected ومحدش بيناديها مباشرة.** الطريق الوحيد ليها: `goToStep` → observable → `dispatchStepMethod`. ده اللي بيخلي كل خطوة تتشاف في الـ UI والـ APDU trace.

---

## 2. قلب الحكاية: EmvEngine و EmvStep و onXxx

### 2.1 نوعين من الـ events

| | `TransactionStep` | `EmvStep` |
|---|---|---|
| لمين | الـ business / الـ UI النهائي | مهندس EMV / الـ banner / الـ trace |
| الـ subject | `BehaviorSubject` + `toSerialized()` | `PublishSubject` **مش serialized** |
| بيعمل replay؟ | آه، آخر قيمة (عشان كده `SearchCardFragment` بيتجاهل لحد `TRANSACTION_STARTED`) | لأ |
| بيشغّل كود؟ | engine بس: `isEnd()` → `running=false` | **آه** — كل step بيشغّل الـ `onXxx` بتاعه |
| بيتنشر إمتى | أي وقت (حتى من الـ UI thread وقت الـ cancel) | بس وهو `running` — بعد النهاية بيتساب (dropped + note في الـ trace) |

`TransactionStep` بالترتيب: `IDLE → TRANSACTION_STARTED → WAITING_FOR_CARD → CARD_DETECTED → APPLICATION_SELECTED → CARD_READ → CARDHOLDER_VERIFIED → ONLINE_REQUIRED → ONLINE_PROCESSING → ONLINE_COMPLETED → APPROVED|DECLINED → COMPLETED` أو `ERROR` في أي لحظة.
**النهاية = `COMPLETED` أو `ERROR` بس** (`TransactionStep.isEnd()`). `APPROVED`/`DECLINED` دايماً بعدها `COMPLETED`؛ `ERROR` لوحده.

### 2.2 `goToStep` بيعمل إيه بالظبط

```text
vendor code: goToStep(EmvStep.X, detail)
  └─ AbstractEmvBehavior.goToStep
       ├─ cancelled?  → notifyError("Transaction cancelled") لو لسه running، وخلاص
       └─ enterStep → engine.notifyEmvStep(X, detail)
            ├─ !running → dropped (note في الـ trace)  ← ده اللي بيمنع "نهاية مرتين"
            ├─ apduTrace.kernelStep(...)
            └─ emvSteps.onNext(event)                   ← synchronous، نفس الـ thread
                 ├─ MainViewModel (observeOn main → banner)  — بيتعمله post بس
                 └─ EmvStepObserver (آخر subscriber)
                      └─ dispatchStepMethod(X) → onX(engine, config, card)
                           └─ ممكن جوّاه goToStep(Y) → re-entrant، Y يخلص قبل ما X يرجع
  ← goToStep بيرجع بعد ما onX (وكل اللي نادته) خلصت
```

ليه كده:
- **synchronous ومفيش `observeOn`/`subscribeOn`**: الـ kernel بينادي callback ومستني return value؛ لازم الـ step يكون خلص قبل ما الـ callback يرجع. والـ step بيعمل APDU/PIN/host، يعني blocking — مينفعش على computation thread.
- **`EmvStepObserver` بيتعمل subscribe من جديد في كل `prepare`/`start`** عشان يبقى آخر subscriber. لو مكانش آخر، الـ subscribers اللي بعده كانوا هيشوفوا الـ step الجاي قبل الحالي (لأن الجاي بيتنشر من جوه الحالي).
- **بيتعمل dispose أول ما الـ transaction تنتهي** (`isEnd`)، فـ step من كود لسه بيـ unwind عمره ما يشغّل method.

### 2.3 `askStep` / `answerStep` — لما الـ kernel مستني إجابة

الـ kernel callback (اختيار application، PIN) لازم يرجّع `int`. الشغل نفسه (dialog، PIN pad) جوه الـ `onXxx`:

```java
// callback (مثال: onWaitAppSelect)
return askStep(EmvStep.WAIT_APPLICATION_SELECTION, detail, USER_CANCEL /* failClosed */);

// step method
protected void onWaitApplicationSelection(...) { answerStep(dialog.selectBlocking()); }
```

- `askStep` بيعمل stack من `StepAnswer` (يدعم nesting)، يعمل `goToStep`، ويرجع اللي اتعمله `answerStep`.
- لو محدش جاوب (cancel، الـ transaction خلصت، exception، أو نسيت `answerStep`) → بيرجع **`failClosed`** ويكتب `no answer for …` في الـ trace. **الـ kernel عمره ما يستنى للأبد.**
- `answerStep` من غير `askStep` = لا حاجة (plain `goToStep` محدش مستنيه).

### 2.4 إزاي الـ transaction بتخلص

| من فين | بيعمل إيه |
|---|---|
| `finishApproved/Declined(msg)` (mag/manual) | `notifyApproved/Declined` + `notifyCompleted` |
| `finishWithOutcome(step, detail, msg)` (chip/clss) | يحط `tx.outcome` → `goToStep(TRANSACTION_COMPLETION)` → `onTransactionCompletion` → `reportOutcome` |
| `finishError(msg)` | `notifyError` → `ERROR` |
| exception جوه أي `onXxx` | `EmvStepObserver` يمسكه → `finishError` |
| step مش معروف / `null` | `unknownStep` → `finishError` (مرة واحدة بس) |
| `engine.cancel()` | `behavior.cancel()` + `notifyError("Transaction cancelled")` لو running |

أول `ERROR`/`COMPLETED` → `running=false` → أي step بعدها dropped. ده الحل بتاع bug "ERROR وبعدين DECLINED + COMPLETED".

### 2.5 الـ retry (fallback / try again / see phone / try another interface)

مفيش `startTransaction` جديد. الـ behavior بيقول `engine.requestRetry(config.withMode(X), prompt)`، وبعد ما `engine.start(...)` يرجع، الـ listener في `PosTerminal` يعمل `consumePendingRetry()` → `triggerSearch` تاني **على نفس الـ thread ونفس `begin()`**. الـ trace بيكمل بـ `RETRY #n`، والـ prompt بيظهر في `WAITING_FOR_CARD` كـ `KEY_RETRY_PROMPT`.

| الـ outcome | الـ mode الجديد | الـ prompt |
|---|---|---|
| `fallback()` (contact) | `MAGSTRIPE` | Chip not readable — swipe the card |
| `tryAnotherInterface()` | `CHIP` | Insert the card |
| `tryAgain()` | نفس الـ mode | Card not read — tap again… |
| `seePhone()` | `CONTACTLESS` | See phone — verify on your phone, then tap again |

`seePhone` و `markTryAgainOnCommunicationError` بيشاركوا `MAX_TAP_RETRIES = 3` — بعدهم decline.

### 2.6 جدول الـ 17 EmvStep في PAX

| # | EmvStep | مين بينشره | Contact (`onXxx` بيعمل) | Contactless granular (EFT/PayPass/PayWave) | Contactless atomic (Amex/JCB/Mir/PBOC/Pure/RuPay/DPAS) | Mag / Manual |
|---|---|---|---|---|---|---|
| 0 | TERMINAL_INITIALIZATION | `prepare()` | `prepareKernel` (DAL، تحميل الـ params لو محتاج، `preTransProcess`) | = | = | = (بيرجع true بدري) |
| 1 | SEARCH_CARD | `onSearchStarted` / `onSearchRetry` / `onDetect2ndTap` | فاضي (الـ search الحقيقي في `PosTerminal`) | = | = | = |
| 2 | APPLICATION_SELECTION | `start()` | `runContactAppSelect` | `runContactlessAppSelect` | = | مش بيتوصل |
| 3 | WAIT_APPLICATION_SELECTION | `onWaitAppSelect(first)` → askStep | `answerApplicationSelection` | = (نادر) | = | — |
| 4 | FINAL_APPLICATION_SELECTION | `onWaitAppSelect(retry)` → askStep | `answerApplicationSelection` | = | = | — |
| 5 | READ_APPLICATION_DATA | contact: بعد 2؛ granular: بعد 6؛ atomic: `onReadCardOk`؛ mag: `start()` | `runContactReadAppData` | `runContactlessReadAppData` | فاضي (اتعمل جوه الـ bundle) | `CARD_READ` → `goToStep(START_ONLINE_PROCESS)` |
| 6 | SET_TRANSACTION_DATA | contact: بعد 5؛ granular: بعد 2؛ atomic: `confirmCard` | `runContactSetTransactionData` | `runContactlessSetTransactionData` | فاضي | — |
| 7 | OFFLINE_DATA_AUTHENTICATION | بعد 6 (contact)، بعد 5 (granular)، بعد 8 (PayWave) | `runContactCardAuth` | `runContactlessCardAuth` | مش بيتنشر | — |
| 8 | PROCESS_RESTRICTIONS | بعد 7؛ PayWave بعد 5؛ atomic بعد 2 مباشرة | `runContactStartTransaction` (EMVStartTrans bundle) | `runContactlessProcessRestrictions` | → `runContactlessStartTransaction` | — |
| 9 | CARDHOLDER_VERIFICATION | `onCardHolderPwd` (online: askStep؛ offline: goToStep) | online: `enterPin` + answer | online PIN بعد الـ bundle | = | — |
| 10 | OFFLINE_PIN_VERIFICATION | `onCardHolderPwd(offline)` → askStep | `enterPin` (pad بس — الـ kernel بيعمل verify) | مش مدعوم | — | — |
| 11 | TERMINAL_RISK_MANAGEMENT | **مش بيتنشر في PAX** | فاضي (جوه EMVStartTrans) | فاضي | فاضي | — |
| 12 | TERMINAL_ACTION_ANALYSIS | **مش بيتنشر في PAX** | فاضي | فاضي | فاضي | — |
| 13 | START_ONLINE_PROCESS | `startOnlineProcess()`؛ mag: من 5 | `authorizeOnline()` لو `tx.onlineRequested` | = | = | `authorizeOnline()` → 16 |
| 14 | ISSUER_AUTHENTICATION | `runIssuerAuthentication` / `announceIssuerAuthentication` / `onlineDenied` / `onlineCardDenied` | `EMVCompleteTrans` (2nd GAC) لو `tx.issuerResponse` | 2nd tap + `completeTransProcess` | = | — |
| 15 | SCRIPT_PROCESSING | `runIssuerScriptProcessing` (2nd tap)؛ `completeApproved` (إعلان بس) | فاضي (EMVCompleteTrans عملها) | `processIssuerScripts` (PayWave: `Clss_IssScriptProc_Wave`) | = | — |
| 16 | TRANSACTION_COMPLETION | `finishWithOutcome`؛ mag: من 13 | `reportOutcome(tx.outcome)` | = | = | `finishApproved/Declined` |

> "فاضي" معناها body فيه comment بيقول الـ kernel عمل الحاجة دي — مش stub. كل الـ `onXxx` abstract (ما عدا `onSearchCard`) عشان vendor ينسى phase ياخد compile error مش no-op ساكت.

### 2.7 مين بينشر الـ TransactionSteps

| TransactionStep | فين |
|---|---|
| TRANSACTION_STARTED | `AbstractEmvBehavior.prepare` |
| WAITING_FOR_CARD | `onSearchStarted` / `onSearchRetry` / `onCardRemoved` (PosTerminal)، `onDetect2ndTap` (PAX) |
| CARD_DETECTED | `EngineReportingListener.handleCardFound` |
| APPLICATION_SELECTED | `answerApplicationSelection`، `onReadCardOk` |
| CARD_READ | contact: `showConfirmCard`؛ clss: `confirmCard`؛ mag/manual: `onReadApplicationData` |
| CARDHOLDER_VERIFIED | `enterPin` (فيه `KEY_ONLINE_PIN`، `KEY_PIN_BYPASS`، `KEY_PIN_TRIES_LEFT`) |
| ONLINE_REQUIRED / PROCESSING / COMPLETED | `PosTerminal.authorize` — حوالين نداء الـ host |
| APPROVED / DECLINED + COMPLETED | `reportOutcome` (فيه PAN/TVR/TAC/IAC/field 55) أو `finishApproved/Declined` |
| ERROR | `notifyError` من أي مكان (cancel، timeout، reader، kernel، online failed) |

---

## 3. الـ Transaction flow: من الزرار لحد callback الـ kernel

### 3.1 الطريق كله، thread بـ thread

```text
[main]  AmountFragment: amount + type (+ reference لو REFUND) → SearchCardFragment.newInstance
[main]  SearchCardFragment.onViewCreated → viewModel.acceptCard(type, amountMinor, ref)
[main]  MainViewModel.acceptCard → PosTerminal.acceptCard
          → startTransaction(new TransactionConfig(type, amount, EntryMethod.ANY).withReferenceNumber)
          → current.compareAndSet(null, t)  ── false = busy → الـ UI تعرض showBusy
          → transactionScheduler.scheduleDirect(t::run)         ── يرجع فوراً
────────────────────────────────────────────────────────────────────────────────
[tx]    Transaction.run → engine.begin()  (running=true، retry=0، trace جديد)
[tx]    triggerSearch → engine.prepare(config) → PaxEmvBehavior.prepare
          tx = new TransactionState()
          → AbstractEmvBehavior.prepare: initEmvObserver، TRANSACTION_STARTED
          → enterStep(TERMINAL_INITIALIZATION) → onTerminalInitialization
               EmvDeviceImpl.setApduTrace، prepareKernel(config)   (تفاصيل §6.3)
          ← false → ERROR "Terminal initialization failed"
[tx]    PaxTerminal.searchCard(config, EngineReportingListener)
          readerType = mag/icc/picc حسب config و kernel.contactReady / contactlessReady
          listener.onSearchStarted → WAITING_FOR_CARD + EmvStep SEARCH_CARD
          pollUntilCardOrTimeout (30s، retry على PICC/ICC errors بـ onSearchRetry)
          handlePollingResult → onChipDetected / onContactlessDetected / onMagstripeDetected
[tx]    handleCardFound(card) → CARD_DETECTED → engine.start(config, card)
          → AbstractEmvBehavior.start → goToStep(firstStepAfterSearch(card))
               chip/clss → APPLICATION_SELECTION ؛ mag/manual → READ_APPLICATION_DATA
               … السلسلة كلها synchronous (§4، §5) لحد TRANSACTION_COMPLETION …
        ← engine.start يرجع
[tx]    consumePendingRetry() ≠ null → triggerSearch(retryConfig)  (نفس الـ thread)
[tx]    finally: current.compareAndSet(this, null)  ── الـ terminal فاضي
────────────────────────────────────────────────────────────────────────────────
[main]  MainViewModel: transactionSteps / emvSteps (observeOn main) → LiveData
[main]  SearchCardFragment: banner = EmvStep؛ outcome = APPROVED/DECLINED/ERROR؛
        result panel (PAN/TVR/TAC/IAC/ICC)؛ retry prompt؛ APDU trace (debug)
```

### 3.2 لما الـ kernel يكلمنا: callback → step → إجابة

مثال PIN أونلاين (contact):

```text
[tx] runContactStartTransaction → kernel.contact.startTransProcess() → EMVCallback.EMVStartTrans (native)
       native يحتاج PIN → EmvCallBackListener.emvGetHolderPwd (ContactProcess)
         → emvProcessListener.onCardHolderPwd(true, bypass, remain, null)   ← PaxEmvBehavior
              tx.pinRequest = new PinRequest(...)
              askStep(CARDHOLDER_VERIFICATION, "online PIN", USER_CANCEL)
                → goToStep → emvSteps → onCardholderVerification
                     → enterPin(request): CARDHOLDER_VERIFIED، provisionOnlinePinKey،
                       PaxPinPad + PinService.getEncryptedPinData (blocking)
                     → answerStep(CONTACT_OK | NO_PASSWORD | USER_CANCEL)
              ← يرجع الإجابة
         ← native يكمل
```

مثال الـ online (أي card):

```text
startXxxTransProcess شاف RESULT_REQ_ONLINE (أو refund / offline approved — §4.4)
  → startOnlineProcess()                    tx.onlineRequested = true
     → goToStep(START_ONLINE_PROCESS) → onStartOnlineProcess
          → tx.lastAuth = authorizeOnline()
               buildField55() عن طريق readIccTag (فاضي → online failed من غير ما يبعت)
               addOnlineRequestData: PAN، PIN block، المفتاح المشفّر، EmvTransactionResult
               → engine.authorize(config) → PosTerminal.authorize
                    ONLINE_REQUIRED، ONLINE_PROCESSING
                    communication.authorize(config).timeout(90s).blockingGet()
                    ONLINE_COMPLETED
  ← toOnlineResultWrapper(tx.lastAuth)      null/failed → FAILED (fail closed)
```

### 3.3 الـ cancel

```text
[main] زرار Cancel / onCleared → MainViewModel.cancel → PosTerminal.cancelTransaction
         transaction.cancelled = true → isSearchCancelled() true
         cancelCardSearch() → ICardReaderHelper.stopPolling()
         engine.cancel() → behavior.cancel() (cancelled flag) + ERROR "Transaction cancelled"
[tx]   السلسلة بتشوف isCancelled() عند أول stage/goToStep → finishXxxStage (يقفل الـ readers)
       الـ host call نفسه مبيتقطعش (ممكن يكون اتوافق عليه) — بيستنى الرد أو الـ timeout
       الـ terminal يفضل busy لحد ما Transaction.run يخلص
```

---

## 4. Contact flow

### 4.1 الـ stages (كل stage = native call واحد = EmvStep واحد)

| EmvStep | runner في `PaxEmvBehavior` | `ContactProcess` | native | callbacks جوّاه |
|---|---|---|---|---|
| APPLICATION_SELECTION | `runContactAppSelect` (+ `selectApplicationWithRetry`: 3 محاولات، power-cycle 200ms) | `selectApplication` | `EMVAppSelect` | `emvWaitAppSel` → `onWaitAppSelect` (WAIT/FINAL) |
| READ_APPLICATION_DATA | `runContactReadAppData` | `readApplicationData` | `EMVReadAppData` (GPO + read records) | `emvSetParam` → `resetParam(AID)` (9F33/9F40/9F35 بتاعة الـ AID المختار) |
| SET_TRANSACTION_DATA | `runContactSetTransactionData` | `setTransactionData` | — | `showConfirmCard` (CARD_READ) + `changeTAG9CValue` (9C) |
| OFFLINE_DATA_AUTHENTICATION | `runContactCardAuth` | `cardAuthentication` | `addCapk` + `EMVCardAuth` | — |
| PROCESS_RESTRICTIONS | `runContactStartTransaction` → `startContactTransProcess` | `startTransProcess` | `EMVStartTrans` = restrictions → CVM → TRM → TAA → **1st GAC** | `emvGetHolderPwd` → `onCardHolderPwd` |
| START_ONLINE_PROCESS | `startOnlineProcess()` | — | — | — |
| ISSUER_AUTHENTICATION | `runIssuerAuthentication` → `completeContactIssuerAuthentication` | `completeTransProcess` | `EMVCompleteTrans` = 89/91 + scripts + **2nd GAC** | — |
| SCRIPT_PROCESSING | إعلان بس من `completeApproved` | — | (اتعمل جوه EMVCompleteTrans) | — |
| TRANSACTION_COMPLETION | `finishContactStage` → `checkContactResult` → `finishWithOutcome` | — | — | — |

كل runner بنفس الشكل:

```text
cancelled? → finishError
tx.transResult = kernel.contact.xxx()
proceed = ret == EMV_OK && !isContactTransactionFinished()   (transResult.getTransResult() == null)
!proceed → unregister listener + finishContactStage (يقفل الـ readers + checkContactResult)
proceed  → advanceContactStage(NEXT)  (يعيد check على cancel قبل goToStep)
```

الـ ICC reader فاضل مفتوح من app select لحد start transaction، عشان كده `advanceContactStage` بيعيد check على `isCancelled()` بنفسه — `goToStep` لو لقى cancel بيرجع من غير ما يشغّل الـ stage الجاي، فالـ cleanup كان هيضيع.

### 4.2 الترتيب ده ليه

ده ترتيب الـ PAX contact kernel نفسه: `EMVAppSelect` → `EMVReadAppData` → (confirm card + 9C) → `EMVCardAuth` → `EMVStartTrans` → `EMVCompleteTrans`. الـ SET_TRANSACTION_DATA في الـ contact **بعد** الـ read (عكس الـ contactless) لأن الـ contact kernel بياخد المبلغ في `preTransProcess`/`EMVStartTrans`، والـ stage دي بتأكد الـ card وتظبط 9C بعد ما الـ PAN اتقرا.

### 4.3 الـ parameters جوه الـ contact kernel

- `preTransProcess` (كل transaction): `EMVCoreInit` → `setEmvAndMCKParam` بـ **أول AID** كـ default (+ `forceOnline = 1` دايماً) → `EMVSetPCIModeParam` → `EMVAddApp` لكل AID.
- بعد اختيار الـ application: `emvSetParam` → `resetParam(aid)`: يدوّر على الـ AID (full أو partial match) ويحط الـ 9F33/9F40/9F35 بتوعه بـ `EMVSetParameter` **و** `EMVSetTLVData` (لأن `EMVSetParameter` لوحده مكانش بيغيّر 9F33)، وكمان PIN bypass.

### 4.4 الـ online والـ 2nd GAC (`startContactTransProcess`)

| الـ card قال إيه في 1st GAC | بيحصل إيه |
|---|---|
| `ret != EMV_OK` | يرجع الكود → decline/error |
| refund (أي حاجة) أو TC "offline approved" | `needsHostWithoutArqc` → host من غير ARQC → `announceIssuerAuthentication` → **رد الـ host هو النتيجة** (مفيش 2nd GAC) |
| ARQC (`RESULT_REQ_ONLINE`) | `startOnlineProcess` → `runIssuerAuthentication` → **2nd GAC دايماً** (EMV Book 3، AET-146) حتى لو الـ host رفض أو فشل |
| AAC (مش refund) | decline |

بعد الـ 2nd GAC (`completeContactIssuerAuthentication`):

| الـ host | الـ card في 2nd GAC | النتيجة |
|---|---|---|
| APPROVE | TC | `RESULT_ONLINE_APPROVED` |
| APPROVE | غير كده | `RESULT_ONLINE_CARD_DENIED` |
| FAILED | أي حاجة | `RESULT_ONLINE_FAILED` / `..._CARD_APPROVED` → **online failed** (terminal أونلاين بس) |
| DENIAL | — | `RESULT_ONLINE_DENIED` |

### 4.5 النتيجة (`checkContactResult` → outcome)

| TransResultEnum | method | Outcome |
|---|---|---|
| ONLINE_APPROVED | `onlineApproved` → `completeApproved` (+ SCRIPT_PROCESSING إعلان) | APPROVED |
| ONLINE_DENIED | `onlineDenied` (ISSUER_AUTHENTICATION "denied by issuer") | DECLINED |
| ONLINE_CARD_DENIED | `onlineCardDenied` | DECLINED |
| ONLINE_FAILED / FAILED_CARD_APPROVED | `onlineFailed` | ERROR |
| OFFLINE_APPROVED | `offlineApproved` → **online failed** (مفيش offline approve) | ERROR |
| OFFLINE_DENIED / default / `null` | `offlineDenied` | DECLINED |
| SIMPLE_FLOW_END | `simpleFlowEnd` | APPROVED |
| FALLBACK | `fallback()` → retry MAGSTRIPE | — |

---

## 5. Contactless flow

### 5.1 قبل الـ tap: الـ entry point

`prepareKernel` → `runPreTransProcess` → `ClssProcess.preTransProcess`:
`Clss_CoreInit_Entry` → `Clss_DelAllAidList_Entry` / `DelAllPreProcInfo` → `ClssEntryAddAid.addApp()` (AIDs كل kernel عنده rows؛ kernel اتمسح = مفيش AIDs = مش بيتختار) → `Clss_SetMCVersion_Entry(3)` → `Clss_PreTransProc_Entry` (limits per AID).
لو فشل → `kernel.contactlessReady = false` → `PaxTerminal` مش هيسمع على الـ PICC خالص في الـ transaction دي.

### 5.2 Application selection (`runContactlessAppSelect`)

`ClssProcess.selectApplication`: `Clss_AppSlt_Entry` → loop على `Clss_FinalSelect_Entry` (`EMV_RSP_ERR`/`APP_BLOCK`/`ICC_BLOCK`/`CLSS_RESELECT_APP` → `Clss_DelCurCandApp_Entry` والـ candidate الجاي) → `Clss_GetPreProcInterFlg_Entry` + `Clss_GetFinalSelectData_Entry` → `ClssKernelProcessFactory(kernType).build()` بالـ params والـ listener.
بعدها: `firstStageAfterAppSelect()` → granular: `SET_TRANSACTION_DATA`؛ atomic: `PROCESS_RESTRICTIONS`.

### 5.3 Granular vs Atomic

`ClssKernelProcess.supportsGranularSteps()`:
- **granular (true): EFT، PayPass، PayWave** — الـ SDK بتاعهم فيه native call لكل phase، فكل phase step لوحده.
- **atomic (false): Amex، JCB، Mir، PBOC، Pure، RuPay، DPAS** — الـ transaction كلها call واحد (`startTransProcess`)؛ الـ steps بتتنشر من الـ callbacks (`onReadCardOk` → READ_APPLICATION_DATA، `confirmCard` → SET_TRANSACTION_DATA) والـ `onXxx` بتاعها فاضي.

### 5.4 ترتيب كل kernel (= ترتيب الـ kernel نفسه)

| EmvStep | PayPass (`ClssPayPassProcess`) | PayWave (`ClssPayWaveProcess`) | EFT (`ClssEFTProcess`) |
|---|---|---|---|
| SET_TRANSACTION_DATA | `coreInit` (`Clss_CoreInit_MC`، `SetParam_MC`، FinalSelectData) + `setPayPassParam` (DF8117/18/19/1F، limits) | `coreInit` (`CoreInit_Wave`، **ReaderParam** فيه `aucTmCap` = 9F33، FinalSelectData) + `setTransParam` (`SetVisaAidParam_Wave` floor/limits، `SetTransData_Wave`) | `CoreInit_EFT` + ReaderParam + AidParam + `SetTransData_EFT` |
| READ_APPLICATION_DATA | `Clss_InitiateApp_MC` + `Clss_ReadData_MC` | `Clss_Proctrans_Wave` (GPO + records + الـ AC type) — see phone / use contact / AAC هنا | `Clss_InitApp_EFT` + `Clss_ReadAppData_EFT` |
| ↓ الترتيب | ODA ثم PR | **PR ثم ODA** (`restrictionsBeforeOfflineDataAuth() = true`) | ODA ثم PR |
| OFFLINE_DATA_AUTHENTICATION | تحميل CAPK + revocation بس (M/Chip) — الـ ODA نفسه جوه `TransProc` | `Clss_CardAuth_Wave` (لو TC ومش refund، أو TTQ byte1 bit1) | `PreTAAProc` + CAPK + `Clss_CardAuth_EFT` |
| PROCESS_RESTRICTIONS | ولا حاجة (مفيش call) | `Clss_ProcRestric_Wave` (qVSDC/Wave2) | `Clss_ProcRestrict_EFT` |
| الـ bundle (`startTransProcess`) | `Clss_TransProc_MC_MChip`/`_Mag` = ODA+PR+CVM+TRM+TAA+GAC، ثم see phone (UIRD 0x20 أو DF4B)، `outcomeParamSet` → `genTransResult` | `genTransResult`: `Clss_GetCvmType_Wave` + الـ AC type (AAC من READ → decline) | `Clss_CVMProc_EFT` + `Clss_PostTAAProc_EFT` |
| 2nd tap / completion | `completeTransProcess` | FinalSelect + `Clss_IssuerAuth_Wave` | `Clss_CompleteOnlineTrans_EFT` |
| SCRIPT_PROCESSING | default (OK) | `Clss_IssScriptProc_Wave` + `onRemoveCard` | default |

> **9F33**: Visa/contact = قيمة واحدة 3 bytes. PayPass = tags منفصلة (DF8117 card data input، DF8118 CVM required، DF8119 no CVM، DF811F security) والـ kernel هو اللي بيبني 9F33. متجمعهمش في قيمة واحدة.

مسار الـ runners (granular):

```text
runContactlessAppSelect ─► SET ─► runContactlessSetTransactionData
                                   └► READ ─► runContactlessReadAppData
       PayPass/EFT: ─► ODA ─► runContactlessCardAuth ─► PR ─► runContactlessProcessRestrictions ─┐
       PayWave:     ─► PR  ─► runContactlessProcessRestrictions ─► ODA ─► runContactlessCardAuth ─┤
                                                                                              ▼
                                                              runContactlessStartTransaction (مش step جديد)
atomic: runContactlessAppSelect ─► PR ─► runContactlessProcessRestrictions ─► runContactlessStartTransaction
```

أي stage يرجّع `CLSS_RESELECT_APP` → `reselectApplication()` (`Clss_DelCurCandApp_Entry` + select تاني) → يبدأ الـ candidate الجديد من `firstStageAfterAppSelect()`. الـ atomic بيعمل نفس الـ loop جوه `ClssProcess.startTransProcess`.

### 5.5 `startContactlessTransProcess` (بعد الـ stages)

```text
tx.clsTransResult = kernel.contactless.startTransProcess()
ret != OK → markTryAgainOnCommunicationError (ICC_CMD_ERR/ICC_RESET_ERR/CLSS_TRY_AGAIN → TRY_AGAIN لو retries < 3)
confirmCard()               ← بننادي الـ callback بنفسنا: SET_TRANSACTION_DATA (atomic بس)،
                              9F1C = TID من الـ cache، CARD_READ
CVM online PIN؟ → onCardHolderPwd(true, ...)  (NO_PASSWORD = bypass → online من غير PIN)
refund / offline approved → host من غير ARQC → رد الـ host هو النتيجة
ARQC → startOnlineProcess()
        host مش approved → announceIssuerAuthentication → النتيجة = رد الـ host
        approved → runIssuerAuthentication → completeContactlessIssuerAuthentication:
             isNeedSecondTap? → onDetect2ndTap (WAITING_FOR_CARD + SEARCH_CARD "Tap the same card again")
                              → completeTransProcess → OK → runIssuerScriptProcessing → SCRIPT_PROCESSING
             مش محتاج → ONLINE_APPROVED زي ما هو
```

### 5.6 النتيجة (`checkContactlessResult`)

| الحالة | method | النهاية |
|---|---|---|
| OK + ONLINE_APPROVED | `onlineApproved` | APPROVED |
| OK + OFFLINE_APPROVED | `offlineApproved` → online failed | ERROR |
| OK + SIMPLE_FLOW_END | `simpleFlowEnd` | APPROVED |
| OK + غير كده | `offlineDenied(CLSS_DECLINE)` | DECLINED |
| CLSS_SEE_PHONE | `seePhone` (+ `clsLastNeedSeePhone` للـ tap الجاي) | retry CONTACTLESS (≤3) |
| TRY_ANOTHER_INTERFACE / `CLSS_USE_CONTACT` | `tryAnotherInterface` | retry CHIP |
| TRY_AGAIN | `tryAgain` | retry نفس الـ mode |
| ONLINE_DENIED / FAILED / CARD_DENIED / OFFLINE_DENIED | زي الـ contact | DECLINED / ERROR |

CDCVM (`CVM_CONSUMER_DEVICE`) مع OK = الموبايل عمل verify خلاص = CVM ناجح عادي. بس `RESULT_CLSS_SEE_PHONE` هو اللي معناه "اعمل verify على الموبايل وتاب تاني".

### 5.7 الـ callbacks بتاعة `IContactlessCallback`

| callback | مين بيناديه | بيعمل |
|---|---|---|
| `onReadCardOk` | الـ kernel بعد ما يقرا | atomic: `goToStep(READ_APPLICATION_DATA)`؛ `APPLICATION_SELECTED` |
| `confirmCard` | `startContactlessTransProcess` (إحنا) | atomic: `goToStep(SET_TRANSACTION_DATA)`؛ 9F1C؛ `CARD_READ` |
| `onWaitAppSelect` | shared مع الـ contact | askStep |
| `onCardHolderPwd` | shared | askStep |
| `startOnlineProcess` | shared | §3.2 |
| `onRemoveCard` | PayPass/PayWave | log بس |
| `onDetect2ndTap` | completion | prompt + SEARCH_CARD |
| `needSeePhone` | الـ kernel في الـ tap الجاي | بيرجع `clsLastNeedSeePhone` ويصفّره |

> الـ contact والـ contactless **مفصولين عن قصد** (runners منفصلة، state منفصلة: `transResult` / `clsTransResult`). الحاجات الـ shared هي الـ callbacks اللي الـ interfaces الاتنين بيطلبوها بنفس الـ signature بس.

---

## 6. EMV param apply flow

الـ params ليها 4 أماكن: **JSON assets** (أول boot) → **GreenDAO DB** (الحقيقة) → **in-memory cache** (`kernel.params`، `EmvParamService.cachedBuilder`) → **الـ native kernel** (كل transaction).

### 6.1 أول boot: الـ seed

```text
EmvEnhanceApp.onCreate → TerminalFactory.create → new PaxTerminal → PosTerminal ctor → initializeVendor
  → ensureEmvConfigInitialized: pref "emv_config_initialized"؟ لأ →
       new ConfigInit().init()   ← thread منفصل "ConfigInit"
         config.json → ConfigParamService
         acquirer.json / issuer.json / card_range.json → AcquirerIssuerService
         insertTerminalConfig()   (TID/MID/MCC... من الـ config — قيم demo)
         contactAid.json, capk.json, amex/paypass/paywave/dpas/eft/jcb/mir/pboc/pure/rupay.json
           → EmvParamService.insertXxx → GreenDAO
       pref = true
```

### 6.2 الـ download من الـ host

```text
[main] EmvParamActivity "Download EMV params" → downloadEmvParams()
[io]   OnboardingClient.ensureValidAccessToken (HostHeaders.build(sn), state)
       → PaxEmvParamUpdateService.downloadAndApply(authHeaders, posType)
           → EmvParamDownloadClient.download → HostApiConnection.emvFileDownload → byte[]  (:network، bytes بس)
           → EmvParamUpdater.applyFromZip(bytes)   (:bizentity)
               unzip: *.emv → emvXml ، *.clss → clssXml
               saveForDiagnosis (debug بس) → Android/data/<pkg>/files/emv_param/
               applyEmvXml:  EmvXmlParamParser.parse
                   AIDs موجودة؟  → EmvAid deleteAll + insertEmvAid
                   CAPKs موجودة؟ → Capk + CapkRevoke deleteAll + insertEmvCapk
                   (section مش موجود أو فاضي = يتساب زي ما هو)
               applyClssXml: ClssXmlParamParser.parse(xml, TerminalWideValues(contactAids))
                   PayPass / PayWave / Amex(+DRL): deleteAll + insert
                   removeKernelsNotInPackage: **الـ clss package هو الحقيقة كلها**
                       PayPass/PayWave/Amex مش في الـ package → اتمسحوا
                       DPAS/EFT/JCB/Mir/PBOC/Pure/RuPay (مالهمش parser) → اتمسحوا
               → EmvParamUpdateResult (applied / removed / failed / sectionsIn)
[main] result.summarize() على الشاشة
       result.changedDatabase() → OnboardingState.requireTerminalInitialization()  ("terminal_initialized" = false)
```

جوه الـ parser (اللي اتعمل على rxSet):
- PayWave: `TERMINALCAPABILITIES` (مشتركة لكل الـ AIDs) → `terminalType` (9F35)، `securityCapability`، `terminalCapability` (9F33 كاملة 3 bytes).
- PayWave floor limit = `ContactlessFloorLimit` (+Supported)، ولو مش موجود → `TerminalFloorLimit`.
- **`OTHERCONFIGURATION` (TID/MCC/merchant) مش بيتاخد من الـ XML** — قرار.
- Terminal-wide values (9F35، 9F40، security) لـ clss AIDs اللي ناقصاها: من الـ contact XML في نفس الـ package، وإلا من الـ contact AIDs اللي في الـ DB.

### 6.3 التحميل للـ kernel (كل transaction)

```text
prepare → onTerminalInitialization → prepareKernel(config)
  EmvFlowRuntime.init / isReady   (DAL)
  initializeTerminalIfNeeded:
      "terminal_initialized" false؟ → kernel.params.reloadFromDatabase()   ← cache جديد من الـ DB
                                     markTerminalInitialized()
      true → الـ cache اللي في الـ memory (getCachedEmvParam: لو ناقص أي جزء بيحمّل من الـ DB)
  closeReaders(true)              (defensive)
  kernel.contact = new ContactProcess()
  runPreTransProcess(config, INSERT|INTERNAL_WAVE):
      EmvTransParam: 9C من procCode، المبلغ، 9F03 = 0، TID (من الـ cache)، currency، date/time،
                     9F41 = 0، FLOWTYPE_COMPLETE، PCI mode، PIN lengths، 60s timeout
      EmvProcessParam: termConfig + CAPKs + transParam (+ contact AIDs | + الـ 10 clss params)
      kernel.contact.preTransProcess      فشل → يشيل INSERT
      kernel.contactless.preTransProcess  فشل → يشيل WAVE
  kernel.contactReady / contactlessReady  → PaxTerminal.searchCard يختار الـ readers
```

ليه flag مش reload كل مرة: التحميل من الـ DB تقيل؛ بيحصل مرة بعد كل onboarding (`markOfflineOnboarded`) أو download بيغيّر الـ DB، والـ trace بيكتب وقته.

> ملاحظة: `EmvParamUpdater` بيعمل `new EmvParamService()` جديد، فالـ `insertXxx` بيحدّث cache الـ instance ده بس — مش `kernel.params`. عشان كده الـ flag هو اللي بيوصّل التغيير للـ kernel.

### 6.4 الـ params بتوصل للـ card إمتى

| param | بيدخل الـ kernel فين |
|---|---|
| contact AIDs + أول AID كـ default | `ContactProcess.preTransProcess` (`EMVAddApp`، `setEmvAndMCKParam`) |
| 9F33/9F40/9F35 بتاعة الـ AID المختار | `emvSetParam` → `resetParam` (بعد الـ selection، قبل GPO) |
| CAPK + revocation | contact: `cardAuthentication` → `addCapk`؛ clss: ODA stage → `addCapkRevList` |
| clss AIDs + limits | `ClssEntryAddAid.addApp` + `Clss_PreTransProc_Entry` |
| params الـ kernel نفسه (TTQ، 9F33، floor، CVM limit، DF81xx) | `setTransactionData` بتاع كل kernel (granular) أو جوه الـ bundle (atomic) |
| TID لـ field 55 في clss | `confirmCard` → `setTlv(9F1C)` |

### 6.5 تشوف إيه اللي محمّل

- `EmvParamActivity` → `EmvParamListFragment`: كل category، ودوسة على row → `showCached()` → `PaxTerminal.kernelParams()` (الـ cache الحقيقي) → `ParamDump.describe` (hex، nested). لو مش في الـ cache لسه → قيم الـ DB.
- زرار "Downloaded XML" (debug): آخر `emv_param.emv` / `clss_param.clss` اتحمّلوا.
- تغيير في الـ schema = `SCHEMA_VERSION` 1 من غير migrations → **uninstall** (زي عمود PayWave `TERMINAL_CAPABILITY` الجديد).

---

## 7. الـ Threads

| Thread | بيشغّل | ممنوع |
|---|---|---|
| main | الـ UI، `acceptCard`، `cancelTransaction`، الـ LiveData، الـ dialogs (`AppSelectDialog`، `PaxPinPad`) | أي blocking (EMV، host، print، DB) |
| transaction (`Executors.newSingleThreadExecutor`) | `Transaction.run`: search → EMV كله → host (`blockingGet`) → retry | interrupt؛ أكتر من transaction (`AtomicReference current`) |
| print (single thread) | `printReceipt` بالترتيب | — |
| `Schedulers.io()` | download الـ params، onboarding | — |
| `ConfigInit` | seed أول boot | — |

الـ transaction thread **بيستنى** الـ main: `AppSelectDialog.selectBlocking()` والـ PIN pad. عشان كده الـ dialog لازم يتقفل في كل نهاية (`finishContactStage` بيعمل `activePinPad.dismiss()`).

---

## 8. لما تيجي تعدّل

1. **phase جديدة أو ترتيب جديد**: ابدأ من ترتيب الـ native kernel (الـ SDK doc / الـ ClssXxxProcess)، مش من ترتيب الـ enum. الـ step اسمه = اللي الـ kernel بيعمله فعلاً.
2. **شغل حقيقي جوه الـ `onXxx`**، والـ callback يرجّع الإجابة بس (`askStep`). متحطش شغل UI جوه الـ callback.
3. **fail closed**: أي إجابة مش واصلة = `USER_CANCEL` / online failed / decline. مفيش offline approve.
4. **contact ≠ contactless**: runner جديد لكل واحد، حتى لو الكود شبه بعض.
5. **متناديش `onXxx` مباشرة** — `goToStep`.
6. **متعملش `observeOn` على `emvSteps` في الـ behavior** ولا تـ serialize الـ subject.
7. **PAX SDK modules** (`emvbase`، `emvlib*`، `poslib`، `commonutils`): تتلمس بس لما الـ kernel نفسه يحتاج (زي `setTransactionData` / `issuerScriptProcessing`)، وبنفس ترتيب الـ native calls.
8. **schema** اتغيرت → DAO بإيدك (ordinal، DDL، bind، read) + uninstall على الجهاز.
9. اختبر: `:core` unit tests (65)، XML (20)، DAO (3) — وبعدين الجهاز بصفوف `report_easylink.xlsx`.
