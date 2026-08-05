# Design Q&A and Layer Summary

Extracted from conversation answers on the EmvEnhance refactor.

---

## Q1 - What enhanced the layer model?

**PosTerminal** is the only public API - it owns card search for every vendor.

**EmvBehavior** runs EMV only after an entry method is chosen (chip / tap / swipe / manual).

**EmvEngine** is just an event bus - no business logic.

Vendors live in separate packages behind **TerminalFactory**; unused host stubs, adapters, and reporters were removed from the wrong layers.

---

## Q2 - Why were CommunicationBehavior and PrinterBehavior inside AbstractEmvBehavior?

They were there for convenience: shared defaults for online authorize and print after EMV steps.

They did **not** match the job description of `EmvBehavior` (vendor EMV lifecycle). Host and printer are side services. Correct place: **PosTerminal**.

---

## Q3 - Do we need AbstractEmvBehavior online/print methods?

No. Those methods were removed:

- `onOnlineRequired` auto-authorize  
- `onApproved` -> print  
- `deliverOnlineResult` / `buildReceipt` abstract helpers  

`AbstractEmvBehavior` was deleted. Vendors implement `EmvBehavior` directly.

---

## Q4 - Where do CommunicationBehavior and PrinterBehavior go?

In **abstract `PosTerminal`**:

```
PosTerminal
  ├── CommunicationBehavior
  ├── PrinterBehavior
  ├── searchCard(...)
  └── EmvBehavior (EMV only)
```

---

## Q5 - What is CardSearchListener.Adapter?

A **convenience no-op base** for `CardSearchListener`.

The interface has many methods. `Adapter` implements all as empty so a caller can override only what they care about.

In this project it is **unused** - `PosTerminal` uses a full `EngineReportingListener`. Safe to delete if desired.

---

## Final layer map

| Layer | Role |
|-------|------|
| UI | Display; calls `acceptCard` / cancel |
| PosTerminal | Search, host, printer, orchestration |
| EmvBehavior | Vendor EMV after entry selected |
| EmvEngine | Subjects + notify/dispatch |
| TerminalFactory | Vendor selection only |
| emvflow | PAX runtime helpers (not core) |

---

## Branches / PRs produced

| Branch | Focus | PR |
|--------|-------|----|
| `cursor/pax-emv-behavior-bridge-a435` | PaxEmvBehavior bridge, searchCard listener | #2 |
| `cursor/full-posterminal-refactor-a435` | Full PosTerminal-centric refactor | #3 |
| `cursor/package-layout-refactor-a435` | Packages + host on PosTerminal | #4 |
| `cursor/transaction-step-dispatch-4c01` | Earlier step-dispatcher (Branch N baseline) | base |
