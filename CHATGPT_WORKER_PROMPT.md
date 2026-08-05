# Universal PieCloak ChatGPT worker prompt

Copy everything below into a new ChatGPT channel.

---

Work on `wsg138/PieCloak` as the next sequential repository worker.

Use the GitHub connector, repository tools, GitHub Actions evidence, and a local repository checkout when available. Do not rely on assumptions or context from previous chats.

The coordination system lives on `main`. Product implementation lives on the active PR #11 branch. Complete exactly one active work package, record the result on `main`, and stop.

## Required first actions

Read these files from the current `main` branch, in order:

1. `CHATGPT_START_HERE.md`
2. `ai-agents/AGENTS.md`
3. `ai-agents/WORKSPACE-STATE.md`
4. `ai-agents/reports/agent-handoffs/CURRENT.md`
5. the timestamped report referenced by `CURRENT.md`
6. `ai-agents/work-packages/<current_package>.md`
7. `ai-agents/WORKSPACE-MANIFEST.md`
8. only the earlier handoffs explicitly required by the current handoff or package

Then inspect live GitHub state:

- the current `main` SHA;
- PR #11 state, base, branch, and exact head SHA;
- all open and draft pull requests;
- active branches and recent commits or merges;
- unresolved review threads and requested changes;
- current exact-head GitHub Actions and status checks;
- whether another worker changed `main` routing or the PR branch after the recorded handoff.

Reconcile every discrepancy. Live code and GitHub state take priority over stale records. Never overwrite newer work.

## Work selection

Read `current_package` from `ai-agents/WORKSPACE-STATE.md` on `main` and execute exactly that package.

Do not choose a later package because it looks easier. Do not begin the next package after completing the selected one.

Resume PR #11 and the active implementation branch named by the state file. Do not create a competing implementation PR unless the active branch is proven irrecoverable; if that happens, record the evidence and stop for owner direction.

## Branch separation

Keep the two histories separate:

### PR #11 implementation branch

Put these changes on the PR branch:

- plugin source;
- tests;
- build files and workflows;
- plugin metadata and configuration;
- ordinary product documentation such as README, TESTING, or MAINTAINING changes required by the package.

Create focused, intentional commits. One independent confirmed defect per commit is preferred when practical, including its regression tests.

### `main` coordination branch

After the implementation commits are pushed and reviewed, make one coordination-only commit directly to `main` containing only:

- one new timestamped report under `ai-agents/reports/agent-handoffs/`;
- the updated `ai-agents/WORKSPACE-STATE.md`;
- the updated `ai-agents/reports/agent-handoffs/CURRENT.md`;
- the appended `ai-agents/reports/agent-handoffs/INDEX.md`;
- package/rule corrections only when the discovered facts require them and the reason is documented.

Never put plugin code, tests, build changes, workflows, plugin metadata, or ordinary product documentation directly on `main` during this remediation workflow.

Do not merge `main` into the PR branch merely to obtain the coordination files. Read them from `main` through GitHub. Merge or synchronize code only when a real base conflict requires it and document the reason.

Immediately before updating either branch, verify that its remote head has not moved. If `main` routing has advanced to another package, stop rather than publishing a stale handoff.

## Current product direction

The active production target is Minecraft `1.21.11` on the server's Leaf/Paper-compatible stack, with Geyser/Floodgate compatibility required.

Do not switch the active target to Paper 26.2. A stable Paper `26.2` or newer build is a future upgrade target. Keep version-specific assumptions isolated at the build, Paper adapter, or packet-wrapper boundary so the later move is deliberate and contained.

## Implementation expectations

For the selected package:

- inspect the relevant source, tests, build configuration, workflows, plugin metadata, and documentation;
- address the real failure mechanism, including partial failures and cleanup;
- add focused regression tests that fail before the fix and exercise production paths where practical;
- keep retries, queues, maps, caches, registries, and logging bounded;
- define what state is committed after each packet write or external action;
- make repeated repair idempotent or explicitly stage it;
- handle disconnect, world change, despawn, shutdown, partial startup, and entity-ID reuse where relevant;
- preserve Java and Bedrock/Geyser behavior;
- avoid speculative abstraction that is not needed for 1.21.11.

Do not hide a failure behind a blanket catch that leaves client and server state inconsistent.

## Required hostile review

After implementation appears complete, stop coding and review the entire package diff as if another developer wrote it.

At minimum inspect:

- failure between every pair of packet writes or state mutations;
- duplicate spawn, destroy, replay, or relationship packets;
- dropped transitions and stale retry work;
- state committed before an external action succeeds;
- scheduler rejection and shutdown races;
- disable, re-enable, and partial-startup cleanup;
- optional dependency absence and class loading;
- entity-ID reuse and stale registries;
- sensitive data in logs;
- tests that pass without proving the intended behavior;
- current 1.21.11 compatibility;
- unnecessary coupling that would obstruct a future stable 26.2 upgrade.

Fix every package blocker and confirmed defect. Record unrelated or optional findings for the final coordinator rather than expanding the package indefinitely.

## Validation

Validate the exact final PR-branch head after all implementation changes are complete.

Run the current repository build and package-specific tests, including repeated runs for concurrency-sensitive tests. Inspect the shaded JAR and plugin metadata when relevant. Inspect exact-head GitHub Actions and unresolved review threads.

Never claim a test, server scenario, scanner, or workflow passed without direct evidence. Mark unrun live-server, Leaf, Geyser, or injected-network-failure scenarios as unverified.

## Handoff on `main`

Use `ai-agents/reports/agent-handoffs/TEMPLATE.md`.

The new report must record:

- selected package and why it was current;
- starting `main` coordination SHA;
- starting and ending PR branch SHAs;
- PR number and branch;
- implementation commits;
- exact behavior changed;
- files and architecture affected;
- tests, commands, CI runs, and results;
- hostile-review findings and fixes;
- remaining risks and unverified live behavior;
- next package, or the exact blocker if incomplete.

Because the handoff is committed separately to `main`, record the exact ending PR head directly in the report.

If the package completes, advance `current_package` to the next package and leave state `READY_FOR_AGENT`. If blocked, keep the current package selected, set state `BLOCKED`, and state the exact evidence or input needed.

Commit the report, state, current pointer, and index together to `main` as one coordination-only commit. Verify the live main commit afterward.

## Prohibited actions

Do not:

- merge or close PR #11;
- push product code directly to `main`;
- start a second package;
- force-push the implementation branch;
- deploy a JAR or modify the production server;
- access production data, credentials, Discord routes, or hosting;
- suppress tests or scanners merely to get a green result;
- claim Paper 26.2 support without a selected stable build and direct validation;
- discard another worker's commits.

## Final response

Return:

- selected package;
- starting and ending PR heads;
- implementation commits pushed;
- behavior fixed;
- exact validation evidence;
- remaining risks or manual tests;
- main coordination commit;
- new handoff path;
- next selected package.

Then stop. Do not continue into the next package in the same channel.