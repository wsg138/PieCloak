---
id: 02-block-transition-reliability
title: Make block visibility transitions failure-isolated and repairable
role: worker
expected_commits: 2-3
---

# Package 02 — Block-transition reliability

## Goal

Ensure block and block-entity HIDE/SHOW operations cannot permanently desynchronize a viewer, discard adjacent transitions, leak managed block data, or crash packet handling after a partial failure.

## Confirmed problems

- `PackedBlockTransitionQueue` consumes an entry even when the callback throws, and unvisited transitions in that entry are discarded.
- `PacketEventsBlockViewController` performs multi-packet HIDE/SHOW work without per-transition failure isolation or retry/reconciliation.
- Disabling tile checks commits mode and visibility state before every client repair succeeds.
- Uncached standalone block-entity data currently passes through and can log complete NBT.
- `MAP_CHUNK_BULK` deliberately throws a runtime exception.

## Required design properties

1. One transition failure must not discard unrelated transitions.
2. Retrying must be idempotent or stage-aware.
3. Retry work must be bounded, deduplicated, and cleaned on disconnect/world change.
4. A stale retry must not override newer visibility or mode state.
5. Mode-disable repair must be repeatable until all affected client views converge.
6. Managed block-entity data must not fail open solely because cache population or packet order was unexpected.
7. Non-managed virtual signs or plugin-created block entities must not be broken by a blanket cancellation rule.
8. Unsupported packet types must be handled safely and observably without throwing from the listener.
9. Sensitive NBT must not be logged.

## Required implementation work

- Change queue-drain semantics or controller consumption so callback failure cannot discard remaining packed work.
- Introduce a block transition reconciliation mechanism with explicit keys, bounds, and stale-state checks.
- Define HIDE and SHOW stages, including fake block, real block, and block-entity NBT writes.
- Make mode toggling distinguish desired mode from client repairs still pending.
- Classify uncached block-entity packets based on whether the target is managed; fail closed or safely defer managed data and pass non-managed virtual data.
- Replace the `MAP_CHUNK_BULK` crash with safe handling and a bounded diagnostic.

## Required tests

Inject a writer failure at each meaningful stage:

- fake-block HIDE write;
- real-block SHOW write;
- block-entity NBT SHOW write;
- one transition in the middle of a packed entry;
- one repair while disabling checks.

Prove:

- later transitions still run;
- retries do not multiply;
- stale retries are discarded after mode/world changes;
- retries converge after a transient failure;
- retry structures remain bounded under repeated failure;
- managed unknown block data does not leak;
- non-managed virtual block data still works;
- unsupported bulk packets do not throw.

Avoid tests that only exercise a helper map while bypassing the real controller/queue path.

## Suggested commit boundaries

1. Preserve and reconcile failed block transitions.
2. Make mode-disable repair idempotent.
3. Harden unknown block-entity and unsupported packet handling.

Combine boundaries when the implementation is inseparable; do not create fixup noise.

## Acceptance criteria

No single block packet-write exception can permanently strand state, discard unrelated transitions, or leak a managed block entity. All failure queues and diagnostics are bounded.

## Intended next route

Advance to `03-entity-transition-reconciliation`.
