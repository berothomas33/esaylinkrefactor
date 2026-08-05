# EmvBehavior vs Host/Printer Ownership

**Branch:** `cursor/package-layout-refactor-a435`  
**Related commits:** Remove AbstractEmvBehavior; Own Communication/Printer on PosTerminal  

---

## Problem

`AbstractEmvBehavior` held `CommunicationBehavior` and `PrinterBehavior` and auto-ran:

- `onOnlineRequired` -> host authorize  
- `onApproved` -> print receipt  

That mixed **EMV lifecycle** with **host/printer side services**. The class name `EmvBehavior` did not match those jobs.

---

## Decision

### Removed

- `AbstractEmvBehavior` entirely  
- Auto host authorize / auto print from EMV layer  
- Temporary removal of unused host stubs (later restored on PosTerminal)

### Correct ownership

```
PosTerminal
  ├── CommunicationBehavior  -> authorize()
  ├── PrinterBehavior        -> printReceipt()
  ├── searchCard(...)
  └── EmvBehavior            -> EMV only
```

### Rules

1. **`EmvBehavior`** = vendor EMV after entry selection (prepare / start / cancel)  
2. **`PosTerminal`** = owns readers, host, printer  
3. Vendors may use the **same** `CommunicationBehavior` instance for online  
4. Printer stays on the terminal - not inside EmvBehavior  

---

## Why this is better

| Concern | Wrong place | Right place |
|---------|-------------|-------------|
| Card search | Engine / EmvBehavior | PosTerminal |
| Host authorize | AbstractEmvBehavior | PosTerminal |
| Receipt print | AbstractEmvBehavior.onApproved | PosTerminal.printReceipt |
| EMV kernel flow | - | EmvBehavior |

---

## Current wiring

```java
protected PosTerminal(
    EmvEngine engine,
    EmvBehavior behavior,
    CommunicationBehavior communication,
    PrinterBehavior printer)
```

Public helpers:

- `authorize(TransactionConfig)`  
- `printReceipt(List<String>)`  
- `communication()` / `printer()`  

Vendor example:

```
PaxTerminal
  -> HostDefaults.declineUntilWired() + logPrinter
  -> PaxEmvBehavior(kernel, communication)  // same communication instance
```
