---
id: 05-optional-integrations-hardening
title: Guard optional integrations and close remaining correctness gaps
role: worker
expected_commits: 2-3
---

# Package 05 — Optional integrations and hardening

## Goal

Make optional plugin integrations truly optional, prevent stale bypass identity, and fix the remaining smaller correctness and logging defects from the deep review.

## Confirmed problems

- `FancyCompatibility` is instantiated unconditionally although FancyNPCs and FancyHolograms are soft dependencies.
- Listener method signatures directly reference optional API classes and may fail class loading or registration when a plugin is absent.
- Optional entity IDs are added to a global bypass registry without reliable removal, allowing stale growth and ID reuse hazards.
- The update checker configures timeouts on one connection but reads from a newly opened unconfigured connection.
- The join-time update-notification arithmetic is reversed.
- Full block-entity NBT can be logged.

## Required design properties

1. PieCloak enables normally when neither optional Fancy plugin is installed.
2. It also works when exactly one is installed and when both are installed.
3. Optional API classes are not resolved on an absent-plugin path.
4. Compatibility listener registration and cleanup follows the lifecycle contract from package 04.
5. Bypass identity cannot survive entity removal, plugin reload, world change, or entity-ID reuse.
6. Registry size is bounded by live relevant entities, not historical creations.
7. Update-check connections use the configured timeouts on the actual stream and close/cancel cleanly.
8. Join-window logic uses monotonic tick arithmetic with wraparound considered where appropriate.
9. Logs identify the problem without serializing sensitive or unbounded NBT.

## Required implementation work

- Independently detect each optional plugin before loading its adapter.
- Isolate optional classes behind separately loaded adapter classes or reflection only when necessary and carefully guarded.
- Use removal events, UUID/generation identity, or another proven lifecycle mechanism to clear bypass state safely.
- Add bounded reconciliation on plugin enable/reload for already-existing optional entities if supported.
- Fix the actual connection used by the update checker and ensure timeout/cancellation behavior.
- Correct the join-time predicate and tests.
- Replace sensitive NBT diagnostics with type/location/size or a redacted summary.
- Recheck `plugin.yml` soft-dependency names and ordering.

## Required tests

Prove:

- no Fancy classes available;
- only FancyNPCs available;
- only FancyHolograms available;
- both available;
- create/remove/recreate with entity-ID reuse;
- registry cleanup on disable and world changes;
- update checker connect/read timeout using a controllable test server or fake connection;
- no second unconfigured connection is opened;
- join notification inside and outside the intended window;
- diagnostics never include full NBT payload.

## Suggested commit boundaries

1. Make optional Fancy adapters absence-safe and lifecycle-owned.
2. Make bypass identity removal and ID reuse safe.
3. Fix update checking, join-window arithmetic, and sensitive diagnostics.

## Acceptance criteria

Optional integrations cannot prevent startup, stale bypass state cannot exempt unrelated entities, and the remaining confirmed correctness/logging issues are covered by tests.

## Intended next route

Advance to `06-final-integration-review` and set the workspace state for coordinator review.
