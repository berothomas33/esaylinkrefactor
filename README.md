# EmvEnhanceRefactor

Vendor-agnostic POS EMV: **one `PosTerminal` API for any card / any vendor**.

## Architecture

```text
UI  →  PosTerminal
         ├── prepare() → onTerminalInitialization()
         ├── searchCard()
         └── start()   → goToStep(APPLICATION_SELECTION)
                           vendor method → goToStep(next) → EmvStep observable → next method
                           …
                           finishApproved() / finishDeclined()
```

Each vendor overrides step methods and advances with `goToStep(EmvStep)`.
See [`doc/architecture/emv-step-methods-on-behavior.md`](doc/architecture/emv-step-methods-on-behavior.md).

## Package layout

### `:core` — `com.emvenhance.core.*`

| Package | Contents |
|---------|----------|
| `terminal` | `PosTerminal` (owns host + printer), `EmvBehavior` (EMV only) |
| `engine` | `EmvEngine` |
| `card` | `EntryMethod`, `CardPresence`, `CardSearchListener`, `TransactionConfig` |
| `event` | `TransactionStep(Event)`, `EmvStep(Event)` |
| `host` | `CommunicationBehavior`, `PrinterBehavior`, `AuthResult` — owned by PosTerminal |

### `:network` — `com.emvenhance.network.*`

| Package | Contents |
|---------|----------|
| *(root)* | `HostApiConnection` (Retrofit), `HostApiClient`, `RetrofitCommunicationBehavior` — real `CommunicationBehavior` impl, online purchase + 2nd GAC issuer data |
| `env` | `EnvironmentProvider` — holds `BuildConfig.baseUrl` from `:app`'s "environment" flavor dimension |
| `model` | `PurchaseRequest`, `PurchaseResponse`, `GeneralResponse`, `DataModel` — the last two are the generic encrypted-envelope shape shared with `onboarding` |
| `tlv` | `BerTlv` — minimal BER-TLV reader for pulling ARPC/issuer-script tags out of the response |
| `onboarding` | `OnboardingClient`, `OnboardingApiConnection`, `OnboardingState`, `ChallengeGenerator`, `OnboardingException` — the offline (pair terminal) and online (per-bank TMK provisioning) onboarding cycles; see `onboarding.model` for the six endpoints' request/response shapes |

`HostApiConnection.emvFileDownload` + `EmvParamDownloadClient` fetch the EMV/CLSS parameter
package as raw bytes only — see `:bizentity` below for where those bytes get unzipped, parsed,
and applied.

### `:bizentity` — `com.pax.configservice.*`, `com.pax.bizentity.*` (PAX-only)

`ConfigInit` seeds the EMV/CLSS GreenDAO tables once, at first boot, from the bundled JSON assets
under `src/main/assets` (`contactAid.json`, `capk.json`, `paypass.json`, …) — those stay in place
as the offline first-boot fallback. Downloading a fresh parameter package and re-applying it later
is a separate, repeatable path:

| Package | Contents |
|---------|----------|
| `configservice.xml` | `EmvXmlParamParser` (contact: `emv_param.emv` → AID/CAPK/revocation, with the ICS/CARDSCHEME profile join `contactAid.json`'s per-AID fields need), `ClssXmlParamParser` (contactless: `clss_param.clss` → PayPass/PayWave/Amex — DPAS/EFT/JCB/MIR/PBOC/PURE/RUPAY aren't covered, see its javadoc) |
| `configservice.impl.EmvParamUpdater` | Unzips a downloaded package, parses each file, and re-applies it through the same `EmvParamService` insert methods `ConfigInit` uses — clearing each affected table first so a repeat download replaces rather than accumulates |

`app/src/pax/.../PaxEmvParamUpdateService` wires the two together (`EmvParamDownloadClient` for
the bytes, `EmvParamUpdater` to apply them), returning `EmvParamUpdateResult` (which sections
applied, with row counts, or the first error) rather than a bare boolean so a caller can show what
actually happened.

`app/src/pax/.../ui/EmvParamActivity` is that caller — the existing EMV-param category browser
screen now also has an Account ID / Token credentials section feeding two actions above the
browser: "Download EMV params" (shows `EmvParamUpdateResult.summarize()`) and "Start onboarding"
(runs `OnboardingClient#runOfflineCycle`, shows success or the failure message). Neither is wired
into app startup — both are debug/setup actions a technician triggers from this screen. The two
credentials fields are reused as-is for both actions' request headers (`Account-Id`, and `Token`
as a bearer `Authorization` header) — a placeholder convention, since neither endpoint's real
header contract is known yet (see `EmvParamActivity#buildHeaders`).

**No reference parser was available** to build `EmvXmlParamParser`/`ClssXmlParamParser` against —
their javadoc flags every field mapping that's a best-effort reconstruction (tag names, the
ICS/CARDSCHEME join, and a handful of `EmvAid` fields — `onlinePin`/`priority`/`randTransSel`/
`velocityCheck`/`floorLimitCheckFlg` — with no source tag in the sample file at all) rather than
a verified one. Review against real EMV certification testing before relying on this for a live
update.

### `:emvflow` — `com.emvenhance.emvflow.*`

| Package | Contents |
|---------|----------|
| `runtime` | `EmvFlowRuntime` (DAL / WMRouter lazy init) |
| `preprocess` | `EmvPreProcessFacade` |
| `progress` | `EmvStepProgress` |
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

PAX card readers (chip / mag / contactless / PIN) need Neptune Lite permissions in
the **pax** flavor manifest (`app/src/pax/AndroidManifest.xml`):

```xml
<uses-permission android:name="com.pax.permission.ICC" />
<uses-permission android:name="com.pax.permission.PICC" />
<uses-permission android:name="com.pax.permission.MAGCARD" />
<uses-permission android:name="com.pax.permission.PED" />
<uses-permission android:name="com.pax.permission.PRINTER" />
```

Missing `ICC` is `IccDevException ICC#101` / `IccException: Not Permission for icc` on
`icc.close()`. Rebuild and reinstall the **pax** APK after adding them. If they stay
denied on the device, install via PAXSTORE / a PAX-signed package — PayDroid treats
these as signature permissions on some firmware.

## Card search events

`CardSearchListener`: started · chip · contactless · mag · manual · removed · timeout · cancelled · error.
