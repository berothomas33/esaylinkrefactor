# Prompt — bring `coroutineDesign` level with `transactionOwnsSteps-oct27`

Paste everything below the line into a new thread that works on `coroutineDesign`.

---

You are working on the EasyLink Android EMV POS project (PAX A920), repo
`berothomas33/esaylinkrefactor`. Work on branch **`coroutineDesign`** only (Kotlin + coroutines,
no Rx). Never commit to, push to or rewrite `transactionOwnsSteps-oct27`,
`transactionOwnsSteps`, or any `*-clean*` branch — they are the source, read them with
`git show` / `git diff`.

**Goal.** `coroutineDesign` (today `870eb83`, 22/10) reaches the same stage as
`transactionOwnsSteps-oct27` (`2ab85e5`, 28/10): every fix, every simplification, every removal,
the same tests — in Kotlin. The shared Java modules end byte-identical.

**Read first** (on `transactionOwnsSteps-oct27`, fetch it):
1. `doc/CLAUDE.md` — how Ebram works (Egyptian Arabic, short; design first; one change per
   commit with a long body; say what is not device-tested), his design rules 1–16, the current
   architecture (§6).
2. `doc/catchup-coroutine.md` — the step list: §2 what is already on both (skip), §3 what to
   port in order **S → T → U → V**, §4 done-when, and the Kotlin rules at the end.
3. `doc/REFERENCE.md` §2 (the transaction), §2.7 (ack / reversal), §3.5 (who ends a transaction).

**How to work, every step:**
1. Read the source commit's body and diff (`git show <sha>`).
2. Show Ebram, briefly: what `coroutineDesign` has today, what will change, the Kotlin shape.
3. **Wait for his go** before writing code.
4. One commit per step, title as in the table; long body: what, why, what was checked, what was
   not (device, Gradle).
5. Java modules (`emvlib`, `bizentity`, `emvbase`, `poslib`, `commonutils`, `bizlib`,
   `emvservice`, `emvflow`) stay Java: when the file matches the source's parent, apply the
   source diff as is (`git diff <sha>^ <sha> -- <file> | git apply`).
6. Run the core tests after each step (Kotlin harness: kotlinc + JUnit + kotlinx-coroutines-test
   with stub jars if there is no Android SDK). Read the result before committing — a red run is
   never committed. For each new guard, show a test that fails when the guard is removed.

**Never:**
- reorder native kernel calls; drop payment-safety code without asking; make a kernel callback
  `suspend`; put steps in a `StateFlow` (it conflates APPROVED into COMPLETED); interrupt the
  transaction thread; use `Dispatchers.Default` / `IO` for the kernel.
- delete anything before saying what and why in one line and waiting.
- put credentials, tokens, keys, PANs, track data or serial numbers in code, docs or commits.

**The one step that needs a design decision first — V3 (`2e4fbb4`, the transaction runs from its
own steps).** Ebram's goal: the transaction has its own TransactionStep events that drive it, the
way EmvSteps drive the EMV (`TRANSACTION_STARTED` → `onTransactionStarted`, `NEXT_ATTEMPT` →
`onNextAttempt`, then `onEnd`). The old Kotlin rule says "the transaction on its executor, never
`launch`" — it came from the risk that a cancelled job never runs and the terminal stays busy.
Propose this mapping and wait for his decision:
- the transaction's steps stay `MutableSharedFlow(replay = Int.MAX_VALUE)`;
- `begin()` (caller's thread, after the busy CAS): `launch` one collector on the single-thread
  transaction dispatcher (`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`), then
  emit `TRANSACTION_STARTED` (replay means the collector can't miss it);
- the collector's job is cancelled **only** by `onEnd` (as the Rx observer is disposed only
  there), so it always runs and always frees the terminal; any exception in a step method →
  ERROR + `onEnd`;
- `NEXT_ATTEMPT` is emitted under the steps lock only while the transaction runs; the end is
  still delivered by `onEnd` after `current` is cleared.

**Done when** (`doc/catchup-coroutine.md` §4): the Kotlin does what the Java does, same tests and
mutation checks; `git diff coroutineDesign:vendor-pax/libs transactionOwnsSteps-oct27:vendor-pax/libs` is empty;
the device form gets a `coroutineDesign` column. Nothing on either branch after `19cc019` is
device-tested yet — say so in every report.

Start with step **S1** (`ddd8dc1`): read it, show Ebram what changes, and wait.
