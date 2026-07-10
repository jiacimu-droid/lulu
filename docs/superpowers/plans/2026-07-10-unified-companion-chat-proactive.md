# Unified Companion Chat And Proactive Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ordinary chat and proactive wakeups read and write one assistant-isolated companion snapshot, with durable commitments that survive unrelated chat and record real execution outcomes.

**Architecture:** A role-neutral `CompanionRuntime` owns atomic reducer application and a `CompanionPerceptionAssembler` creates the same perception packet for foreground and background decisions. `CompanionStore` remains business truth; AlarmManager and legacy UI models are temporary compatibility projections. Existing model planners can be adapted while call sites migrate, but no scheduler operation may delete unrelated commitments.

**Tech Stack:** Kotlin, coroutines, kotlinx.serialization, Android DataStore Preferences, AlarmManager, Koin, JUnit.

---

## File Structure

- Create `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionPerception.kt`: role-neutral foreground/background perception contracts and deterministic assembler.
- Create `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt`: atomic state, relationship, concern, and commitment orchestration.
- Create `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionPerceptionAssemblerTest.kt`: assistant isolation, ordering, and packet bounds.
- Create `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRuntimeReducerTest.kt`: pure runtime mutation and commitment execution tests.
- Modify `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionModels.kt`: add structured action context and execution result fields with serialization defaults.
- Modify `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`: register `CompanionRuntime` and inject it into `ChatService`.
- Modify `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`: assemble foreground perception, persist unified turn state, append commitments, and stop clearing targeted work.
- Modify `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt`: resolve durable commitments, execute validated transitions, record outcomes, and schedule the next persisted item.
- Modify `app/src/main/java/me/rerere/rikkahub/service/LuluIntentModelPlanner.kt`: add temporary conversion helpers from model output to role-neutral runtime mutations.
- Delete `app/src/main/java/me/rerere/rikkahub/service/LuluIntentPlanner.kt` and its test only after production references are zero.

### Task 1: Shared Perception Packet

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionPerception.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionPerceptionAssemblerTest.kt`

- [ ] **Step 1: Write focused packet tests**

Cover these cases:

```kotlin
@Test fun `assembler rejects a snapshot owned by another assistant`()
@Test fun `assembler exposes only active concerns and actionable commitments`()
@Test fun `assembler orders due work before later work`()
@Test fun `assembler bounds persona context facts and recent turns`()
```

- [ ] **Step 2: Add role-neutral contracts**

Use platform-independent text turns so the runtime does not depend on Compose or `UIMessage`:

```kotlin
data class CompanionPerceptionInput(
    val assistantId: String,
    val assistantName: String,
    val persona: String,
    val conversationId: String? = null,
    val recentTurns: List<CompanionConversationTurn> = emptyList(),
    val contextFacts: List<CompanionContextFact> = emptyList(),
    val availableToolNames: Set<String> = emptySet(),
    val memoryContext: String = "",
    val nowMillis: Long,
)

data class CompanionPerceptionPacket(
    val assistantId: String,
    val assistantName: String,
    val persona: String,
    val conversationId: String?,
    val snapshot: CompanionSnapshot,
    val recentTurns: List<CompanionConversationTurn>,
    val contextFacts: List<CompanionContextFact>,
    val activeConcerns: List<CompanionConcern>,
    val actionableCommitments: List<CompanionCommitment>,
    val availableToolNames: Set<String>,
    val memoryContext: String,
    val nowMillis: Long,
)
```

`CompanionPerceptionAssembler.assemble(input, snapshot)` must require exact assistant ownership, trim and bound external text, sort concerns by importance/next perception, and sort commitments by due time.

- [ ] **Step 3: Run the focused test if test compilation permits**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "me.rerere.rikkahub.data.companion.CompanionPerceptionAssemblerTest" --console=plain
```

If unrelated existing test sources prevent compilation, record that and use `:app:compileDebugKotlin` after implementation.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/java/me/rerere/rikkahub/data/companion/CompanionPerception.kt app/src/test/java/me/rerere/rikkahub/data/companion/CompanionPerceptionAssemblerTest.kt
git commit -m "Add unified companion perception packet"
```

### Task 2: Atomic Companion Runtime

**Files:**
- Create: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionModels.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRuntimeReducerTest.kt`

- [ ] **Step 1: Extend action data compatibly**

Add defaulted structured fields; old JSON must continue decoding:

```kotlin
@Serializable
data class CompanionActionPlan(
    val type: CompanionActionType = CompanionActionType.NONE,
    val toolName: String? = null,
    val argumentsJson: String = "{}",
    val userFacingSummary: String = "",
    val contextText: String = "",
    val category: String = "",
    val preferredToolNames: List<String> = emptyList(),
)

@Serializable
data class CompanionActionResult(
    val success: Boolean,
    val summary: String,
    val completedAt: Long,
    val outputReference: String? = null,
)
```

Add `lastActionResult: CompanionActionResult? = null` and `attemptCount: Int = 0` to `CompanionCommitment`.

- [ ] **Step 2: Write pure mutation tests**

Test a pure `reduceCompanionRuntimeState` function for:

- unrelated chat leaves existing commitments untouched;
- a proposed commitment becomes active in the same mutation;
- begin execution performs `ACTIVE -> DUE -> EXECUTING`;
- success stores an action result and fulfills the commitment;
- failure stores the error, transitions through `FAILED`, and optionally schedules retry;
- all mutations ignore foreign assistant IDs.

- [ ] **Step 3: Implement runtime API**

```kotlin
class CompanionRuntime(private val store: CompanionStore) {
    fun snapshot(assistantId: String): CompanionSnapshot
    fun perception(input: CompanionPerceptionInput): CompanionPerceptionPacket
    suspend fun applyTurn(mutation: CompanionTurnMutation): CompanionSnapshot
    suspend fun beginCommitment(assistantId: String, commitmentId: String, nowMillis: Long): CompanionCommitment?
    suspend fun finishCommitment(
        assistantId: String,
        commitmentId: String,
        result: CompanionActionResult,
        retryAt: Long? = null,
    ): CompanionCommitment?
    fun nextCommitment(assistantId: String, nowMillis: Long = System.currentTimeMillis()): CompanionCommitment?
}
```

`applyTurn` must call all reducers in one `CompanionStore.update`, update global applied relationship event IDs atomically, and never replace a complete list from a stale caller snapshot.

- [ ] **Step 4: Verify and commit**

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git add app/src/main/java/me/rerere/rikkahub/data/companion/CompanionModels.kt app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRuntimeReducerTest.kt
git commit -m "Add atomic companion runtime"
```

### Task 3: Foreground Chat Integration

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/service/LuluIntentModelPlanner.kt`

- [ ] **Step 1: Register and inject the runtime**

Register `CompanionRuntime(store = get())` as a singleton and add it to the `ChatService` constructor.

- [ ] **Step 2: Stop destructive queue reset**

Delete only the `clearTargetedQueue(context)` call from `sendMessage`. Keep resetting the random proactive pulse because user activity changes the next generic wakeup, but an unrelated user message must not cancel explicit commitments.

- [ ] **Step 3: Build the foreground packet**

Before turn planning, map the current conversation to bounded `CompanionConversationTurn` values and assemble a packet with:

- assistant persona;
- current unified snapshot;
- recent user/assistant turns;
- memory recall context already loaded for generation;
- available tool names;
- minutes since previous interaction as a context fact.

- [ ] **Step 4: Persist the completed turn atomically**

After successful generation:

- keep `recordLuluPresenceTurn` temporarily for existing UI;
- map its newest `LuluState` through `toCompanionState()`;
- upsert a concern for each accepted follow-up;
- upsert and activate a durable commitment for each accepted reminder plan;
- use stable `subjectKey` values derived from category, target time, and normalized source text;
- retain every unrelated active commitment.

The temporary model conversion returns `CompanionTurnMutation`; it must not write the store itself.

- [ ] **Step 5: Schedule only the earliest durable commitment**

Replace `replaceTargetedQueue` with a call that mirrors `companionRuntime.nextCommitment(assistantId)` into AlarmManager. The alarm is a wakeup projection; the commitment remains in DataStore.

- [ ] **Step 6: Compile and commit**

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
rg -n "clearTargetedQueue|replaceTargetedQueue" app/src/main/java/me/rerere/rikkahub/service/ChatService.kt
git add app/src/main/java/me/rerere/rikkahub/di/AppModule.kt app/src/main/java/me/rerere/rikkahub/service/ChatService.kt app/src/main/java/me/rerere/rikkahub/service/LuluIntentModelPlanner.kt
git commit -m "Persist companion turns from chat"
```

Expected `rg`: no matches in `ChatService.kt`.

### Task 4: Durable Alarm Projection

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt`
- Test: `app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRuntimeReducerTest.kt`

- [ ] **Step 1: Add commitment identity to alarm metadata**

Add `EXTRA_COMMITMENT_ID` and `KEY_TARGETED_COMMITMENT_ID`. `scheduleCommitment` writes only the currently mirrored commitment ID, assistant ID, due time, and display-safe context needed for process restoration.

- [ ] **Step 2: Resolve business truth from the runtime**

Inject `CompanionRuntime` into `ProactiveMessageTriggerService`. On targeted wakeup, resolve the ID against the matching assistant snapshot. Ignore missing, terminal, or foreign-assistant commitments and immediately schedule the next valid item.

- [ ] **Step 3: Begin execution before generation**

Call `beginCommitment` before model/tool execution. If the validated state machine cannot reach `EXECUTING`, do not generate a proactive message for that commitment.

- [ ] **Step 4: Replace queue popping**

After every targeted outcome, ask `CompanionRuntime.nextCommitment` and mirror that one item. Remove production calls to `popCurrentTargetedAndScheduleNext`; only remove the old queue functions and preference key once `rg` reports zero production references.

- [ ] **Step 5: Compile and commit**

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git add app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt app/src/test/java/me/rerere/rikkahub/data/companion/CompanionRuntimeReducerTest.kt
git commit -m "Schedule proactive work from durable commitments"
```

### Task 5: Real Action Result Feedback

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt`

- [ ] **Step 1: Record all terminal outcomes**

Use these mappings:

```text
nonblank generated reply -> FULFILLED(success=true, summary="message delivered")
[PASS] after fresh appraisal -> FULFILLED(success=true, summary="reappraised; no message needed")
provider/tool exception -> FAILED(success=false, concrete error summary)
recoverable failure -> FAILED -> RETRY_SCHEDULED with bounded backoff
disabled proactive setting -> CANCELLED with explicit reason
```

- [ ] **Step 2: Emit relationship evidence only from outcomes**

On fulfillment emit an idempotent `COMMITMENT_FULFILLED` event with small positive reliability/trust deltas. On final failure emit `COMMITMENT_FAILED` with a reliability reduction and tension increase. Use the commitment ID as `sourceId`; never infer closeness from a generic chat turn.

- [ ] **Step 3: Ensure catch paths reschedule**

The service-level exception path must persist failure before `stopSelf()`, then mirror the next commitment or the retry. It must not leave an `EXECUTING` item indefinitely.

- [ ] **Step 4: Verify and commit**

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git diff --check
git add app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt app/src/main/java/me/rerere/rikkahub/data/companion/CompanionRuntime.kt
git commit -m "Feed proactive outcomes into companion state"
```

### Task 6: Compatibility State And Legacy Planner Removal

**Files:**
- Modify: `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt`
- Modify: `app/src/main/java/me/rerere/rikkahub/service/LuluIntentModelPlanner.kt`
- Delete when unreferenced: `app/src/main/java/me/rerere/rikkahub/service/LuluIntentPlanner.kt`
- Delete when unreferenced: `app/src/test/java/me/rerere/rikkahub/service/LuluIntentPlannerTest.kt`

- [ ] **Step 1: Read unified state in both planning paths**

Foreground and proactive planning must both receive the same `CompanionPerceptionPacket`. Legacy `LuluState` remains a UI projection only and is not the planning source.

- [ ] **Step 2: Replace deterministic fallback output types**

Move fallback decisions to role-neutral companion decision types. The fallback may recognize explicit time/care signals but must not inspect a fixed role name or hardcode housekeeper, lover, friend, or study-supervisor behavior.

- [ ] **Step 3: Remove the legacy planner only after reference audit**

```powershell
rg -n "LuluIntentPlanner|LuluIntentInput|LuluIntentPlan" app/src/main/java
```

Delete `LuluIntentPlanner.kt` only when this command has no production matches. Keep serialization-compatible legacy state models while UI still reads them.

- [ ] **Step 4: Compile and commit**

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
git add -A app/src/main/java/me/rerere/rikkahub/service app/src/main/java/me/rerere/rikkahub/data/service app/src/test/java/me/rerere/rikkahub/service
git commit -m "Remove duplicate companion intent planner"
```

### Task 7: Phase Verification

- [ ] **Step 1: Static invariants**

```powershell
rg -n "clearTargetedQueue|replaceTargetedQueue|KEY_TARGETED_QUEUE" app/src/main/java
rg -n "CompanionRuntime|CompanionPerceptionPacket" app/src/main/java/me/rerere/rikkahub/service/ChatService.kt app/src/main/java/me/rerere/rikkahub/data/service/ProactiveMessageService.kt
rg -n "LuluIntentPlanner" app/src/main/java
git diff --check origin/master..HEAD
```

Expected: no destructive queue call sites; both services use the runtime; no legacy planner reference after Task 6.

- [ ] **Step 2: Main-source compilation**

```powershell
.\gradlew.bat :app:compileDebugKotlin --console=plain
```

- [ ] **Step 3: Focused tests only if existing test-source failures are repaired or bypassable**

Do not spend this phase repairing unrelated stale tests. Record the exact blocker if `testDebugUnitTest` cannot compile.

- [ ] **Step 4: Preserve unrelated localization work**

Confirm TTS localization files remain separate until intentionally committed.

## Self-Review

- Spec coverage: the plan gives foreground and background paths one perception packet, persists assistant-safe state, replaces destructive reminder queues with durable commitments, records real execution outcomes, updates relationships only from evidence, keeps UI compatibility, and gates old planner deletion on zero references.
- Placeholder scan: all lifecycle outcomes, source IDs, scheduling ownership, file paths, verification commands, and deletion gates are explicit.
- Type consistency: `CompanionSnapshot` remains the only aggregate; `CompanionRuntime` is the only mutation coordinator; AlarmManager stores only a wakeup projection; `CompanionActionResult` is persisted on `CompanionCommitment`.
