# EmvEnhanceRefactor

Vendor-agnostic POS EMV: **one `PosTerminal` API for any card / any vendor**.

## Architecture

```text
UI  →  PosTerminal.acceptCard() / startTransaction()
         ├── EmvBehavior.prepare()
         ├── searchCard(config, CardSearchListener)   ← vendor readers
         └── EmvBehavior.start(engine, config, card)  ← vendor EMV

EmvEngine — thin subjects + notify* → behavior.dispatch*
```

| Layer | Owns |
|-------|------|
| `PosTerminal` | Device init, unified card search, cancel |
| `EmvBehavior` | EMV after entry method selected |
| `EmvEngine` | Reactive events for UI (no business logic) |

Adding a vendor = implement `PosTerminal` + `EmvBehavior` only. No core/UI changes.

## Vendors

```text
TerminalFactory.create(VENDOR)
  PAX      → vendor.pax.PaxTerminal      → PaxEmvBehavior
  INGENICO → vendor.ingenico.IngenicoTerminal → IngenicoEmvBehavior (stub)
  FAKE     → vendor.fake.FakeTerminal    → FakeEmvBehavior
```

Gradle: `./gradlew :app:assembleDebug -PVENDOR=PAX`

| `VENDOR` | Notes |
|----------|-------|
| `FAKE` | Phone / UI demo (default debug) |
| `PAX` | Neptune DAL search + PAX kernels (default release) |
| `INGENICO` | Stub until Tetra/Axium SDK is attached |

## Card search events

`CardSearchListener`: started · chip · contactless · mag · manual · removed · timeout · cancelled · error.

Selected `EntryMethod` is carried by `CardPresence` into `EmvBehavior.start`.

## Modules

- `:core` — `PosTerminal`, `EmvEngine`, `EmvBehavior`, card/host types (no vendor SDKs)
- `:emvflow` — PAX runtime helpers (DAL init, preprocess, device, step gap-fill)
- `:app` — UI + `TerminalFactory` + vendor packages
- PAX stack — `emvbase`, `emvlib`, `emvservice`, `poslib`, …
