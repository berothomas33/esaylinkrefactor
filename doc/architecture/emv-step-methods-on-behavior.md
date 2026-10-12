# EMV steps: vendor method + goToStep

## الفكرة

مفيش runner بيرصّ كل الخطوات في `if (!run) return`.

1. الـ engine / `start` يدخل أول خطوة (مثلاً `APPLICATION_SELECTION`)
2. يبلّغ على **EmvStep observable**
3. ينادي method الـ vendor (`onApplicationSelection`)
4. الـ vendor يخلّص شغله، وبعدين:

```java
goToStep(EmvStep.WAIT_APPLICATION_SELECTION);
```

`goToStep` بتنادي `dispatchStepMethod` على طول. `dispatchStepMethod` بتتأكد إن المعاملة ما اتلغتش،
وبعدين تنشر الخطوة على **EmvStep observable** بتاع الـ engine (للـ UI)، وتعمل `switch` على الخطوة
وتنادي الـ `onXxx` بتاعتها. كله بيشتغل على الـ thread بتاع المعاملة، فالسلسلة كلها بتخلص قبل ما
`start()` ترجع، و`PosTerminal` معتمد على كده علشان يشوف الـ retry بعدها على طول.

## مثال (Fake)

```java
@Override
public void onApplicationSelection(EmvEngine engine, TransactionConfig config, CardPresence card) {
    // شغل الـ vendor هنا…
    goToStep(EmvStep.WAIT_APPLICATION_SELECTION);
}

@Override
public void onWaitApplicationSelection(...) {
    goToStep(EmvStep.FINAL_APPLICATION_SELECTION);
}
```

تخطي (CLSS / mag): اختار الخطوة الجاية بنفسك:

```java
goToStep(EmvStep.READ_APPLICATION_DATA);      // تخطي wait/final
goToStep(EmvStep.TRANSACTION_COMPLETION);   // أو للنهاية
```

إنهاء المعاملة:

```java
finishApproved("ONLINE APPROVED");
finishDeclined("…");
finishError("…");
```

## ملاحظة

`onTerminalInitialization` **متستدعش** `goToStep` — بعدها `PosTerminal.searchCard`.
`start()` هو اللي يفتح أول خطوة بعد البحث.

## PAX

`PaxEmvBehavior extends AbstractEmvBehavior`:

| Path | How it advances |
|------|-----------------|
| Mag / Manual | `goToStep` like Fake (`READ` → `ONLINE` → `COMPLETION`) |
| Chip / CLSS | `onApplicationSelection` starts PAX kernel; callbacks use **`announceStep`** (observable only — kernel owns the phase, no re-enter `onXxx`) |

```java
// Kernel callback example
announceKernelStep(EmvStep.CARDHOLDER_VERIFICATION, "online PIN");
```

## الإلغاء

- `EmvEngine` بيفتكر آخر `EmvStep` اتنشرت، سواء جت من `goToStep` أو من `announceStep`. لما المعاملة
  تتلغي بيبلّغ مرة واحدة بس: `Transaction cancelled at <الخطوة>`.
- لو الإلغاء وصل بين خطوتين، `dispatchStepMethod` بيوقف قبل الخطوة الجاية. بيكتب في الـ APDU trace
  الخطوة اللي وقف عندها والخطوة اللي ما اشتغلتش، وبعدين ينادي `onFlowCancelled()` علشان الـ vendor
  يقفل الـ readers والـ listener والـ PIN pad.
- لو الـ vendor لاحظ الإلغاء جوه خطوة، يستخدم `finishCancelled()` مش `finishError(...)`.
