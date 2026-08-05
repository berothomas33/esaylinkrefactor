# EmvEnhanceRefactor

Vendor-agnostic POS EMV: **one `PosTerminal` API for any card / any vendor**.

## Architecture

```text
UI  →  PosTerminal.acceptCard() / startTransaction()
         ├── EmvBehavior.prepare()     → EmvEngine.prepareFlow (TERMINAL_INITIALIZATION)
         ├── searchCard(...)           ← vendor readers
         └── EmvBehavior.start(card)   → EmvEngine.runFlow (SEARCH_CARD → … → COMPLETION)

EmvEngine — orchestrator (registry + transition policy + wait/resume) + Rx event bus
EmvStepBehavior — one class per EmvStep (execute / onSignal)
```

**Behavior-driven design:** see
[`doc/architecture/emv-behavior-driven-architecture.md`](doc/architecture/emv-behavior-driven-architecture.md).

Fake vendor is the first vertical slice (`FakeEmvBehavior` + `FakeKernelPort` +
`StandardEmvBehaviors`). PAX still uses the legacy `PaxEmvBehavior` god-object
until adapted onto `EmvKernelPort`.

## Package layout

### `:core` — `com.emvenhance.core.*`

| Package | Contents |
|---------|----------|
| `terminal` | `PosTerminal` (owns host + printer), `EmvBehavior` (EMV only) |
| `engine` | `EmvEngine` (orchestrator), `EmvContext`, `EnginePhase` |
| `behavior` | `EmvStepBehavior`, `BehaviorResult`, `BehaviorBridge`, registry/policy interfaces |
| `port` | `EmvKernelPort`, `EmvInteractionPort`, `EmvSignal` |
| `card` | `EntryMethod`, `CardPresence`, `CardSearchListener`, `TransactionConfig` |
| `event` | `TransactionStep(Event)`, `EmvStep(Event)` |
| `host` | `CommunicationBehavior`, `PrinterBehavior`, `AuthResult` — owned by PosTerminal |

### `:emvflow` — `com.emvenhance.emvflow.*`

| Package | Contents |
|---------|----------|
| `behavior` | One `*Behavior` per `EmvStep` + `StandardEmvBehaviors` |
| `registry` | `DefaultEmvBehaviorRegistry` |
| `policy` | `DefaultEmvTransitionPolicy` |
| `scheduler` | `EmvScheduler` |
| `cleanup` | `DefaultEmvCleanupHandler` |
| `runtime` | `EmvFlowRuntime` (DAL / WMRouter lazy init) |
| `preprocess` | `EmvPreProcessFacade` |
| `progress` | `EmvStepProgress` (legacy gap-fill; retire after PAX adapter) |
| `device` | `EmvDeviceImpl`, cipher mode |
| `pin` | `IPinTask` |

### `:emvservice:export` — `com.pax.emvservice.export.*`

| Package | Contents |
|---------|----------|
| `api` | `IEmvBase`, `IEmvCallback`, `IEmvCardInfoService`, … |
| `contact` / `contactless` | Contact / CLSS service + result listeners |
| `mag` / `manual` | Magstripe / manual entry APIs |
| `pin` / `version` / `constant` / `exceptions` | Supporting APIs |

### `:emvservice:emv` — `com.pax.emvservice.emv.*`

| Package | Contents |
|---------|----------|
| `init` | `EmvInit` |
| `contact` / `contactless` / `mag` / `manual` / `pin` / `version` | Concrete Router services |

### `:emvbase` — `com.pax.emvbase.*` (unchanged roots)

`constant` · `param` (common/contact/clss) · `process` (contact/contactless/entity/enums) · `utils`

### `:app` — vendors

```text
vendor.TerminalFactory
vendor.pax / vendor.fake / vendor.ingenico
```

## Vendors

```text
TerminalFactory.create(VENDOR)
  PAX      → PaxTerminal → PaxEmvBehavior
  INGENICO → IngenicoTerminal → IngenicoEmvBehavior (stub)
  FAKE     → FakeTerminal → FakeEmvBehavior
```

Gradle: `./gradlew :app:assembleDebug -PVENDOR=PAX`

## Card search events

`CardSearchListener`: started · chip · contactless · mag · manual · removed · timeout · cancelled · error.
