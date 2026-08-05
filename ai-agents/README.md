# PieCloak AI-agent workspace

This directory coordinates sequential remediation work for PieCloak PR #11.

Each new ChatGPT channel completes exactly one selected package, reviews it harshly, validates the exact PR head, records a durable handoff on `main`, advances the routing state, and stops. Package 06 performs the independent full-PR review.

## Canonical entry point

Copy the prompt in [`/CHATGPT_WORKER_PROMPT.md`](../CHATGPT_WORKER_PROMPT.md) into every new ChatGPT channel.

## Files

- `AGENTS.md` — permanent branch, safety, review, validation, and handoff rules.
- `WORKSPACE-STATE.md` — current package and routing state.
- `WORKSPACE-MANIFEST.md` — repository map, platform target, and validation routes.
- `work-packages/` — bounded package definitions and acceptance criteria.
- `reports/agent-handoffs/` — timestamped reports, template, index, and current pointer.

## Branch model

- `main` stores this coordination workspace and handoffs.
- PR #11 branch `agent/sync-upstream-clean-history` stores implementation, tests, build changes, workflows, and product documentation.

Workers update both branches in that order: implementation commits to the PR branch first, then one coordination-only handoff commit to `main`. Product code must never be added directly to `main` by this workflow.

## Source-of-truth order

1. Current owner instructions.
2. Live GitHub and current code.
3. `AGENTS.md` process rules.
4. `WORKSPACE-STATE.md` routing after reconciliation.
5. The selected work package.
6. The latest handoff as evidence and context.

## Platform direction

- Current target: Minecraft 1.21.11 on the server's Leaf/Paper-compatible stack.
- Geyser/Floodgate compatibility remains required.
- Future target: stable Paper 26.2-or-newer.
- Future adaptation should remain at the build/platform boundary rather than spreading through core visibility logic.

## Normal channel flow

1. Paste the universal prompt into a new channel.
2. Read the current coordination files from `main`.
3. Reconcile both branch heads and all live PR/CI/review state.
4. Complete only the selected package on the PR branch.
5. Perform a separate hostile review and exact-head validation.
6. Push clean implementation commits.
7. Add the handoff and route the next package in one direct coordination commit to `main`.
8. Stop.

No worker merges PR #11 without a new explicit owner instruction.