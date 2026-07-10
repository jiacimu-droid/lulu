# Role-Neutral Presence Implementation Plan

> **For agentic workers:** Keep legacy serialized names for compatibility. Use lightweight static verification only on this device.

**Goal:** Prevent deterministic companion fallbacks from forcing every Assistant into Lulu's feminine, intimate, supervisor, or housekeeper behavior.

**Architecture:** Keep storage and protocol identifiers unchanged, but make all generated fallback text role-neutral and evidence-bound. Persona-specific behavior remains available through the Assistant system prompt and model-generated presence metadata.

---

### Task 1: Neutralize state and silence fallbacks

- [ ] Update tests to reject automatic intimacy and loneliness.
- [ ] Start new roles at a cautious relationship position.
- [ ] Rewrite silence projection as context continuity without attachment assumptions.
- [ ] Rewrite fallback inner voice and self-scene without invented physical contact.

### Task 2: Neutralize expression affordances

- [ ] Update expression tests to require boundary-safe hints.
- [ ] Remove fixed cute, feminine, hugging, bed, and proximity gestures.
- [ ] Keep mood, energy, timing, and interaction intensity useful to the UI.
- [ ] Rename user-facing relationship context from intimacy to relationship position.

### Task 3: Neutralize planners and tool defaults

- [ ] Replace hardcoded Lulu and female pronouns in chat planning.
- [ ] Remove digital-housekeeper and automatic-supervisor motives from rolling judgments.
- [ ] Replace Lulu alarm labels and camera reasons with role-neutral wording.
- [ ] Change memory extraction defaults to the current role.

### Task 4: Verify and publish

- [ ] Search production prompt text for remaining hardcoded persona leaks.
- [ ] Run `git diff --check` and inspect the commit range.
- [ ] Commit and push `master` without running a full Gradle build.
