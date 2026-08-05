---
id: 03-entity-transition-reconciliation
title: Replace blanket entity retries with staged idempotent reconciliation
role: worker
expected_commits: 1-2
---

# Package 03 — Entity-transition reconciliation

## Goal

Make entity SHOW/HIDE repair safe after a failure at any packet stage, without duplicate spawn packets, infinite retries, stale relationship packets, or server/client visibility divergence.

## Confirmed problems

- The current retry catches an exception around the entire multi-packet transition.
- A spawn may succeed while a later correction, replay, passenger, or leash packet fails.
- Because `clientVisible` is committed only at the end, the retry can send another spawn for the same client entity ID.
- Retry attempts have no terminal bound or backoff and can repeat on outgoing traffic forever.
- Direct forced-show paths can commit server visibility before packet reconciliation succeeds.

## Required design properties

1. Reconciliation must know whether the client entity is absent, spawned, synchronized, or relationship-incomplete.
2. Non-idempotent spawn/destroy operations must never be blindly repeated.
3. Later replay/correction/relationship stages must be safely repeatable or individually tracked.
4. The desired visibility state remains authoritative; stale retries must not undo it.
5. Retry storage must have hard per-viewer and global bounds, deduplication, and cleanup.
6. Persistent failure must degrade safely and produce rate-limited diagnostics rather than infinite hot-loop work.
7. World epoch, entity UUID, entity ID reuse, despawn, disconnect, and view replacement must invalidate stale work.
8. Minecart/passenger/leash ordering from the earlier fixes must remain correct.
9. Bypass viewers must converge without entering ordinary managed filtering.

## Required implementation work

- Replace or redesign `EntityTransitionRetryQueue` and the blanket `processEntityTransitionSafely` model.
- Define explicit transition/reconciliation stages and committed client state.
- Make SHOW resume after the last confirmed stage without duplicate spawn.
- Make HIDE safe if destroy succeeds but local bookkeeping fails, or vice versa.
- Make direct relationship-support forced shows use the same reconciliation contract.
- Define bounded retry policy and a final recovery action for persistent failure.
- Preserve bounded current-state replay data from the prior fix.

## Required tests

Use an injectable packet writer or equivalent test seam and fail each stage separately:

- spawn;
- absolute position correction;
- head look;
- metadata/equipment/attribute/effect/velocity replay;
- passenger relationship;
- vehicle relationship;
- leash relationship;
- destroy.

Prove:

- a post-spawn failure does not send a second spawn;
- retry resumes at the correct stage;
- a newer HIDE cancels stale SHOW repair and vice versa;
- persistent failures hit a defined bound and do not leak memory;
- disconnect/world change/despawn clears work;
- minecart passenger visibility and ordering remain correct;
- repeated transient failures eventually converge.

Tests must execute the production reconciliation path, not only queue helper behavior.

## Suggested commit boundaries

1. Introduce staged entity reconciliation and bounded retry state.
2. Route direct/relationship shows through the same contract and add failure-stage tests.

## Acceptance criteria

Entity visibility converges after transient failures without duplicate spawn/destroy operations, infinite retry loops, or stale state surviving lifecycle changes.

## Intended next route

Advance to `04-engine-scheduling-lifecycle`.
