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

ده ينشر الخطوة على الـ observable وينفّذ الـ method الجاية.

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

## الـ step stream (RxJava)

`goToStep` → `dispatchStepMethod` → `stepStream.onNext(step)` بس، و`Observer` واحد (`StepObserver`) هو اللي ينادي `onXxx`.

- الخطوة الجاية بتتنفّذ **بعد** ما الـ method الحالية ترجع (queued)، مش جواها.
- نفس الـ thread ومن غير scheduler: `prepare()` / `start()` مبيرجعوش غير لما كل الخطوات تخلص — `PosTerminal` بيقرا نتيجة الـ init والـ retry بعدهم على طول.
- لو معاملة جديدة بدأت والقديمة (اللي اتلغت) لسه واقفة جوه خطوة (host / PIN pad)، الجديدة بتستنى (`stepLock`) لحد ما القديمة تخلص، وبعدين خطواتها بتتنفّذ على الـ thread بتاعها.
- exception أو `Error` في `onXxx` → `finishError`، والـ stream يفضل شغّال.
- الـ subscription بتتفتح وتتقفل جوه `prepare()` / `start()`؛ أي `goToStep` بعد ما يرجعوا مبيعملش حاجة. `cancel()` بيعلّم الـ flag بس، عشان خطوة اتحطت في الـ queue قبل الـ cancel تلحق تقفل الـ readers.

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
