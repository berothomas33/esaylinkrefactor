# EMV step methods on EmvBehavior

**Approach:** Template Method — every EMV phase is a method on {@code EmvBehavior}.
Vendors extend {@code AbstractEmvBehavior} and override only the steps they own.

## Why this (not one class per step)

Vendors already extend one `EmvBehavior`. Putting `onApplicationSelection()`,
`onReadApplicationData()`, … on that type means each vendor writes its action
in its own step method — no registry, no 17 separate classes, no giant switch
inside `start()`.

## Contract

```text
PosTerminal
  → prepare()  → onTerminalInitialization()
  → searchCard()          (PosTerminal / SEARCH_CARD)
  → start()
       → onSearchCard()   (default Skip)
       → onApplicationSelection()
       → onWaitApplicationSelection()
       → onFinalApplicationSelection()
       → onReadApplicationData()
       → onSetTransactionData()
       → onOfflineDataAuthentication()
       → onProcessRestrictions()
       → onCardholderVerification()
       → onOfflinePinVerification()
       → onTerminalRiskManagement()
       → onTerminalActionAnalysis()
       → onStartOnlineProcess()
       → onIssuerAuthentication()
       → onScriptProcessing()
       → onTransactionCompletion()
```

Each method returns {@code EmvStepResult}: Continue / Skip / Fail / Approved / Declined.

## Classes

| Type | Role |
|------|------|
| `EmvBehavior` | Declares lifecycle + all `onXxx` step hooks (defaults = Continue/Skip) |
| `EmvStepResult` | Step outcome |
| `AbstractEmvBehavior` | Runs steps in order; vendors override `onXxx` |
| `FakeEmvBehavior` | Demo overrides per step |
| `IngenicoEmvBehavior` | Stub overrides; ready for SDK |
| `PaxEmvBehavior` | Still kernel-callback `start()`; can migrate hooks later |

## Vendor recipe

```java
public class MyVendorEmvBehavior extends AbstractEmvBehavior {
    @Override
    public EmvStepResult onApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence card) {
        // vendor-specific AID / PPSE work
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onStartOnlineProcess(EmvEngine e, TransactionConfig c, CardPresence card) {
        AuthResult auth = e.authorize(c);
        // store auth…
        return EmvStepResult.continueResult();
    }
}
```
