---
id: 04-engine-scheduling-lifecycle
title: Fix async scheduling races and complete plugin lifecycle cleanup
role: worker
expected_commits: 2-3
---

# Package 04 — Engine scheduling and lifecycle

## Goal

Prevent the async engine from wedging during partial task submission and make plugin startup, shutdown, and same-JVM re-enable behavior internally consistent.

## Confirmed problems

- `tickThreadsRunning` is initialized before worker submission. A fast worker plus later submission failure can move the counter to zero without any worker performing finalization.
- Static listener and controller singletons retain the old plugin/engine and reject or skip a second initialization.
- PacketEvents listeners are registered in constructors without a complete unregister/reset path.
- `onDisable()` does not coordinate active async work or clear global registries.
- Partial startup can leave null fields or registered partial components and then throw again during disable.

## Required design properties

1. Tick ownership/finalization happens exactly once for every accepted tick.
2. Partial submission, scheduler rejection, worker exception, and shutdown cannot leave the engine permanently running.
3. No counter can become negative or reach zero without finalization.
4. Shutdown prevents new ticks, fences or drains accepted work, and performs bounded cleanup.
5. Startup is transactional enough that partial failure can unwind registered components.
6. Same-JVM disable/re-enable either works with fresh components or is explicitly rejected before stale listeners/controllers can run. Prefer safe reinitialization.
7. Static registries and singleton references have explicit lifecycle owners and reset methods.
8. PacketEvents and Bukkit listener registration is paired with unregistering.
9. Shutdown methods are null-safe and idempotent.

## Required implementation work

- Redesign worker submission accounting so only successfully scheduled work is counted, or use a completion primitive whose finalization contract cannot race.
- Add an engine shutdown state and safe finalization path.
- Add lifecycle methods to relevant controllers, registries, and listeners.
- Register components through an owner that can unwind them in reverse order.
- Make `RaycastedAntiESP.onDisable()` safe after any partial `onEnable()` point.
- Ensure metrics/log shutdown cannot prevent the remaining cleanup.
- Do not use an unbounded wait on the Paper main thread.

## Required tests

At minimum prove:

- rejection before the first worker;
- rejection after one or more workers are scheduled;
- a worker completing before later submission fails;
- worker exception;
- shutdown during scheduling;
- shutdown while workers are running;
- repeated shutdown calls;
- partial startup failure at several registration points;
- disable then enable in the same JVM creates fresh effective listeners/controllers;
- old instances cannot receive events after disable.

Use deterministic latches/fake runners rather than timing-only sleeps.

## Suggested commit boundaries

1. Fix exact-once async tick finalization under partial submission.
2. Add complete controller/listener/registry shutdown and reset.
3. Make plugin startup/disable transactional and null-safe.

## Acceptance criteria

No scheduler failure can wedge the engine, and all plugin-owned asynchronous and listener state has a clear, tested lifecycle.

## Intended next route

Advance to `05-optional-integrations-hardening`.
