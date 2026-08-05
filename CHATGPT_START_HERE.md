# PieCloak sequential ChatGPT workflow

The coordination documents for PR #11 live on `main`. Implementation work remains on the PR branch.

## Start the next worker

Open [`CHATGPT_WORKER_PROMPT.md`](CHATGPT_WORKER_PROMPT.md), copy the prompt, and paste it into a new ChatGPT channel.

Use the same prompt for every channel. The worker determines the current package from the files on `main`, reconciles them with live GitHub, completes exactly one package, leaves the durable handoff on `main`, and stops.

## Branch responsibilities

- `main`: agent rules, package definitions, routing state, and handoff reports only.
- PR #11 branch `agent/sync-upstream-clean-history`: plugin code, tests, build files, workflows, and product documentation.

A worker must not merge PR #11. A worker must not place product code on `main`. The final package performs the independent full-PR review and returns a READY or NOT READY verdict for the owner.

## Required reading

Every worker reads from `main`, in order:

1. `CHATGPT_START_HERE.md`
2. `ai-agents/AGENTS.md`
3. `ai-agents/WORKSPACE-STATE.md`
4. `ai-agents/reports/agent-handoffs/CURRENT.md`
5. the handoff referenced by `CURRENT.md`
6. the selected file under `ai-agents/work-packages/`
7. `ai-agents/WORKSPACE-MANIFEST.md`

The current production target is Minecraft 1.21.11. Paper 26.2-or-newer is a future stable upgrade target and must remain a contained platform/build change.