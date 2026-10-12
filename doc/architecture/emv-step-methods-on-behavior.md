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

ده يبعت الخطوة على الـ `steps` subject اللي جوه `AbstractEmvBehavior`. الـ Observer الوحيد بتاعه
هو `dispatchStepMethod`: يتأكد إن المعاملة ما اتلغتش، ينشر الخطوة على **EmvStep observable** بتاع
الـ engine (للـ UI)، وبعدين يعمل `switch` على الخطوة وينادي الـ `onXxx` بتاعها.

الـ subject بيشتغل serialized ومن غير scheduler. يعني لو `goToStep` اتنادت من جوه `onXxx`، الخطوة
الجاية بتتحط في الطابور وتشتغل بعد ما `onXxx` الحالية ترجع، على نفس الـ thread بتاع المعاملة. الخطوات
بتشتغل ورا بعض في loop بدل ما كل method تنادي اللي بعدها والـ stack يكبر، والسلسلة كلها بتخلص قبل
ما `start()` ترجع، و`PosTerminal` معتمد على كده علشان يشوف الـ retry بعدها على طول. مفيش
`observeOn(computation())` لأن نداءات الـ kernel بتاعة الـ vendor بتعمل block.

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
