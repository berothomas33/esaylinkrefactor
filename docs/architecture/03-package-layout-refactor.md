# Package Layout Refactor

**Branch:** `cursor/package-layout-refactor-a435`  
**PR:** https://github.com/berothomas33/esaylinkrefactor/pull/4  

---

## Goal

Arrange packages by responsibility across `core`, `emvflow`, `emvservice`, and `emvbase`. Behavior unchanged - structure and import cleanup.

---

## `:core` -> `com.emvenhance.core.*`

| Package | Contents |
|---------|----------|
| `terminal` | `PosTerminal`, `EmvBehavior` |
| `engine` | `EmvEngine` |
| `card` | `EntryMethod`, `CardPresence`, `CardSearchListener`, `TransactionConfig` |
| `event` | `TransactionStep(Event)`, `EmvStep(Event)` |
| `host` | `CommunicationBehavior`, `PrinterBehavior`, `AuthResult`, `HostDefaults` |

---

## `:emvflow` -> `com.emvenhance.emvflow.*`

| Package | Contents |
|---------|----------|
| `runtime` | `EmvFlowRuntime` (DAL / WMRouter lazy init) |
| `preprocess` | `EmvPreProcessFacade` |
| `progress` | `EmvStepProgress` |
| `device` | `EmvDeviceImpl`, cipher mode |
| `pin` | `IPinTask` |

---

## `:emvservice:export` -> `com.pax.emvservice.export.*`

| Package | Contents |
|---------|----------|
| `api` | `IEmvBase`, `IEmvCallback`, `IEmvCardInfoService`, ... |
| `contact` / `contactless` | Contact / CLSS service + result listeners |
| `mag` / `manual` | Magstripe / manual entry APIs |
| `pin` / `version` / `constant` / `exceptions` | Supporting APIs |

Router keys unchanged (still string keys + interface class).

---

## `:emvservice:emv` -> `com.pax.emvservice.emv.*`

| Package | Contents |
|---------|----------|
| `init` | `EmvInit` |
| `contact` / `contactless` / `mag` / `manual` / `pin` / `version` | Concrete Router services |

---

## `:emvbase` -> `com.pax.emvbase.*`

Already structured; documented with package-info:

- `constant` - tags / constants  
- `param` - AID / CAPK / terminal params (common / contact / clss)  
- `process` - contact / contactless callbacks and results  
- `utils` - helpers  

---

## `:app` vendors

```
vendor.TerminalFactory
vendor.pax / vendor.fake / vendor.ingenico
```

---

## Verification

`:core`, `:emvflow`, `:emvservice:*`, `:emvlib`, `:app` compile OK.
