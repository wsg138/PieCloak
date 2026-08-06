---
id: 07-controller-ownership-reenable
title: Controller ownership and same-JVM re-enable
role: implementation
expected_commits: 1-or-2-focused-fixes
---

# Package 07 — Controller ownership and same-JVM re-enable

## Why this package is current

Owner review after package 06 confirmed that the entity packet-controller singleton fields survive normal close. `PacketEntityViewController.SELF` and `PacketEventsEntityViewController.SELF` can therefore retain the first controller instance after disable. A second enable in the same classloader attempts to construct a replacement controller and fails as a duplicate instance.

This is a release blocker. Package 06's `READY_FOR_OWNER` verdict is superseded.

## Goal

Make entity-controller ownership explicit, rollback-safe, idempotently releasable, and safe for same-JVM disable/re-enable without weakening the existing shutdown fence.

## Required behavior

- The active controller owns both singleton references as one lifecycle unit.
- Normal owning close releases both singleton references.
- A close from a stale or non-owning controller must never clear a newer owner's references.
- Repeated close is idempotent.
- Failure during base, PacketEvents, or platform-controller construction must rollback every singleton assignment made by that failed construction.
- Listener-unregistration or other close failure must not strand singleton ownership. The failure must still propagate so the existing lifecycle safety fence can block unsafe re-enable when registrations may remain live.
- Successful close followed by construction in the same classloader must create and expose a new controller.
- Partial startup cleanup must leave either a fully owned active controller or no controller ownership; never a half-owned pair.
- Keep the current Minecraft 1.21.11 / Leaf / PacketEvents boundary intact. Do not mix in package 08 respawn changes.

## Required inspection

Inspect together:

- `core/.../PacketEntityViewController.java`;
- `packetevents/.../PacketEventsEntityViewController.java`;
- Paper PacketEvents controller registration and close paths;
- `RaycastedAntiESP` startup, `LifecycleScope`, reset, and re-enable fencing;
- controller lifecycle tests and platform test dependencies;
- all callers of the controller singleton accessors.

## Required regression tests

Add focused tests proving at least:

1. construct → close/release → reconstruct succeeds in the same classloader;
2. failed construction rolls back both singleton references;
3. repeated close/release is harmless;
4. a stale owner cannot clear a different current owner;
5. cleanup failure still releases owned singleton references while surfacing the failure;
6. partial ownership cannot remain visible after rollback.

Tests must exercise production ownership code rather than only a mock copy of the algorithm.

## Hostile review

After the fix, review failure at every ownership step:

- core singleton claimed but PacketEvents singleton not yet claimed;
- both claimed but listener registration fails;
- listener unregistration throws;
- lifecycle close runs more than once;
- old close races or runs after replacement construction;
- startup fails before `activeLifecycle` publication;
- reset runs after a fenced shutdown;
- same-JVM re-enable after clean shutdown.

Confirm no cleanup path clears another controller's ownership and no failure is hidden.

## Validation

Validate the exact final PR head with:

```bash
./gradlew clean verifyCurrentPlatformBaseline :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon
```

Run the focused ownership tests repeatedly if they contain any concurrency or race coverage. Inspect exact-head Build and Static analysis runs and unresolved PR review threads. Keep Leaf startup/disable/re-enable as manual unless directly exercised.

## Output and next route

If complete, record the exact ending PR head and advance routing to `08-respawn-visibility-state-invalidation` with `READY_FOR_AGENT`. Do not begin package 08 in this channel.
