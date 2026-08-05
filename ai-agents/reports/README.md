# Agent handoff reports

Every worker channel adds one timestamped Markdown report under `agent-handoffs/` on `main` after its implementation commits are pushed to PR #11.

## File naming

Use:

`YYYY-MM-DD-HHMM-package-id-short-title.md`

Use UTC or include the timezone in the report. Never overwrite an earlier worker's report after another worker has used it; add a superseding report instead.

## Required format

Use [`agent-handoffs/TEMPLATE.md`](agent-handoffs/TEMPLATE.md).

The report must record the exact ending PR branch SHA because implementation and coordination are committed separately.

## Coordination update

After the implementation branch is reviewed and validated, make one direct coordination-only commit to `main` containing:

- the new timestamped report;
- updated `WORKSPACE-STATE.md`;
- updated `CURRENT.md`;
- appended `INDEX.md`.

Do not include source, tests, build files, workflows, plugin metadata, configuration, or ordinary product documentation in that main commit.

## Pointer files

- `CURRENT.md` points to exactly one latest handoff and current package.
- `INDEX.md` is append-only in chronological order.

The next agent must verify every recorded SHA and status against live GitHub. A handoff is evidence, not authority.