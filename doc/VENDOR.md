# VENDOR.md — what a vendor is, and how to add one

A **vendor** is one POS hardware family (PAX today; Ingenico and Fake as the other two). Each
vendor is its own Gradle module (`:vendor-pax`, `:vendor-ingenico`, `:vendor-fake`). `core` and
`app/src/main` never import a vendor class (rule 14): the app reaches the vendor only through the
seam below, and the vendor gives data — the screens are the app's.

```
app (src/main: every screen)  ──►  core (PosTerminal, AbstractEmvBehavior, …)
        │ flavor picks one                         ▲ extends / implements
        ▼                                           │
   :vendor-xyz  ────────────────────────────────────┘
```

---

## 1. What a vendor must have (the compiler checks it)

| Class | Why it is required | If it's missing |
|---|---|---|
| `com.emvenhance.vendor.TerminalFactory` | **By name.** `EmvEnhanceApp` calls `TerminalFactory.create(this)`. Same package and class name in every vendor — no interface | the flavor's build fails: `cannot find symbol TerminalFactory` |
| `com.emvenhance.vendor.VendorApplication` | **By name.** `EmvEnhanceApp extends VendorApplication`. PAX: `extends BaseApplication` (the PAX SDK reads `BaseApplication.getAppContext()`); others: `extends Application` | the flavor's build fails: `cannot find symbol VendorApplication` |
| a `PosTerminal` subclass (`PaxTerminal`) | **By inheritance.** `create()` returns a `PosTerminal`, which is abstract: `searchCard`, `cancelCardSearch` | can't instantiate `PosTerminal`; the compiler asks for both methods |
| an `AbstractEmvBehavior` subclass (`PaxEmvBehavior`) | **By inheritance.** `PosTerminal`'s constructor takes the behavior and runs it for each attempt (`prepare` / `start`, handed the attempt's `Transaction`). The EMV engine is `PosTerminal`'s; the behavior reads it with `engine()`, which goes through that `Transaction`: 16 step hooks + `readIccTag` | the compiler asks for all 17 |

Not required as a class: the **printer**. `PosTerminal`'s constructor needs a `PrinterBehavior`
object — `HostDefaults.logPrinter("Xyz")` is enough (Fake does that). A real printer is a class
`implements PrinterBehavior` (`PaxPrinter`). The **host** is a `CommunicationBehavior` object
too: `SaleCommunicationBehavior` (`:network`) for the real host.

## 2. What a vendor may add (defaults when it doesn't)

| What | Contract (core) | Default |
|---|---|---|
| Application choice dialog | `extends AbstractAppSelectDialog`: `show(labels)`, `dismiss()`; the base does the wait, the 30 s timeout, the cancel, `NO_ANSWER` | no choice screen |
| PIN pad | `extends AbstractPinPadBehavior` (`PinPadBehavior`) | no PIN pad |
| EMV parameters screen | `implements EmvParams`, returned by `PosTerminal.emvParams()` | `null`: the EMV params buttons are hidden |
| Serial number | override `PosTerminal.serialNumber()` | `""` |
| Hardware permissions | override `requiredPermissions()` (the home screen asks for them) | none |
| Country / currency | override `countries()` / `setCountry()` | empty: the country button is hidden |
| Start-up work | override `initializeVendor()` (runs in `PosTerminal`'s constructor) | nothing |
| Host timeout | override `onlineTimeoutMs()` | 90 s |

## 3. What stays inside the vendor (no core contract)

Anything only that vendor's kernel understands: PAX's `PaxTransactionState` (what one attempt
learns between EMV stages), `PaxKernel` (the SDK wrapper), `PaxHardwarePermissions`. Each vendor
organizes its own; a core base for them would be an empty layer (rule 5).

---

## 4. How the pieces work

**The transaction (`PosTerminal`, core — the vendor doesn't touch it).** One transaction thread;
`startTransaction` → `process`: TRANSACTION_STARTED, then per attempt `prepare` (terminal
initialization) → `searchCard` → EMV; a retry is the next turn of the loop; the end is published
once, after the terminal is free. The vendor never starts threads for it, never decides busy or
cancel.

**`searchCard(config, listener)`** — on the transaction thread, may block:
- call `listener.onSearchStarted(config)`, then poll the reader;
- report **exactly one** of `onChipDetected` / `onContactlessDetected` / `onMagstripeDetected` /
  `onManualEntrySelected` / `onSearchTimeout` / `onSearchCancelled` / `onReaderError`, then return;
- stop when `isSearchCancelled()` turns true. `cancelCardSearch()` is called from the UI thread
  to stop the polling.

**The EMV steps (`AbstractEmvBehavior`).** The 16 hooks are the EMV stages in EMV Book 3 order.
Each hook does **its own kernel call** (rule 1) and moves on with `goToStep(next)`; the kernel's
real order wins (rule 2). A kernel that runs everything in one call (atomic) does it in one hook
and leaves the others empty with a comment (rule 11). Tools the base gives:

| Tool | For |
|---|---|
| `goToStep(step)` | next stage; returns `false` once the transaction ended (a cancel) — release what you hold |
| `publish(event)` | a transaction step the screen shows (`CARD_READ`, `APPLICATION_SELECTED`, `CARDHOLDER_VERIFIED`…), on the attempt's `Transaction`; dropped once it ended |
| `askStep(step, detail, failClosed)` / `answerStep(value)` | a kernel callback that needs the cardholder (app choice, PIN): the step method answers; no answer → `failClosed` |
| `authorizeOnline()` | send the sale to the host (builds field 55 from `readIccTag`) |
| `finishApproved` / `finishDeclined` / `finishError` | end the transaction with its outcome (an APPROVED / DECLINED event of your own carries your audit data) |
| `isSynchronousEntry(card)` | magstripe / manual (no chip session) |
| `isRunning()` / `isCancelled()` | a wait of your own that must give up once the transaction ended; a result that must not be reported after a cancel |

Kernel callbacks (rule 12): thin, never `suspend` / Rx, and on every path — exceptions included —
they answer the kernel with a fail-closed value. Nothing is approved without a host answer
(rule 4).

---

## 5. Adding vendor `xyz`, step by step

1. **Module.** Copy `vendor-fake/` to `vendor-xyz/`; in `vendor-xyz/build.gradle` set
   `namespace 'com.emvenhance.vendor.xyz'`, keep `api project(':core')`, add the SDK
   (`implementation files('libs/xyz-sdk.aar')`) and `implementation project(':network')` for the
   real host.
2. **`settings.gradle`:** `include ':vendor-xyz'`.
3. **`app/build.gradle`:** a flavor in the `vendor` dimension and its dependency:
   ```gradle
   xyz { dimension "vendor"; applicationIdSuffix ".xyz"; buildConfigField 'String', 'VENDOR', '"XYZ"' }
   xyzImplementation project(':vendor-xyz')
   ```
4. **The four required classes** (package `com.emvenhance.vendor` for the first two):
   ```java
   public final class TerminalFactory {
       private TerminalFactory() { }
       public static PosTerminal create(Application application) { return new XyzTerminal(); }
   }

   public class VendorApplication extends Application { }   // or the SDK's Application

   public class XyzTerminal extends PosTerminal {
       public XyzTerminal() {
           super(new XyzEmvBehavior(), new SaleCommunicationBehavior(/* context, serial */),
                   HostDefaults.logPrinter("XyzPrinter"));
       }
       @Override public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
           listener.onSearchStarted(config);
           while (!isSearchCancelled()) {
               // poll the XYZ reader; on a card: listener.onChipDetected(card); return card;
           }
           listener.onSearchCancelled();
           return null;
       }
       @Override protected void cancelCardSearch() { /* xyzSdk.stopSearch() */ }
   }

   public class XyzEmvBehavior extends AbstractEmvBehavior {
       // onTerminalInitialization … onTransactionCompletion: each stage's kernel call, then goToStep(next)
       // onStartOnlineProcess: lastAuth = authorizeOnline();
       // onTransactionCompletion: finishApproved / finishDeclined from lastAuth (no answer = declined)
       // readIccTag(tag): the kernel's TLV
   }
   ```
5. **Replace the fake data** with SDK calls, one stage at a time; build `xyzEnvtestDevDebug`.
6. **Then the optional parts** (§2): app choice, PIN pad, `EmvParams`, serial, permissions.

**Check before calling it done:** `fakeDebug` still builds (app/src/main has no vendor import);
nothing in `app/src/main` or `core` names `xyz`; a sale ends APPROVED / DECLINED / FAILED /
CANCELLED exactly once; Cancel during the search and during the host call ends CANCELLED.

The smallest complete example: `vendor-fake` (5 files). The full one: `vendor-pax`.
