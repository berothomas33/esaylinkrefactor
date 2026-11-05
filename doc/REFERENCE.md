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
| الـ EMV المشترك: المحاولات، `goToStep`، `askStep`، `askTheBank`، الـ 16 `onXxx` | `core/.../terminal/AbstractEmvBehavior.java` |
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
| `ENDING` | `onEnding` | الإغلاقات في loop واحدة: لكل طلب أُرسل، `closingFor(i)` ثم `closeAtHost` (تأكيد أو إلغاء). بعدها يتحرر الجهاز وتُسلَّم النهاية |
| الباقي | — | تقرير للشاشة: `ONLINE_*`، `CARD_*`، `APPROVED` / `DECLINED` / `FAILED`، `ACKNOWLEDGING` / `REVERSING` / `CLOSING_COMPLETED`، `COMPLETED` / `ERROR` / `CANCELLED` |

**الترتيب:** `TRANSACTION_STARTED → WAITING_FOR_CARD → CARD_DETECTED → … → ONLINE_REQUIRED → ONLINE_PROCESSING → ONLINE_COMPLETED → APPROVED|DECLINED → ENDING → (ACKNOWLEDGING|REVERSING → CLOSING_COMPLETED)… → COMPLETED`، أو `FAILED → ENDING → … → ERROR`، أو `CANCELLING → ENDING → … → CANCELLED`.

- **حالة العملية هي خطواتها (لا field):** أول خطوة "قرار" تُسلَّم هي الحالة: `decision()` تبحث في الخطوات المحفوظة (`ReplaySubject`، يحفظ كل شيء) عن أول `CANCELLING` أو `APPROVED` أو `DECLINED` أو `FAILED` (`TransactionStep.isDecision()`). `isRunning()` = لا قرار؛ `isCancelled()` = القرار `CANCELLING`. أول قرار يكسب (`decide`)، وما بعده يُتجاهل.

| القرار | من أين | النهاية التي تُسلَّم أخيرًا (`endOfDecision`) |
|---|---|---|
| `CANCELLING` | `cancel()` | `CANCELLED` |
| `APPROVED` / `DECLINED` | `finishApproved` / `finishDeclined` → `emvFinished` | `COMPLETED` |
| `FAILED` + الرسالة | `finishError` / `end(error)`، أو الـ `finally` ("Transaction ended without an outcome") | `ERROR` بالرسالة نفسها |

- **لماذا `FAILED`:** `ERROR` هي النهاية التي تعرضها الشاشة، والجهاز مشغول حتى تنتهي الإغلاقات. فالفشل يحتاج خطوة تظهر لحظة حدوثه، والنهاية تأتي بعدها.
- **النهاية:** الـ `finally` في `runTransaction` يتأكد أن هناك قرارًا ويسلّم `ENDING`. الجهاز يبقى مشغولًا حتى تنتهي كل الإغلاقات، ثم `current = null`، ثم تُسلَّم النهاية.
- **الإلغاء:** `cancel()` ينشر `CANCELLING` (قرار). يتوقف الـ EMV عند أول `goToStep`. لا يُقاطَع الـ thread أبدًا.
- **الـ vendor لا يقرر بالنشر:** `AbstractEmvBehavior.publish` يُسقط كل خطوة قرار أو نهاية.
- **إعادة المحاولة:** يطلبها الـ EMV (`requestRetry`)، ثم يبدأ `run` محاولة جديدة من `TERMINAL_INITIALIZATION` في العملية نفسها.
- ⚠ `onEnding` تعمل والـ lock مأخوذ، ومعها نداءات البنك؛ لذلك `cancel` و `publish` و `decide` تنظر في القرار قبل أخذ الـ lock، فلا تنتظر الإغلاقات. لذلك الخطوات التي قد تنتظر البنك أو شخصًا قبل تحديد النهاية تبقى تقارير.

## 4. خطوات الـ EMV

**القواعد:** لا يستدعي أحد `onXxx` مباشرة، بل `goToStep` (التي تُسقط الخطوة بعد النهاية وتعيد `false`). التشغيل متزامن على الـ transaction thread (بلا `observeOn` ولا serialize). الـ callback الذي ينتظر شخصًا يسأل الخطوة عبر `askStep`، ويرد دائمًا (`USER_CANCEL` عند الشك).

| # | EmvStep | Contact (PAX) | Contactless granular | Contactless atomic | Swipe / Manual |
|---|---|---|---|---|---|
| 0 | TERMINAL_INITIALIZATION | `PaxKernelSession` جديد + `prepareKernel`، ثم `searchCard()` | = | = | = |
| 1 | SEARCH_CARD | `PaxCardSearch.search` | = | = | = |
| 2 | APPLICATION_SELECTION | `runContactAppSelect` (`EMVAppSelect`) | `runContactlessAppSelect` | = | — |
| 3 / 4 | WAIT / FINAL_APPLICATION_SELECTION | `askStep` ← `onWaitAppSelect` | = | = | — |
| 5 | READ_APPLICATION_DATA | `runContactReadAppData` (`EMVReadAppData`) | `runContactlessReadAppData` | من `onReadCardOk`، بعد 6 (بلا عمل) | `CARD_READ` ← 13 |
| 6 | SET_TRANSACTION_DATA | `runContactSetTransactionData` (9C) | `runContactlessSetTransactionData` | من `onReadCardOk`، قبل 5 (بلا عمل) | — |
| 7 | OFFLINE_DATA_AUTHENTICATION | `runContactCardAuth` (`EMVCardAuth`) | `runContactlessCardAuth` | — | — |
| 8 | PROCESS_RESTRICTIONS | `runContactStartTransaction` (`EMVStartTrans` = PR+CVM+TRM+TAA+1st GAC)، ثم 13 إن ذهب الكارت إلى البنك، وإلا تنتهي المرحلة | `runContactlessProcessRestrictions` ← `runContactlessStartTransaction` (مثله) | الاستدعاء الواحد كله، ثم مثله | — |
| 9 / 10 | CARDHOLDER / OFFLINE_PIN_VERIFICATION | `askStep` ← `onCardHolderPwd` | online PIN بعد الاستدعاء | = | — |
| 11 / 12 | TERMINAL_RISK_MANAGEMENT / ACTION_ANALYSIS | لا تُنشر (داخل `EMVStartTrans`) | = | = | — |
| 13 | ONLINE_AUTHORIZATION | `askTheBank()` (أول سطر في الـ hook، لكل البطاقات)، ثم `runContactOnline`: 14 دائمًا (الـ 2nd GAC) | `runContactlessOnline`: 14 إن وافق البنك، وإلا النهاية | = | `askTheBank()` ثم `finishNonEmv`: النتيجة من `bankAnswer()` |
| 14 | ISSUER_AUTHENTICATION | `runContactIssuerAuthentication`: `EMVCompleteTrans` (ARPC + scripts + 2nd GAC)، ثم نهاية المرحلة | `runContactlessIssuerAuthentication`: Visa: الـ tap الثاني + `Clss_IssuerAuth_Wave`، ثم النهاية | = | — |
| 15 | SCRIPT_PROCESSING | عنوان فقط (داخل `EMVCompleteTrans`) | Visa: `Clss_IssScriptProc_Wave` | = | — |

- **لا `TRANSACTION_COMPLETION`:** لم يكن فيها عمل kernel في أي vendor، فحُذفت (16 hook الآن). بعد نهاية المرحلة (`finishXStage` → `checkXResult`) تُبلَّغ النتيجة مباشرة (`completeApproved` / `completeDeclined` → `finishApproved` / `finishDeclined`، أو `finishError`).
- **كل خطوة تعمل عملها ثم تسلّم التالية** (`advanceContactStage` / `advanceContactlessStage`). لا تعمل خطوة داخل أخرى: البنك والـ issuer لم يعودا داخل الـ hook الخاص بـ `PROCESS_RESTRICTIONS`. نهاية المرحلة (`finishContactStage` / `finishContactlessStage`: إغلاق الـ reader ثم قراءة النتيجة) في آخر خطوة تعمل، أو حيث يتوقف المسار. إن كانت العملية قد انتهت (إلغاء، أو خطأ قبلها) لا تُقرأ النتيجة مرة ثانية: العملية تنتهي مرة واحدة.
- الكود يتبع ترتيب الـ kernel لا الأرقام (PayWave: PR ثم ODA؛ atomic: تُعلَن 5 و 6 حين يقول الـ kernel إنهما حدثتا).
- الكل `protected abstract`: vendor ينسى خطوة يحصل على خطأ ترجمة. و `onTerminalInitialization` يجب أن تنتهي بـ `searchCard()`.

## 5. الطريق إلى البنك

```java
// AbstractEmvBehavior — the EMV asks the bank
protected AuthResult askTheBank()          // field 55 + card data → bankRequest.send(config); keeps the answer and returns it
protected final AuthResult bankAnswer()    // reads it, never asks again
// PosTerminal — attach(this::sendToBank), once, in its constructor
public AuthResult sendToBank(config)       // names the request, records it, ONLINE_*, communication.connectToHost(sent)
// SaleCommunicationBehavior — connectToHost: token → exchange → sale / refund (plain steps)
```

- **لماذا `attach`:** `PosTerminal` يحتاج الـ behavior في الـ constructor الخاص به، والـ behavior يحتاج `sendToBank`، فيُوصَل بعد الإنشاء مرة واحدة (setter injection).
- **`startOnlineProcess()` و `confirmCard()`** في واجهات PAX فقط: لا يناديهما الـ kernel ولا كودنا. الذهاب إلى البنك هو خطوة `ONLINE_AUTHORIZATION` نفسها، و `startOnlineProcess` ترجع `bankAnswer()` بشكل PAX لو سألها أحد. عمل البطاقة المقروءة (9F1C، `CARD_READ`) في `onReadCardOk`، التي يناديها الـ kernel بعد القراءة وقبل أن يقرر.
- `bankAnswer` يُحفظ فقط حين يُرسل الطلب. إن رجعت `askTheBank` مبكرًا (لا field 55، أو إلغاء) يبقى `null`، ويُعامل كـ online failed.
- **ARQC وحده يذهب إلى البنك** (الاسترداد كالبيع). الاستثناء: TC (`RESULT_OFFLINE_APPROVED`) يُرسل دون ARQC ويكون رد البنك هو النتيجة. AAC = رفض.

**النهاية الآمنة** (عند `ENDING`، لكل طلب أُرسل):

| الرد | النهاية | يُرسل |
|---|---|---|
| موافقة، والقرار APPROVED، وآخر طلب | — | تأكيد (`orchestration/acknowledgement`) |
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
| البنك والـ issuer خطوتان بعد `PROCESS_RESTRICTIONS`؛ عمل البطاقة المقروءة في `onReadCardOk` | يعملان داخل الـ hook الخاص بـ `PROCESS_RESTRICTIONS`؛ `confirmCard` بعد الـ 1st GAC |
| `BankRequest.send` (ملف مستقل)، `PosTerminal.sendToBank` | `AbstractEmvBehavior.Authorizer.authorize`، `PosTerminal.authorize` |
| `CommunicationBehavior.connectToHost` | `CommunicationBehavior.authorize` |
| `ONLINE_AUTHORIZATION` / `onOnlineAuthorization` | `START_ONLINE_PROCESS` / `onStartOnlineProcess` |
| `TransactionStepObserver` مدخل وحيد، `ENDING` بـ loop للإغلاقات، الحالة من الخطوات (`decision()`، `FAILED`)، لا `TRANSACTION_COMPLETION` | الـ switch تستدعيه عدة جهات، `onEnd` بحلقة، `CLOSED`، field `end` |
| `TERMINAL_INITIALIZATION` تبدأ البحث | `run` يبدأ البحث بـ `if` |
| `field55()` abstract، `Field55.TagReader` | `readIccTag` abstract، `IntFunction` |
| `CardPresence.isEmv()` / `isNonEmv()` | `AbstractEmvBehavior.isNonEmv(card)` |
| issuer step تبني رد PAX من `bankAnswer()`؛ لا إعلان ثانٍ | `tx.issuerResponse`؛ إعلان ثانٍ في `onlineDenied` / `onlineCardDenied` |
| network بخطوات متتالية، `callTimeout` 25 ث، adapter متزامن، `closeAtHost` 60 ث | سلسلة Rx (`flatMap`)، بلا `callTimeout`، `closeAtHost` 15 ث |
