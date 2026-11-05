# EasyLink — مرجع الكود

> مكتوب من كود فرع **`transactionStepObserver`** (2026-11-05). كل اسم هنا موجود في الكود.
> الرسوم في `doc/EasyLink-EMV-Flows.html` / `.pdf` (و https://claude.ai/artifact/S69zGozrJmfoGCVe3VkAnh). هذا الملف هو المرجع المختصر: أين كل شيء، ومن يملكه، والقواعد.
> ⚠ = لم يُصلَح بعد، والرقم بين القوسين رقمه في `doc/BACKLOG.md`. آخر تجربة كاملة على الجهاز عند `19cc019` (chip، tap again)؛ ما بعدها لم يُجرَّب على الجهاز إلا ما يُذكر.
> إذا تغيّر الكود ولم يتغيّر المرجع، فالكود هو الصحيح.

## الفهرس

1. [أين كل شيء](#1-أين-كل-شيء)
2. [من يملك ماذا](#2-من-يملك-ماذا)
3. [العملية وخطواتها](#3-العملية-وخطواتها)
4. [خطوات الـ EMV](#4-خطوات-الـ-emv)
5. [الطريق إلى البنك](#5-الطريق-إلى-البنك)
6. [الـ EMV params](#6-الـ-emv-params)
7. [الـ Threads](#7-الـ-threads)
8. [عند التعديل](#8-عند-التعديل)
9. [الفرق مع paxEmvBehaviorSplit](#9-الفرق-مع-paxemvbehaviorsplit)

---

## 1. أين كل شيء

| ماذا | أين |
|---|---|
| الجهاز، العملية، خطواتها، الإلغاء، النهاية الآمنة | `core/.../terminal/PosTerminal.java` (+ `Transaction`) |
| الـ EMV المشترك: المحاولات، `goToStep`، `askStep`، `askTheBank`، الـ 17 `onXxx` | `core/.../terminal/AbstractEmvBehavior.java` |
| طريق الـ EMV إلى البنك | `core/.../terminal/BankRequest.java` |
| شريط خطوات الـ EMV والـ APDU trace | `core/.../emv/EmvEngine.java`، `ApduTrace.java` |
| field 55 | `core/.../emv/Field55.java` (+ `field55()` في كل vendor) |
| البطاقة | `core/.../card/CardPresence.java` (`isEmv()` / `isNonEmv()`) |
| PAX: الخطوات، ردود الـ kernel، الطريق إلى البنك | `vendor-pax/.../PaxEmvBehavior.java` |
| PAX: البحث، الـ PIN، رد البنك بشكل PAX، القارئات، بيانات البطاقة، الإعدادات، المحاولة | `PaxCardSearch`، `PaxPinEntry`، `PaxHostAnswer`، `PaxReaders`، `PaxCardData`، `PaxSetup`، `PaxKernelSession` |
| الـ contact kernel | `vendor-pax/libs/sdk/emvlib/dpas/.../ContactProcess.java` |
| الـ contactless kernels | `vendor-pax/libs/sdk/emvlib/.../contactless/ClssProcess.java` + `Clss*Process` |
| الاتصال بالبنك | `network/.../SaleCommunicationBehavior.java`، `HostApiClient.java` |
| الـ params (cache، XML، التنزيل) | `vendor-pax/libs/ours/bizentity/.../EmvParamService.java`، `EmvParamUpdater.java` |

## 2. من يملك ماذا

كل حالة لها مالك واحد ونسخة واحدة:

| الحالة | المالك |
|---|---|
| الجهاز مشغول | `PosTerminal.current` |
| حالة العملية (قيد التنفيذ / انتهت / أُلغيت) | `Transaction.end` (يُكتب داخل الـ lock فقط) |
| خطوات العملية | `Transaction.steps` (`ReplaySubject`، وهو الـ lock أيضًا) |
| خطوات الـ EMV | `EmvEngine.emvSteps` |
| إعادة المحاولة | `AbstractEmvBehavior` (`requestRetry` / `retryCount()`) |
| رد البنك عند الـ EMV | `AbstractEmvBehavior.bankAnswer()` (يُمسح مع كل محاولة) |
| رد البنك للنهاية الآمنة | `Transaction` → `OnlineRequest.answer` |
| ما عُرف في المحاولة الحالية (PAX) | `PaxKernelSession` (جديد مع كل محاولة) |

- الـ EMV (`AbstractEmvBehavior` والـ vendor) لا يعرف الـ threads ولا الانشغال ولا البنك مباشرة؛ معه `BankRequest` فقط.
- `PosTerminal` لا يعرف أي منطق EMV.
- الشاشة لا تحتفظ بحالة العملية، ولا تعرف أي vendor.

## 3. العملية وخطواتها

`TransactionStepObserver` هو المدخل الوحيد إلى `dispatchTransactionStep`:

| الخطوة | الدالة | ماذا تفعل |
|---|---|---|
| `TRANSACTION_STARTED` | `onTransactionStarted` | تنقل العملية إلى الـ transaction thread (`runTransaction`) |
| `CANCELLING` | `onCancelling` | توقف البحث عن البطاقة |
| `ENDING` | `onEnding` | تبدأ سلسلة الإغلاق: `closeNext(0)` |
| `ACKNOWLEDGING` / `REVERSING` | `onAcknowledging` / `onReversing` | `closeAtHost`: التأكيد / الإلغاء عند البنك |
| `CLOSING_COMPLETED` | `onClosingCompleted` | الطلب التالي، أو تحرير الجهاز وتسليم النهاية |
| الباقي | — | تقرير للشاشة: `ONLINE_*`، `CARD_*`، `APPROVED` / `DECLINED`، `COMPLETED` / `ERROR` / `CANCELLED` |

**الترتيب:** `TRANSACTION_STARTED → WAITING_FOR_CARD → CARD_DETECTED → … → ONLINE_REQUIRED → ONLINE_PROCESSING → ONLINE_COMPLETED → APPROVED|DECLINED → ENDING → (ACKNOWLEDGING|REVERSING → CLOSING_COMPLETED)… → COMPLETED`، أو `ERROR`، أو `CANCELLING` ثم `CANCELLED`.

- **النتيجة:** الـ EMV يبلّغ (`finishApproved` / `finishDeclined` / `finishError` → `Transaction.emvFinished`)، والـ `Transaction` تضبط النهاية. إلغاء يسبق النتيجة يجعلها تُتجاهل.
- **النهاية:** الـ `finally` في `runTransaction` يحدد النهاية (أو ERROR "Transaction ended without an outcome") ويسلّم `ENDING`. الجهاز يبقى مشغولًا حتى تنتهي كل الإغلاقات، ثم `current = null`، ثم تُسلَّم النهاية.
- **الإلغاء:** `cancel()` يضبط `end = CANCELLED` وينشر `CANCELLING`. يتوقف الـ EMV عند أول `goToStep`. لا يُقاطَع الـ thread أبدًا.
- **إعادة المحاولة:** يطلبها الـ EMV (`requestRetry`)، ثم يبدأ `run` محاولة جديدة من `TERMINAL_INITIALIZATION` في العملية نفسها.
- ⚠ دالة الخطوة تعمل والـ lock مأخوذ، فترى الشاشة `ACKNOWLEDGING` / `REVERSING` بعد نداء البنك (10.4). لذلك الخطوات التي قد تنتظر البنك أو شخصًا قبل تحديد النهاية تبقى تقارير.

## 4. خطوات الـ EMV

**القواعد:** لا يستدعي أحد `onXxx` مباشرة، بل `goToStep` (التي تُسقط الخطوة بعد النهاية وتعيد `false`). التشغيل متزامن على الـ transaction thread (بلا `observeOn` ولا serialize). الـ callback الذي ينتظر شخصًا يسأل الخطوة عبر `askStep`، ويرد دائمًا (`USER_CANCEL` عند الشك).

| # | EmvStep | Contact (PAX) | Contactless granular | Contactless atomic | Swipe / Manual |
|---|---|---|---|---|---|
| 0 | TERMINAL_INITIALIZATION | `PaxKernelSession` جديد + `prepareKernel`، ثم `searchCard()` | = | = | = |
| 1 | SEARCH_CARD | `PaxCardSearch.search` | = | = | = |
| 2 | APPLICATION_SELECTION | `runContactAppSelect` (`EMVAppSelect`) | `runContactlessAppSelect` | = | — |
| 3 / 4 | WAIT / FINAL_APPLICATION_SELECTION | `askStep` ← `onWaitAppSelect` | = | = | — |
| 5 | READ_APPLICATION_DATA | `runContactReadAppData` (`EMVReadAppData`) | `runContactlessReadAppData` | من `onReadCardOk` (بلا عمل) | `CARD_READ` ← 13 |
| 6 | SET_TRANSACTION_DATA | `runContactSetTransactionData` (9C) | `runContactlessSetTransactionData` | من `confirmCard` (بلا عمل) | — |
| 7 | OFFLINE_DATA_AUTHENTICATION | `runContactCardAuth` (`EMVCardAuth`) | `runContactlessCardAuth` | — | — |
| 8 | PROCESS_RESTRICTIONS | `runContactStartTransaction` (`EMVStartTrans` = PR+CVM+TRM+TAA+1st GAC) | `runContactlessProcessRestrictions` | الاستدعاء الواحد كله | — |
| 9 / 10 | CARDHOLDER / OFFLINE_PIN_VERIFICATION | `askStep` ← `onCardHolderPwd` | online PIN بعد الاستدعاء | = | — |
| 11 / 12 | TERMINAL_RISK_MANAGEMENT / ACTION_ANALYSIS | لا تُنشر (داخل `EMVStartTrans`) | = | = | — |
| 13 | ONLINE_AUTHORIZATION | `startOnlineProcess` → `askTheBank()` | = | = | `askTheBank()` ← 16 |
| 14 | ISSUER_AUTHENTICATION | `EMVCompleteTrans` (ARPC + scripts + 2nd GAC) | Visa: الـ tap الثاني + `Clss_IssuerAuth_Wave` | = | — |
| 15 | SCRIPT_PROCESSING | عنوان فقط (داخل `EMVCompleteTrans`) | Visa: `Clss_IssScriptProc_Wave` | = | — |
| 16 | TRANSACTION_COMPLETION | `reportOutcome` | = | = | `finishApproved` / `finishDeclined` من `bankAnswer()` |

- الكود يتبع ترتيب الـ kernel لا الأرقام (PayWave: PR ثم ODA؛ atomic: تُعلَن 5 و 6 حين يقول الـ kernel إنهما حدثتا).
- الكل `protected abstract`: vendor ينسى خطوة يحصل على خطأ ترجمة. و `onTerminalInitialization` يجب أن تنتهي بـ `searchCard()`.

## 5. الطريق إلى البنك

```java
// AbstractEmvBehavior — the EMV asks the bank
protected final void askTheBank()          // field 55 + card data → bankRequest.send(config); keeps the answer
protected final AuthResult bankAnswer()    // reads it, never asks again
// PosTerminal — attach(this::sendToBank), once, in its constructor
public AuthResult sendToBank(config)       // names the request, records it, ONLINE_*, communication.connectToHost(sent)
// SaleCommunicationBehavior — connectToHost: token → exchange → sale / refund (plain steps)
```

- **لماذا `attach`:** `PosTerminal` يحتاج الـ behavior في الـ constructor الخاص به، والـ behavior يحتاج `sendToBank`، فيُوصَل بعد الإنشاء مرة واحدة (setter injection).
- **`startOnlineProcess()`** method في واجهات PAX يناديها كودنا بعد أن يرجع الـ kernel بـ ARQC؛ ليست callback من الـ kernel.
- **ARQC وحده يذهب إلى البنك** (الاسترداد كالبيع). الاستثناء: TC (`RESULT_OFFLINE_APPROVED`) يُرسل دون ARQC ويكون رد البنك هو النتيجة. AAC = رفض.

**النهاية الآمنة** (عند `ENDING`، لكل طلب أُرسل):

| الرد | النهاية | يُرسل |
|---|---|---|
| موافقة، APPROVED، COMPLETED، وآخر طلب | — | تأكيد (`orchestration/acknowledgement`) |
| موافقة مع نهاية أخرى، أو طلب قديم | — | إلغاء (`orchestration/reverse`) |
| `failed` (أُرسل ولا رد صالح) | — | إلغاء |
| `notSent` أو رفض | — | لا شيء |

**`notSent`** يقرره الـ network وحده (`answerOf`): كل فشل قبل الإرسال، و `Token refresh: …`، و `Exchange: …`، و `Sale:` لا اتصال أو HTTP 401 / 403. أي شيء آخر بعد إرسال البيع = `failed`.

| المهلة | القيمة |
|---|---|
| كل نداء دفع: `callTimeout` (`HostApiClient.createForPayments`)، adapter متزامن | 25 ث |
| `PosTerminal.sendToBank` (شبكة أمان) | 90 ث |
| `closeAtHost` (شبكة أمان) | 60 ث |

- ⚠ التأكيد / الإلغاء يُرسل مرة واحدة، بلا سجل محفوظ (0.15). ⚠ لم يُجرَّب مع البنك؛ احتياجهما لـ `mToken` افتراض.
- ⚠ Mastercard Single Tap لم يُنفَّذ؛ الخطة في 4.11.

## 6. الـ EMV params

- **قاعدة البيانات (GreenDAO) هي المصدر:** من JSON مرة واحدة (`ConfigInit`)، ثم من ملف الـ host عند Download (`EmvParamUpdater.applyFromZip`).
- **الـ cache** (`EmvParamService`) نسخة في الذاكرة: أي كتابة تفرغه (`invalidate()`)، وأول عملية تملؤه (`getCachedEmvParam` → `loadFromDatabase`، 13 param).
- **الـ kernel لا يحتفظ بشيء:** تُحمَّل الـ params إليه في كل محاولة (`preTransProcess`).
- الـ contact: ما لا يأتي في ملف الـ host يبقى. الـ contactless: ملف الـ host هو المرجع، وأي kernel غير موجود فيه يُحذف.
- TID / MID / MCC / اسم التاجر لا تأتي من ملف الـ host (`OTHERCONFIGURATION` لا يُقرأ عمدًا).
- ⚠ لا فحص لصلاحية الـ CAPKs.
- تغيير الـ schema: الـ DAO يُعدَّل يدويًا + uninstall على الجهاز.

## 7. الـ Threads

| الـ Thread | ما يشغّله | الممنوع |
|---|---|---|
| main | الشاشة، `acceptCard` (فحص الانشغال)، `cancel()`، النوافذ (اختيار التطبيق، الـ PIN) | أي عمل blocking |
| transaction (`transactionScheduler`) | العملية كلها: التهيئة، البحث، الـ EMV، البنك (`blockingGet`)، الإغلاقات | المقاطعة؛ أكثر من عملية |
| print | `printReceipt` بالترتيب | — |
| `Schedulers.io()` | تنزيل الـ params، التسجيل | — |

الـ kernel وكل الـ callbacks على الـ transaction thread فقط، وهو ينتظر الـ main في نوافذ الاختيار والـ PIN، فيجب أن تُغلق النافذة في كل نهاية. ⚠ الإلغاء لا يقطع إدخال الـ online PIN (1.2).

## 8. عند التعديل

1. **ترتيب الـ kernel أولًا**، لا ترتيب الـ enum. الخطوة تظهر حيث يعمل الـ kernel فعلًا.
2. **العمل داخل `onXxx`**، والـ callback يرد فقط (`askStep`).
3. **fail closed:** لا رد = `USER_CANCEL` / online failed / رفض؛ لا موافقة offline.
4. **contact ≠ contactless:** runner لكل منهما.
5. **`goToStep`، لا استدعاء مباشر**، ولا `observeOn` داخل الـ behavior.
6. **مالك واحد لكل حالة** (§2)؛ لا flag ثانٍ.
7. **خطوة عملية جديدة لها دالة:** حالة في `dispatchTransactionStep`؛ وإن كانت قد تنتظر البنك أو شخصًا قبل تحديد النهاية فاجعلها تقريرًا.
8. **الـ PAX SDK** (`emvbase`، `emvlib*`، `poslib`…) لا يُلمس إلا لسبب EMV واضح.
9. **الاختبار:** هذا الفرع بلا tests؛ تُشغَّل tests `paxEmvBehaviorSplit-tests` عليه محليًا. ثم الجهاز بصفوف `report_easylink.xlsx`.

## 9. الفرق مع paxEmvBehaviorSplit

الفرع الأقدم (`paxEmvBehaviorSplit` / `-tests`) يعمل بالطريقة نفسها، مع:

| هنا (`transactionStepObserver`) | هناك |
|---|---|
| `askTheBank()` / `bankAnswer()` | `authorizeOnline()` / `tx.lastAuth` في كل vendor |
| `BankRequest.send` (ملف مستقل)، `PosTerminal.sendToBank` | `AbstractEmvBehavior.Authorizer.authorize`، `PosTerminal.authorize` |
| `CommunicationBehavior.connectToHost` | `CommunicationBehavior.authorize` |
| `ONLINE_AUTHORIZATION` / `onOnlineAuthorization` | `START_ONLINE_PROCESS` / `onStartOnlineProcess` |
| `TransactionStepObserver` مدخل وحيد، `ENDING`، سلسلة إغلاق، `CLOSING_COMPLETED` | الـ switch تستدعيه عدة جهات، `onEnd` بحلقة، `CLOSED` |
| `TERMINAL_INITIALIZATION` تبدأ البحث | `run` يبدأ البحث بـ `if` |
| `field55()` abstract، `Field55.TagReader` | `readIccTag` abstract، `IntFunction` |
| `CardPresence.isEmv()` / `isNonEmv()` | `AbstractEmvBehavior.isNonEmv(card)` |
| issuer step تبني رد PAX من `bankAnswer()`؛ لا إعلان ثانٍ | `tx.issuerResponse`؛ إعلان ثانٍ في `onlineDenied` / `onlineCardDenied` |
| network بخطوات متتالية، `callTimeout` 25 ث، adapter متزامن، `closeAtHost` 60 ث | سلسلة Rx (`flatMap`)، بلا `callTimeout`، `closeAtHost` 15 ث |
