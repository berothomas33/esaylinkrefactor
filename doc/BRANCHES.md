# BRANCHES.md — status of every working branch (2026-10-20)

All branches start from `4a74277`. Device test = the form `report_easylink.xlsx`
(CLAUDE.md §10). "Tests" = JVM unit tests (kotlinc/javac + JUnit in the cloud container; no
Gradle there).

## Summary

| # | Branch | Since 4a74277 | Language | Step dispatch | Transaction API | Device-tested | Use it for |
|---|---|---|---|---|---|---|---|
| 1 | `Finalvision` | 8 | Java | direct call | `startTransaction()` + `cancelTransaction()` | **Yes** (8 Pass, 2 Fail, 14 Not run) | the safe fallback |
| 2 | `rxDispatchEmvSteps` | 12 | Java + Rx | Rx observer on `emvSteps` | same as 1 | **Yes** (same result as 1) | proof that Rx dispatch works on the device |
| 3 | `rxSetTransactionData` | 33 | Java + Rx | Rx observer | same as 1 | **No** | the planned mainline (all EMV/param work) |
| 4 | `rxTransactionDesign` | 36 | Java + Rx | Rx observer | **Completable** (subscribe = start, dispose = cancel) | **No** | the Rx transaction-lifecycle experiment |
| 5 | `coroutineDesign` | 43 | **Kotlin + coroutines** | direct call + `SharedFlow` for observers | **`suspend fun`** (call = start, cancel the caller = cancel, return = free) — the same design as 4 | **No** (not even built with Gradle) | the Kotlin migration, all phases |

Also on the remote: `rxFinalvision` (1 commit, 2026-10-14, "Dispatch EMV step methods from an
emvSteps observer") — the first Rx-dispatch experiment, superseded by `rxDispatchEmvSteps`.
Older `claude/*`, `cursor/*` and feature branches are stale; don't merge them blindly.

## 1. `Finalvision` — Java, direct dispatch

- 8 commits: no step published or run after the end (`190f139`), `EmvBehavior` is the engine's
  contract and the step methods are the base class's (`b2498d9`), **never write 9F03 into the
  kernel** (`cbb0748`, device-confirmed), plus test-only experiments that were reverted.
- Device form (2026-10-15): Pass 2, 3, 6, 8, 19, 20, 21, 22 · Fail 10 (See Phone), 11 (tap
  again) · the rest Not run.
- Status: **stable**, the reference everything else is compared against.

## 2. `rxDispatchEmvSteps` — Java + RxJava dispatch

- = Finalvision + the step methods are run by an Rx observer on `emvSteps` (`fc96da1`), the
  observer stops at the end of a transaction (`1abc7a8`), `TransactionStep.isEnd()` (`11787e3`).
- Device form: identical to Finalvision (same 8 Pass / 2 Fail).
- Status: **stable**; proves `PublishSubject` dispatch is synchronous and re-entrant enough for
  the kernel.

## 3. `rxSetTransactionData` — Java + Rx, the EMV/parameter work

- = rxDispatchEmvSteps + 21 more: every EMV stage in its own step (contact SET after READ;
  PayWave 2 → 6 → 5 → PR → ODA; PayPass CAPK in ODA; CLSS_RESELECT_APP), `askStep`/`answerStep`
  (app selection and PIN in their steps), online / 2nd GAC / scripts / outcome in their own
  steps, one transaction thread with one `AtomicReference` (`2362df8`), parameter download
  deletes kernels the package doesn't bring, debug screens (downloaded XML, cached details), and
  the docs (CLAUDE.md, REFERENCE.md, claudekotlin.md).
- Tests: core 65.
- Status: **not device-tested**. Needs the uninstall + install (schema change) and the extra form
  rows listed in CLAUDE.md §10. This is the branch to device-test first.

## 4. `rxTransactionDesign` — Java + Rx, the transaction as a stream

- = rxSetTransactionData + 2 commits:
  - `ae25378` (2026-10-20): `startTransaction` returns an `Observable` of its own steps
    (subscribe = start, dispose = cancel this one, complete = terminal free).
  - `13e2b02` (2026-10-20, pushed **after** `coroutineDesign` branched): replaces that with a
    **`Completable`** — the steps stay on the engine's single `transactionSteps` stream; the
    `Completable` only starts / cancels / completes. `MainViewModel` subscribes to
    `transactionSteps().skip(1)`.
  - `0bee281` (2026-10-20): one cancel flag (`Transaction.state` PENDING → RUNNING |
    CANCELLED; a cancel before it ran frees the terminal itself), one transaction-steps subject
    with no replay, one end guard (a CAS in `notifyTransactionStep`).
- Tests: core 73.
- Status: **not device-tested**; design still moving (two different designs the same day).
  Form rows to add: 13, 14, 15, 18, Retry after a decline, BUSY while a cancelled search stops.

## 5. `coroutineDesign` — Kotlin + coroutines (new)

Branched from `rxTransactionDesign` at `ae25378` (the Observable design). Five commits after it,
one per phase of `doc/claudekotlin.md`, then the docs, then the port of `13e2b02` + `0bee281`:

| Commit | Phase | What |
|---|---|---|
| `1ffa5c2` | 0 | Kotlin 1.9.22 + coroutines 1.7.3 + lifecycle-ktx 2.6.2; RxJava removed from every module (incl. the unused `api` in commonutils) |
| `3d4992e` | 2 | core: `EmvEngine` (synchronous listeners + `SharedFlow`s), `AbstractEmvBehavior` (step handler, no `CompositeDisposable`), `PosTerminal.startTransaction(): Flow` (callbackFlow), `suspend` host / printer ports, `ApduTrace.changes: StateFlow` |
| `9ed2fe7` | 1 | network: Retrofit `suspend` functions, `SaleCommunicationBehavior` / `OnboardingClient` straight-line suspend code, models with the same Gson wire order |
| `d88fa72` | 3 + 4 | app main: `MainViewModel` on `viewModelScope` + flows, screens collect with `repeatOnLifecycle`; fake / ingenico vendors |
| `8b49693` | 5 | app pax: `PaxEmvBehavior` line for line (native call order diffed: identical), `PaxTerminal`, PIN pad / app-select stay blocking, screens on `lifecycleScope` + `Dispatchers.IO` |
| `8e6208b` | port | core: `13e2b02` + `0bee281` — `suspend fun startTransaction`, `Transaction.state`, `transactionSteps` = `MutableSharedFlow(extraBufferCapacity = 64)` no replay, end CAS in `notifyTransactionStep`, no drain loop / listeners, the behavior's cancel removed |
| `61a36d2` | port | app: `MainViewModel` collects the engine's stream from `init`, `acceptCard` launches the suspend call; `terminalStatus` gone; fake flow test rewritten |

- What is Kotlin: `core`, `network`, all of `app` (main, pax, fake, ingenico). What stays Java on
  purpose (claudekotlin.md §4): the PAX SDK modules (`emvbase`, `emvlib*`, `poslib`,
  `commonutils`, `emvservice`, `emvflow`, `bizlib`) and `bizentity` (GreenDAO). They contain no Rx.
- The rules from claudekotlin.md §2 are kept: one transaction thread (a plain executor, never
  `Dispatchers.Default/IO`), synchronous re-entrant dispatch (a direct call, not a Flow),
  never interrupt, busy = the transaction hasn't returned, drop-after-end, fail closed. The host
  call is the only `runBlocking`, on the transaction thread, with `withTimeout(90 s)`.
- Tests (cloud, kotlinc 1.9.22 + JUnit): core **75** green (the branch's lifecycle and busy
  tests, `EmvEngineEndTest` and `EmvEngineConcurrentStepsTest` ported one to one), network **14**
  (3 ported + MockWebServer suspend tests), fake vendor end-to-end **2**. Guards
  mutation-checked. main+fake, main+ingenico and main+pax **type-check** against android-all 14,
  the PAX module sources/jars and androidx stubs (0 errors).
- Status: **not built with Gradle, not device-tested.** First step on the PC: Android Studio →
  sync → build `paxEnvtestDevDebug` and the fake flavor → `./gradlew test`. Then the whole form,
  in its own column.
- Ported: `13e2b02` + `0bee281` (the branch wins over `claudekotlin.md`;
  `doc/kotlin-rx-transaction.md` §1–3 are stale). Mutation-checked: each guard fails a test,
  except the `current == this` cancel guard (only a race window; no test for it on the branch
  either).
- Known carried-over item: `HostAppKeys.MTOKEN` still holds a captured JWT literal, as on every
  branch (CLAUDE.md §11).

## Recommended order

1. Device-test `rxSetTransactionData` (the EMV work all later branches carry).
2. Build `coroutineDesign` in Android Studio; fix whatever Gradle finds that the cloud stubs
   couldn't (androidx/material APIs are stubbed there).
3. Device-test `coroutineDesign` with the whole form; compare against `rxSetTransactionData`.
4. Branches 4 and 5 now have the same transaction design (Rx `Completable` / `suspend fun` +
   the engine's one steps stream); keep only one of them going forward.
