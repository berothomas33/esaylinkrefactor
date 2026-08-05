# PosTerminal-Centric Architecture Refactor

**Branch:** `cursor/full-posterminal-refactor-a435` / continued in package-layout  
**PR:** https://github.com/berothomas33/esaylinkrefactor/pull/3  

---

## Goal

Make **one `PosTerminal` API work with any card on any vendor**.  
UI and core never import Pax / Ingenico / Fake types.

---

## End-state architecture

```
UI -> PosTerminal.acceptCard() / startTransaction()
       ├── EmvBehavior.prepare()
       ├── searchCard(config, CardSearchListener)   <- vendor readers
       └── EmvBehavior.start(engine, config, card)  <- vendor EMV

EmvEngine - thin subjects + notify* -> behavior.dispatch*
```

| Layer | Owns |
|-------|------|
| `PosTerminal` | Device init, unified card search, cancel, host, printer |
| `EmvBehavior` | EMV after entry method selected |
| `EmvEngine` | Reactive events for UI (no business logic) |

---

## What changed

### Unified card search

- Added `EntryMethod`, `CardSearchListener`, `CardPresence`
- Removed `CardReader`
- `PosTerminal` orchestrates: `prepare -> searchCard(listener) -> behavior.start(card)`
- Vendor terminals fire listener events from native SDKs

### Card search listener events

- Search started
- Chip (ICC) detected
- Contactless (NFC/PICC) detected
- Magstripe detected
- Manual entry selected
- Card removed
- Search timeout / cancelled
- Reader error

### Vendor layout

```
vendor/
  TerminalFactory.java
  pax/       PaxTerminal, PaxEmvBehavior, PaxKernel
  fake/      FakeTerminal, FakeEmvBehavior
  ingenico/  IngenicoTerminal, IngenicoEmvBehavior (stub)
```

### UI

- Primary action: **Present card (any)** -> `PosTerminal.acceptCard()` (`Mode.ANY`)
- Optional chip-only / contactless-only
- Cancel

### Removed layers

- Per-vendor host/printer stub classes (later moved to PosTerminal ownership)
- Router service lookup from app vendor façade
- Contactless/Contact runners from app path
- Callback adapters

---

## Adding a vendor

1. Implement `XxxTerminal extends PosTerminal` (`searchCard`, `cancelCardSearch`)
2. Implement `XxxEmvBehavior implements EmvBehavior`
3. One line in `TerminalFactory`
4. No core / engine / UI changes

---

## Ingenico note

No Ingenico SDK was attached in the workspace. Stubs demonstrate the extension pattern; replace with Tetra/Axium when available. Wire with `-PVENDOR=INGENICO`.

---

## Verification

`:app:compileDebugJavaWithJavac` succeeded for `FAKE`, `INGENICO`, and `PAX`.
