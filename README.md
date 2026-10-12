# EmvEnhanceRefactor

Vendor-agnostic POS EMV: **one `PosTerminal` API for any card / any vendor**.

## Architecture

```text
UI  →  PosTerminal
         ├── prepare() → onTerminalInitialization()
         ├── searchCard()
         └── start()   → goToStep(APPLICATION_SELECTION)
                           vendor method → goToStep(next) → steps subject → dispatchStepMethod → next method
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
| *(root)* | `HostApiConnection` (Retrofit), `HostApiClient`; `HostCommunicationBehavior` — the `CommunicationBehavior` that talks to our host, routing by transaction type; `HostEnvelope` (TEK, exchange, AES envelope, `mToken`), `HostRequests` (request bodies per type), `HostResponses` (answer → `AuthResult`), `HostException`; see below |
| `env` | `EnvironmentProvider` — holds `BuildConfig.baseUrl` from `:app`'s "environment" flavor dimension |
| `model` | `GeneralRequest`/`GeneralResponse`/`DataModel` (the generic encrypted envelope, shared with `onboarding`) and `ExchangeRequest`/`ExchangeResponse`/`SaleRequest`/`RefundRequest`/`SaleExtraData`/`SaleResponse` (the payloads that travel inside that envelope) |
| `tlv` | `BerTlv`, `IssuerResponseFields` — minimal BER-TLV reader plus the ARPC/issuer-script tag extraction `HostResponses` uses |
| `onboarding` | `OnboardingClient`, `OnboardingApiConnection`, `OnboardingState`, `ChallengeGenerator`, `OnboardingException` — the offline (pair terminal) and online (per-bank TMK provisioning) onboarding cycles; see `onboarding.model` for the six endpoints' request/response shapes |
| `crypto` | `RsaPublicKeyEncryptor` — RSA-wraps a symmetric session key (online PIN key or transaction key) with the host's public key from onboarding, so the host can independently recover it; `AesEnvelopeCrypto` — AES-encrypts/decrypts the `encSerializedRequest`/`encSerializedResponse` body under that key. See `app/src/pax/.../PaxEmvBehavior#provisionOnlinePinKey` for the PIN-key use, `HostEnvelope` for the transaction-key use |

`HostApiConnection.emvFileDownload` + `EmvParamDownloadClient` fetch the EMV/CLSS parameter
package as raw bytes only — see `:bizentity` below for where those bytes get unzipped, parsed,
and applied.

`HostCommunicationBehavior` is wired into `PaxTerminal`. For each transaction it opens a
`HostEnvelope` session (a fresh TEK, RSA-wrapped with the host's public key from onboarding, and an
`asyncRequestId`), calls `orchestration/exchange` (TEK, plus the online PIN key when one was
collected), then the request for the transaction's type: `orchestration/sale` or
`orchestration/refund`. Any other type, or a refund without the original reference number, is
online failed without contacting the host. Bodies are built by `HostRequests` from
`TransactionConfig#getEmvResult()`, the `EmvTransactionResult` the vendor `EmvBehavior` assembles
right before going online (see `PaxEmvBehavior#buildEmvTransactionResult`). Headers carry a bearer
token from `OnboardingClient#ensureValidAccessToken`, refreshed when near expiry, and `mToken`
from `HostSettings`. The plain request and response JSON is logged in debug builds under the
`HostComm` Logcat tag. Several request fields (the CVM code, the per-issuer `cardType`/`dcc`
values) are reconstructions flagged in `HostRequests` and the model classes' javadoc.

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

`app/src/pax/.../ui/EmvParamActivity` is that caller — the EMV-param category browser screen also
has a "Download EMV params" button above the browser (shows `EmvParamUpdateResult.summarize()`).
It has no credentials UI of its own: its `Account-Id`/bearer-token headers come from
`OnboardingState#getAccountId`/`#getToken`, entered on the separate `app/src/pax/.../ui/OnboardingActivity`
screen — Account ID / Token fields plus a single "Start onboarding" button
(runs `OnboardingClient#runOfflineCycle`, shows success or the failure message), saving the
credentials on every successful run. Neither screen is wired into app startup — both are
debug/setup actions a technician triggers, reachable from `MainActivity`'s debug panel. Same
placeholder header convention as before (`Account-Id` verbatim, `Token` as a bearer
`Authorization` header), since neither endpoint's real header contract is known yet (see
`OnboardingActivity#buildHeaders`).

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
