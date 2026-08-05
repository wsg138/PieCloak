# Coordination bootstrap handoff

- Date/time: 2026-08-05T15:37:00-04:00
- Agent role: coordinator/bootstrap
- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Coordination starting SHA: `457e9d975297392e5725cbd58667bb209dceb661`
- Pull request: `#11`
- Implementation branch: `agent/sync-upstream-clean-history`
- Recorded implementation head: `33b615d5ef3a937dafdf461c48e9626ee7d342fc`
- Package selected next: `01-current-platform-baseline`
- State: `READY_FOR_AGENT`

## Live reconciliation

At the corrected bootstrap, PR #11 was open, non-draft, and mergeable. The coordination files had briefly been placed on the PR branch in commit `72e28ced382bb7c4e0863693d1510f86950bafb0`. The owner clarified that all routing documents and worker handoffs must live directly on `main`, while PR #11 must remain an implementation-only workstream.

The PR branch is therefore restored to the recorded implementation head `33b615d5ef3a937dafdf461c48e9626ee7d342fc` after the main-hosted workspace is published.

## Owner direction

- Current production target: Minecraft 1.21.11.
- Stable Paper 26.2-or-newer is a future upgrade target.
- Current fixes must not make that later migration unnecessarily difficult.
- Each worker will run in a new ChatGPT channel.
- The owner will paste the same universal prompt into every new channel.
- Each channel completes exactly one package, records what it did on `main`, advances the next route, and stops.

## Deep-review findings routed

The prior hostile review identified release-level or high-risk issues in:

- build and platform target consistency;
- block transition failure handling;
- entity transition retry semantics;
- async scheduling finalization;
- plugin lifecycle cleanup;
- optional Fancy integration loading and stale bypass IDs;
- update checker, join-window arithmetic, unsupported packet handling, and sensitive NBT logging.

The complete package mapping is in `ai-agents/WORKSPACE-STATE.md`.

## Main-hosted coordination framework

Added to `main`:

- root entry instructions;
- one universal copy-paste ChatGPT worker prompt;
- permanent dual-branch operating rules;
- machine-readable workspace state;
- repository manifest;
- six sequential work packages;
- timestamped handoff format, template, index, and current pointer.

No runtime plugin code is changed by this coordination commit.

## Branch boundary

- Product implementation, tests, build files, workflows, metadata, and product docs go to PR #11.
- Agent rules, package routing, and handoffs go directly to `main`.
- A worker updates the PR branch first, then makes one coordination-only commit to `main`.
- Workers do not merge PR #11.
- Only one package runs at a time.
- Package 06 performs the independent final review and owner-facing verdict.

## Next route

Copy `CHATGPT_WORKER_PROMPT.md` into a new ChatGPT channel. The worker must select `01-current-platform-baseline` from current main state, complete it on PR #11, record the handoff on `main`, advance the route to package 02, and stop.