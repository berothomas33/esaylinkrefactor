# EMV Library & PAX JEMV Kernel Architecture

Research document for end-to-end EMV contactless architecture diagrams.
Generated from source inspection of `/Users/macmin/Desktop/EmvEnhanceRefactor`.

---

## Table of Contents

1. [Module Structure](#1-module-structure)
2. [emvlib/dpas2 — Public Classes & Contactless Flow](#2-emvlibdpas2--public-classes--contactless-flow)
3. [emvlib/dpas (Active Build) — Public Classes & Contactless Flow](#3-emvlibdpas-active-build--public-classes--contactless-flow)
4. [emvlib/base — Class Hierarchy & Shared Abstractions](#4-emvlibbase--class-hierarchy--shared-abstractions)
5. [com.pax.jemv.* Packages](#5-compaxjemv-packages)
6. [System.loadLibrary → Native .so Mapping](#6-systemloadlibrary--native-so-mapping)
7. [Key Data / Return-Code Types](#7-key-data--return-code-types)
8. [Entry Point, ClssProcess AID Selection & Kernel Dispatch](#8-entry-point-clssprocess-aid-selection--kernel-dispatch)

---

## 1. Module Structure

### 1.1 Gradle Module Graph

```
settings.gradle
  include ':emvlib'
  include ':emvlib:base'
  include ':emvlib:dpas'      ← linked by emvlib (active)
  include ':emvlib:dpas2'     ← present in repo, NOT linked by emvlib

:emvbase  (COMMON_v103.jar)
    ↑
:emvlib:base  (DEVICE_v103.jar, Entry_v105.jar)
    ↑
:emvlib:dpas  OR  :emvlib:dpas2   (mutually exclusive at runtime via WMRouter)
    ↑
:emvlib  (8 contactless kernel jars + jniLibs)
    ↑
:emvservice:emv → :emvflow
```

### 1.2 Evidence: `emvlib:dpas2` Is NOT Linked

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/build.gradle`

```gradle
41|dependencies {
42|
43|    api fileTree(include: ["*.jar"], dir: "libs")
44|    api project(":emvbase")
45|    api project(":emvlib:dpas")    // ← dpas only; dpas2 absent
46|    annotationProcessor "io.github.meituan-dianping:compiler:$rootProject.routerCompiler"
47|
48|}
```

- Line 45 declares `api project(":emvlib:dpas")`.
- There is **no** `api project(":emvlib:dpas2")` anywhere in this file.
- `settings.gradle` lines 27–28 include both submodules, but only `:emvlib:dpas` is consumed by `:emvlib`.

Both `dpas` and `dpas2` register the same WMRouter keys (`EmvKernelConst.DPAS` for contactless, `EmvKernelConst.EMV` for contact). Only the module on the runtime classpath is active.

### 1.3 Per-Module build.gradle Dependencies

#### `emvlib/build.gradle` (root)

| Dependency | Purpose |
|------------|---------|
| `api fileTree(include: ["*.jar"], dir: "libs")` | 8 contactless kernel JARs |
| `api project(":emvbase")` | Shared types, COMMON jar transitively |
| `api project(":emvlib:dpas")` | DPAS v1 contact + contactless wrappers |

#### `emvlib/base/build.gradle`

| Dependency | Purpose |
|------------|---------|
| `api fileTree(include: ["*.jar"], dir: "libs")` | DEVICE + Entry JARs |
| `api project(":emvbase")` | Base param/process types |

#### `emvlib/dpas/build.gradle`

| Dependency | Purpose |
|------------|---------|
| `api fileTree(include: ["*.jar"], dir: "libs")` | EMV_v105 + DPAS_v100 |
| `api project(":emvlib:base")` | Abstract kernel framework |

#### `emvlib/dpas2/build.gradle`

| Dependency | Purpose |
|------------|---------|
| `api fileTree(include: ["*.jar"], dir: "libs")` | EMV_v106 + DPAS_v101 + DPAS_CT_v100 |
| `api project(":emvlib:base")` | Abstract kernel framework |

### 1.4 Exact `libs/` Contents

#### `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/base/libs/`

| File | Package(s) |
|------|------------|
| `DEVICE_v103.jar` | `com.pax.jemv.device.*` |
| `Entry_v105.jar` | `com.pax.jemv.entrypoint.*` |

#### `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/libs/`

| File | Package(s) |
|------|------------|
| `EMV_v105.jar` | `com.pax.jemv.emv.*` |
| `DPAS_v100.jar` | `com.pax.jemv.dpas.*` |

#### `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas2/libs/`

| File | Package(s) |
|------|------------|
| `EMV_v106.jar` | `com.pax.jemv.emv.*` |
| `DPAS_v101.jar` | `com.pax.jemv.dpas.*` |
| `DPAS_CT_v100.jar` | `com.pax.jemv.ctdpas.*` |

#### `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/libs/`

| File | Package(s) |
|------|------------|
| `AE_v101.jar` | `com.pax.jemv.amex.*` |
| `EFT_v101_D1.jar` | `com.pax.jemv.eftpos.*` |
| `JCB_v100.jar` | `com.pax.jemv.jcb.*` |
| `MC_v100.jar` | `com.pax.jemv.paypass.*` |
| `MIR_v100.jar` | `com.pax.jemv.mir.*` |
| `PURE_v100.jar` | `com.pax.jemv.pure.*` |
| `QPBOC_v100.jar` | `com.pax.jemv.qpboc.*` |
| `RuPay_v100.jar` | `com.pax.jemv.rupay.*` |
| `WAVE_v101.jar` | `com.pax.jemv.paywave.*` |

#### `/Users/macmin/Desktop/EmvEnhanceRefactor/emvbase/libs/` (transitive via emvbase)

| File | Package(s) |
|------|------------|
| `COMMON_v103.jar` | `com.pax.jemv.clcommon.*` |

### 1.5 Exact `jniLibs/armeabi/` Contents

#### `emvlib/base/src/main/jniLibs/armeabi/`

```
libF_DEVICE_LIB_PayDroid.so
libF_PUBLIC_LIB_PayDroid.so
libF_ENTRY_LIB_PayDroid.so
libJNI_ENTRY_v105.so
```

#### `emvlib/dpas/src/main/jniLibs/armeabi/`

```
libF_EMV_LIBC_PayDroid.so
libF_EMV_LIB_PayDroid.so
libJNI_EMV_v105.so
libF_DPAS_LIB_PayDroid.so
libJNI_DPAS_v100.so
```

#### `emvlib/dpas2/src/main/jniLibs/armeabi/`

```
libF_EMV_LIBC_PayDroid.so
libF_EMV_LIB_PayDroid.so
libJNI_EMV_v106_DPASCT.so
libF_DPAS_LIB_PayDroid.so
libJNI_DPAS_v101.so
libF_DPAS_CT_LIB_PayDroid.so
libJNI_DPAS_CT_v100.so
```

#### `emvlib/src/main/jniLibs/armeabi/`

```
libF_MC_LIB_PayDroid.so
libJNI_MC_v100_01.so
libF_WAVE_LIB_PayDroid.so
libJNI_WAVE_v101.so
libF_AE_LIB_PayDroid.so
libJNI_AE_v101.so
libF_JCB_LIB_PayDroid.so
libJNI_JCB_v100.so
libF_MIR_LIB_PayDroid.so
libJNI_MIR_v100.so
libF_QPBOC_LIB_PayDroid.so
libJNI_QPBOC_v100.so
libF_PURE_LIB_PayDroid.so
libJNI_PURE_v100.so
libF_RUPAY_LIB_PayDroid.so
libJNI_RUPAY_v100.so
libF_EFT_LIB_PayDroid.so
libJNI_EFT_v101_D1.so
```

### 1.6 Java Source Files per Module

#### `emvlib/` (root) — `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/`

| File | Role |
|------|------|
| `process/contactless/ClssProcess.java` | Contactless orchestrator (entry point + kernel dispatch) |
| `process/contactless/ClssKernelProcessFactory.java` | Kernel-ID → WMRouter dispatch |
| `process/contactless/ClssEntryAddAid.java` | Register all kernel AIDs to entry point |
| `process/contactless/ClssPbocProcess.java` | qPBOC kernel wrapper |
| `process/contactless/ClssPayPassProcess.java` | PayPass kernel wrapper |
| `process/contactless/ClssPayWaveProcess.java` | payWave kernel wrapper |
| `process/contactless/ClssAEProcess.java` | Amex kernel wrapper |
| `process/contactless/ClssJcbProcess.java` | JCB kernel wrapper |
| `process/contactless/ClssMirProcess.java` | MIR kernel wrapper |
| `process/contactless/ClssPureProcess.java` | PURE kernel wrapper |
| `process/contactless/ClssRuPayProcess.java` | RuPay kernel wrapper |
| `process/contactless/ClssEFTProcess.java` | EFTPOS kernel wrapper |
| `process/contact/EmvProcess.java` | Contact chip router → `BaseContactProcess` |
| `utils/EmvUtils.java` | Native library loader |

#### `emvlib/base/` — `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/base/src/main/java/com/pax/emvlib/base/`

| File | Role |
|------|------|
| `contactless/ClssKernelProcess.java` | Abstract contactless kernel wrapper |
| `contact/BaseContactProcess.java` | Abstract contact chip wrapper |
| `EmvLoadLibImpl.java` | Loads DEVICE + PUBLIC + ENTRY libs |
| `IEmvLoadLibCallback.java` | Library load callback interface |
| `utils/EmvParamConvert.java` | Converts app params → JEMV types |
| `consts/EmvKernelConst.java` | WMRouter key constants |

#### `emvlib/dpas/` — `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/src/main/java/com/pax/emvlib/dpas/`

| File | Role |
|------|------|
| `EmvLoadLibImpl.java` | Loads EMV v105 + DPAS v100 libs |
| `contactless/ClssDpasProcess.java` | DPAS contactless (active) |
| `contact/ContactProcess.java` | DPAS contact chip |

#### `emvlib/dpas2/` — `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas2/src/main/java/com/pax/emvlib/dpas2/`

| File | Role |
|------|------|
| `EmvLoadLibImpl.java` | Loads EMV v106_DPASCT + DPAS v101 + DPAS_CT libs |
| `contactless/ClssDpasProcess.java` | DPAS Connect 2.0 contactless (alternate) |
| `contact/ContactProcess.java` | DPAS Connect 2.0 contact chip |

---

## 2. emvlib/dpas2 — Public Classes & Contactless Flow

> **Note:** dpas2 is present in the repo but **not linked** in the active build. Documented here for architecture completeness.

### 2.1 Public Class: `com.pax.emvlib.dpas2.EmvLoadLibImpl`

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas2/src/main/java/com/pax/emvlib/dpas2/EmvLoadLibImpl.java`

```java
@RouterService(interfaces = IEmvLoadLibCallback.class)
public class EmvLoadLibImpl implements IEmvLoadLibCallback {
    public void load();
}
```

### 2.2 Public Class: `com.pax.emvlib.dpas2.contactless.ClssDpasProcess`

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas2/src/main/java/com/pax/emvlib/dpas2/contactless/ClssDpasProcess.java`

**Annotation:** `@RouterService(interfaces = ClssKernelProcess.class, key = EmvKernelConst.DPAS)`

**Extends:** `ClssKernelProcess<DpasParam>`

```java
public class ClssDpasProcess extends ClssKernelProcess<DpasParam> {

    // Overridden public methods
    public TransResult startTransProcess();
    public TransResult completeTransProcess(IssuerRspData issuerRspData);
    public int getTlv(int tag, ByteArray value);
    public int setTlv(int tag, byte[] value);
    public String getTrack2();
    public boolean isNeedSecondTap(IssuerRspData issuerRspData);

    // Inherited public setters from ClssKernelProcess<DpasParam>
    public ClssKernelProcess<DpasParam> setEmvProcessParam(EmvProcessParam emvProcessParam);
    public ClssKernelProcess<DpasParam> setClssTransParam(Clss_TransParam transParam);
    public ClssKernelProcess<DpasParam> setFinalSelectData(byte[] finalSelectData, int finalSelectDataLen);
    public ClssKernelProcess<DpasParam> setPreProcInterInfo(Clss_PreProcInterInfo preProcInterInfo);
    public ClssKernelProcess<DpasParam> setClssStatusListener(IContactlessCallback clssStatusListener);
    public ClssKernelProcess<DpasParam> setClssParam(DpasParam clssParam);
}
```

**Private methods (internal flow, not public):**

```java
private int init();
private void setParam();
private int processDpas(int pathType);
private TransResult genTransResult();
protected int addCapkAndRevokeList(EMV_CAPK emvCapk, EMV_REVOCLIST emvRevoclist);
```

### 2.3 Public Class: `com.pax.emvlib.dpas2.contact.ContactProcess`

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas2/src/main/java/com/pax/emvlib/dpas2/contact/ContactProcess.java`

**Annotation:** `@RouterService(interfaces = BaseContactProcess.class, key = EmvKernelConst.EMV)`

**Extends:** `BaseContactProcess`

```java
public class ContactProcess extends BaseContactProcess {

    public void registerEmvProcessListener(IContactCallback emvTransProcessListener);
    public int preTransProcess(EmvProcessParam emvProcessParam);
    public TransResult startTransProcess();
    public TransResult completeTransProcess(IssuerRspData issuerRspData);
    public byte[] getTlv(int tag);
    public void setTlv(int tag, byte[] value);
}
```

**dpas2-only contact init:** calls `EMVCallback.DPASCTCoreInit()` at line 94 (not present in dpas v1).

### 2.4 Native API: `com.pax.jemv.dpas.api.ClssDPASApi` (DPAS_v101.jar)

```java
public class ClssDPASApi {
    public static native int Clss_CoreInit_DPAS();
    public static native int Clss_ReadVerInfo_DPAS(ByteArray);
    public static native int Clss_SetTLVDataList_DPAS(byte[], int);
    public static native int Clss_GetTLVDataList_DPAS(byte[], byte, int, ByteArray);
    public static native int Clss_AddCAPK_DPAS(EMV_CAPK);
    public static native void Clss_DelAllCAPK_DPAS();
    public static native int Clss_AddRevocList_DPAS(EMV_REVOCLIST);
    public static native void Clss_DelAllRevocList_DPAS();
    public static native int Clss_SetFinalSelectData_DPAS(byte[], int);
    public static native int Clss_InitiateApp_DPAS(TransactionPath);
    public static native int Clss_ReadData_DPAS();
    public static native int Clss_TransProc_DPAS(byte);
    public static native int Clss_IssuerUpdateProc_DPAS(int, byte[], int);
    public static native int Clss_GetTrackMapData_DPAS(byte, ByteArray);
    public static native int Clss_GetDebugInfo_DPAS();
    public static native int Clss_SetExtendFunction_DPAS(int, byte[], int);
    public static native int Clss_SetCBFun_SendEXData_DPAS();
}
```

### 2.5 DPAS2 Contactless — Complete Ordered Call Sequence

PAX collapses GPO, read records, ODA, CVM, terminal action analysis, and GENERATE AC into three native calls inside the DPAS kernel. Entry-point AID selection happens before the DPAS kernel is invoked.

#### Phase A — Library & Device Init (once per app session)

| # | Call | File:Line |
|---|------|-----------|
| 1 | `EmvUtils.loadLibrary()` | `emvlib/src/main/java/com/pax/emvlib/utils/EmvUtils.java:32` |
| 2 | WMRouter → `EmvLoadLibImpl.load()` (base) | `emvlib/base/.../EmvLoadLibImpl.java:27-36` |
| 3 | WMRouter → `EmvLoadLibImpl.load()` (dpas2) | `emvlib/dpas2/.../EmvLoadLibImpl.java:31-43` |
| 4 | `EmvUtils` loads MC/WAVE/AE/JCB/MIR/QPBOC/PURE/RUPAY/EFT `.so` pairs | `EmvUtils.java:46-80` |
| 5 | `DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance())` | `emvflow/.../EmvPreProcessService.java:50` |

#### Phase B — Pre-Transaction (entry init, AID/config load)

Called from `EmvPreProcessService.start()` → `ContactlessService.preTransProcess()` → `ClssProcess.preTransProcess()`:

| # | Call | File:Line | EMV Step |
|---|------|-----------|----------|
| 6 | `ClssEntryApi.Clss_CoreInit_Entry()` | `ClssProcess.java:74` | Entry kernel init |
| 7 | `ClssEntryApi.Clss_DelAllAidList_Entry()` | `ClssProcess.java:79` | Clear AID list |
| 8 | `ClssEntryApi.Clss_DelAllPreProcInfo()` | `ClssProcess.java:80` | Clear pre-proc info |
| 9 | `ClssEntryAddAid.addApp()` | `ClssProcess.java:83` | Register all kernel AIDs |
| 9a | `ClssEntryApi.Clss_AddAidList_Entry(aid, len, selFlag, KERNTYPE_DEF)` | `ClssEntryAddAid.java:107-108` | Add DPAS AID |
| 9b | `EmvParamConvert.dpasPreProcInfo(aid)` → `Clss_PreProcInfo` with `ucKernType = KERNTYPE_ZIP` | `EmvParamConvert.java:161-175` | DPAS pre-proc limits |
| 9c | `ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo)` | `ClssEntryAddAid.java:110` | Store pre-proc info |
| 10 | `ClssEntryApi.Clss_SetMCVersion_Entry((byte) 0x03)` | `ClssProcess.java:85` | PayPass version hint |
| 11 | `ClssEntryApi.Clss_PreTransProc_Entry(Clss_TransParam)` | `ClssProcess.java:86` | Pre-transaction processing |

#### Phase C — Card Detect → Candidate/AID Selection (entry point)

From `ContactlessService.startTransProcess()` → `ClssProcess.startTransProcess()`:

| # | Call | File:Line | EMV Step |
|---|------|-----------|----------|
| 12 | `ClssProcess.getInstance().registerClssProcessListener(callback)` | `ContactlessService.java:74` | Register UI callback |
| 13 | `ClssEntryApi.Clss_AppSlt_Entry(0, 0)` | `ClssProcess.java:105` | PPSE / build candidate list |
| 14 | `ClssEntryApi.Clss_FinalSelect_Entry(kernType, daArray)` | `ClssProcess.java:118` | Final AID select; sets `kernType.kernType = KERNTYPE_ZIP` for DPAS |
| 15 | *(reselect loop)* `ClssEntryApi.Clss_DelCurCandApp_Entry()` on `EMV_RSP_ERR`/`EMV_APP_BLOCK`/`ICC_BLOCK`/`CLSS_RESELECT_APP` | `ClssProcess.java:120-127` | Delete blocked candidate, retry |
| 16 | `ClssEntryApi.Clss_GetPreProcInterFlg_Entry(clssPreProcInterInfo)` | `ClssProcess.java:146` | Pre-proc intersection flags (TTQ, limit exceed) |
| 17 | `ClssEntryApi.Clss_GetFinalSelectData_Entry(finalSelectData)` | `ClssProcess.java:153` | Final select FCI data |
| 18 | `new ClssKernelProcessFactory(kernType.kernType).build()` | `ClssProcess.java:159-165` | Kernel dispatch (see Section 8) |
| 19 | `clssKernelProcess.startTransProcess()` | `ClssProcess.java:167` | Enter DPAS kernel wrapper |

#### Phase D — DPAS2 Kernel (`ClssDpasProcess.startTransProcess`)

| # | Call | File:Line | EMV Step (kernel-internal) |
|---|------|-----------|---------------------------|
| 20 | `ClssDPASApi.Clss_CoreInit_DPAS()` | `dpas2/.../ClssDpasProcess.java:79` | DPAS kernel init |
| 21 | `ClssDPASApi.Clss_SetFinalSelectData_DPAS(finalSelectData, finalSelectDataLen)` | `dpas2/.../ClssDpasProcess.java:85` | Pass entry final-select data |
| 22 | `setParam()` → multiple `setTlv(tag, value)` → `ClssDPASApi.Clss_SetTLVDataList_DPAS(buf, len)` | `dpas2/.../ClssDpasProcess.java:94-125, 241-255` | Terminal TLV params (TAC, TTQ, amount, date…) |
| 23 | `ClssDPASApi.Clss_InitiateApp_DPAS(transactionPath)` | `dpas2/.../ClssDpasProcess.java:56` | **GPO / application initiation**; sets `transactionPath.path` |
| 24 | `ClssDPASApi.Clss_ReadData_DPAS()` | `dpas2/.../ClssDpasProcess.java:129` | **Read records** (AFL) |
| 25 | *(if `pathType == CLSS_DPAS_EMV`)* `Clss_DelAllRevocList_DPAS()` | `dpas2/.../ClssDpasProcess.java:136` | Clear revocation list |
| 26 | *(if EMV path)* `Clss_DelAllCAPK_DPAS()` | `dpas2/.../ClssDpasProcess.java:137` | Clear CAPK list |
| 27 | *(if EMV path)* `addCapkRevList()` → `Clss_AddCAPK_DPAS` + `Clss_AddRevocList_DPAS` | `ClssKernelProcess.java:111-136`, `dpas2/.../ClssDpasProcess.java:259-264` | Load CAPK/revocation for ODA |
| 28 | `ClssDPASApi.Clss_TransProc_DPAS(clssParam.getExceptFileFlag())` | `dpas2/.../ClssDpasProcess.java:141` | **ODA + CVM + terminal action analysis + GENERATE AC** |
| 29 | `genTransResult()` → `getTlv(TagsTable.LIST)` (Outcome Parameter Set) | `dpas2/.../ClssDpasProcess.java:148-208` | Map outcome → `TransResultEnum` + CVM |
| 30 | `clssStatusListener.onReadCardOk()` | `dpas2/.../ClssDpasProcess.java:71` | UI: card read OK |
| 31 | `clssStatusListener.onRemoveCard()` | `dpas2/.../ClssDpasProcess.java:72` | UI: remove card |

#### Phase E — Post-Kernel (service layer)

From `ContactlessService.startTransProcess()`:

| # | Call | File:Line |
|---|------|-----------|
| 32 | `contactlessCallback.confirmCard()` | `ContactlessService.java:82` |
| 33 | *(if online PIN CVM)* `onCardHolderPwd(true, true, 0, null)` | `ContactlessService.java:93-100` |
| 34 | *(if `RESULT_REQ_ONLINE`)* `startOnlineProcess()` | `ContactlessService.java:103-105` |
| 35 | *(if script + EMV path)* `onDetect2ndTap()` | `ContactlessService.java:116` |
| 36 | `ClssProcess.getInstance().completeTransProcess(issuerRspData)` | `ContactlessService.java:117` |

#### Phase F — Completion / Second Tap

| # | Call | File:Line | EMV Step |
|---|------|-----------|----------|
| 37 | `ClssDPASApi.Clss_IssuerUpdateProc_DPAS(onlineResult, script, script.length)` | `dpas2/.../ClssDpasProcess.java:219` | Issuer auth + script processing + 2nd GEN AC |

---

## 3. emvlib/dpas (Active Build) — Public Classes & Contactless Flow

### 3.1 Public Class: `com.pax.emvlib.dpas.EmvLoadLibImpl`

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/src/main/java/com/pax/emvlib/dpas/EmvLoadLibImpl.java`

```java
@RouterService(interfaces = IEmvLoadLibCallback.class)
public class EmvLoadLibImpl implements IEmvLoadLibCallback {
    public void load();
}
```

Loads: `F_EMV_LIBC_PayDroid`, `F_EMV_LIB_PayDroid`, `JNI_EMV_v105`, `F_DPAS_LIB_PayDroid`, `JNI_DPAS_v100` (lines 33-39).

### 3.2 Public Class: `com.pax.emvlib.dpas.contactless.ClssDpasProcess`

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/src/main/java/com/pax/emvlib/dpas/contactless/ClssDpasProcess.java`

**Annotation:** `@RouterService(interfaces = ClssKernelProcess.class, key = EmvKernelConst.DPAS)`

**Extends:** `ClssKernelProcess<DpasParam>`

```java
public class ClssDpasProcess extends ClssKernelProcess<DpasParam> {

    // Overridden public methods
    public TransResult startTransProcess();
    public TransResult completeTransProcess(IssuerRspData issuerRspData);
    public int getTlv(int tag, ByteArray value);
    public int setTlv(int tag, byte[] value);
    public String getTrack2();
    public boolean isNeedSecondTap(IssuerRspData issuerRspData);

    // Inherited public setters from ClssKernelProcess<DpasParam>
    public ClssKernelProcess<DpasParam> setEmvProcessParam(EmvProcessParam emvProcessParam);
    public ClssKernelProcess<DpasParam> setClssTransParam(Clss_TransParam transParam);
    public ClssKernelProcess<DpasParam> setFinalSelectData(byte[] finalSelectData, int finalSelectDataLen);
    public ClssKernelProcess<DpasParam> setPreProcInterInfo(Clss_PreProcInterInfo preProcInterInfo);
    public ClssKernelProcess<DpasParam> setClssStatusListener(IContactlessCallback clssStatusListener);
    public ClssKernelProcess<DpasParam> setClssParam(DpasParam clssParam);
}
```

**Private inner class (package-private, not public):** `ClssDpassSendOutcome` — reads outcome TLVs after `Clss_TransProc_DPAS`.

**Private methods:**

```java
private int init();
private void setParam();
private int processDpas(TransactionPath pathType);   // note: takes TransactionPath, not int
private TransResult genTransResult();
protected int addCapkAndRevokeList(EMV_CAPK emvCapk, EMV_REVOCLIST emvRevoclist);
```

### 3.3 Public Class: `com.pax.emvlib.dpas.contact.ContactProcess`

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/src/main/java/com/pax/emvlib/dpas/contact/ContactProcess.java`

```java
@RouterService(interfaces = BaseContactProcess.class, key = EmvKernelConst.EMV)
public class ContactProcess extends BaseContactProcess {

    public void registerEmvProcessListener(IContactCallback emvTransProcessListener);
    public int preTransProcess(EmvProcessParam emvProcessParam);
    public TransResult startTransProcess();
    public TransResult completeTransProcess(IssuerRspData issuerRspData);
    public byte[] getTlv(int tag);
    public void setTlv(int tag, byte[] value);
}
```

**dpas v1 contact init:** calls `EMVCallback.EMVCoreInit()` only (line 93); does **not** call `DPASCTCoreInit()`.

### 3.4 Native API: `com.pax.jemv.dpas.api.ClssDPASApi` (DPAS_v100.jar)

Same method signatures as DPAS_v101 (Section 2.4). Both jars expose identical `ClssDPASApi` native method names; version differs in underlying `.so` (`JNI_DPAS_v100` vs `JNI_DPAS_v101`).

### 3.5 dpas (Active) Contactless — Complete Ordered Call Sequence

Phases A–C (library init, pre-transaction, entry-point AID selection) are **identical** to dpas2 (Section 2.5, steps 1–19). The difference begins at the DPAS kernel wrapper.

#### Phase D — dpas Kernel (`ClssDpasProcess.startTransProcess`)

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/src/main/java/com/pax/emvlib/dpas/contactless/ClssDpasProcess.java`

| # | Call | Line | EMV Step (kernel-internal) |
|---|------|------|---------------------------|
| 20 | `ClssDPASApi.Clss_CoreInit_DPAS()` | 87 | DPAS kernel init |
| 21 | `ClssDPASApi.Clss_SetFinalSelectData_DPAS(finalSelectData, finalSelectDataLen)` | 93 | Pass entry final-select data |
| 22 | `setParam()` → `setTlv(...)` → `ClssDPASApi.Clss_SetTLVDataList_DPAS(buf, len)` | 101-131, 238-252 | Terminal TLV params |
| 23 | `getTlv(0x9F38, new ByteArray())` *(PDOL debug log)* | 61 | — |
| 24 | `ClssDPASApi.Clss_InitiateApp_DPAS(transactionPath)` | 63 | **GPO / application initiation** |
| 25 | `ClssDPASApi.Clss_ReadData_DPAS()` | 136 | **Read records** (AFL) |
| 26 | *(if `pathType.path == CLSS_DPAS_EMV`)* `Clss_DelAllRevocList_DPAS()` | 143 | Clear revocation list |
| 27 | *(if EMV path)* `Clss_DelAllCAPK_DPAS()` | 144 | Clear CAPK list |
| 28 | *(if EMV path)* `addCapkRevList()` → `Clss_AddCAPK_DPAS` + `Clss_AddRevocList_DPAS` | 145, 256-261 | Load CAPK/revocation for ODA |
| 29 | `ClssDPASApi.Clss_TransProc_DPAS(clssParam.getExceptFileFlag())` | 148 | **ODA + CVM + terminal action analysis + GENERATE AC** |
| 30 | `ClssDPASApi.Clss_GetDebugInfo_DPAS()` *(debug only)* | 151 | Debug info |
| 31 | `sendOutcome.sendTransDataOutput((byte) 0x07)` | 153 | Read outcome TLVs (0x01=outcome, 0x02=err, 0x04=UI req) |
| 32 | `genTransResult()` using `sendOutcome.outcomeParamSet` | 157-205 | Map outcome → `TransResultEnum` + CVM |
| 33 | `clssStatusListener.onReadCardOk()` | 77 | UI: card read OK |
| 34 | `clssStatusListener.onRemoveCard()` | 78 | UI: remove card |

#### Phase E — Post-Kernel (identical to dpas2, Section 2.5 Phase E)

Steps 32–36 from dpas2 Phase E apply unchanged via `ContactlessService.java`.

#### Phase F — Completion / Second Tap

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/src/main/java/com/pax/emvlib/dpas/contactless/ClssDpasProcess.java`

| # | Call | Line | EMV Step |
|---|------|------|----------|
| 37 | `ClssDPASApi.Clss_IssuerUpdateProc_DPAS(onlineResult, script, script.length)` | 216 | Issuer auth + script + 2nd GEN AC |

**Note:** dpas v1 treats null script as empty byte array (line 212-214); dpas2 returns early with `RESULT_ONLINE_APPROVED` if script is null (line 215-217).

### 3.6 dpas vs dpas2 Contactless Differences Summary

| Aspect | dpas (active) | dpas2 (alternate) |
|--------|---------------|-------------------|
| DPAS JAR | `DPAS_v100.jar` | `DPAS_v101.jar` |
| DPAS `.so` | `JNI_DPAS_v100` | `JNI_DPAS_v101` |
| EMV JAR | `EMV_v105.jar` | `EMV_v106.jar` |
| EMV `.so` | `JNI_EMV_v105` | `JNI_EMV_v106_DPASCT` |
| Extra libs | — | `DPAS_CT_v100.jar`, `F_DPAS_CT_LIB_PayDroid`, `JNI_DPAS_CT_v100` |
| Outcome read | Inner class `ClssDpassSendOutcome.sendTransDataOutput(0x07)` after TransProc | Direct `getTlv` in `genTransResult()` |
| CVM mask | `outcomeParamSet.data[3] & 0x30` | `outcomeParamSet.data[3] & 0xF0` |
| Track2 MAG/ZIP | `Clss_GetTrackMapData_DPAS(0x02, track)` supported | Not implemented (EMV tag 57 only) |
| Null script on complete | Converts to `new byte[0]` | Returns `RESULT_ONLINE_APPROVED` immediately |

---

## 4. emvlib/base — Class Hierarchy & Shared Abstractions

### 4.1 Inheritance Diagram

```
com.pax.emvbase.process.EmvBase                          [abstract, emvbase module]
│
├── com.pax.emvlib.base.contact.BaseContactProcess         [abstract]
│   ├── com.pax.emvlib.dpas.contact.ContactProcess         @RouterService(key="EMV")   ← active
│   └── com.pax.emvlib.dpas2.contact.ContactProcess        @RouterService(key="EMV")   ← alternate
│
└── com.pax.emvlib.process.contactless.ClssProcess         [singleton orchestrator]
    └── delegates to →
        com.pax.emvlib.base.contactless.ClssKernelProcess<T>  [abstract]
            ├── com.pax.emvlib.dpas.contactless.ClssDpasProcess     @RouterService(key="DPAS")  ← active
            ├── com.pax.emvlib.dpas2.contactless.ClssDpasProcess    @RouterService(key="DPAS")  ← alternate
            ├── com.pax.emvlib.process.contactless.ClssPbocProcess  @RouterService(key="PBOC")
            ├── com.pax.emvlib.process.contactless.ClssPayPassProcess @RouterService(key="MC")
            ├── com.pax.emvlib.process.contactless.ClssPayWaveProcess @RouterService(key="PAYWAVE")
            ├── com.pax.emvlib.process.contactless.ClssAEProcess    @RouterService(key="AMEX")
            ├── com.pax.emvlib.process.contactless.ClssJcbProcess     @RouterService(key="JCB")
            ├── com.pax.emvlib.process.contactless.ClssMirProcess     @RouterService(key="MIR")
            ├── com.pax.emvlib.process.contactless.ClssPureProcess   @RouterService(key="PURE")
            ├── com.pax.emvlib.process.contactless.ClssRuPayProcess   @RouterService(key="RUPAY")
            └── com.pax.emvlib.process.contactless.ClssEFTProcess     @RouterService(key="EFT")

com.pax.emvlib.base.IEmvLoadLibCallback                  [interface]
├── com.pax.emvlib.base.EmvLoadLibImpl                     [always loaded]
├── com.pax.emvlib.dpas.EmvLoadLibImpl                     [active]
└── com.pax.emvlib.dpas2.EmvLoadLibImpl                    [alternate]
```

### 4.2 `EmvBase` (emvbase module)

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvbase/src/main/java/com/pax/emvbase/process/EmvBase.java`

```java
public abstract class EmvBase {
    public abstract int preTransProcess(EmvProcessParam emvParam);
    public abstract TransResult startTransProcess();
    public abstract TransResult completeTransProcess(IssuerRspData issuerRspData);
    public abstract byte[] getTlv(int tag);
    public abstract void setTlv(int tag, byte[] value);
}
```

### 4.3 `BaseContactProcess` (emvlib/base)

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/base/src/main/java/com/pax/emvlib/base/contact/BaseContactProcess.java`

```java
public abstract class BaseContactProcess extends EmvBase {
    public abstract void registerEmvProcessListener(IContactCallback emvTransProcessListener);
}
```

Both `dpas.ContactProcess` and `dpas2.ContactProcess` extend this and implement all `EmvBase` + `registerEmvProcessListener` methods.

### 4.4 `ClssKernelProcess<T>` (emvlib/base)

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/base/src/main/java/com/pax/emvlib/base/contactless/ClssKernelProcess.java`

```java
public abstract class ClssKernelProcess<T extends BaseParam<? extends BaseAid>> {

    // Abstract — each kernel implements
    public abstract TransResult startTransProcess();
    public abstract TransResult completeTransProcess(IssuerRspData issuerRspData);
    public abstract int getTlv(int tag, ByteArray value);
    public abstract int setTlv(int tag, byte[] value);
    public abstract String getTrack2();
    public abstract boolean isNeedSecondTap(IssuerRspData issuerRspData);
    protected abstract int addCapkAndRevokeList(EMV_CAPK emvCapk, EMV_REVOCLIST emvRevoclist);

    // Concrete setters (fluent builder pattern)
    public ClssKernelProcess<T> setEmvProcessParam(EmvProcessParam emvProcessParam);
    public ClssKernelProcess<T> setClssTransParam(Clss_TransParam transParam);
    public ClssKernelProcess<T> setFinalSelectData(byte[] finalSelectData, int finalSelectDataLen);
    public ClssKernelProcess<T> setPreProcInterInfo(Clss_PreProcInterInfo preProcInterInfo);
    public ClssKernelProcess<T> setClssStatusListener(IContactlessCallback clssStatusListener);
    public ClssKernelProcess<T> setClssParam(T clssParam);

    // Concrete helper
    protected int addCapkRevList();
    protected static String getTrack2FromTag57(String tag57);
}
```

**Protected fields shared by dpas/dpas2:**

```java
protected EmvProcessParam emvProcessParam;
protected Clss_TransParam transParam;
protected T clssParam;
protected byte[] finalSelectData;
protected int finalSelectDataLen;
protected Clss_PreProcInterInfo preProcInterInfo;
protected TransactionPath transactionPath = new TransactionPath();
protected IContactlessCallback clssStatusListener;
```

Both `dpas.ClssDpasProcess` and `dpas2.ClssDpasProcess` inherit `addCapkRevList()` which reads CAPK RID/keyId from kernel TLVs and delegates to `addCapkAndRevokeList()`.

### 4.5 `IEmvLoadLibCallback` (emvlib/base)

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/base/src/main/java/com/pax/emvlib/base/IEmvLoadLibCallback.java`

```java
public interface IEmvLoadLibCallback {
    void load();
}
```

Each submodule (`base`, `dpas`, `dpas2`) provides a `@RouterService` implementation. `EmvUtils.loadLibrary()` iterates all registered implementations via WMRouter.

### 4.6 `EmvParamConvert` (emvlib/base)

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/base/src/main/java/com/pax/emvlib/base/utils/EmvParamConvert.java`

Public static methods used across all kernels:

```java
public static EMV_APPLIST toEMVApp(EmvAid aidParam);
public static EMV_CAPK toEMVCapk(Capk capk);
public static Clss_PreProcInfo PayPassPreProcInfo(PayPassAid aid);
public static Clss_PreProcInfo PayWavePreProcInfo(PayWaveAid aid, byte transType);
public static Clss_PreProcInfo expressPayPreProcInfo(AmexAid aid);
public static Clss_PreProcInfo pbocPreProcInfo(PbocAid aid);
public static Clss_PreProcInfo dpasPreProcInfo(DpasAid aid);
public static Clss_PreProcInfo eftPreProcInfo(EFTAid aid);
public static Clss_PreProcInfo jcbPreProcInfo(JcbAid aid);
public static Clss_PreProcInfo mirPreProcInfo(MirAid aid);
public static Clss_PreProcInfo purePreProcInfo(PureAid aid);
public static Clss_PreProcInfo ruPayPreProcInfo(RuPayAid aid);
public static CvmResultEnum convertCVM(int result);
public static int getPayWaveInterFloorLimitIndexByTransType(byte transType, List<PayWaveInterFloorLimit> list);
```

### 4.7 `EmvKernelConst` (emvlib/base)

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/base/src/main/java/com/pax/emvlib/base/consts/EmvKernelConst.java`

```java
public static final String EMV = "EMV";
public static final String AMEX = "AMEX";
public static final String DPAS = "DPAS";
public static final String EFT = "EFT";
public static final String JCB = "JCB";
public static final String MC = "MC";
public static final String MIR = "MIR";
public static final String PAYWAVE = "PAYWAVE";
public static final String PBOC = "PBOC";
public static final String PURE = "PURE";
public static final String RUPAY = "RUPAY";
```

---

## 5. com.pax.jemv.* Packages

All `com.pax.jemv.*` code is **JAR only** (proprietary PAX binaries). No Java source exists in this repository.

### 5.1 `com.pax.jemv.clcommon` — COMMON_v103.jar (via emvbase)

**Role:** Shared data types and return codes used by all kernels.

**Classes:**

```
ACType, ByteArray, CLSS_TORN_LOG_RECORD, ClssTmAidList,
Clss_CandList, Clss_CandListEx, Clss_Combination, Clss_MCAidParam,
Clss_PreProcInfo, Clss_PreProcInfoEx, Clss_PreProcInterInfo,
Clss_ProgramID, Clss_ProgramID_II, Clss_ReaderParam, Clss_SchemeID_Info,
Clss_TransParam, Clss_VisaAidParam, CvmType, DDAFlag,
EMV_APPLIST, EMV_CAPK, EMV_REVOCLIST, KernType, OnlineResult,
OutcomeParam, RetCode, TransactionPath, TransactionResult, VisaSchemeID
```

### 5.2 `com.pax.jemv.device` — DEVICE_v103.jar (emvlib/base)

**Role:** Device abstraction layer bridging terminal hardware (ICC, PICC, PED, clock, random) to all kernels.

**Key classes:**

| Class | Role |
|-------|------|
| `DeviceManager` | Singleton; implements `IDevice`; delegates to app-provided device |
| `DeviceApi` | Native device API |
| `IDevice` | Interface for ICC APDU, PIN verify, time, random, RSA |
| `model.ApduSendL2` / `model.ApduRespL2` | APDU send/receive structures |
| `model.DeviceRetCode` | Device return codes |
| `model.RsaPinKeyL2` | RSA PIN key structure |
| `model.TransactionInterface` | Transaction interface enum |

### 5.3 `com.pax.jemv.entrypoint` — Entry_v105.jar (emvlib/base)

**Role:** L2 Entry Point kernel — PPSE selection, candidate list management, final AID select, kernel routing.

**Key classes:**

| Class | Role |
|-------|------|
| `api.ClssEntryApi` | All entry-point native methods (see Section 8) |
| `def.Clss_ASRPD` | ASRPD data structure |
| `listener.ClssEntryCBFunApi` | Entry callback function API |
| `listener.IClssEntryCBFun` | Entry callback interface |

### 5.4 `com.pax.jemv.emv` — EMV_v105.jar (dpas) / EMV_v106.jar (dpas2)

**Role:** Contact (chip) EMV L2 kernel — app select, GPO, read records, ODA, CVM, GAC, completion.

**Key classes:**

| Class | Role |
|-------|------|
| `api.EMVApi` | All contact EMV native methods (abstract base) |
| `api.EMVCallback` | Extends EMVApi; adds callback listener for app select, PIN, amount |
| `api.EMVCallback$EmvCallbackListener` | Callback interface for kernel → app events |
| `model.EmvParam` | Terminal EMV parameters |
| `model.EmvMCKParam` | MCK (merchant) parameters including bypass PIN, TRM AIP |
| `model.EmvEXTMParam` | Extended terminal parameters |
| `model.EmvTMECParam` | Terminal EC parameters |
| `model.AppLabelList` | Application label list |
| `model.EMV_CANDLIST` | Contact candidate list entry |
| `model.ElementAttr` | ICC tag element attribute |

**dpas2-only native method:** `EMVApi.DPASCTCoreInit()` (present in EMV_v106, absent from v105).

### 5.5 `com.pax.jemv.dpas` — DPAS_v100.jar (dpas) / DPAS_v101.jar (dpas2)

**Role:** D-PAS / ZIP contactless kernel.

**Key classes:**

| Class | Role |
|-------|------|
| `api.ClssDPASApi` | All DPAS contactless native methods (Section 2.4) |
| `listener.ClssDPASCBFunApi` | DPAS callback function API (dpas2 jar only) |
| `listener.IClssDPASCBFun` | DPAS callback interface (dpas2 jar only) |

### 5.6 `com.pax.jemv.ctdpas` — DPAS_CT_v100.jar (dpas2 only)

**Role:** D-PAS Connect contact (chip) extensions.

**Key classes:**

| Class | Role |
|-------|------|
| `api.CTDPASApi` | Contact DPAS Connect native methods |
| `listener.CTDPASCBFunApi` | CT DPAS callback API |
| `listener.ICTDPASCBFun` | CT DPAS callback interface |

```java
public class CTDPASApi {
    public static native int CT_CoreInit_DPAS();
    public static native int CT_ReadVerInfo_DPAS(ByteArray);
    public static native int CT_InitiateApp_DPAS();
    public static native int CT_StartCVM_DPAS(ByteArray, ByteArray);
    public static native int CT_CompleteCVM_DPAS(int, ByteArray, ByteArray);
    public static native int CT_CardAuthAdditionalCheck1_DPAS();
    public static native int CT_CardAuthAdditionalCheck2_DPAS();
    public static native int CT_WriteDataStorageProc_DPAS(byte[], int);
    public static native int CT_SetExtendFunction_DPAS(byte[]);
    public static native int CT_SetCBFun_SendEXData_DPAS();
    public static native int CT_GetDebugInfo_DPAS();
}
```

### 5.7 `com.pax.jemv.qpboc` — QPBOC_v100.jar (emvlib)

**Role:** qPBOC / UnionPay contactless kernel.

**Key classes:**

| Class | Role |
|-------|------|
| `api.ClssPbocApi` | qPBOC native methods |
| `api.ClssPbocCBFunApi` | qPBOC callback API |
| `api.ClssPbocCBFunApi$IClssPbocCBFun` | qPBOC callback interface |
| `model.Clss_PbocAidParam` | qPBOC AID parameters |
| `model.Clss_PbocTornConfig` | Torn transaction log config |

### 5.8 `com.pax.jemv.paypass` — MC_v100.jar (emvlib)

**Role:** Mastercard PayPass / MChip contactless kernel.

**Key classes:** `api.ClssPassApi`, `listener.ClssPassCBFunApi`, `listener.IClssPassCBFun`

### 5.9 `com.pax.jemv.paywave` — WAVE_v101.jar (emvlib)

**Role:** Visa payWave / qVSDC contactless kernel.

**Key classes:** `api.ClssWaveApi`

### 5.10 `com.pax.jemv.amex` — AE_v101.jar (emvlib)

**Role:** American Express ExpressPay contactless kernel.

**Key classes:** `api.ClssAmexApi`, `model.CLSS_AEAIDPARAM`, `model.Clss_ReaderParam_AE`, `model.Clss_AddReaderParam_AE`, `model.ONLINE_PARAM`, `model.TransactionMode`

### 5.11 `com.pax.jemv.jcb` — JCB_v100.jar (emvlib)

**Role:** JCB contactless kernel.

**Key classes:** `api.ClssJCBApi`

### 5.12 `com.pax.jemv.mir` — MIR_v100.jar (emvlib)

**Role:** MIR (National Payment Card System, Russia) contactless kernel.

**Key classes:** `api.ClssMIRApi`

### 5.13 `com.pax.jemv.pure` — PURE_v100.jar (emvlib)

**Role:** PURE contactless kernel.

**Key classes:** `api.ClssPUREApi`

### 5.14 `com.pax.jemv.rupay` — RuPay_v100.jar (emvlib)

**Role:** RuPay contactless kernel.

**Key classes:** `api.ClssRuPayApi`

### 5.15 `com.pax.jemv.eftpos` — EFT_v101_D1.jar (emvlib)

**Role:** EFTPOS contactless kernel.

**Key classes:** `api.ClssEFTPOSApi`, `model.Clss_EFTAidParam`

---

## 6. System.loadLibrary → Native .so Mapping

### 6.1 Orchestrator

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/utils/EmvUtils.java`

```java
public static void loadLibrary() {
    // 1. WMRouter: all IEmvLoadLibCallback implementations
    List<IEmvLoadLibCallback> callbackList = Router.getAllServices(IEmvLoadLibCallback.class);
    for (IEmvLoadLibCallback callback : callbackList) {
        callback.load();
    }
    // 2. Direct loads for 8 contactless kernels (lines 46-80)
}
```

Load order: base → dpas (or dpas2 if swapped) → then EmvUtils direct loads.

### 6.2 Complete Mapping Table

| Java Class | File | `System.loadLibrary(...)` | Native `.so` File |
|------------|------|---------------------------|-------------------|
| `com.pax.emvlib.base.EmvLoadLibImpl` | `emvlib/base/.../EmvLoadLibImpl.java:29` | `"F_DEVICE_LIB_PayDroid"` | `libF_DEVICE_LIB_PayDroid.so` |
| | `:32` | `"F_PUBLIC_LIB_PayDroid"` | `libF_PUBLIC_LIB_PayDroid.so` |
| | `:35` | `"F_ENTRY_LIB_PayDroid"` | `libF_ENTRY_LIB_PayDroid.so` |
| | `:36` | `"JNI_ENTRY_v105"` | `libJNI_ENTRY_v105.so` |
| `com.pax.emvlib.dpas.EmvLoadLibImpl` | `emvlib/dpas/.../EmvLoadLibImpl.java:33` | `"F_EMV_LIBC_PayDroid"` | `libF_EMV_LIBC_PayDroid.so` |
| | `:34` | `"F_EMV_LIB_PayDroid"` | `libF_EMV_LIB_PayDroid.so` |
| | `:35` | `"JNI_EMV_v105"` | `libJNI_EMV_v105.so` |
| | `:38` | `"F_DPAS_LIB_PayDroid"` | `libF_DPAS_LIB_PayDroid.so` |
| | `:39` | `"JNI_DPAS_v100"` | `libJNI_DPAS_v100.so` |
| `com.pax.emvlib.dpas2.EmvLoadLibImpl` | `emvlib/dpas2/.../EmvLoadLibImpl.java:33` | `"F_EMV_LIBC_PayDroid"` | `libF_EMV_LIBC_PayDroid.so` |
| | `:34` | `"F_EMV_LIB_PayDroid"` | `libF_EMV_LIB_PayDroid.so` |
| | `:35` | `"JNI_EMV_v106_DPASCT"` | `libJNI_EMV_v106_DPASCT.so` |
| | `:38` | `"F_DPAS_LIB_PayDroid"` | `libF_DPAS_LIB_PayDroid.so` |
| | `:39` | `"JNI_DPAS_v101"` | `libJNI_DPAS_v101.so` |
| | `:42` | `"F_DPAS_CT_LIB_PayDroid"` | `libF_DPAS_CT_LIB_PayDroid.so` |
| | `:43` | `"JNI_DPAS_CT_v100"` | `libJNI_DPAS_CT_v100.so` |
| `com.pax.emvlib.utils.EmvUtils` | `emvlib/.../EmvUtils.java:47` | `"F_MC_LIB_PayDroid"` | `libF_MC_LIB_PayDroid.so` |
| | `:48` | `"JNI_MC_v100_01"` | `libJNI_MC_v100_01.so` |
| | `:51` | `"F_WAVE_LIB_PayDroid"` | `libF_WAVE_LIB_PayDroid.so` |
| | `:52` | `"JNI_WAVE_v101"` | `libJNI_WAVE_v101.so` |
| | `:55` | `"F_AE_LIB_PayDroid"` | `libF_AE_LIB_PayDroid.so` |
| | `:56` | `"JNI_AE_v101"` | `libJNI_AE_v101.so` |
| | `:59` | `"F_JCB_LIB_PayDroid"` | `libF_JCB_LIB_PayDroid.so` |
| | `:60` | `"JNI_JCB_v100"` | `libJNI_JCB_v100.so` |
| | `:63` | `"F_MIR_LIB_PayDroid"` | `libF_MIR_LIB_PayDroid.so` |
| | `:64` | `"JNI_MIR_v100"` | `libJNI_MIR_v100.so` |
| | `:67` | `"F_QPBOC_LIB_PayDroid"` | `libF_QPBOC_LIB_PayDroid.so` |
| | `:68` | `"JNI_QPBOC_v100"` | `libJNI_QPBOC_v100.so` |
| | `:71` | `"F_PURE_LIB_PayDroid"` | `libF_PURE_LIB_PayDroid.so` |
| | `:72` | `"JNI_PURE_v100"` | `libJNI_PURE_v100.so` |
| | `:75` | `"F_RUPAY_LIB_PayDroid"` | `libF_RUPAY_LIB_PayDroid.so` |
| | `:76` | `"JNI_RUPAY_v100"` | `libJNI_RUPAY_v100.so` |
| | `:79` | `"F_EFT_LIB_PayDroid"` | `libF_EFT_LIB_PayDroid.so` |
| | `:80` | `"JNI_EFT_v101_D1"` | `libJNI_EFT_v101_D1.so` |

**Naming convention:** Each kernel has a `libF_<KERNEL>_LIB_PayDroid.so` (framework/library) paired with a `libJNI_<KERNEL>_v<version>.so` (JNI bridge). `F_PUBLIC_LIB_PayDroid` and `F_EMV_LIBC_PayDroid` are shared infrastructure libraries with no corresponding JAR.

---

## 7. Key Data / Return-Code Types

All types below are in **`/Users/macmin/Desktop/EmvEnhanceRefactor/emvbase/libs/COMMON_v103.jar`** (`com.pax.jemv.clcommon.*`).

### 7.1 `RetCode`

Return code constants (all `public static final int`):

```
EMV_OK, ICC_RESET_ERR, ICC_CMD_ERR, ICC_BLOCK, EMV_RSP_ERR, EMV_APP_BLOCK,
EMV_NO_APP, EMV_USER_CANCEL, EMV_TIME_OUT, EMV_DATA_ERR, EMV_NOT_ACCEPT,
EMV_DENIAL, EMV_KEY_EXP, EMV_NO_PINPAD, EMV_NO_PASSWORD, EMV_SUM_ERR,
EMV_NOT_FOUND, EMV_NO_DATA, EMV_OVERFLOW, NO_TRANS_LOG, RECORD_NOTEXIST,
LOGITEM_NOTEXIST, ICC_RSP_6985, EMV_PARAM_ERR, CLSS_USE_CONTACT, EMV_FILE_ERR,
CLSS_TERMINATE, CLSS_FAILED, CLSS_DECLINE, CLSS_TRY_ANOTHER_CARD, CLSS_PARAM_ERR,
CLSS_WAVE2_OVERSEA, CLSS_WAVE2_TERMINATED, CLSS_WAVE2_US_CARD, CLSS_WAVE3_INS_CARD,
CLSS_RESELECT_APP, CLSS_CARD_EXPIRED, EMV_NO_APP_PPSE_ERR, CLSS_USE_VSDC,
CLSS_CVMDECLINE, CLSS_REFER_CONSUMER_DEVICE, CLSS_LAST_CMD_ERR, CLSS_API_ORDER_ERR,
CLSS_TORN_CARDNUM_ERR, CLSS_TRON_AID_ERR, CLSS_TRON_AMT_ERR,
CLSS_CARD_EXPIRED_REQ_ONLINE, CLSS_FILE_NOT_FOUND, CLSS_TRY_AGAIN,
CLSS_REQ_JUSTOUCH_APP, CLSS_DECRYPT_FAILED, CLSS_AUTH_CONSUMER_DEVICE,
CLSS_PAYMENT_NOT_ACCEPT
```

### 7.2 `ByteArray`

```java
public byte[] data;
public int length;
public ByteArray();
public ByteArray(int initialCapacity);
```

Used as output buffer for all `GetTLVData*` native methods.

### 7.3 `Clss_TransParam`

```java
public long ulAmntAuth;
public long ulAmntOther;
public long ulTransNo;
public byte ucTransType;
public byte[] aucTransDate;    // 3 bytes BCD YYMMDD
public byte[] aucTransTime;    // 3 bytes BCD HHMMSS
public Clss_TransParam();
public Clss_TransParam(long ulAmntAuth, long ulAmntOther, long ulTransNo,
                       byte ucTransType, byte[] aucTransDate, byte[] aucTransTime);
```

Built in `ClssProcess.convertToClssTransParam()` at `ClssProcess.java:215-223`.

### 7.4 `Clss_PreProcInfo`

Per-AID pre-processing limits registered at entry-point init.

```java
public long ulTermFLmt;
public long ulRdClssTxnLmt;
public long ulRdCVMLmt;
public long ulRdClssFLmt;
public byte[] aucAID;
public byte ucAidLen;
public byte ucKernType;           // e.g. KernType.KERNTYPE_ZIP for DPAS
public byte ucCrypto17Flg;
public byte ucZeroAmtNoAllowed;
public byte ucStatusCheckFlg;
public byte[] aucReaderTTQ;         // Terminal Transaction Qualifiers (Tag 9F66)
public byte ucTermFLmtFlg;
public byte ucRdClssTxnLmtFlg;
public byte ucRdCVMLmtFlg;
public byte ucRdClssFLmtFlg;
public byte[] aucRFU;
```

For DPAS, built by `EmvParamConvert.dpasPreProcInfo(DpasAid)` at `EmvParamConvert.java:161-175` with `ucKernType = KernType.KERNTYPE_ZIP`.

### 7.5 `Clss_PreProcInterInfo`

Post-intersection flags returned by entry point after final select; passed to kernel wrappers.

```java
public byte[] aucAID;
public byte ucAidLen;
public byte ucZeroAmtFlg;
public byte ucStatusCheckFlg;
public byte[] aucReaderTTQ;
public byte ucCLAppNotAllowed;
public byte ucTermFLmtExceed;
public byte ucRdCLTxnLmtExceed;
public byte ucRdCVMLmtExceed;
public byte ucRdCLFLmtExceed;       // if 1, DPAS sets TVR byte 4 bit 8
public byte ucTermFLmtFlg;
public byte[] aucTermFLmt;
public byte ucCrypto17Flg;
public byte ucRandomSelect;
```

Obtained via `ClssEntryApi.Clss_GetPreProcInterFlg_Entry()` at `ClssProcess.java:146`.

### 7.6 `EMV_APPLIST`

Contact AID application list entry.

```java
public byte[] appName;
public byte[] aid;
public byte aidLen;
public byte selFlag;                // 0=partial, 1=full match
public byte priority;
public byte targetPer;
public byte maxTargetPer;
public byte floorLimitCheck;
public byte randTransSel;
public byte velocityCheck;
public long floorLimit;
public long threshold;
public byte[] tacDenial;
public byte[] tacOnline;
public byte[] tacDefault;
public byte[] acquierId;
public byte[] dDOL;
public byte[] tDOL;
public byte[] version;
public byte[] riskManData;
```

Built by `EmvParamConvert.toEMVApp(EmvAid)` at `EmvParamConvert.java:50-72`.

### 7.7 `EMV_CAPK`

```java
public byte[] rID;                  // 5-byte RID
public byte keyID;
public byte hashInd;
public byte arithInd;
public short modulLen;
public byte[] modul;
public byte exponentLen;
public byte[] exponent;
public byte[] expDate;
public byte[] checkSum;
```

### 7.8 `EMV_REVOCLIST`

```java
public byte[] ucRid;
public byte ucIndex;
public byte[] ucCertSn;
public EMV_REVOCLIST(byte[] rid, byte keyId, byte[] certSn);
```

### 7.9 `TransactionPath`

```java
public int path;
// DPAS constants:
public static final int CLSS_DPAS_MAG;
public static final int CLSS_DPAS_EMV;
public static final int CLSS_DPAS_ZIP;
// PBOC constants:
public static final int CLSS_VISA_QVSDC;
public static final int CLSS_VISA_VSDC;
// ... (Visa, MC, JCB paths also defined)
```

Set by `Clss_InitiateApp_DPAS(transactionPath)` — output parameter indicating MAG/EMV/ZIP path.

### 7.10 `OutcomeParam`

Outcome nibble constants for contactless result parsing:

```java
public static final int CLSS_OC_APPROVED;
public static final int CLSS_OC_DECLINED;
public static final int CLSS_OC_ONLINE_REQUEST;
public static final int CLSS_OC_END_APPLICATION;
public static final int CLSS_OC_SELECT_NEXT;
public static final int CLSS_OC_TRY_ANOTHER_INTERFACE;
public static final int CLSS_OC_TRY_AGAIN;
public static final int CLSS_OC_NO_CVM;
public static final int CLSS_OC_OBTAIN_SIGNATURE;
public static final int CLSS_OC_ONLINE_PIN;
public static final int CLSS_OC_CONFIRM_CODE_VER;
```

Read from Outcome Parameter Set (Tag DF8129 / `TagsTable.LIST`) after `Clss_TransProc_DPAS`.

### 7.11 `KernType`

```java
public int kernType;
public static final int KERNTYPE_DEF;
public static final int KERNTYPE_JCB;
public static final int KERNTYPE_MC;
public static final int KERNTYPE_VIS;
public static final int KERNTYPE_PBOC;
public static final int KERNTYPE_AE;
public static final int KERNTYPE_ZIP;       // DPAS
public static final int KERNTYPE_EFT;
public static final int KERNTYPE_PURE;
public static final int KERNTYPE_MIR;
public static final int KERNTYPE_RUPAY;
// ... additional types
```

Set as output by `ClssEntryApi.Clss_FinalSelect_Entry(kernType, daArray)`.

### 7.12 `ACType`

```java
public int type;
public static final int AC_AAC;     // Decline
public static final int AC_TC;      // Offline approve
public static final int AC_ARQC;    // Online request
```

Used by contact EMV (`EMVStartTrans`, `EMVCompleteTrans`) and qPBOC (`Clss_Proctrans_Pboc`).

### 7.13 `CvmType`

```java
public int type;
public static final int RD_CVM_NO;
public static final int RD_CVM_SIG;
public static final int RD_CVM_ONLINE_PIN;
public static final int RD_CVM_OFFLINE_PIN;
public static final int RD_CVM_CONSUMER_DEVICE;
public static final int RD_CVM_REQ_SIG;
public static final int RD_CVM_REQ_ONLINE_PIN;
```

### 7.14 `DDAFlag`

```java
public int flag;
public static final int SUCCESS;
public static final int FAIL;
```

Used by qPBOC `Clss_CardAuth_Pboc`.

### 7.15 `Clss_ReaderParam`

Terminal reader parameters for qPBOC/payWave kernels.

```java
public long ulReferCurrCon;
public short usMchLocLen;
public byte[] aucMchNameLoc;
public byte[] aucMerchCatCode;
public byte[] aucMerchantID;
public byte[] acquierId;
public byte[] aucTmID;
public byte ucTmType;
public byte[] aucTmCap;
public byte[] aucTmCapAd;
public byte[] aucTmCntrCode;
public byte[] aucTmTransCur;
public byte ucTmTransCurExp;
public byte[] aucTmRefCurCode;
public byte ucTmRefCurExp;
public byte[] aucRFU;
```

---

## 8. Entry Point, ClssProcess AID Selection & Kernel Dispatch

### 8.1 AID Registration (`ClssEntryAddAid`)

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssEntryAddAid.java`

Called once during `ClssProcess.preTransProcess()` at line 83.

For each configured kernel param, registers AIDs and pre-proc info:

| Method | Kernel | KernType set in PreProcInfo | File:Line |
|--------|--------|----------------------------|-----------|
| `addAmexAid()` | Amex | `KERNTYPE_AE` | 86-97 |
| `addPayPassAid()` | PayPass | `KERNTYPE_MC` | 57-68 |
| `addPayWaveAid()` | payWave | `KERNTYPE_VIS` | 71-83 |
| `addDpasAid()` | **DPAS** | **`KERNTYPE_ZIP`** | **100-111** |
| `addEFTAid()` | EFTPOS | `KERNTYPE_EFT` | 114-125 |
| `addJcbAid()` | JCB | `KERNTYPE_JCB` | 128-139 |
| `addMirAid()` | MIR | `KERNTYPE_MIR` | 142-153 |
| `addPbocAid()` | qPBOC | `KERNTYPE_PBOC` | 156-166 |
| `addPureAid()` | PURE | `KERNTYPE_PURE` | 169-180 |
| `addRuPayAid()` | RuPay | `KERNTYPE_RUPAY` | 183-194 |

Each AID registration follows the same two-step pattern:

```java
ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte) aid.getAid().length,
        aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
Clss_PreProcInfo info = EmvParamConvert.<kernel>PreProcInfo(aid);
ClssEntryApi.Clss_SetPreProcInfo_Entry(info);
```

**DPAS-specific** (`addDpasAid`, lines 100-111):

```java
ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte) aid.getAid().length,
        aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
Clss_PreProcInfo clssPreProcInfo = EmvParamConvert.dpasPreProcInfo(aid);
ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
```

`dpasPreProcInfo` sets `ucKernType = KernType.KERNTYPE_ZIP` and copies TTQ from `DpasAid.getTtq()`.

### 8.2 Entry Point API — Full Method List

**Class:** `com.pax.jemv.entrypoint.api.ClssEntryApi` (Entry_v105.jar)

```java
public static native int Clss_CoreInit_Entry();
public static native int Clss_ReadVerInfo_Entry(ByteArray);
public static native int Clss_SetMCVersion_Entry(byte);
public static native int Clss_SetPreProcInfo_Entry(Clss_PreProcInfo);
public static native int Clss_SetPreProcInfoEx_Entry(Clss_PreProcInfoEx);
public static native void Clss_DelAllPreProcInfo();
public static native int Clss_DelPreProcInfo_Entry(byte[], byte);
public static native void Clss_DelAllAidList_Entry();
public static native int Clss_DelAidList_Entry(byte[], byte);
public static native int Clss_AddAidList_Entry(byte[], byte, byte, byte);
public static native int Clss_GetCandList_Entry(Clss_CandList[]);
public static native int Clss_SetCandList_Entry(Clss_CandList[], int);
public static native int Clss_GetCandListEx_Entry(Clss_CandListEx[]);
public static native int Clss_SetCandListEx_Entry(Clss_CandListEx[], int);
public static native int Clss_AddCombination_Entry(Clss_Combination);
public static native int Clss_DelCombination_Entry(byte[], byte, byte[], byte);
public static native void Clss_DelAllCombination_Entry();
public static native int Clss_GetPreProcInterFlgByAid_Entry(byte[], byte, byte[], byte, Clss_PreProcInterInfo);
public static native int Clss_SetAmount_Entry(byte[]);
public static native int Clss_PreTransProc_Entry(Clss_TransParam);
public static native int Clss_AppSlt_Entry(int, int);
public static native int Clss_FinalSelect_Entry(KernType, ByteArray);
public static native int Clss_GetFinalSelectData_Entry(ByteArray);
public static native int Clss_GetPreProcInterFlg_Entry(Clss_PreProcInterInfo);
public static native int Clss_DelCurCandApp_Entry();
public static native int Clss_GetErrorCode_Entry();
public static native int Clss_SetExtendFunction_Entry(byte[]);
public static native int Clss_GetExtendFunction_Entry(int, byte[], int);
public static native int clss_AppSelect_Entry_UnlockApp(Clss_TransParam, ClssTmAidList);
public static native int Clss_SetCBFun_AddAPDUToTransLog_Entry();
public static native int Clss_GetDebugInfo_Entry(int, byte[]);
public static native int Clss_SetTLVDataList_Entry(byte[], int);
public static native int Clss_GetTLVData_Entry(byte[], int, ByteArray);
public static native int Clss_GetASRPDByIndex_Entry(int, Clss_ASRPD);
```

### 8.3 ClssProcess AID Selection Loop

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssProcess.java`

#### Pre-Transaction (lines 71-86)

1. `ClssEntryApi.Clss_CoreInit_Entry()` — line 74
2. `ClssEntryApi.Clss_DelAllAidList_Entry()` — line 79
3. `ClssEntryApi.Clss_DelAllPreProcInfo()` — line 80
4. `clssEntryAddAid.addApp()` — line 83 (registers all kernel AIDs)
5. `ClssEntryApi.Clss_SetMCVersion_Entry((byte) 0x03)` — line 85
6. `ClssEntryApi.Clss_PreTransProc_Entry(convertToClssTransParam())` — line 86

#### Start Transaction — Candidate Selection Loop (lines 102-180)

```
while (true) {
    // Step 1: Build candidate list from PPSE
    ClssEntryApi.Clss_AppSlt_Entry(0, 0)                          // line 105

    // Step 2: Final select — sets kernType.kernType
    ClssEntryApi.Clss_FinalSelect_Entry(kernType, daArray)        // line 118

    // Step 2a: Reselect on block/error
    if (ret == EMV_RSP_ERR || EMV_APP_BLOCK || ICC_BLOCK || CLSS_RESELECT_APP) {
        ClssEntryApi.Clss_DelCurCandApp_Entry()                   // line 122
        continue;  // try next candidate
    }

    // Step 3: Get pre-proc intersection flags
    ClssEntryApi.Clss_GetPreProcInterFlg_Entry(clssPreProcInterInfo) // line 146

    // Step 4: Get final select data for kernel
    ClssEntryApi.Clss_GetFinalSelectData_Entry(finalSelectData)    // line 153

    // Step 5: DISPATCH to kernel by kernType.kernType
    clssKernelProcess = new ClssKernelProcessFactory(kernType.kernType)
        .setEmvProcessParam(...)
        .setClssTransParam(...)
        .setFinalSelectData(...)
        .setPreProcInterInfo(...)
        .setClssStatusListener(...)
        .build();                                                 // lines 159-165

    // Step 6: Run kernel
    transResult = clssKernelProcess.startTransProcess()           // line 167

    // Step 6a: Kernel requests reselect
    if (transResult.getResultCode() == CLSS_RESELECT_APP) {
        ClssEntryApi.Clss_DelCurCandApp_Entry()                   // line 169
        continue;
    }

    return transResult;  // done
}
```

### 8.4 Kernel Dispatch by Kernel ID

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssKernelProcessFactory.java`

The `kernType.kernType` integer (set by `Clss_FinalSelect_Entry`) is passed to `ClssKernelProcessFactory` constructor at `ClssProcess.java:159`.

Dispatch occurs in `getKernelProcess(int kernelType)` at lines 49-83:

| `kernType.kernType` constant | WMRouter key | Param loaded | ClssKernelProcess impl | File |
|------------------------------|--------------|--------------|----------------------|------|
| `KernType.KERNTYPE_VIS` | `EmvKernelConst.PAYWAVE` | `getPayWaveParam()` | `ClssPayWaveProcess` | emvlib root |
| `KernType.KERNTYPE_MC` | `EmvKernelConst.MC` | `getPayPassParam()` | `ClssPayPassProcess` | emvlib root |
| `KernType.KERNTYPE_AE` | `EmvKernelConst.AMEX` | `getAmexParam()` | `ClssAEProcess` | emvlib root |
| `KernType.KERNTYPE_PBOC` | `EmvKernelConst.PBOC` | `getPbocParam()` | `ClssPbocProcess` | emvlib root |
| `KernType.KERNTYPE_EFT` | `EmvKernelConst.EFT` | `getEftParam()` | `ClssEFTProcess` | emvlib root |
| `KernType.KERNTYPE_JCB` | `EmvKernelConst.JCB` | `getJcbParam()` | `ClssJcbProcess` | emvlib root |
| `KernType.KERNTYPE_MIR` | `EmvKernelConst.MIR` | `getMirParam()` | `ClssMirProcess` | emvlib root |
| `KernType.KERNTYPE_PURE` | `EmvKernelConst.PURE` | `getPureParam()` | `ClssPureProcess` | emvlib root |
| `KernType.KERNTYPE_RUPAY` | `EmvKernelConst.RUPAY` | `getRuPayParam()` | `ClssRuPayProcess` | emvlib root |
| **`KernType.KERNTYPE_ZIP`** | **`EmvKernelConst.DPAS`** | **`getDpasParam()`** | **`ClssDpasProcess`** | **emvlib/dpas** (active) |

Example dispatch for DPAS (lines 78-80):

```java
case KernType.KERNTYPE_ZIP:
    clssParam = emvProcessParam.getDpasParam();
    return Router.getService(ClssKernelProcess.class, EmvKernelConst.DPAS);
```

`build()` at lines 112-121 then chains setters and loads the selected AID's params:

```java
return getKernelProcess(kernelType)
    .setEmvProcessParam(emvProcessParam)
    .setClssTransParam(transParam)
    .setFinalSelectData(finalSelectData, finalSelectDataLen)
    .setPreProcInterInfo(preProcInterInfo)
    .setClssStatusListener(clssStatusListener)
    .setClssParam(clssParam.loadSelectedAid(finalSelectData)
                    .loadFromConfig(emvProcessParam.getTermConfig())
                    .loadFromEmvTransParam(emvProcessParam.getEmvTransParam()));
```

WMRouter resolves `EmvKernelConst.DPAS` ("DPAS") to whichever `ClssDpasProcess` is on the classpath — `com.pax.emvlib.dpas.contactless.ClssDpasProcess` in the active build.

### 8.5 End-to-End Flow Diagram (Contactless, DPAS Card)

```
EmvPreProcessService.start()
  └─ ContactlessService.preTransProcess()
       └─ ClssProcess.preTransProcess()
            ├─ ClssEntryApi.Clss_CoreInit_Entry()
            ├─ ClssEntryAddAid.addApp()  [all kernels incl. DPAS AIDs]
            └─ ClssEntryApi.Clss_PreTransProc_Entry(Clss_TransParam)

ContactlessEmvRunner.start()
  └─ ContactlessService.startTransProcess()
       └─ ClssProcess.startTransProcess()
            ├─ ClssEntryApi.Clss_AppSlt_Entry()           ← PPSE / candidates
            ├─ ClssEntryApi.Clss_FinalSelect_Entry()     ← AID select, kernType=KERNTYPE_ZIP
            ├─ ClssEntryApi.Clss_GetPreProcInterFlg_Entry()
            ├─ ClssEntryApi.Clss_GetFinalSelectData_Entry()
            ├─ ClssKernelProcessFactory(KERNTYPE_ZIP).build()
            │    └─ Router → ClssDpasProcess (dpas module)
            └─ ClssDpasProcess.startTransProcess()
                 ├─ ClssDPASApi.Clss_CoreInit_DPAS()
                 ├─ ClssDPASApi.Clss_SetFinalSelectData_DPAS()
                 ├─ setParam() → Clss_SetTLVDataList_DPAS()
                 ├─ ClssDPASApi.Clss_InitiateApp_DPAS()    ← GPO
                 ├─ ClssDPASApi.Clss_ReadData_DPAS()       ← Read Records
                 ├─ addCapkRevList() → Clss_AddCAPK/RevocList_DPAS()
                 ├─ ClssDPASApi.Clss_TransProc_DPAS()     ← ODA+CVM+TAA+GAC
                 └─ genTransResult() → Outcome Parameter Set

       └─ (if online) ContactlessService handles PIN/online/2nd tap
            └─ ClssDpasProcess.completeTransProcess()
                 └─ ClssDPASApi.Clss_IssuerUpdateProc_DPAS()
```

---

*Document generated from source at `/Users/macmin/Desktop/EmvEnhanceRefactor`. All file paths are absolute from project root.*
