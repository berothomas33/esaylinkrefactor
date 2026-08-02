# PAX EMV Service Layer — Complete Architecture Reference

**Project:** `/Users/macmin/Desktop/EmvEnhanceRefactor`  
**Modules covered:** `emvservice/export`, `emvservice/emv`, `emvbase` (callbacks), `emvlib` (kernel delegation)  
**Purpose:** Source material for end-to-end EMV transaction architecture/sequence diagrams.

**Architecture note:** `emvservice/emv` is a thin WMRouter-registered facade. All kernel I/O is delegated to `emvlib` (`EmvProcess` for contact, `ClssProcess` for contactless), which calls `com.pax.jemv.*` Java wrappers backed by JNI native libraries loaded via `System.loadLibrary`. The active contact kernel build uses `emvlib:dpas` (not `dpas2`), registered as `BaseContactProcess` key `"EMV"`.

---

## Table of Contents

1. [Public Interface Surface (`emvservice/export`)](#1-public-interface-surface-emvserviceexport)
2. [Implementation Classes](#2-implementation-classes)
3. [CONTACT Flow — Numbered Call Sequence](#3-contact-flow--numbered-call-sequence)
4. [CONTACTLESS Flow — Numbered Call Sequence](#4-contactless-flow--numbered-call-sequence)
5. [Per-Kernel Contactless Sequences](#5-per-kernel-contactless-sequences)
6. [`com.pax.jemv.*` Classes and Native Libraries](#6-compaxjemv-classes-and-native-libraries)
7. [Contactless Kernel Selection / AID Dispatch](#7-contactless-kernel-selection--aid-dispatch)
8. [WMRouter `@RouterService` Registrations](#8-wmrouter-routerservice-registrations)
9. [EMV Phase → Code Mapping](#9-emv-phase--code-mapping)

---

## 1. Public Interface Surface (`emvservice/export`)

All paths relative to: `/Users/macmin/Desktop/EmvEnhanceRefactor/emvservice/export/src/main/java/com/pax/emvservice/export/`

### 1.1 `EmvServiceConstant`

**File:** `EmvServiceConstant.java`

```java
public class EmvServiceConstant {
    public static final String EMVSERVICE_PARAM = "emvService_param";
    public static final String EMVSERVICE_CONTACT = "emvService_contact";
    public static final String EMVSERVICE_CONTACTLESS = "emvService_contactLess";
    public static final String EMVSERVICE_EMV_VERSION = "emvService_emvVersion";
    public static final String EMVSERVICE_MANUAL_CARD = "emvService_manualCard";
    public static final String EMVSERVICE_MAG_CARD = "emvService_magCard";
    public static final String EMVSERVICE_PIN = "emvService_pin";
    public static final String EMVSERVICE_EMV_RSP = "emvService_emvResponse";
    public static final String EMVSERVICE_EMV_STATUS_CHECK = "emvService_status_check";
}
```

Constants `EMVSERVICE_EMV_RSP`, `EMVSERVICE_EMV_STATUS_CHECK`, and `EMVSERVICE_PARAM` are defined but no `@RouterService` implementation was found in this repository for them.

---

### 1.2 `IEmvBase`

**File:** `IEmvBase.java`

```java
public interface IEmvBase {
    public byte[] getTlv(int tag);
    void setTlv(int tag, byte[] value);
    @NonNull String getPan();
    @NonNull String getPanBlock();
    String getMaskedPan(String pattern);
    String getExpireDate();
    String getCardholderName();
    Issuer getMatchedIssuerByPan();
    String getTrack2Data();
}
```

---

### 1.3 `IEmvContactService extends IEmvBase`

**File:** `IEmvContactService.java`

```java
public interface IEmvContactService extends IEmvBase {
    void setUserCancel(boolean userCancel);
    void timeOut(boolean isTimeOut);
    boolean isTimeOut();
    boolean isUserCancel();
    int preTransProcess(EmvProcessParam emvProcessParam);
    int startTransProcess(IContactCallback contactCallback);
    void checkContactResult(IContactResultListener listener);
}
```

**Implementation:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvservice/emv/src/main/java/com/pax/emvservice/emv/contact/EmvContactService.java`

---

### 1.4 `IEmvContactlessService extends IEmvBase`

**File:** `IEmvContactlessService.java`

```java
public interface IEmvContactlessService extends IEmvBase {
    int preTransProcess(EmvProcessParam emvProcessParam);
    int startTransProcess(IContactlessCallback contactlessCallback);
    int getKernelType();
    void checkClsResult(IContactlessResultListener clsResultListener);
    byte[] getCapability();
    boolean getIsLastNeedSeePhone();
    void setIsLastNeedSeePhone(boolean isLastNeedSeePhone);
}
```

**Implementation:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvservice/emv/src/main/java/com/pax/emvservice/emv/contactless/ContactlessService.java`

---

### 1.5 `IContactResultListener`

**File:** `contact/IContactResultListener.java`

```java
public interface IContactResultListener {
    void fallback();
    void onlineDenied();
    void offlineDenied(int resultCode);
    void onlineCardDenied(int resultCode);
    void offlineApproved(boolean needSignature, boolean needSetARC);
    void onlineApproved(boolean needSignature);
    void onlineFailed();
    void simpleFlowEnd();
}
```

---

### 1.6 `IContactlessResultListener`

**File:** `contactless/IContactlessResultListener.java`

```java
public interface IContactlessResultListener {
    void seePhone();
    void tryAnotherInterface();
    void tryAgain();
    void onlineDenied();
    void offlineDenied(int resultCode);
    void onlineCardDenied(int resultCode);
    void offlineApproved(boolean needSignature);
    void onlineApproved(boolean needSignature);
    void onlineFailed();
    void simpleFlowEnd();
}
```

---

### 1.7 Transaction Callbacks (in `emvbase`, passed into service methods)

**`IContactCallback`** — `/Users/macmin/Desktop/EmvEnhanceRefactor/emvbase/src/main/java/com/pax/emvbase/process/contact/IContactCallback.java`

```java
public interface IContactCallback {
    int onWaitAppSelect(boolean isFirstSelect, List<CandidateAID> candList);
    int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes, byte[] pinData);
    int showConfirmCard();
    int showEnterTip();
    OnlineResultWrapper startOnlineProcess();
}
```

**`IContactlessCallback`** — `/Users/macmin/Desktop/EmvEnhanceRefactor/emvbase/src/main/java/com/pax/emvbase/process/contactless/IContactlessCallback.java`

```java
public interface IContactlessCallback {
    void onReadCardOk();
    void onRemoveCard();
    boolean needSeePhone();
    int confirmCard();
    int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes, byte[] pinData);
    int showEnterTip();
    OnlineResultWrapper startOnlineProcess();
    void onDetect2ndTap();
}
```

---

### 1.8 `IEmvCallback` (abstract class)

**File:** `IEmvCallback.java`

```java
public abstract class IEmvCallback {
    private final ConditionVariable cv;
    private final IEmvBase emvBase;
    public IEmvCallback(ConditionVariable cv, IEmvBase emvBase);
}
```

No public methods beyond constructor.

---

### 1.9 `IPinService`

**File:** `pin/IPinService.java`

```java
public interface IPinService {
    byte[] externalOfflinePinData(String encryptPinData, String panBlock);
    byte[] getEncryptedPinData(String panBlock, boolean supportBypass, boolean landscape) throws PinException;
    void setInputPinListener(@Nullable PinInputCallback.Callback pedInputPinListener);
}
```

---

### 1.10 `PinInputCallback`

**File:** `pin/PinInputCallback.java`

```java
public interface PinInputCallback {
    interface Callback {
        void keyEvent(EKeyCode key);
    }
    interface PCICallback extends Callback {}
    interface NormalCallback extends Callback {}
    public enum EKeyCode {
        KEY_0, KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6, KEY_7, KEY_8, KEY_9,
        KEY_ENTER, KEY_CANCEL, KEY_CLEAR, KEY_FUNC, KEY_ALPH, KEY_STAR, NO_KEY;
    }
}
```

---

### 1.11 `IEmvVersionService`

**File:** `IEmvVersionService.java`

```java
public interface IEmvVersionService {
    String getEmvVersion();
    String getEntryVersion();
    String getPayPassVersion();
    String getPayWaveVersion();
    String getRupayVersion();
    String getPbocVersion();
    String getJcbVersion();
    String getAmexVersion();
    String getDpasVersion();
    String getMIRVersion();
    String getPureVersion();
    String getEFTVersion();
}
```

---

### 1.12 `IEmvCardInfoService`

**File:** `IEmvCardInfoService.java`

```java
public interface IEmvCardInfoService {
    String getTrack1();
    String getTrack2();
    String getTrack3();
    String getPan();
    String getMaskedPan();
    String getCardSequence();
    String getExpireDate();
    String getCardHolderName();
    String getServiceCode();
}
```

---

### 1.13 `IMagCardService`

**File:** `IMagCardService.java`

```java
public interface IMagCardService {
    void magRead();
    @Nullable String getTrack1();
    @Nullable String getTrack2();
    @Nullable String getTrack3();
    @Nullable String getPan();
    @NonNull String getPanBlock();
    @Nullable String getMaskedPan(String pattern);
    @Nullable String getExpireDate();
    @Nullable String getCardholderName();
    @Nullable Issuer getMatchedIssuerByPan();
    boolean isValidPan();
    @Nullable String getServiceCode();
}
```

---

### 1.14 `IManualCardService`

**File:** `IManualCardService.java`

```java
public interface IManualCardService {
    String getPan(String cardNo);
    String getMaskedPan(String cardNo, String pattern);
    String getPanBlock(String cardNo);
}
```

---

### 1.15 `ICardInfoValidationHelper`

**File:** `ICardInfoValidationHelper.java` — empty class, no methods.

---

### 1.16 `PinException`

**File:** `exceptions/PinException.java`

```java
public class PinException extends Exception {
    public PinException(String errCode, String errMsg);
    public PinException(String module, String errCode, String errMsg);
    public PinException(String module, String errCode, String errMsg, String extraInfo);
    public PinException(String module, String errCode, String errMsg, Throwable throwable);
    public PinException(String module, String errCode, String errMsg, String extraInfo, Throwable throwable);
    public String getErrModule();
    public String getErrCode();
    public String getErrMsg();
}
```

---

## 2. Implementation Classes

### 2.1 Service layer (`emvservice/emv`)

| Class | Implements | Delegates to |
|---|---|---|
| `EmvContactService` | `IEmvContactService` | `EmvProcess.getInstance()` |
| `ContactlessService` | `IEmvContactlessService` | `ClssProcess.getInstance()` |
| `EmvVersionService` | `IEmvVersionService` | `com.pax.jemv.*` version APIs |
| `PinService` | `IPinService` | POS PIN hardware |
| `MagCardService` | `IMagCardService` | Magnetic stripe reader |
| `ManualCardService` | `IManualCardService` | PAN utilities |
| `EmvInit` | `IModuleInit` | `EmvUtils.loadLibrary()` |

### 2.2 Kernel delegation layer (`emvlib`)

| Class | Role |
|---|---|
| `EmvProcess` | Singleton facade; resolves `BaseContactProcess` via Router key `"EMV"` |
| `ClssProcess` | Contactless entry-point orchestrator; dispatches to `ClssKernelProcess` by `kernType.kernType` |
| `ClssKernelProcessFactory` | Maps `KernType.*` → Router key → kernel process instance |
| `ContactProcess` (in `emvlib/dpas`) | Contact EMV via `EMVCallback` |
| `ClssPayPassProcess`, `ClssPayWaveProcess`, `ClssPbocProcess`, etc. | Per-scheme contactless kernels |

### 2.3 Abstract base (`emvbase`)

**`EmvBase`** — `/Users/macmin/Desktop/EmvEnhanceRefactor/emvbase/src/main/java/com/pax/emvbase/process/EmvBase.java`

```java
public abstract class EmvBase {
    public abstract int preTransProcess(EmvProcessParam emvParam);
    public abstract TransResult startTransProcess();
    public abstract TransResult completeTransProcess(IssuerRspData issuerRspData);
    public abstract byte[] getTlv(int tag);
    public abstract void setTlv(int tag, byte[] value);
}
```

Documented phases in `EmvBase` javadoc:
- `preTransProcess`: core/entry init + add AID
- `startTransProcess`: detect card → select app → init app → read app data → ODA → TRM → cardholder auth → terminal behavior analysis → 1st GENERATE AC
- `completeTransProcess`: issuer authentication → script processing → complete trans (2nd GENERATE AC)

## 3. CONTACT Flow — Numbered Call Sequence

**Entry point:** `IEmvContactService.startTransProcess(IContactCallback)`  
**Service impl:** `EmvContactService` → `EmvProcess` → `ContactProcess` (`emvlib/dpas`) → `com.pax.jemv.emv.api.EMVCallback`

### 3.1 Phase A — Init (`preTransProcess`)

| # | Method / Kernel Call | File:Line |
|---|---|---|
| 1 | `EmvContactService.preTransProcess(EmvProcessParam emvProcessParam)` | `/Users/macmin/Desktop/EmvEnhanceRefactor/emvservice/emv/src/main/java/com/pax/emvservice/emv/contact/EmvContactService.java:96` |
| 2 | `EmvProcess.getInstance().preTransProcess(emvProcessParam)` | `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contact/EmvProcess.java:59` |
| 3 | `Router.getService(BaseContactProcess.class, EmvKernelConst.EMV)` → returns `ContactProcess` | `EmvProcess.java:38` |
| 4 | `ContactProcess.preTransProcess(emvProcessParam)` — stores capk/revoke/aid/trans/term config | `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/src/main/java/com/pax/emvlib/dpas/contact/ContactProcess.java:84` |
| 5 | **`EMVCallback.EMVCoreInit()`** | `ContactProcess.java:93` |
| 6 | `setEmvAndMCKParam()` | `ContactProcess.java:98` |
| 7 | **`EMVCallback.EMVSetCallback()`** | `ContactProcess.java:115` |
| 8 | **`EMVCallback.EMVGetParameter(EmvParam emvParam)`** | `ContactProcess.java:116` |
| 9 | **`EMVCallback.EMVGetMCKParam(EmvMCKParam mckParam)`** | `ContactProcess.java:117` |
| 10 | `EMVCallback.getInstance()` → `emvCallback.setCallbackListener(new EmvCallBackListener())` | `ContactProcess.java:118-119` |
| 11 | **`EMVCallback.EMVSetParameter(EmvParam emvParam)`** | `ContactProcess.java:152` |
| 12 | **`EMVCallback.EMVSetMCKParam(EmvMCKParam mckParam)`** | `ContactProcess.java:153` |
| 13 | **`EMVCallback.EMVSetPCIModeParam(pciMode, pinLenSet, pciTimeout)`** | `ContactProcess.java:105` |
| 14 | `addAID(List<EmvAid> aidList)` — loop: **`EMVCallback.EMVAddApp(EMV_APPLIST)`** via `EmvParamConvert.toEMVApp(emvAid)` | `ContactProcess.java:107, 156-174` |

### 3.2 Phase B — Transaction (`startTransProcess`) — 1st pass

| # | EMV Phase | Method / Kernel Call | File:Line |
|---|---|---|---|
| 15 | Register callback | `EmvContactService.startTransProcess` → `EmvProcess.registerEmvProcessListener(contactCallback)` → `ContactProcess.registerEmvProcessListener` | `EmvContactService.java:113`, `EmvProcess.java:52-55`, `ContactProcess.java:78-81` |
| 16 | Reset state | `cachedTrack2Data = null`, `timeOut(false)`, `setUserCancel(false)` | `EmvContactService.java:110-112` |
| 17 | Delegate | `EmvProcess.getInstance().startTransProcess()` → `ContactProcess.startTransProcess()` | `EmvContactService.java:114`, `EmvProcess.java:67-69`, `ContactProcess.java:177` |
| 18 | **Application selection** | **`EMVCallback.EMVAppSelect(0, transParam.getTransTraceNo())`** | `ContactProcess.java:181` |
| 18a | *(kernel callback during EMVAppSelect)* **Candidate list UI** | `EmvCallBackListener.emvWaitAppSel(int tryCnt, EMV_APPLIST[] appLists, int appNum)` → builds `List<CandidateAID>` → `IContactCallback.onWaitAppSelect(boolean isFirstSelect, List<CandidateAID> candList)` → `emvCallback.setCallBackResult(int index)` | `ContactProcess.java:459-475` |
| 18b | *(kernel callback during EMVAppSelect/ReadAppData, before GPO)* **AID-specific param reset** | `EmvCallBackListener.emvSetParam()` → **`EMVCallback.EMVGetTLVData((short) 0x4F, ByteArray)`** → `resetParam(byte[] aid)` → **`EMVCallback.EMVGetParameter`**, **`EMVCallback.EMVSetParameter`**, **`EMVCallback.EMVSetTLVData(0x9F33)`**, **`EMVCallback.EMVSetTLVData(0x9F40)`**, **`EMVCallback.EMVSetTLVData(0x9F35)`**, **`EMVCallback.EMVGetMCKParam`**, **`EMVCallback.EMVSetMCKParam`** | `ContactProcess.java:599-617, 405-451` |
| 19 | **Read application data** | **`EMVCallback.EMVReadAppData()`** | `ContactProcess.java:197` |
| 20 | Card confirm UI (app layer, not kernel callback) | `IContactCallback.showConfirmCard()` | `ContactProcess.java:202-206` |
| 21 | Set transaction type tag | `changeTAG9CValue()` → `setTlv(0x9C, new byte[]{emvParam.transType})` → **`EMVCallback.EMVSetTLVData((short) tag, value, len)`** | `ContactProcess.java:209`, `254-256`, `398-401` |
| 22 | **ODA preparation — load CAPK/revocation** | `addCapk()` → **`EMVCallback.EMVGetTLVData(TagsTable.CAPK_RID)`** or **`EMVGetTLVData(0x84)`** → **`EMVCallback.EMVGetTLVData(TagsTable.CAPK_ID)`** → **`EMVCallback.EMVAddCAPK(EMV_CAPK)`** → **`EMVCallback.EMVDelAllRevocList()`** → **`EMVCallback.EMVAddRevocList(EMV_REVOCLIST)`** | `ContactProcess.java:212-213, 291-332` |
| 23 | **Offline data authentication (SDA/DDA/CDA)** | **`EMVCallback.EMVCardAuth()`** | `ContactProcess.java:214-217` |
| 24 | Simple flow early exit | if `transParam.getFlowType() == EmvTransParam.FLOWTYPE_SIMPLE` → return `RESULT_SIMPLE_FLOW_END` | `ContactProcess.java:220-222` |
| 25 | Large amount setup | if `authAmt > 0xFFFFFFFF` → **`EMVCallback.EMVSetAmount(amountBytes, amountOtherBytes)`** | `ContactProcess.java:231-233` |
| 26 | **Processing restrictions + CVM + TRM + TAA + 1st GENERATE AC** *(single kernel call)* | **`EMVCallback.EMVStartTrans(long authAmt, long cashbackAmt, ACType acType)`** | `ContactProcess.java:234-237` |
| 26a | *(kernel callback during EMVStartTrans)* **Amount input** | `EmvCallBackListener.emvInputAmount(long[] amt)` → sets `amt[0]`/`amt[1]` from `transParam` → `emvCallback.setCallBackResult(RetCode.EMV_OK)` | `ContactProcess.java:479-497` |
| 26b | *(kernel callback during EMVStartTrans)* **Cardholder verification / PIN** | `EmvCallBackListener.emvGetHolderPwd(int tryFlag, int remainCnt, byte[] pinData)` → `IContactCallback.onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes, byte[] pinData)` → `emvCallback.setCallBackResult(int result)` | `ContactProcess.java:500-541` |
| 26c | *(kernel callback)* **PIN OK notification** | `EmvCallBackListener.emvVerifyPINOK()` | `ContactProcess.java:549-551` |
| 26d | *(kernel callback)* **Terminal dynamic TLV provision** | `EmvCallBackListener.emvUnknowTLVData(short tag, ByteArray data)` → for tags `0x9A`, `0x9F1E`, `0x9F21`, `0x9F37`, `0xFF01`: **`DeviceManager.getInstance().getTime(byte[])`**, **`DeviceManager.getInstance().readSN(byte[])`**, **`DeviceManager.getInstance().getRand(byte[], int)`** | `ContactProcess.java:554-591` |
| 27 | Map 1st AC result | `acType.type == ACType.AC_TC` → `RESULT_OFFLINE_APPROVED`; `AC_AAC` → `RESULT_OFFLINE_DENIED`; else → `RESULT_REQ_ONLINE` | `ContactProcess.java:242-250` |
| 28 | Read CVM result | `getCvm()` → `getTlv(0x9F34)` → **`EMVCallback.EMVGetTLVData((short) tag, ByteArray)`** | `ContactProcess.java:249, 258-288, 381-389` |
| 29 | Return to service | `EmvContactService` receives `TransResult` | `EmvContactService.java:114-115` |

### 3.3 Phase C — Online + 2nd GENERATE AC (when `TransResultEnum.RESULT_REQ_ONLINE`)

| # | Method / Kernel Call | File:Line |
|---|---|---|
| 30 | Check online required | `transResultEnum == TransResultEnum.RESULT_REQ_ONLINE` | `EmvContactService.java:121` |
| 31 | **Online processing (app layer)** | `IContactCallback.startOnlineProcess()` → returns `OnlineResultWrapper` | `EmvContactService.java:123` |
| 32 | Update trans result from host | `transResult.setResultCode(...)`, `transResult.setTransResult(...)` | `EmvContactService.java:128-129` |
| 33 | **2nd pass delegate** | `EmvProcess.getInstance().completeTransProcess(IssuerRspData issuerRspData)` → `ContactProcess.completeTransProcess(issuerRspData)` | `EmvContactService.java:144`, `EmvProcess.java:75-77`, `ContactProcess.java:336` |
| 34 | Set issuer auth data tags | `setTlv(0x89, issuerRspData.getAuthCode())` → **`EMVCallback.EMVSetTLVData`**; `setTlv(0x91, issuerRspData.getAuthData())` | `ContactProcess.java:339-351` |
| 35 | **Issuer authentication + script processing + 2nd GENERATE AC** | **`EMVCallback.EMVCompleteTrans(byte onlineResult, byte[] script, int scriptLen, ACType acType)`** | `ContactProcess.java:356` |
| 36 | On script failure debug | **`EMVCallback.EMVGetScriptResult(ByteArray scriptResult)`** | `ContactProcess.java:362-364` |
| 37 | Map 2nd AC | `acType.type == ACType.AC_TC` → `RESULT_ONLINE_APPROVED`; `AC_AAC` → `RESULT_ONLINE_CARD_DENIED` | `ContactProcess.java:372-376` |
| 38 | Service merges host + card outcome | Maps `EOnlineResult.APPROVE/FAILED/DENIAL` with 2nd AC result → `TransResultEnum.RESULT_ONLINE_APPROVED / RESULT_ONLINE_CARD_DENIED / RESULT_ONLINE_FAILED / RESULT_ONLINE_FAILED_CARD_APPROVED / RESULT_ONLINE_DENIED` | `EmvContactService.java:148-169` |
| 39 | Unregister listener | `EmvProcess.getInstance().registerEmvProcessListener(null)` | `EmvContactService.java:174` |

### 3.4 Phase D — Result dispatch

| # | Method | File:Line |
|---|---|---|
| 40 | `EmvContactService.checkContactResult(IContactResultListener listener)` — maps `TransResultEnum` + `CvmResultEnum` to listener methods (`fallback`, `offlineApproved`, `onlineApproved`, `onlineDenied`, `onlineCardDenied`, `onlineFailed`, `simpleFlowEnd`, `offlineDenied`) | `EmvContactService.java:274-321` |

### 3.5 TLV accessors (contact)

| # | Method | File:Line |
|---|---|---|
| — | `EmvContactService.getTlv(int tag)` → `EmvProcess.getTlv(tag)` → `ContactProcess.getTlv(tag)` → **`EMVCallback.EMVGetTLVData((short) tag, ByteArray value)`** | `EmvContactService.java:325-327`, `EmvProcess.java:83-87`, `ContactProcess.java:381-389` |
| — | `EmvContactService.setTlv(int tag, byte[] value)` → **`EMVCallback.EMVSetTLVData((short) tag, byte[] value, int len)`** | `EmvContactService.java:330-332`, `ContactProcess.java:398-401` |

### 3.6 Internal kernel callbacks not invoked in normal flow

These are implemented in `EmvCallBackListener` but are stubs or unused in the traced path:

| Callback method | File:Line | Notes |
|---|---|---|
| `emvAdviceProc()` | `ContactProcess.java:544-546` | Log only |
| `certVerify()` | `ContactProcess.java:594-596` | Empty (PBOC credential verify) |
| `emvVerifyPINfailed(byte[] reserved)` | `ContactProcess.java:620-622` | Returns 0 |
| `cRFU2()` | `ContactProcess.java:625-627` | Returns 0 |

## 4. CONTACTLESS Flow — Numbered Call Sequence

**Entry point:** `IEmvContactlessService.startTransProcess(IContactlessCallback)`  
**Service impl:** `ContactlessService` → `ClssProcess` → Entry Point kernel → `ClssKernelProcessFactory` → per-scheme `ClssXxxProcess` → `com.pax.jemv.*`

### 4.1 Phase A — Init (`preTransProcess`)

| # | Method / Kernel Call | File:Line |
|---|---|---|
| 1 | `ContactlessService.preTransProcess(EmvProcessParam emvProcessParam)` — stores `flowType` | `/Users/macmin/Desktop/EmvEnhanceRefactor/emvservice/emv/src/main/java/com/pax/emvservice/emv/contactless/ContactlessService.java:59-61` |
| 2 | `ClssProcess.getInstance().preTransProcess(emvProcessParam)` | `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssProcess.java:71` |
| 3 | `new ClssEntryAddAid(emvProcessParam)` | `ClssProcess.java:73` |
| 4 | **`ClssEntryApi.Clss_CoreInit_Entry()`** | `ClssProcess.java:74` |
| 5 | **`ClssEntryApi.Clss_DelAllAidList_Entry()`** | `ClssProcess.java:79` |
| 6 | **`ClssEntryApi.Clss_DelAllPreProcInfo()`** | `ClssProcess.java:80` |
| 7 | `clssEntryAddAid.addApp()` — registers all scheme AIDs (see Section 7) | `ClssProcess.java:83` |
| 8 | **`ClssEntryApi.Clss_SetMCVersion_Entry((byte) 0x03)`** | `ClssProcess.java:85` |
| 9 | `convertToClssTransParam()` — builds `Clss_TransParam` from `EmvTransParam` | `ClssProcess.java:86, 215-223` |
| 10 | **`ClssEntryApi.Clss_PreTransProc_Entry(Clss_TransParam transParam)`** | `ClssProcess.java:86` |

### 4.2 Phase B — Entry-point app selection + kernel dispatch (`ClssProcess.startTransProcess`)

| # | EMV Phase | Method / Kernel Call | File:Line |
|---|---|---|---|
| 11 | Register callback | `ContactlessService.startTransProcess` → `ClssProcess.registerClssProcessListener(contactlessCallback)` | `ContactlessService.java:74`, `ClssProcess.java:242-244` |
| 12 | Reset state | `cachedTrack2Data = null` | `ContactlessService.java:73` |
| 13 | **Build candidate list** | **`ClssEntryApi.Clss_AppSlt_Entry(0, 0)`** | `ClssProcess.java:105` |
| 14 | **Final select loop start** | `while (true)` | `ClssProcess.java:113` |
| 15 | **AID → kernel ID resolution** | **`ClssEntryApi.Clss_FinalSelect_Entry(KernType kernType, ByteArray daArray)`** — sets `kernType.kernType` | `ClssProcess.java:118` |
| 16 | Reselect on block/error | if ret in `{EMV_RSP_ERR, EMV_APP_BLOCK, ICC_BLOCK, CLSS_RESELECT_APP}` → **`ClssEntryApi.Clss_DelCurCandApp_Entry()`** → `continue` | `ClssProcess.java:120-127` |
| 17 | RuPay special case | if `kernType.kernType == KernType.KERNTYPE_RUPAY` and error → **`ClssEntryApi.Clss_GetExtendFunction_Entry(0x03, status, len)`** | `ClssProcess.java:129-141` |
| 18 | Get pre-processing flags | **`ClssEntryApi.Clss_GetPreProcInterFlg_Entry(Clss_PreProcInterInfo clssPreProcInterInfo)`** | `ClssProcess.java:146` |
| 19 | Get final select data | **`ClssEntryApi.Clss_GetFinalSelectData_Entry(ByteArray finalSelectData)`** | `ClssProcess.java:153` |
| 20 | **Kernel factory dispatch** | `new ClssKernelProcessFactory(kernType.kernType).setEmvProcessParam(...).setClssTransParam(...).setFinalSelectData(...).setPreProcInterInfo(...).setClssStatusListener(...).build()` | `ClssProcess.java:159-165` |
| 21 | Factory: resolve kernel | `Router.getService(ClssKernelProcess.class, EmvKernelConst.*)` (see Section 7 mapping) | `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssKernelProcessFactory.java:49-83, 112-121` |
| 22 | **Kernel transaction (1st pass)** | `clssKernelProcess.startTransProcess()` — per-kernel sequence in Section 5 | `ClssProcess.java:167` |
| 23 | Reselect on kernel request | if `transResult.getResultCode() == RetCode.CLSS_RESELECT_APP` → **`ClssEntryApi.Clss_DelCurCandApp_Entry()`** → `continue` | `ClssProcess.java:168-174` |
| 24 | Return kernel result | `return transResult` | `ClssProcess.java:179` |

### 4.3 Phase C — Service-layer post-kernel steps (`ContactlessService.startTransProcess`)

| # | Method | File:Line |
|---|---|---|
| 25 | Check kernel result | if `resultCode != RetCode.EMV_OK` → return | `ContactlessService.java:79-80` |
| 26 | **Card confirm UI** | `contactlessCallback.confirmCard()` | `ContactlessService.java:82` |
| 27 | Simple flow exit | if `flowType == EmvTransParam.FLOWTYPE_SIMPLE` → `RESULT_SIMPLE_FLOW_END` | `ContactlessService.java:89-91` |
| 28 | **Online PIN (service layer, before host)** | if `cvmResult == CVM_ONLINE_PIN \|\| CVM_ONLINE_PIN_SIG` → `contactlessCallback.onCardHolderPwd(true, true, 0, null)` | `ContactlessService.java:93-100` |
| 29 | **Online processing (app layer)** | if `transResultEnum == RESULT_REQ_ONLINE` → `contactlessCallback.startOnlineProcess()` → `OnlineResultWrapper` | `ContactlessService.java:103-107` |
| 30 | Host denied/failed short-circuit | if `onlineTransResultEnum != RESULT_ONLINE_APPROVED` → return without 2nd tap | `ContactlessService.java:109-112` |
| 31 | Second tap check | `ClssProcess.getInstance().isNeedSecondTap(issuerRspData)` → `clssKernelProcess.isNeedSecondTap(issuerRspData)` | `ContactlessService.java:114` |
| 32 | **Second tap + completion** | if `needSecondTap`: `contactlessCallback.onDetect2ndTap()` → `ClssProcess.completeTransProcess(issuerRspData)` → `clssKernelProcess.completeTransProcess(issuerRspData)` | `ContactlessService.java:116-117`, `ClssProcess.java:190-191` |
| 33 | No second tap | `transResult = new TransResult(EMV_OK, RESULT_ONLINE_APPROVED, CVM_NO_CVM)` | `ContactlessService.java:119` |
| 34 | Restore signature CVM | if original cvm was `CVM_SIG` or `CVM_ONLINE_PIN_SIG` → set `CVM_SIG` | `ContactlessService.java:122-124` |
| 35 | Unregister callback | `ClssProcess.unregisterClssProcessListener()` | `ContactlessService.java:128`, `ClssProcess.java:245-250` |

### 4.4 Phase D — Result dispatch

| # | Method | File:Line |
|---|---|---|
| 36 | `ContactlessService.checkClsResult(IContactlessResultListener clsResultListener)` — maps result codes to `seePhone`, `tryAnotherInterface`, `tryAgain`, `offlineApproved`, `onlineApproved`, `onlineDenied`, `onlineFailed`, `onlineCardDenied`, `offlineDenied`, `simpleFlowEnd` | `ContactlessService.java:148-197` |

### 4.5 TLV / capability accessors (contactless)

| # | Method / Kernel Call | File:Line |
|---|---|---|
| — | `ContactlessService.getTlv(int tag)` → `ClssProcess.getTlv(tag)` → `clssKernelProcess.getTlv(tag, ByteArray)` | `ContactlessService.java:233-235`, `ClssProcess.java:195-201` |
| — | `ContactlessService.setTlv(int tag, byte[] value)` → `clssKernelProcess.setTlv(tag, value)` | `ContactlessService.java:238-240`, `ClssProcess.java:211-213` |
| — | `ContactlessService.getCapability()` → **`EMVApi.EMVGetParameter(EmvParam emvParam)`** → returns `emvParam.capability` | `ContactlessService.java:206-209` |
| — | `ContactlessService.getKernelType()` → `ClssProcess.getKernType().kernType` | `ContactlessService.java:139-140`, `ClssProcess.java:234-236` |

## 5. Per-Kernel Contactless Sequences

After ClssProcess step 22 (`clssKernelProcess.startTransProcess()`), the following kernel-specific sequences execute.

### 5.1 PayPass / MC — `ClssPayPassProcess`

**Router key:** `EmvKernelConst.MC` = `"MC"`  
**Java API:** `com.pax.jemv.paypass.api.ClssPassApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssPayPassProcess.java`

| # | EMV Phase | Kernel Call | File:Line |
|---|---|---|---|
| K1 | Core init | **`ClssPassApi.Clss_CoreInit_MC(byte dataExchangeSupportFlag)`** | `73-77` |
| K2 | Set kernel TLV params | **`ClssPassApi.Clss_SetParam_MC(byte[] tlvParam, int len)`** | `81` |
| K3 | Register callback | `ClssPassCBFunApi.getInstance().setICBFun(clssPassListener)` → **`ClssPassApi.Clss_SetCBFun_SendTransDataOutput_MC()`** | `83-84` |
| K4 | Final select data | **`ClssPassApi.Clss_SetFinalSelectData_MC(byte[] finalSelectData, int finalSelectDataLen)`** | `86` |
| K5 | Set AID/term/trans params | `setPayPassParam()` → many **`ClssPassApi.Clss_SetTLVDataList_MC(...)`** and **`ClssPassApi.Clss_SetTagPresent_MC(...)`** | `342-395` |
| K6 | Application initialization | **`ClssPassApi.Clss_InitiateApp_MC()`** | `98` |
| K7 | Read application data | **`ClssPassApi.Clss_ReadData_MC(TransactionPath transactionPath)`** | `104` |
| K8 | ODA prep (MChip path) | **`ClssPassApi.Clss_DelAllRevocList_MC_MChip()`**, **`ClssPassApi.Clss_DelAllCAPK_MC_MChip()`**, `addCapkRevList()` → **`ClssPassApi.Clss_AddCAPK_MC_MChip(EMV_CAPK)`**, **`ClssPassApi.Clss_AddRevocList_MC_MChip(EMV_REVOCLIST)`** | `202-204, 51-70` |
| K9 | TRM + CVM + TAA + 1st GAC (MChip) | **`ClssPassApi.Clss_TransProc_MC_MChip(ACType acType)`** | `207` |
| K9b | Mag-stripe path | **`ClssPassApi.Clss_TransProc_MC_Mag(ACType acType)`** | `217` |
| K10 | UI callbacks | `clssStatusListener.onReadCardOk()`, `onRemoveCard()` | `123-125` |
| K11 | Outcome read (via callback) | `IClssPassCBFun.sendTransDataOutput(byte b)` → `getTlv(0xDF8129)` Outcome Parameter Set, `getTlv(0xDF8116)` User Interface Request | `426-438` |
| K12 | 2nd GAC / completion | **`completeTransProcess` is no-op** — returns `RESULT_ONLINE_APPROVED` | `226-227` |

---

### 5.2 payWave / Visa — `ClssPayWaveProcess`

**Router key:** `EmvKernelConst.PAYWAVE` = `"PAYWAVE"`  
**Java API:** `com.pax.jemv.paywave.api.ClssWaveApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssPayWaveProcess.java`

| # | EMV Phase | Kernel Call | File:Line |
|---|---|---|---|
| K1 | Core init | **`ClssWaveApi.Clss_CoreInit_Wave()`** | `173` |
| K2 | Reader params | **`ClssWaveApi.Clss_SetReaderParam_Wave(Clss_ReaderParam readerParam)`** | `183` |
| K3 | Final select data | **`ClssWaveApi.Clss_SetFinalSelectData_Wave(byte[] finalSelectData, int len)`** | `189` |
| K4 | Visa AID params | **`ClssWaveApi.Clss_SetVisaAidParam_Wave(Clss_VisaAidParam)`** | `152` |
| K5 | DRL (optional) | **`ClssWaveApi.Clss_SetDRLParam_Wave(Clss_ProgramID)`** if tag 0x9F5A present | `131, 158-166` |
| K6 | Trans data | **`ClssWaveApi.Clss_SetTransData_Wave(Clss_TransParam, Clss_PreProcInterInfo)`** | `169` |
| K7 | Main proc incl. 1st GAC | **`ClssWaveApi.Clss_Proctrans_Wave(TransactionPath transactionPath, ACType acType)`** | `207` |
| K8 | MSD path | **`ClssWaveApi.Clss_GetMSDType_Wave()`**, **`ClssWaveApi.Clss_nGetTrack2MapData_Wave(ByteArray)`** | `265-269` |
| K9 | QVSDC/WAVE2: processing restrictions | **`ClssWaveApi.Clss_ProcRestric_Wave()`** | `285` |
| K10 | QVSDC/WAVE2: ODA | **`ClssWaveApi.Clss_DelAllRevocList_Wave()`**, **`ClssWaveApi.Clss_DelAllCAPK_Wave()`**, `addCapkRevList()` → **`ClssWaveApi.Clss_AddCAPK_Wave`**, **`ClssWaveApi.Clss_AddRevocList_Wave`** | `295-297, 404-422` |
| K11 | QVSDC/WAVE2: card auth | **`ClssWaveApi.Clss_CardAuth_Wave(ACType acType, DDAFlag flag)`** | `303` |
| K12 | CVM read | **`ClssWaveApi.Clss_GetCvmType_Wave()`** | `316` |
| K13 | UI callbacks | `onReadCardOk()`, `onRemoveCard()` | `218-220` |
| K14 | **2nd tap: issuer auth + scripts** | **`ClssEntryApi.Clss_FinalSelect_Entry(KernType, ByteArray)`** → **`ClssWaveApi.Clss_IssuerAuth_Wave(byte[] authData, int len)`** → **`ClssWaveApi.Clss_IssScriptProc_Wave(byte[] script, int len)`** | `373-394` |
| K15 | Second tap detection | `isNeedSecondTap`: reads tag 0x9F6C (CTQ), checks TTQ byte 3 bit 0x80 and CTQ byte 2 bit 0x40 | `451-468` |

---

### 5.3 qPBOC — `ClssPbocProcess`

**Router key:** `EmvKernelConst.PBOC` = `"PBOC"`  
**Java API:** `com.pax.jemv.qpboc.api.ClssPbocApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssPbocProcess.java`

| # | EMV Phase | Kernel Call | File:Line |
|---|---|---|---|
| K1 | Read version | **`ClssPbocApi.Clss_ReadVerInfo_Pboc(ByteArray version)`** | `130` |
| K2 | Core init | **`ClssPbocApi.Clss_CoreInit_Pboc()`** | `132` |
| K3 | QUICS flag | **`ClssPbocApi.Clss_SetQUICSFlag_Pboc(byte quicsFlag)`** | `138` |
| K4 | Torn config | **`ClssPbocApi.Clss_TornSetConfig_Pboc(Clss_PbocTornConfig)`** | `144` |
| K5 | Clear expired torn logs | loop **`ClssPbocApi.Clss_ClearTornLog_Pboc((byte) 2, delTornFlag)`** | `170-177` |
| K6 | Reader params | **`ClssPbocApi.Clss_SetReaderParam_Pboc(Clss_ReaderParam)`** | `153` |
| K7 | AID params | **`ClssPbocApi.Clss_SetPbocAidParam_Pboc(Clss_PbocAidParam)`** | `158` |
| K8 | Final select data | **`ClssPbocApi.Clss_SetFinalSelectData_Pboc(byte[] finalSelectData, int len)`** | `165` |
| K9 | Trans data | **`ClssPbocApi.Clss_SetTransData_Pboc(Clss_TransParam, Clss_PreProcInterInfo)`** | `68` |
| K10 | Torn processing | **`ClssPbocApi.Clss_TornProcessing_Pboc((byte) 0, tornBuff)`** → optional offline torn: **`ClssPbocApi.Clss_CardAuth_Pboc(ACType, DDAFlag)`** | `201-274, 77` |
| K11 | Main proc incl. 1st GAC | **`ClssPbocApi.Clss_Proctrans_Pboc(TransactionPath, ACType)`** | `95` |
| K12 | QVSDC path ODA | **`ClssPbocApi.Clss_DelAllRevocList_Pboc()`**, **`ClssPbocApi.Clss_DelAllCAPK_Pboc()`**, **`ClssPbocApi.Clss_CardAuth_Pboc(ACType, DDAFlag)`** | `306-327` |
| K13 | VSDC path ODA | same **`Clss_CardAuth_Pboc`** | `330-346` |
| K14 | CVM | **`ClssPbocApi.Clss_GetCvmType_Pboc(CvmType)`** + QPS credit rules | `351-389` |
| K15 | UI callbacks | `onReadCardOk()`, `onRemoveCard()` | `113-115` |
| K16 | 2nd GAC / completion | **`completeTransProcess` is no-op** | `401-402` |

---

### 5.4 DPAS / ZIP — `ClssDpasProcess`

**Router key:** `EmvKernelConst.DPAS` = `"DPAS"`  
**Java API:** `com.pax.jemv.dpas.api.ClssDPASApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/src/main/java/com/pax/emvlib/dpas/contactless/ClssDpasProcess.java`

| # | EMV Phase | Kernel Call | File:Line |
|---|---|---|---|
| K1 | Core init | **`ClssDPASApi.Clss_CoreInit_DPAS()`** | `87` |
| K2 | Final select data | **`ClssDPASApi.Clss_SetFinalSelectData_DPAS(byte[] finalSelectData, int len)`** | `93` |
| K3 | Set TLV params | `setParam()` → **`ClssDPASApi.Clss_SetTLVDataList_DPAS(...)`** via `setTlv` | `101-131, 238-252` |
| K4 | App init | **`ClssDPASApi.Clss_InitiateApp_DPAS(TransactionPath transactionPath)`** | `63` |
| K5 | Read app data | **`ClssDPASApi.Clss_ReadData_DPAS()`** | `136` |
| K6 | ODA prep (EMV path) | **`ClssDPASApi.Clss_DelAllRevocList_DPAS()`**, **`ClssDPASApi.Clss_DelAllCAPK_DPAS()`**, `addCapkRevList()` → **`ClssDPASApi.Clss_AddCAPK_DPAS`**, **`ClssDPASApi.Clss_AddRevocList_DPAS`** | `142-145, 256-261` |
| K7 | TRM + CVM + TAA + 1st GAC | **`ClssDPASApi.Clss_TransProc_DPAS(byte exceptFileFlag)`** | `148` |
| K8 | Outcome read | `sendOutcome.sendTransDataOutput((byte) 0x07)` → reads tag LIST (0xDF8129), 0xDF8116, 0xDF8115 | `153, 293-305` |
| K9 | UI callbacks | `onReadCardOk()`, `onRemoveCard()` | `76-78` |
| K10 | **Online completion** | **`ClssDPASApi.Clss_IssuerUpdateProc_DPAS(byte onlineResult, byte[] script, int scriptLen)`** | `216` |
| K11 | Second tap detection | `isNeedSecondTap`: script non-empty AND `transactionPath.path == CLSS_DPAS_EMV` | `281-285` |

---

### 5.5 Amex ExpressPay — `ClssAEProcess`

**Router key:** `EmvKernelConst.AMEX` = `"AMEX"`  
**Java API:** `com.pax.jemv.amex.api.ClssAmexApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssAEProcess.java`

| # | EMV Phase | Kernel Call | File:Line |
|---|---|---|---|
| K1 | Core init | **`ClssAmexApi.Clss_CoreInit_AE()`** (skipped if `needSeePhone()`) | `59, 184` |
| K2 | Extend function | **`ClssAmexApi.Clss_SetExtendFunction_AE(byte[] exFunction)`** | `66` |
| K3 | Reader params | **`ClssAmexApi.Clss_SetReaderParam_AE(Clss_ReaderParam_AE)`**, **`ClssAmexApi.Clss_SetAddReaderParam_AE(Clss_AddReaderParam_AE)`** | `102, 109` |
| K4 | AID params | **`ClssAmexApi.Clss_SetAEAidParam_AE(CLSS_AEAIDPARAM)`** | `130` |
| K5 | Final select data | **`ClssAmexApi.Clss_SetFinalSelectData_AE(byte[], int)`** | `136` |
| K6 | Trans data | **`ClssAmexApi.Clss_SetTransData_AE(Clss_TransParam, Clss_PreProcInterInfo)`** | `142` |
| K7 | GPO / main proc | **`ClssAmexApi.Clss_Proctrans_AE(TransactionMode transMode)`** | `203` |
| K8 | Read record | **`ClssAmexApi.Clss_ReadRecord_AE(ByteArray)`** | `210` |
| K9 | ODA | `addCapkRevList()` → **`ClssAmexApi.Clss_CardAuth_AE()`** | `217-223, 224` |
| K10 | DRL | **`ClssAmexApi.Clss_AddDRL_AE(Clss_ProgramID_II)`** | `172` |
| K11 | 1st GAC / start trans | **`ClssAmexApi.Clss_StartTrans_AE(byte supportFullOnline, ByteArray adviceFlg, ByteArray onlineFlg)`** | `255` |
| K12 | Delayed auth (optional) | **`ClssAmexApi.Clss_GetAddReaderParam_AE`**, **`ClssAmexApi.Clss_CompleteTrans_AE(...)`** | `282-290` |
| K13 | CVM | **`ClssAmexApi.Clss_GetCvmType_AE()`** | `302` |
| K14 | UI callbacks | `onReadCardOk()`, `onRemoveCard()` | `243-245` |

---

### 5.6 JCB — `ClssJcbProcess`

**Router key:** `EmvKernelConst.JCB` = `"JCB"`  
**Java API:** `com.pax.jemv.jcb.api.ClssJCBApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssJcbProcess.java`

| # | Kernel Call | File:Line |
|---|---|---|
| K1 | **`ClssJCBApi.Clss_CoreInit_JCB()`** | `71` |
| K2 | **`ClssJCBApi.Clss_SetFinalSelectData_JCB(byte[], int)`** | `76` |
| K3 | **`ClssJCBApi.Clss_InitiateApp_JCB(TransactionPath)`** | `81` |
| K4 | **`ClssJCBApi.Clss_ReadData_JCB()`** | `89` |
| K5 | **`ClssJCBApi.Clss_DelAllRevocList_JCB()`**, **`ClssJCBApi.Clss_DelAllCAPK_JCB()`**, `addCapkRevList()` | `108-109` |
| K6 | EMV path: **`ClssJCBApi.Clss_TransProc_JCB(byte exceptFileFlg)`** OR legacy: **`ClssJCBApi.Clss_CardAuth_JCB()`** then **`Clss_TransProc_JCB`** | `113-124` |
| K7 | Outcome via **`ClssJCBApi.Clss_GetTLVDataList_JCB(...)`** tag 0xDF8129 | `176` |

---

### 5.7 RuPay — `ClssRuPayProcess`

**Router key:** `EmvKernelConst.RUPAY` = `"RUPAY"`  
**Java API:** `com.pax.jemv.rupay.api.ClssRuPayApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssRuPayProcess.java`

| # | EMV Phase | Kernel Call | File:Line |
|---|---|---|---|
| K1 | Core init | **`ClssRuPayApi.Clss_CoreInit_RuPay()`** | `49` |
| K2 | Final select | **`ClssRuPayApi.Clss_SetFinalSelectData_RuPay(byte[], int)`** — on `CLSS_RESELECT_APP` → **`ClssEntryApi.Clss_DelCurCandApp_Entry()`** | `91-102` |
| K3 | Set params | `clssBaseParameterSet()` → **`ClssRuPayApi.Clss_SetTLVDataList_RuPay(...)`** via `setTlv` | `112-161, 334-348` |
| K4 | App init | **`ClssRuPayApi.Clss_InitiateApp_RuPay()`** | `176` |
| K5 | Read app data | **`ClssRuPayApi.Clss_ReadData_RuPay()`** | `192` |
| K6 | ODA | **`ClssRuPayApi.Clss_DelAllRevocList_RuPay()`**, **`ClssRuPayApi.Clss_DelAllCAPK_RuPay()`**, **`ClssRuPayApi.Clss_CardAuth_RuPay()`** | `199-204` |
| K7 | Trans proc | **`ClssRuPayApi.Clss_TransProc_RuPay(byte exceptFileFlg)`** | `212` |
| K8 | 1st GAC | **`ClssRuPayApi.Clss_StartTrans_RuPay()`** — comment: "first terminal action analysis, transaction recovery, first card action analysis" | `220` |
| K9 | **2nd GAC / completion** | **`ClssRuPayApi.Clss_CompleteTrans_RuPay(byte onlineResult, byte[] script, int scriptLen, ByteArray scriptRstOut, ACType acTypeOut)`** | `308-310` |

---

### 5.8 EFT POS — `ClssEFTProcess`

**Router key:** `EmvKernelConst.EFT` = `"EFT"`  
**Java API:** `com.pax.jemv.eftpos.api.ClssEFTPOSApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssEFTProcess.java`

| # | EMV Phase | Kernel Call | File:Line |
|---|---|---|---|
| K1 | Core init | **`ClssEFTPOSApi.Clss_CoreInit_EFT()`** | `51` |
| K2 | Reader params | **`ClssEFTPOSApi.Clss_SetReaderParam_EFT(Clss_ReaderParam)`** | `58` |
| K3 | Final select | **`ClssEFTPOSApi.Clss_SetFinalSelectData_EFT(byte[], int)`** | `61` |
| K4 | AID params | **`ClssEFTPOSApi.Clss_SetAidParam_EFT(Clss_EFTAidParam)`** | `64` |
| K5 | Trans data | **`ClssEFTPOSApi.Clss_SetTransData_EFT(Clss_TransParam, Clss_PreProcInterInfo)`** | `70` |
| K6 | App init | **`ClssEFTPOSApi.Clss_InitApp_EFT()`** | `73` |
| K7 | Read app data | **`ClssEFTPOSApi.Clss_ReadAppData_EFT()`** | `80` |
| K8 | Pre-TAA (1st GAC phase) | **`ClssEFTPOSApi.Clss_PreTAAProc_EFT(ACType preAcType)`** | `88` |
| K9 | ODA | **`ClssEFTPOSApi.Clss_DelAllCAPK_EFT()`**, **`Clss_DelAllRevocList_EFT()`**, **`ClssEFTPOSApi.Clss_CardAuth_EFT()`** | `101-106` |
| K10 | Processing restrictions | **`ClssEFTPOSApi.Clss_ProcRestrict_EFT()`** | `113` |
| K11 | CVM | **`ClssEFTPOSApi.Clss_CVMProc_EFT(CvmType cvmType)`** | `117` |
| K12 | Post-TAA (terminal/card action analysis) | **`ClssEFTPOSApi.Clss_PostTAAProc_EFT(ACType postAcType)`** | `125` |
| K13 | **Online completion** | **`ClssEFTPOSApi.Clss_CompleteOnlineTrans_EFT(byte onlineResult)`** | `217` |

---

### 5.9 Pure — `ClssPureProcess`

**Router key:** `EmvKernelConst.PURE` = `"PURE"`  
**Java API:** `com.pax.jemv.pure.api.ClssPUREApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssPureProcess.java`

| # | Kernel Call | File:Line |
|---|---|---|
| K1 | **`ClssPUREApi.Clss_CoreInit_PURE()`** | `98` |
| K2 | **`ClssPUREApi.Clss_SetFinalSelectData_PURE(byte[], int)`** | `99` |
| K3 | **`ClssPUREApi.Clss_InitiateApp_PURE(Clss_PreProcInterInfo)`** | `104` |
| K4 | **`ClssPUREApi.Clss_ReadData_PURE()`** | `108` |
| K5 | **`ClssPUREApi.Clss_DelAllCAPK_PURE()`**, **`Clss_DelAllRevocList_PURE()`**, `addCapkRevList()` | `113-115` |
| K6 | Start trans / 1st GAC | **`ClssPUREApi.Clss_StartTrans_PURE(byte exceptFileFlg)`** | `117` |
| K7 | ODA | **`ClssPUREApi.Clss_CardAuth_PURE()`** | `121` |
| K8 | **Completion / scripts** | **`ClssPUREApi.Clss_CompleteTrans_PURE(byte[] script, int len)`** | `288` |

---

### 5.10 MIR — `ClssMirProcess`

**Router key:** `EmvKernelConst.MIR` = `"MIR"`  
**Java API:** `com.pax.jemv.mir.api.ClssMIRApi`  
**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssMirProcess.java`

| # | Kernel Call | File:Line |
|---|---|---|
| K1 | **`ClssMIRApi.Clss_CoreInit_MIR()`** | `48` |
| K2 | **`ClssMIRApi.Clss_SetFinalSelectData_MIR(byte[], int, TransactionPath)`** | `54` |
| K3 | **`ClssMIRApi.Clss_DataExchange_MIR(byte[], int, ByteArray)`** | `61` |
| K4 | Protocol 1 or 2 processing via **`ClssMIRApi`** trans/auth APIs (path-dependent) | `71-83` |
| K5 | Outcome via TLV tag LIST (0xDF8129) and 0xDF8116 | `85-88` |

## 6. `com.pax.jemv.*` Classes and Native Libraries

### 6.1 Java packages and primary API classes invoked

| Package | Primary API class | Used by |
|---|---|---|
| `com.pax.jemv.emv.api` | `EMVCallback` | Contact EMV (`ContactProcess`) — all contact kernel calls |
| `com.pax.jemv.emv.api` | `EMVApi` | `ContactlessService.getCapability()` — `EMVGetParameter` |
| `com.pax.jemv.emv.model` | `EmvParam`, `EmvMCKParam` | Contact param setup |
| `com.pax.jemv.entrypoint.api` | `ClssEntryApi` | Contactless entry point: AID list, final select, kernel ID |
| `com.pax.jemv.paypass.api` | `ClssPassApi` | PayPass (MC) contactless |
| `com.pax.jemv.paypass.listener` | `ClssPassCBFunApi`, `IClssPassCBFun` | PayPass outcome callbacks |
| `com.pax.jemv.paywave.api` | `ClssWaveApi` | payWave (Visa) contactless |
| `com.pax.jemv.qpboc.api` | `ClssPbocApi` | qPBOC contactless |
| `com.pax.jemv.qpboc.model` | `Clss_PbocAidParam`, `Clss_PbocTornConfig` | qPBOC params |
| `com.pax.jemv.dpas.api` | `ClssDPASApi` | DPAS/ZIP contactless |
| `com.pax.jemv.amex.api` | `ClssAmexApi` | Amex ExpressPay |
| `com.pax.jemv.amex.model` | `CLSS_AEAIDPARAM`, `Clss_ReaderParam_AE`, `Clss_AddReaderParam_AE`, `ONLINE_PARAM`, `TransactionMode` | Amex params |
| `com.pax.jemv.jcb.api` | `ClssJCBApi` | JCB contactless |
| `com.pax.jemv.mir.api` | `ClssMIRApi` | MIR contactless |
| `com.pax.jemv.rupay.api` | `ClssRuPayApi` | RuPay contactless |
| `com.pax.jemv.pure.api` | `ClssPUREApi` | Pure contactless |
| `com.pax.jemv.eftpos.api` | `ClssEFTPOSApi` | EFT POS contactless |
| `com.pax.jemv.eftpos.model` | `Clss_EFTAidParam` | EFT params |
| `com.pax.jemv.device` | `DeviceManager` | Contact callback TLV provision (time, SN, random) |
| `com.pax.jemv.device.model` | `ApduSendL2`, `ApduRespL2` | PayPass APDU logging callback |
| `com.pax.jemv.clcommon` | `RetCode`, `KernType`, `ACType`, `ByteArray`, `Clss_TransParam`, `Clss_PreProcInterInfo`, `Clss_PreProcInfo`, `Clss_ReaderParam`, `Clss_ProgramID`, `Clss_VisaAidParam`, `EMV_CAPK`, `EMV_REVOCLIST`, `EMV_APPLIST`, `TransactionPath`, `OutcomeParam`, `CvmType`, `DDAFlag`, `OnlineResult`, `Clss_ProgramID_II` | Shared types across all kernels |

### 6.2 `System.loadLibrary` call sites and load order

Libraries are loaded at app init via:

1. `EmvInit.init()` → `EmvUtils.loadLibrary()`  
   **File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvservice/emv/src/main/java/com/pax/emvservice/emv/EmvInit.java:38`  
   **File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/utils/EmvUtils.java:32-81`

2. `EmvUtils.loadLibrary()` first calls **`Router.getAllServices(IEmvLoadLibCallback.class)`** and invokes each registered callback's `load()` method.

#### 6.2.1 `emvlib/base/EmvLoadLibImpl` — `@RouterService(interfaces = IEmvLoadLibCallback.class)`

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/base/src/main/java/com/pax/emvlib/base/EmvLoadLibImpl.java`

| Load order | `System.loadLibrary(...)` | Line | Backs |
|---|---|---|---|
| 1 | `"F_DEVICE_LIB_PayDroid"` | 29 | `DeviceManager` / ICC device I/O |
| 2 | `"F_PUBLIC_LIB_PayDroid"` | 32 | Shared EMV infrastructure |
| 3 | `"F_ENTRY_LIB_PayDroid"` | 35 | Entry Point kernel |
| 4 | `"JNI_ENTRY_v105"` | 36 | Entry Point JNI wrapper → `ClssEntryApi` |

#### 6.2.2 `emvlib/dpas/EmvLoadLibImpl` — `@RouterService(interfaces = IEmvLoadLibCallback.class)` *(active build)*

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas/src/main/java/com/pax/emvlib/dpas/EmvLoadLibImpl.java`

| Load order | `System.loadLibrary(...)` | Line | Backs |
|---|---|---|---|
| 5 | `"F_EMV_LIBC_PayDroid"` | 33 | Contact EMV C runtime |
| 6 | `"F_EMV_LIB_PayDroid"` | 34 | Contact EMV kernel |
| 7 | `"JNI_EMV_v105"` | 35 | Contact JNI wrapper → `EMVCallback`, `EMVApi` |
| 8 | `"F_DPAS_LIB_PayDroid"` | 38 | DPAS contactless kernel |
| 9 | `"JNI_DPAS_v100"` | 39 | DPAS JNI wrapper → `ClssDPASApi` |

#### 6.2.3 `EmvUtils.loadLibrary()` — direct loads after Router callbacks

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/utils/EmvUtils.java`

| Load order | `System.loadLibrary(...)` | Line | Backs |
|---|---|---|---|
| 10 | `"F_MC_LIB_PayDroid"` | 47 | PayPass kernel |
| 11 | `"JNI_MC_v100_01"` | 48 | PayPass JNI → `ClssPassApi` |
| 12 | `"F_WAVE_LIB_PayDroid"` | 51 | payWave kernel |
| 13 | `"JNI_WAVE_v101"` | 52 | payWave JNI → `ClssWaveApi` |
| 14 | `"F_AE_LIB_PayDroid"` | 55 | Amex kernel |
| 15 | `"JNI_AE_v101"` | 56 | Amex JNI → `ClssAmexApi` |
| 16 | `"F_JCB_LIB_PayDroid"` | 59 | JCB kernel |
| 17 | `"JNI_JCB_v100"` | 60 | JCB JNI → `ClssJCBApi` |
| 18 | `"F_MIR_LIB_PayDroid"` | 63 | MIR kernel |
| 19 | `"JNI_MIR_v100"` | 64 | MIR JNI → `ClssMIRApi` |
| 20 | `"F_QPBOC_LIB_PayDroid"` | 67 | qPBOC kernel |
| 21 | `"JNI_QPBOC_v100"` | 68 | qPBOC JNI → `ClssPbocApi` |
| 22 | `"F_PURE_LIB_PayDroid"` | 71 | Pure kernel |
| 23 | `"JNI_PURE_v100"` | 72 | Pure JNI → `ClssPUREApi` |
| 24 | `"F_RUPAY_LIB_PayDroid"` | 75 | RuPay kernel |
| 25 | `"JNI_RUPAY_v100"` | 76 | RuPay JNI → `ClssRuPayApi` |
| 26 | `"F_EFT_LIB_PayDroid"` | 79 | EFT kernel |
| 27 | `"JNI_EFT_v101_D1"` | 80 | EFT JNI → `ClssEFTPOSApi` |

#### 6.2.4 Alternate (NOT active): `emvlib/dpas2/EmvLoadLibImpl`

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/dpas2/src/main/java/com/pax/emvlib/dpas2/EmvLoadLibImpl.java`

Present in repo but **not included** in active `emvlib/build.gradle` (which uses `:emvlib:dpas` only).

| `System.loadLibrary(...)` | Line |
|---|---|
| `"F_EMV_LIBC_PayDroid"` | 33 |
| `"F_EMV_LIB_PayDroid"` | 34 |
| `"JNI_EMV_v106_DPASCT"` | 35 |
| `"F_DPAS_LIB_PayDroid"` | 38 |
| `"JNI_DPAS_v101"` | 39 |
| `"F_DPAS_CT_LIB_PayDroid"` | 42 |
| `"JNI_DPAS_CT_v100"` | 43 |

Also adds `EMVCallback.DPASCTCoreInit()` in dpas2 `ContactProcess.preTransProcess` at line 94.

### 6.3 Native library file locations

- No `.so` files are present in this workspace (glob search returned 0 results).
- JAR dependencies are referenced via `api fileTree(include: ["*.jar"], dir: "libs")` in `emvlib/build.gradle:43` and `emvlib/dpas/build.gradle:43`, but `emvlib/libs/` is empty in the checked-out tree.
- Native libraries are expected to be packaged with the PAX PayDroid SDK at build/runtime on device.

---

## 7. Contactless Kernel Selection / AID Dispatch

### 7.1 Where kernel selection happens

Kernel selection is a **two-stage** process:

**Stage 1 — AID registration (before card tap):**  
`ClssEntryAddAid.addApp()` in `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssEntryAddAid.java`

For each configured scheme, the code calls:
```java
ClssEntryApi.Clss_AddAidList_Entry(aid.getAid(), (byte) aid.getAid().length, aid.getSelFlag(), (byte) KernType.KERNTYPE_DEF);
ClssEntryApi.Clss_SetPreProcInfo_Entry(clssPreProcInfo);
```

All AIDs are registered with `KernType.KERNTYPE_DEF` — the Entry Point kernel resolves the actual kernel type at final selection time based on the card's AID and kernel ID.

Schemes registered (each in its own `addXxxAid()` method):

| Method | Param source | File:Line |
|---|---|---|
| `addAmexAid()` | `emvProcessParam.getAmexParam().getAidList()` | `ClssEntryAddAid.java:86-97` |
| `addPayPassAid()` | `getPayPassParam().getAidList()` | `57-68` |
| `addPayWaveAid()` | `getPayWaveParam().getAidList()` | `71-83` |
| `addDpasAid()` | `getDpasParam().getAidList()` | `100-111` |
| `addEFTAid()` | `getEftParam().getAidList()` | `114-125` |
| `addJcbAid()` | `getJcbParam().getAidList()` | `128-139` |
| `addMirAid()` | `getMirParam().getAidList()` | `142-153` |
| `addPbocAid()` | `getPbocParam().getAidList()` | `156-166` |
| `addPureAid()` | `getPureParam().getAidList()` | `169-180` |
| `addRuPayAid()` | `getRuPayParam().getAidList()` | `183-194` |

**Stage 2 — Runtime kernel ID resolution (on card tap):**  
`ClssProcess.startTransProcess()` in `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssProcess.java`

Exact logic:

1. **`ClssEntryApi.Clss_AppSlt_Entry(0, 0)`** (line 105) — builds candidate application list from registered AIDs and card response.

2. **`while (true)` loop** (line 113):

   a. **`ClssEntryApi.Clss_FinalSelect_Entry(KernType kernType, ByteArray daArray)`** (line 118)
   - **This is the AID → kernel-ID dispatch point.**
   - On success: `kernType.kernType` is set to one of the `KernType.KERNTYPE_*` constants.
   - On `EMV_RSP_ERR`, `EMV_APP_BLOCK`, `ICC_BLOCK`, or `CLSS_RESELECT_APP`: call **`ClssEntryApi.Clss_DelCurCandApp_Entry()`** and `continue` the loop (lines 120-127).
   - On other failure: return `TransResult` with `RESULT_OFFLINE_DENIED` (lines 128-142).
   - Special RuPay handling: if `kernType.kernType == KernType.KERNTYPE_RUPAY`, call **`ClssEntryApi.Clss_GetExtendFunction_Entry(0x03, status, len)`** to detect SW 6283 → map to `EMV_APP_BLOCK` (lines 129-141).

   b. **`ClssEntryApi.Clss_GetPreProcInterFlg_Entry(Clss_PreProcInterInfo)`** (line 146)

   c. **`ClssEntryApi.Clss_GetFinalSelectData_Entry(ByteArray finalSelectData)`** (line 153)

   d. **`new ClssKernelProcessFactory(kernType.kernType).build()`** (lines 159-165) — see mapping below.

   e. **`clssKernelProcess.startTransProcess()`** (line 167)

   f. If result is `RetCode.CLSS_RESELECT_APP`: **`ClssEntryApi.Clss_DelCurCandApp_Entry()`** and `continue` (lines 168-174).

3. Return final `TransResult` (line 179).

### 7.2 `KernType.kernType` → Java process mapping

**File:** `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/src/main/java/com/pax/emvlib/process/contactless/ClssKernelProcessFactory.java:49-83`

```java
switch (kernelType) {
    case KernType.KERNTYPE_VIS:
        clssParam = emvProcessParam.getPayWaveParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.PAYWAVE);
    case KernType.KERNTYPE_MC:
        clssParam = emvProcessParam.getPayPassParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.MC);
    case KernType.KERNTYPE_AE:
        clssParam = emvProcessParam.getAmexParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.AMEX);
    case KernType.KERNTYPE_PBOC:
        clssParam = emvProcessParam.getPbocParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.PBOC);
    case KernType.KERNTYPE_EFT:
        clssParam = emvProcessParam.getEftParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.EFT);
    case KernType.KERNTYPE_JCB:
        clssParam = emvProcessParam.getJcbParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.JCB);
    case KernType.KERNTYPE_MIR:
        clssParam = emvProcessParam.getMirParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.MIR);
    case KernType.KERNTYPE_PURE:
        clssParam = emvProcessParam.getPureParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.PURE);
    case KernType.KERNTYPE_RUPAY:
        clssParam = emvProcessParam.getRuPayParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.RUPAY);
    case KernType.KERNTYPE_ZIP:
        clssParam = emvProcessParam.getDpasParam();
        return Router.getService(ClssKernelProcess.class, EmvKernelConst.DPAS);
    default:
        throw new IllegalArgumentException("Unsupported Kernel " + kernelType);
}
```

**`EmvKernelConst` keys** — `/Users/macmin/Desktop/EmvEnhanceRefactor/emvlib/base/src/main/java/com/pax/emvlib/base/consts/EmvKernelConst.java`:

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

Factory `build()` also chains parameter setup (line 112-121):
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

### 7.3 Exposing selected kernel to callers

`ContactlessService.getKernelType()` → `ClssProcess.getKernType().kernType`  
**Files:** `ContactlessService.java:139-140`, `ClssProcess.java:234-236`

---

## 8. WMRouter `@RouterService` Registrations

All use annotation: `com.sankuai.waimai.router.annotation.RouterService`

### 8.1 `emvservice/emv` module

| Class | File | Annotation |
|---|---|---|
| `EmvContactService` | `/Users/macmin/Desktop/EmvEnhanceRefactor/emvservice/emv/src/main/java/com/pax/emvservice/emv/contact/EmvContactService.java:44` | `@RouterService(interfaces = IEmvContactService.class, key = EmvServiceConstant.EMVSERVICE_CONTACT, singleton = true)` |
| `ContactlessService` | `.../contactless/ContactlessService.java:47` | `@RouterService(interfaces = IEmvContactlessService.class, key = EmvServiceConstant.EMVSERVICE_CONTACTLESS, singleton = true)` |
| `EmvVersionService` | `.../version/EmvVersionService.java:39` | `@RouterService(interfaces = IEmvVersionService.class, key = EmvServiceConstant.EMVSERVICE_EMV_VERSION)` |
| `PinService` | `.../pin/PinService.java:36` | `@RouterService(interfaces = IPinService.class, key = EmvServiceConstant.EMVSERVICE_PIN)` |
| `MagCardService` | `.../mag/MagCardService.java:33` | `@RouterService(interfaces = IMagCardService.class, key = EmvServiceConstant.EMVSERVICE_MAG_CARD, singleton = true)` |
| `ManualCardService` | `.../manual/ManualCardService.java:25` | `@RouterService(interfaces = IManualCardService.class, key = EmvServiceConstant.EMVSERVICE_MANUAL_CARD)` |
| `EmvInit` | `.../EmvInit.java:29` | `@RouterService(interfaces = IModuleInit.class, key = ConfigServiceConstant.INIT_EMV)` where `INIT_EMV = "init_emv"` |

### 8.2 Route key summary (service lookup strings)

| Route constant | String value | Lookup example |
|---|---|---|
| `EmvServiceConstant.EMVSERVICE_CONTACT` | `"emvService_contact"` | `Router.getService(IEmvContactService.class, "emvService_contact")` |
| `EmvServiceConstant.EMVSERVICE_CONTACTLESS` | `"emvService_contactLess"` | `Router.getService(IEmvContactlessService.class, "emvService_contactLess")` |
| `EmvServiceConstant.EMVSERVICE_EMV_VERSION` | `"emvService_emvVersion"` | |
| `EmvServiceConstant.EMVSERVICE_PIN` | `"emvService_pin"` | |
| `EmvServiceConstant.EMVSERVICE_MAG_CARD` | `"emvService_magCard"` | |
| `EmvServiceConstant.EMVSERVICE_MANUAL_CARD` | `"emvService_manualCard"` | |
| `ConfigServiceConstant.INIT_EMV` | `"init_emv"` | Module init for SO loading |
| `EmvServiceConstant.EMVSERVICE_PARAM` | `"emvService_param"` | Defined, no impl found |
| `EmvServiceConstant.EMVSERVICE_EMV_RSP` | `"emvService_emvResponse"` | Defined, no impl found |
| `EmvServiceConstant.EMVSERVICE_EMV_STATUS_CHECK` | `"emvService_status_check"` | Defined, no impl found |

### 8.3 `emvlib` kernel registrations

| Class | File | Annotation |
|---|---|---|
| `ContactProcess` | `emvlib/dpas/.../contact/ContactProcess.java:60` | `@RouterService(interfaces = BaseContactProcess.class, key = EmvKernelConst.EMV)` → key `"EMV"` |
| `ClssPayPassProcess` | `emvlib/.../ClssPayPassProcess.java:44` | `@RouterService(interfaces = ClssKernelProcess.class, key = EmvKernelConst.MC)` → `"MC"` |
| `ClssPayWaveProcess` | `emvlib/.../ClssPayWaveProcess.java:50` | key `"PAYWAVE"` |
| `ClssPbocProcess` | `emvlib/.../ClssPbocProcess.java:48` | key `"PBOC"` |
| `ClssDpasProcess` | `emvlib/dpas/.../ClssDpasProcess.java:46` | key `"DPAS"` |
| `ClssAEProcess` | `emvlib/.../ClssAEProcess.java:51` | key `"AMEX"` |
| `ClssJcbProcess` | `emvlib/.../ClssJcbProcess.java:40` | key `"JCB"` |
| `ClssMirProcess` | `emvlib/.../ClssMirProcess.java:38` | key `"MIR"` |
| `ClssRuPayProcess` | `emvlib/.../ClssRuPayProcess.java:42` | key `"RUPAY"` |
| `ClssPureProcess` | `emvlib/.../ClssPureProcess.java:40` | key `"PURE"` |
| `ClssEFTProcess` | `emvlib/.../ClssEFTProcess.java:43` | key `"EFT"` |
| `EmvLoadLibImpl` (base) | `emvlib/base/.../EmvLoadLibImpl.java:24` | `@RouterService(interfaces = IEmvLoadLibCallback.class)` — no key; discovered via `Router.getAllServices` |
| `EmvLoadLibImpl` (dpas) | `emvlib/dpas/.../EmvLoadLibImpl.java:28` | `@RouterService(interfaces = IEmvLoadLibCallback.class)` — no key |
| `ContactProcess` (dpas2) | `emvlib/dpas2/.../ContactProcess.java:57` | `@RouterService(interfaces = BaseContactProcess.class, key = "EMV")` — alternate, not active |
| `ClssDpasProcess` (dpas2) | `emvlib/dpas2/.../ClssDpasProcess.java:43` | key `"DPAS"` — alternate, not active |
| `EmvLoadLibImpl` (dpas2) | `emvlib/dpas2/.../EmvLoadLibImpl.java:28` | no key — alternate, not active |

### 8.4 Consumer lookup sites (reference)

| Consumer | File:Line | Services resolved |
|---|---|---|
| `ContactEmvRunner` | `/Users/macmin/Desktop/EmvEnhanceRefactor/emvflow/src/main/java/com/emvenhance/emvflow/contact/ContactEmvRunner.java:25-26, 40-41` | `IEmvContactService` key `"emvService_contact"` |
| `ContactlessEmvRunner` | `emvflow/.../ContactlessEmvRunner.java:25-26, 41-42` | `IEmvContactlessService` key `"emvService_contactLess"` |
| `EmvPreProcessFacade` | `emvflow/.../EmvPreProcessFacade.java:76-78` | Both contact + contactless |
| `EmvProcess` | `emvlib/.../EmvProcess.java:38` | `BaseContactProcess` key `"EMV"` |
| `ClssKernelProcessFactory` | `emvlib/.../ClssKernelProcessFactory.java:53-80` | `ClssKernelProcess` keys per `KernType` |
| `CardReaderHelper` | `bizlib/.../CardReaderHelper.java:162` | `IMagCardService` key `"emvService_magCard"` |

---

## 9. EMV Phase → Code Mapping

### 9.1 Contact (`ContactProcess` + `EMVCallback`)

| EMV Book 3 Phase | Java/kernel call | When |
|---|---|---|
| Core init | `EMVCallback.EMVCoreInit()` | `preTransProcess` |
| Add AID | `EMVCallback.EMVAddApp()` | `preTransProcess` |
| Application selection | `EMVCallback.EMVAppSelect()` + callback `emvWaitAppSel` → `onWaitAppSelect` | `startTransProcess` step 1 |
| AID-specific param (pre-GPO) | callback `emvSetParam` → `resetParam` | during `EMVAppSelect`/`EMVReadAppData` |
| Read application data | `EMVCallback.EMVReadAppData()` | `startTransProcess` step 2 |
| Offline data authentication | `addCapk()` + `EMVCallback.EMVCardAuth()` | `startTransProcess` step 3 |
| Processing restrictions | inside `EMVCallback.EMVStartTrans()` | `startTransProcess` step 4 |
| Cardholder verification (CVM/PIN) | inside `EMVStartTrans()` + callback `emvGetHolderPwd` → `onCardHolderPwd` | `startTransProcess` step 4 |
| Terminal risk management | inside `EMVStartTrans()` | `startTransProcess` step 4 |
| Terminal action analysis | inside `EMVStartTrans()` | `startTransProcess` step 4 |
| 1st GENERATE AC | inside `EMVStartTrans()` → `ACType acType` | `startTransProcess` step 4 |
| Online processing | `IContactCallback.startOnlineProcess()` | `EmvContactService` when `RESULT_REQ_ONLINE` |
| Issuer authentication | inside `EMVCallback.EMVCompleteTrans()` | `completeTransProcess` |
| Script processing | inside `EMVCompleteTrans()` | `completeTransProcess` |
| 2nd GENERATE AC | inside `EMVCompleteTrans()` → `ACType acType` | `completeTransProcess` |

### 9.2 Contactless — generic entry + per-kernel

| EMV Phase | Entry Point | Per-kernel examples |
|---|---|---|
| Entry init + AID list | `ClssEntryApi.Clss_CoreInit_Entry`, `Clss_AddAidList_Entry`, `Clss_PreTransProc_Entry` | — |
| Application selection | `Clss_AppSlt_Entry`, `Clss_FinalSelect_Entry` → sets `kernType` | — |
| Application initialization | — | `Clss_InitiateApp_MC`, `Clss_Proctrans_Wave`, `Clss_InitiateApp_DPAS`, `Clss_InitApp_EFT` |
| Read application data | — | `Clss_ReadData_MC`, (inside `Clss_Proctrans_Wave`), `Clss_ReadData_DPAS`, `Clss_ReadAppData_EFT` |
| Offline data authentication | — | `Clss_CardAuth_Wave`, `Clss_CardAuth_Pboc`, `Clss_CardAuth_EFT`, `Clss_CardAuth_RuPay`, `addCapkRevList` + kernel AddCAPK |
| Terminal risk management | — | Inside `Clss_TransProc_MC_MChip`, `Clss_TransProc_DPAS`, `Clss_TransProc_RuPay`, `Clss_ProcRestric_Wave` |
| Cardholder verification | Service layer: `onCardHolderPwd`; kernel: `Clss_CVMProc_EFT`, `Clss_GetCvmType_Wave/Pboc/AE` | |
| Terminal action analysis | — | Inside `Clss_TransProc_*`, `Clss_PreTAAProc_EFT`, `Clss_PostTAAProc_EFT`, `Clss_StartTrans_RuPay` |
| 1st GENERATE AC | — | `Clss_TransProc_MC_MChip(acType)`, `Clss_Proctrans_Wave(acType)`, `Clss_Proctrans_Pboc(acType)`, `Clss_StartTrans_AE`, `Clss_StartTrans_RuPay` |
| Online processing | `IContactlessCallback.startOnlineProcess()` | — |
| Issuer authentication | — | `Clss_IssuerAuth_Wave`, `Clss_IssuerUpdateProc_DPAS` |
| Script processing | — | `Clss_IssScriptProc_Wave`, `Clss_CompleteTrans_RuPay`, `Clss_CompleteTrans_PURE` |
| 2nd GENERATE AC / completion | — | `Clss_IssuerAuth_Wave` + `Clss_IssScriptProc_Wave` (payWave 2nd tap); `Clss_CompleteTrans_RuPay`; `Clss_CompleteOnlineTrans_EFT`; PayPass/PBOC: no-op |

---

*Document generated from source analysis of `/Users/macmin/Desktop/EmvEnhanceRefactor`. No source files were modified.*
