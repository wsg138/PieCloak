---
id: 08-respawn-visibility-state-invalidation
title: Respawn visibility-state invalidation
role: implementation
expected_commits: 1-or-2-focused-fixes
---

# Package 08 — Respawn visibility-state invalidation

## Why this package follows package 07

Owner review confirmed that a same-world `RESPAWN` currently takes the same-world fast path in world-state handling. That path preserves the visibility epoch, tracked views, pending transitions, retry work, replay state, and client-visibility assumptions created before respawn. A queued pre-respawn SHOW can then execute against the new client entity universe and leave a hidden entity as a persistent client-side ghost.

Package 07 must first make controller lifecycle ownership reliable so package 08 can reason about one active controller and its retry state.

## Goal

Treat every respawn as a new client visibility generation, including respawns whose world name and UUID are unchanged, so no pre-respawn block or entity work can mutate the post-respawn client state.

## Required behavior

- Every outbound `RESPAWN` advances or replaces the viewer visibility epoch even when the world is unchanged.
- Invalidate pending entity and block transitions, staged retries, post-spawn reconciliation tasks, replay assumptions, expected relationship work, and stale client-visible state associated with the pre-respawn generation.
- Rebuild self/world state without leaving the player stuck in an odd transition epoch when parsing or cleanup fails.
- A pre-respawn SHOW, HIDE, relationship replay, block repair, or retry must be rejected after respawn.
- Entity-ID reuse after respawn must not inherit pre-respawn visibility or reconciliation state.
- Managed and bypass packet paths must apply equivalent respawn invalidation.
- Preserve ordinary same-world non-respawn packets; do not turn every repeated world-state observation into a destructive reset.
- Preserve Java and Geyser/Floodgate behavior and the current Minecraft 1.21.11 target.

## Required inspection

Inspect together:

- `PacketEntityViewController.handleWorldStatePacket` and all callers;
- PacketEvents `RESPAWN` handling in managed and bypass paths;
- `PlayerData` world epochs and transition fencing;
- entity/player/block views and packed transition queues;
- entity and block retry queues;
- Netty pending reconciliation and replay state;
- client-visible flags, self entity state, and expected destroy state;
- disconnect, dimension change, relog, and entity-ID reuse tests.

## Required regression tests

Add focused production-path tests proving at least:

1. queued SHOW → same-world respawn → authoritative hidden spawn rejects the stale SHOW and sends no stale spawn;
2. the post-respawn hidden entity is not left as an unreconciled client-side entity;
3. same-world respawn advances the visibility epoch and clears pre-respawn transition/retry/reconciliation state;
4. managed and bypass respawn paths use the same invalidation contract;
5. stale relationship and block-repair work cannot cross the respawn boundary;
6. ordinary repeated same-world observations that are not respawns do not erase valid state.

## Hostile review

Review failure before, during, and after every respawn mutation, including missing world names/UUIDs, packet-send tasks already queued, send-then-throw behavior, entity-ID reuse, disconnect during respawn, and shutdown with stale work present. Ensure state is not committed in an order that permits old work to become current again.

## Validation

Validate the exact final PR head with the current clean build, focused respawn tests, full tests, shaded JAR inspection, static analysis, exact-head GitHub Actions, and unresolved review-thread inspection. Keep live Leaf and Geyser/Floodgate scenarios explicitly unverified unless directly run.

## Output and next route

If complete, advance routing to `09-final-integration-pr-cleanup-release-review`. Do not merge PR #11.
