# PieCloak sequential-agent operating rules

These rules apply to every ChatGPT or repository-agent session using this workspace.

## 1. One package per channel

Complete exactly the work package named by `current_package` in `ai-agents/WORKSPACE-STATE.md` on `main`.

Do not begin the next package after finishing the current one. Record the next route and stop. Do not broaden a package merely because adjacent cleanup is convenient.

A worker may fix a newly discovered defect outside the written package only when it is a direct consequence of the package changes or makes the package unsafe. Otherwise record it for the final coordinator.

## 2. Two-branch operating model

This workflow intentionally separates coordination from implementation.

### `main` — coordination only

`main` contains:

- permanent agent rules;
- current routing state;
- work-package definitions;
- repository manifest;
- timestamped handoffs and pointers;
- the universal ChatGPT worker prompt.

During this remediation program, ordinary workers may commit directly to `main` only for the coordination update required after their package. That commit must not contain product code.

### PR #11 implementation branch

The active branch named in `WORKSPACE-STATE.md` contains:

- source and tests;
- build files and GitHub workflows;
- plugin metadata and configuration;
- normal product documentation required by a package.

Workers push implementation commits to that branch and do not merge PR #11.

Do not copy the agent workspace onto the PR branch. Do not merge `main` into the PR merely to obtain routing files. Read current coordination files directly from `main`.

## 3. Required reading and live reconciliation

Before editing, read from current `main`, in order:

1. `CHATGPT_START_HERE.md`
2. `ai-agents/AGENTS.md`
3. `ai-agents/WORKSPACE-STATE.md`
4. `ai-agents/reports/agent-handoffs/CURRENT.md`
5. the report referenced by `CURRENT.md`
6. the current work-package file
7. `ai-agents/WORKSPACE-MANIFEST.md`
8. only earlier reports explicitly required by the current package or handoff

Then verify live GitHub state:

- current `main` SHA;
- PR #11 state, base, active branch, and exact head;
- all other open or draft PRs that may overlap;
- active branches and recent commits or merges;
- unresolved review threads and requested changes;
- current-head CI and status checks;
- whether another worker advanced either branch.

Live code and GitHub state override stale records. Never overwrite newer work.

## 4. Current platform and upgrade boundary

The current production target is Minecraft `1.21.11` on a Leaf/Paper-compatible server, with Geyser/Floodgate support required.

Do not target Paper 26.2 yet. Paper 26.2-or-newer is a future stable-upgrade target.

Package 01 establishes the coherent current baseline. Later packages must:

- keep version-specific behavior in build configuration, Paper adapters, or packet wrappers;
- avoid hard-coding future Paper internals into `core`;
- avoid reflection unless no supported API exists and failure is explicit;
- preserve Java and Bedrock-compatible behavior;
- document assumptions likely to need review during a later 26.2 upgrade.

## 5. Implementation branch and commit policy

- Resume the existing active PR rather than opening a competing PR.
- Never merge PR #11 as a worker.
- Never force-push the implementation branch.
- Reconcile with the latest remote head immediately before pushing.
- Prefer one intentional commit per independent confirmed defect, including its tests and necessary product documentation.
- Do not leave `fixup!`, WIP, debugging, revert/reapply, or formatting-only noise.
- A package should normally produce one to three implementation commits.
- If experimentation creates noisy history, use an isolated temporary branch and integrate only clean commits onto the current active head.

Do not rewrite an earlier worker's shared commits merely for aesthetics.

## 6. Coordination commit policy

After the final implementation head is pushed and validated, update `main` with one coordination-only commit.

Normally that commit changes only:

- a new timestamped handoff report;
- `ai-agents/WORKSPACE-STATE.md`;
- `ai-agents/reports/agent-handoffs/CURRENT.md`;
- `ai-agents/reports/agent-handoffs/INDEX.md`.

A worker may correct a package definition or permanent rule only when newly verified facts make the existing routing inaccurate or unsafe. Explain the change in the report.

Before writing to `main`:

1. fetch the live main head;
2. confirm `current_package` still names the package just completed;
3. confirm no newer handoff exists;
4. rebase the coordination update on the live main tree without importing PR code;
5. commit the routing files atomically when practical;
6. verify the resulting live main SHA and file contents.

If main routing advanced, do not overwrite it. Reconcile and stop if another worker already completed the package.

## 7. Implementation standards

Inspect and account for, as relevant:

- packet ordering and partial-write failures;
- idempotency and duplicate prevention;
- bounded retries, queues, maps, caches, and logging;
- world, dimension, disconnect, despawn, and entity-ID reuse cleanup;
- startup, partial startup, disable, re-enable, and full restart behavior;
- scheduler rejection, shutdown races, and worker finalization;
- Paper/Leaf thread rules and PacketEvents callback context;
- optional provider present and absent behavior;
- Java and Geyser/Floodgate clients;
- invalid configuration and safe failure behavior;
- sensitive data in logs and exceptions;
- tests that can pass without proving the requested behavior.

Do not mask failures with a blanket catch that leaves server and client state inconsistent. Packet repair logic must define what has already been sent, what is safe to repeat, and how stale work is discarded.

## 8. Required hostile review

After implementation appears complete, stop coding and review the complete package diff as though another developer wrote it.

At minimum check:

- whether the fix addresses the actual failure mechanism;
- whether failure can occur between any two state changes or packet writes;
- whether retrying repeats a non-idempotent operation;
- whether one failure discards unrelated queued work;
- whether state is committed before an external action succeeds;
- whether cleanup handles disconnect, world change, despawn, shutdown, and ID reuse;
- whether a map or queue can grow without a hard bound;
- whether tests inject failure at each meaningful stage;
- whether mocks avoid exercising the real implementation;
- whether current-target compatibility was directly tested;
- whether future-upgrade flexibility was preserved without speculative abstraction.

Classify findings as package blocker, confirmed defect, coordinator follow-up, or optional cleanup. Fix package blockers and confirmed defects.

## 9. Validation and evidence

Validation must apply to the exact final implementation head after tracked implementation changes are complete.

Use the commands and workflows current to the repository after package 01. Normally include:

- required generated-source steps;
- clean Gradle compilation and all relevant tests;
- module-specific failure-path tests;
- shaded Paper JAR build and integrity inspection;
- configured static analysis;
- repeated runs for concurrency-sensitive tests;
- exact-head GitHub Actions status;
- unresolved review-thread inspection.

Never claim a check passed without direct evidence. Cancelled, skipped, superseded, different-head, or merge-ref-only results are not exact-head evidence.

Live server behavior that was not exercised must remain explicitly unverified.

## 10. Durable handoff requirements

Each worker adds one new timestamped report under:

`ai-agents/reports/agent-handoffs/`

Use `TEMPLATE.md`. The report must include:

- package ID and title;
- starting coordination main SHA;
- starting and ending PR branch SHAs;
- PR and branch;
- implementation commits;
- exact scope completed;
- files and architecture changed;
- tests, commands, CI runs, and results;
- hostile-review findings and fixes;
- remaining risks and unverified live behavior;
- next package and why it is safe, or the exact blocker.

Because reports live on `main`, they may record the exact final PR head without creating a self-referential implementation commit.

Update `WORKSPACE-STATE.md`, `CURRENT.md`, and `INDEX.md` in the same coordination commit.

Do not edit older timestamped handoffs after another worker has relied on them. Add a superseding report when correction is necessary.

## 11. State transitions

Use:

- `READY_FOR_AGENT`
- `IMPLEMENTING`
- `REVIEWING`
- `VALIDATING`
- `BLOCKED`
- `FINAL_REVIEW`
- `READY_FOR_OWNER`
- `NOT_READY`

The committed state after a completed worker package should normally be `READY_FOR_AGENT` with `current_package` advanced. If blocked, keep the current package selected and record exactly what evidence or input is required.

## 12. Prohibited actions

Without a newer explicit owner instruction, do not:

- merge or close PR #11;
- place product code or tests directly on `main`;
- deploy a JAR or modify the production server;
- access production data, credentials, Discord routes, or hosting;
- change the current target away from Minecraft 1.21.11;
- claim Paper 26.2 support without a stable selected target and direct validation;
- suppress tests or scanners merely to obtain a green result;
- create a competing implementation PR;
- silently discard another worker's commits.

## 13. Blocked work

When a genuine blocker prevents safe completion:

1. verify it with code, logs, authoritative documentation, or a minimal reproduction;
2. do not invent an API, version, behavior, or test result;
3. record the exact blocker in the handoff and state file on `main`;
4. keep the current package selected;
5. push partial implementation only when it is independently safe and not misleading;
6. stop.

## 14. Final worker response

Report the selected package, starting and ending PR heads, implementation commits, behavior fixed, validation evidence, remaining live risks, main coordination commit, handoff path, and next package. Then stop.