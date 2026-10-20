# BACKLOG.md — what is missing, broken or risky (2026-10-20)

A whole-project gap scan on `rxTransactionDesign` (`0bee281`). Four read-only scans covered core +
UI, PAX, network, and params + build. Every item names a file and line that was read. Items marked
✔ were re-checked by hand; "unverified" means the code points to it but a device or spec is needed
to prove it. Nothing here is fixed yet. Read with `CLAUDE.md` (rules, §11 open list) and
`REFERENCE.md` (flows).

- **§11** = already in `CLAUDE.md` §11 (yes / partly / no).
- Severity: **H** money, card data, hang or crash · **M** wrong behavior or missing feature ·
  **L** hygiene.
- One experiment = one commit. Show the native call order for EMV fixes. Device-test after.

---

## 0. Do first (money, PCI, hangs)

| # | Sev | Where | What | §11 |
|---|---|---|---|---|
| 0.1 | H ✔ | `ui/SearchCardFragment`, `AmountFragment:87` (`addToBackStack`) | **Back does not cancel the sale**: no `OnBackPressedCallback`; the ViewModel lives on, the search / EMV continues, a card tapped now goes to the host with no screen | no |
| 0.2 | H ✔ | `SearchCardFragment:134-143,205-211`, `MainActivity:70`, `PaxEmvBehavior` `KEY_PAN` | **Full PAN shown and printed in release** (with TVR/TAC/IAC/field 55); only the APDU trace is `BuildConfig.DEBUG`-gated | no |
| 0.3 | H ✔ | `network/.../SaleResponse.java:19` + `SaleCommunicationBehavior.toAuthResult:497-506` | **Fails open**: `responseCode` is an `int` → missing field = 0 = APPROVED; approved RC always forced to "00" | no |
| 0.4 | H ✔ | `emvlib/dpas/.../ContactProcess.java` `certVerify` / `emvVerifyPINfailed` | No `setCallBackResult` → the native side blocks forever (javap: `cv.block()` no timeout) → terminal BUSY until restart | yes |
| 0.5 | H | `PosTerminal.authorize:110-122`, `PaxEmvBehavior:2274`, `ContactProcess:381-390`, `ClssPayWaveProcess:493-496` | **No reversal / no acknowledgement**: host timeout after send, host-approved + card AAC / ARPC fail / script fail / 2nd tap missed, cancel after approve. `AuthResult.getAsyncRequestId` has no caller; no endpoint in `HostApiConnection` | yes (triggers no) |
| 0.6 | H ✔ | `PaxEmvBehavior.checkContactlessResult:1059-1087`; `ClssPayPassProcess:185-197`, Mir :126, DPAS :155-172 | SEE_PHONE / TRY_AGAIN / TRY_ANOTHER_INTERFACE built with `ret == EMV_OK` fall into the OK branch → **decline**. Likely root of **#10 See Phone** on Mastercard phones | yes |
| 0.7 | H ✔ | `ContactProcess.resetParam:469` | `EMVGetMCKParam` where `EMVSetMCKParam` is meant → the selected AID's PIN-bypass never reaches the kernel; AID[0]'s applies to all | yes |
| 0.8 | H ✔ | `bizentity/.../EmvParamService.capkConvert:975-985`, `getCachedEmvParam:842` → `ContactProcess:88,116` | No revocations (or no AIDs) → list stays **null** → NPE in `preTransProcess` → every sale "Terminal initialization failed", contactless too | partly |
| 0.9 | H | `EmvParamUpdater:121-137,156-178`, `BaseDaoHelper:80-91` | `deleteAll` + `insert` not in one tx, insert errors swallowed, `changedDatabase()` ignores the delete → empty tables + stale cache → 0.8 after restart. Fix: one `runInTx`, throw, count deletes | yes (cache gap no) |
| 0.10 | H | `CapkRevokeBean.getKeyIDByte:95`, `EmvXmlParamParser:235` | Missing KeyID → `""` → `byte[0][0]` → AIOOBE on every reload → no transaction runs | no |
| 0.11 | H ✔ | `bizentity/build.gradle:15` (+ bizlib, emvbase), `DaoManager:52-62` | `RELEASE` hard-coded `false` → **DB never encrypted** (SQLCipher off), SQL value logging on in release; `DATABASE_PWD` silently a placeholder. Enabling needs migration / uninstall | no |
| 0.12 | H | `network/.../HostAppKeys.java:29-32,62-66` | Live mToken JWT + fixed app keys / accountId in source and git history (rotate them); `BuildConfig.MTOKEN_TEMP` read by nothing | partly |
| 0.13 | H | `HostApiClient:36-37`, `HostHeaders:47,64,106` | OkHttp `Level.BODY` + header logging **in every build**: bearer token, apiKey, mToken, SACS, envelope keys | yes |
| 0.14 | H | `PaxEmvBehavior.tryAgain:2321-2326` | Kernel-made `RESULT_TRY_AGAIN` has no cap → endless re-tap loop (JCB/RuPay/Mir/PayPass once 0.6 is fixed) | yes |

---

## 1. Payment safety / fail closed

| # | Sev | Where | What | §11 |
|---|---|---|---|---|
| 1.1 | M ✔ | `AppSelectDialog.selectBlocking:46-53` | Timeout / no Activity returns **index 0** = selects an app (rule 4 says `USER_CANCEL`) | no |
| 1.2 | M | `AppSelectDialog`, `PaxEmvBehavior:1898`, `PinService:47` | Cancel can't interrupt app-select (30 s) or online PIN (60 s); dialogs not dismissed on cancel | no |
| 1.3 | M | `PaxEmvBehavior:1868-1871`, `EmvDeviceImpl.pedVerifyPlainPin:234-271` | Each offline PIN request makes a new `PaxPinPad`; the previous is orphaned; a correct PIN never dismisses its pad | no |
| 1.4 | M, unverified | `PaxEmvBehavior:2221-2230`, `ClssPayWaveProcess:466` | Visa 2nd tap doesn't wait for a card: prompt, then `Clss_FinalSelect_Entry` at once → card-denied after host approve (form #12) | no |
| 1.5 | M | `SearchCardFragment:186-190,273-274` | Every ERROR shows "TRANSACTION DECLINED" — incl. "host didn't answer" → operator retries a sale that may be approved (with 0.5: double charge) | no |
| 1.6 | M | `PaxEmvBehavior:959-982` vs `CLAUDE.md §7` | A sale whose kernel gave **TC** goes to the host without ARQC and a host approve is accepted. `REFERENCE §4.4` = code; decide if that is the rule | no |
| 1.7 | M | `ContactProcess:288-307`, `ClssPayPassProcess:267-271` | CVM mapping: contact 0x03/0x05 (offline PIN + sig) → `hasPin=false`; PayPass CDCVM → `CVM_OFFLINE_PIN` → `hasPin=true` to the host | no |
| 1.8 | M, unverified | `PaxEmvBehavior:1985-1998` | Online PIN goes ahead with no host public key; missing TLK idx 0 shows as "cancelled"; PEK never wiped; `ped.setIntervalTime(1,1)` | no |
| 1.9 | M | `ContactProcess:118-120`, `PaxEmvBehavior:1701-1729` | No contact AIDs → `setEmvAndMCKParam` throws → whole `prepareKernel` fails (contactless too). Catch per kernel | no |
| 1.10 | M | `network/.../tlv/BerTlv:41,51`, `IssuerResponseFields:27-31` | TLV reader: ≤2-byte tags, 1-byte lengths; scripts as values only, first of each (PAX may want whole 71/72 TLVs — unverified) | no |
| 1.11 | M, unverified | `SaleCommunicationBehavior:500` → `PaxEmvBehavior:2503` | Decline RC `5` → "5" → 8A is 1 byte, not "05" | no |
| 1.12 | M | `FakeEmvBehavior:90,198-206`; `IngenicoTerminal:25` (`approveAlways`) | Fake approves offline / on `null` auth; **an Ingenico prod×live build approves every card** (no variant filter) | partly |
| 1.13 | L | `AbstractEmvBehavior.authorizeOnline:217-219` | Cancel returns `declined("17")` (host-shaped) instead of `failed` | no |
| 1.14 | L | `ClssMirProcess:299-308` | `while(true)` on TRY_AGAIN, no cap / wait / cancel (seed-only: download deletes Mir) | no |

## 2. Contactless kernels

| # | Sev | Where | What | §11 |
|---|---|---|---|---|
| 2.1 | M | `PaxEmvBehavior:145,1079,2234` | `clsLastNeedSeePhone` survives across transactions; only Amex/Mir/Pure read it → a Visa/MC See Phone skips `Clss_CoreInit_AE` on a later Amex tap; Mir/Pure first See Phone always declines | no |
| 2.2 | M | atomic kernels (Amex, JCB, Mir, PBOC, Pure, RuPay, DPAS) | No tests, no form rows; Pure TRY_AGAIN → RESELECT (deletes candidate); TODOs: PBOC torn (:71,:96), Mir/Pure/PayWave exception file; ODA never published | no |
| 2.3 | L, unverified | `PaxEmvBehavior:914-923` | `isNeedSecondTap=false` → `completeTransProcess` never runs for EFT/Amex/JCB/Pure/PBOC (REFERENCE §5.4 says EFT does) | no |
| 2.4 | L | `ClssProcess:237-246` | Atomic reselect loop can land on a granular kernel without its stages | no |
| 2.5 | M | `PaxEmvBehavior:2449`, `TagsTable:39` | TAC Denial read from DF8121 (PayPass-only) → empty/wrong for contact & PayWave (display only) | yes |
| 2.6 | — | #11 "tap again" | Code path looks right; no root cause. Suspect a return code outside {ICC_CMD_ERR, ICC_RESET_ERR, CLSS_TRY_AGAIN}, or no `PiccDevException` on a short tap — needs a device log | yes |

## 3. Parameters / database

| # | Sev | Where | What | §11 |
|---|---|---|---|---|
| 3.1 | M | `ConfigInit:51,60-71,125-137`, `PaxTerminal:107-108` | Seed on a fire-and-forget thread; flag set **before** it finishes; failures never retried; a sale can start mid-seed; `insertTerminalConfig` on a throwaway instance | partly |
| 3.2 | M | `VendorDebugPanel:17-21`, `activity_main.xml:108` | "Insert EMV param" visible in release: re-seeds demo JSON over host params, duplicates CAPKs/revocations, revives removed kernels (rule 7) | no |
| 3.3 | M | `assets/capk.json` | 88 CAPKs: 76 expired, 9 bad SHA-1, 8 duplicate (RID,index); no validator anywhere | yes |
| 3.4 | M | `EmvParamUpdater:120-132` vs `195-213` | Contact: missing section keeps the seed; contactless: missing = deleted. A clss file with no known section wipes all kernels | no |
| 3.5 | M | `XmlDomUtils:144-166`, parsers, `ConvertUtils:191-229` | Bad number → 0; bad hex (TAC, modulus, TTQ) → garbage, no error | partly |
| 3.6 | M | `EmvXmlParamParser:53-62,128-141`; `ClssXmlParamParser:210-437` | Fields guessed (priority, onlinePin, floor check, 9F01 null, PayPass flags, PayWave domesticOnly, Amex fullOnline…); no real host **contact** XML in the repo | no |
| 3.7 | M | `DaoMaster:20`, both `onUpgrade` | `SCHEMA_VERSION=1`; any bump drops all tables (trans_data, torn log); pre-rxSet install lacks `paywave_aid.TERMINAL_CAPABILITY` → crash. Fix: v2 + `ALTER TABLE` | partly |
| 3.8 | M | `EmvParamService:928-937` | Demo TID/MID/MCC/merchant; `findAllAcquirers().get(0)` throws on an empty table | partly |
| 3.9 | M | `PaxEmvBehavior:1676-1720` | No AID/CAPK presence check, no "terminal not configured" state | yes |
| 3.10 | M | `EmvParamActivity:124-127` | Manual download only; no version / hash / signature check | partly |
| 3.11 | L | `EmvParamService:102,813-905` | `cachedBuilder` unsynchronized (tx thread + param screen thread); no contact AIDs → DB read on every call | no |
| 3.12 | L | `EmvParamUpdater:73-82` | No zip-entry size cap; several `*.emv` → last wins | no |
| 3.13 | L | `paywave.json` | No `terminalCapability` → 9F33 = 00 00 + security until the first download (DAO end to end ✔ consistent) | no |

## 4. Host / network

| # | Sev | Where | What | §11 |
|---|---|---|---|---|
| 4.1 | M | network (0 uses of `HttpException` / `errorBody`) | UI shows "HTTP 403", not the server text | yes |
| 4.2 | M | `OnboardingClient:159-179`, `HostSettings:29-32` | Token refreshed only from saved expiry; a 401/403 isn't retried; mToken never refreshed (~3 days) | partly |
| 4.3 | M | `HostApiClient:22,40-41` | 75 s connect + 75 s read per call, 3 chained calls inside the 90 s budget; no `callTimeout`; `isRetry` always 0, no inquiry | no |
| 4.4 | M | `AbstractEmvBehavior:221`, `PaxEmvBehavior:2096`, `SaleCommunicationBehavior:164-168` | **Swipe and manual can never go online** (no `EmvTransactionResult` → `SaleException`) | partly |
| 4.5 | M | `core/.../AuthResult` (no ref field), `SaleCommunicationBehavior:405,497-506` | Sale's `referenceNumber` / TID / MID dropped → receipt can't show what a refund needs; refund `transactionId` always null | partly |
| 4.6 | M | `TransactionType:13-17,182`, `AmountFragment:25-27` | CASH_IN live with placeholder procCode `010000` (= ISO withdrawal); host refuses only after full EMV; no withdraw/void/balance/settlement | yes |
| 4.7 | M, unverified | `OnboardingState:6-11,182-213` | `java.time` with minSdk 24, no desugaring → `NoClassDefFoundError` on API 24/25 (not caught) | no |
| 4.8 | M | `OnboardingActivity:112-121`, `HostHeaders:76-89` | Every secret header shown on screen | no |
| 4.9 | L | `AesEnvelopeCrypto:37`, `HostApiClient:39-43`, `DataModel`, `Hex.decode` | AES-CBC no MAC, no cert pinning, asyncRequestId not matched, createToken signature unchecked, lax hex | no |
| 4.10 | L | `OnboardingState:113-162`, `HostSettings:52` | Secrets in plain SharedPreferences; sales gated only on "key + token present" | no |

## 5. Live gate (security / build)

| # | Sev | Where | What | §11 |
|---|---|---|---|---|
| 5.1 | H | `app/build.gradle:24-30,96-104`, all proguard files | Release: `minifyEnabled false`, no `signingConfig`, empty keep rules (minify would break GreenDAO / fastjson / JNI); dev/staging/live flavors differ in nothing | no |
| 5.2 | M | `EmvDebugger.d` in every `getTlv`/`setTlv`, `ApduTrace` logcat, `EmvLog` "TransactionStep", `SaleCommunicationBehavior.logSerialized`, `ContactProcess:365-376` | PAN, track 2, name, cryptograms, 89/91, scripts in logcat (debug builds) | yes |
| 5.3 | L | `AndroidManifest.xml:13` | `allowBackup="true"` → plaintext DB + token prefs via adb backup | no |
| 5.4 | L | root `build.gradle` ext | okhttp 4.9.1 (CVE-2021-0341, fixed 4.9.2), fastjson 1.1.72 EOL, greendao / sqlcipher 4.5.4 unmaintained, mockito 1.10.19 | no |
| 5.5 | L | `assets/config.json:33-36` | Default password hashes (unsalted) shipped, unused | no |
| 5.6 | L | `AbstractEmvBehavior:41-56`, `ApduTrace:82-85` | `activeConfig` (PIN block, PEK, track 2) kept after the end; trace not cleared while logging is off | no |

## 6. UI / features

| # | Sev | Where | What | §11 |
|---|---|---|---|---|
| 6.1 | H | `SearchCardFragment:316-324` | **Receipt**: only PAN/TVR/TAC/IAC/ICC — no merchant, TID/MID, date, type, amount, auth code, ref no., result, AID/label, entry mode, copy label | no |
| 6.2 | M | `MainActivity:47`, `MainViewModel:113` | MainActivity Cancel does nothing (its ViewModel starts no sale) — remove or rewire | no |
| 6.3 | M | `MainActivity:60-90` | Status after a sale lost (only COMPLETED delivered, no data); ERROR toast repeats on rotation (no `Event`) | no |
| 6.4 | M | `SearchCardFragment:155-215` | Rotate after the outcome: outcome / result panels gone, Cancel back, BUSY and banner lost (form #18) | no |
| 6.5 | M | `SearchCardFragment:103-105`, `PaxTerminal:114-118` | Manual entry: stub button, `onManualEntrySelected` commented out | yes |
| 6.6 | M | `AmountFragment:104-117` | Amount 0 accepted; no upper bound / n12 max | no |
| 6.7 | M | `PosTerminal.printReceipt:146-150` | Print failure only `printStackTrace`; UI already said "Sent to printer"; no reprint | no |
| 6.8 | L | `KEY_PIN_TRIES_LEFT` / `KEY_PIN_BYPASS` / `KEY_ONLINE_PIN` | Produced, never shown ("last PIN try"); no message during the 90 s host wait | no |
| 6.9 | L | fake/ingenico `VendorDebugPanel:12-16` | `btnOnboarding` visible and unwired | no |
| 6.10 | L | `PaxTerminal:143-168`, `PaxEmvBehavior:1562` (unverified) | Readers not closed after a mag sale / search timeout / cancel | no |
| 6.11 | L | `PosTerminal` retry `handleCardFound:481-487` | Each retry nests `searchCard` + `prepare` on the stack and re-publishes TRANSACTION_STARTED | partly |
| 6.12 | L | `PaxEmvBehavior:1656,1766` | 9F41 always 0; issuer name literal "PAX Issuer" | no |

## 7. Tests to add (none exist for these)

- **app**: no `app/src/test` at all. `MainViewModel` (IDLE reset, busy → `getTerminalBusy`, cancel only its own, `onCleared` cancels); `AmountFragment.parseAmountMinor` (0, rounding, overflow — pull it out first).
- **core**: retry end to end (same transaction, adjusted config, "RETRY #n", cancelled → no search); `prepare()==false` → one ERROR; `authorizeOnline` (cancelled never calls host; manual skips field 55); `finishApproved/Declined` order; `printReceipt` failure; `TransactionConfig.withX` keeps the rest.
- **network**: `toAuthResult` (approve / decline / missing code — 0.3), `decryptEnvelope` errors, AES round trip + IV‖ct layout, RSA PKCS#1 parse, BerTlv / IssuerResponseFields, HmacSigner vs the captured run, `isTokenValid`, REFUND envelope, `cardFields` expiry / track-2.
- **bizentity**: `EmvParamUpdater` (zip, delete/insert, `removeKernelsNotInPackage`, `changedDatabase`), contact XML CAPK + revocation fixture, Amex parsing, `XmlDomUtils` (BOM, prolog, bad numbers), `capkConvert` / `getCachedEmvParam` on empty tables, `ConfigInit`, DAO bind/read round trips.
- **PAX kernels**: atomic kernels (2.2); `checkContactlessResult` mapping (0.6) as a pure function.

## 8. Dead code (bare name and `Class::method` searched)

`PosTerminal.communication()` / `printer()` / `dispose()` (app never calls it), `HostDefaults.declineUntilWired`,
`FakePinPad`, `IngenicoPinPad`, `CardSearchListener.onCardRemoved` (no vendor calls it),
`TransactionStepEvent.KEY_AMOUNT`, `EmvEnhanceApp.getOnboarding()` / `getOnboardingState()`,
`RetrofitCommunicationBehavior` + `HostApiConnection#purchase` + `Purchase*` (would send PAN + PIN
block in plain JSON), the online onboarding cycle (`OnboardingClient:218-261`, `OnboardingOnline*`),
`OnboardingState.markOnlineOnboarded` / `isOnlineOnboarded` / `isFullyOnboarded` / `getSacs` /
`getChallenge`, `AuthResult.getAsyncRequestId` (needed by 0.5 — keep), `BuildConfig.MTOKEN_TEMP`.
Ask before deleting (`CLAUDE.md` §12).

## 9. Docs that no longer match the code

- `TransactionStepEvent:8-21` javadoc still says BehaviorSubject + replay; `AbstractEmvBehavior:402-407,477-481` still says the end disposes the observer.
- `AppSelectDialog` javadoc: "only for 2+ candidates" (debug shows it for 1) and refers to a non-existent `PaxPinPad#showAndWait`.
- `CLAUDE.md` §2.3 "the adapter always calls `setCallBackResult`" — not true until 0.4 is fixed; §7 TC rule vs code (1.6).
- `REFERENCE.md`: fixed in the same commit as this file (see its §4.3, §5.4, §5.5, §5.7, §2.5, §7).

---

## Suggested order

1. **Before any more device testing**: 0.4 (hang), 0.8 + 0.10 (no sale at all), 0.1 (Back), 0.3 (fail open).
2. **Form bugs**: 0.6 + 0.14 + 2.1 (#10 See Phone / try again), 0.7, 1.1–1.3.
3. **Parameters**: 0.9, 3.1, 3.2, 3.4, 3.5, then 3.3 / 3.7.
4. **Host**: 0.5 (needs the host's reversal / ack API), 4.1–4.5.
5. **Live gate** (one branch): 0.2, 0.11–0.13, 5.1–5.6, 4.8, 4.10, 1.12.
6. Receipt (6.1), then the UI rows, tests alongside each fix.
