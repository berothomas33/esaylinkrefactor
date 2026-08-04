# EmvEnhanceRefactor — multi-vendor EMV / POS enhance

## Architecture

```text
UI
  → PosTerminal.searchCard(config, CardSearchListener)   // vendor-owned readers
       → EmvBehavior.start(engine, config, CardPresence) // vendor-owned EMV
            → EmvEngine (subjects + notify* → behavior.dispatch*)

  Vendors:
    PaxTerminal      → PaxEmvBehavior      (Neptune DAL + PAX kernels)
    IngenicoTerminal → IngenicoEmvBehavior (stub until Tetra/Axium SDK attached)
    FakeTerminal     → FakeEmvBehavior     (UI / phone demo)
```

Card detection is a `PosTerminal` responsibility. Adding a vendor only requires a
vendor `PosTerminal` + matching `EmvBehavior` — no core engine / orchestration changes.

## Select vendor

In `app/build.gradle` / BuildConfig:

| `VENDOR` | Adapter |
|----------|---------|
| `FAKE` | phone / UI demo (default debug) |
| `PAX` | Neptune DAL search + PAX EMV kernels (default release) |
| `INGENICO` | stub — Tetra/Axium SDK not in this repo |

Gradle example: `./gradlew :app:assembleDebug -PVENDOR=PAX`

## 7 EMV steps (`EmvStep`)

1. Application selection  
2. Initiate application (GPO)  
3. Read application data  
4. Offline auth + restrictions  
5. Cardholder verification (CVM)  
6. Terminal risk + action analysis  
7. Online / completion (+ **print receipt**)

## State vs events

- **StateFlow / BehaviorSubject** → `TransUiState` (step, PAN, result)  
- **SharedFlow / PublishSubject** → `EmvEvent` (dialogs, print, 2nd tap, step toast)

## Modules

- `:core` — ports, steps, POS behaviors, facade (no vendor SDKs)  
- `:emvflow` — PAX Contact/Contactless runners + EmvDeviceImpl  
- PAX stack — emvbase, emvlib, emvservice, poslib, …  
- `:app` — UI + adapters
