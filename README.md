# EmvEnhanceRefactor — multi-vendor EMV / POS enhance

## Architecture

```text
UI (Coroutines / RxJava)
  → PosEmvFacade          (:core)
      → EmvStepEngine     (7 EMV steps + print / 2nd tap / remove card)
      → DevicePorts
           ├─ FakeDeviceFactory
           ├─ PaxDeviceFactory      (:emvflow + PAX libs)
           ├─ IngenicoDeviceFactory (stub)
           └─ SunmiDeviceFactory    (stub)
```

## Select vendor

In `app/build.gradle` / BuildConfig:

| `VENDOR` | Adapter |
|----------|---------|
| `FAKE` | phone / UI demo (default debug) |
| `PAX` | real Router EMV via `:emvflow` (default release) |
| `INGENICO` | stub — wire Tetra/Axium later |
| `SUNMI` | stub — wire Sunmi Pay later |

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
