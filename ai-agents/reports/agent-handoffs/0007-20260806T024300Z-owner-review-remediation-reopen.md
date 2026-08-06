# Owner-review superseding handoff — remediation sequence reopened

- Date/time: 2026-08-06 02:43 UTC
- Agent role: remediation coordinator
- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Coordination starting SHA: `9b7ca5832ea3c278b75e05e3eaf0ae29e067a1ce`
- Pull request: `#11`
- Implementation branch: `agent/sync-upstream-clean-history`
- Recorded implementation head: `72489966f5c45261e61538c8725c955750fd188b`
- State: `READY_FOR_AGENT`
- Current package: `07-controller-ownership-reenable`

## Why package 06 is superseded

Package 06 recorded `READY_FOR_OWNER` at PR head `72489966f5c45261e61538c8725c955750fd188b`. Owner review subsequently confirmed two release blockers that the package-06 source review and exact-head CI did not detect. The package-06 implementation history and evidence remain factual, but its readiness verdict is no longer valid.

PR #11 is `NOT READY` until packages 07 and 08 are completed and package 09 issues a new final verdict.

## Confirmed blocker 1 — controller ownership survives close

`PacketEntityViewController.SELF` is claimed by the first entity controller and `PacketEventsEntityViewController.SELF` caches that controller. The Paper controller close path unregisters its PacketEvents listener but does not release either singleton. A clean disable followed by enable in the same classloader therefore attempts to construct a replacement while the old static owner remains and can fail as a duplicate instance.

Partial construction can also strand only part of the ownership pair. Cleanup must be ownership-aware, rollback-safe, idempotent, and must not clear a newer owner. Listener cleanup failure must continue to propagate into the existing re-enable safety fence even if singleton ownership itself is released.

This blocker is routed to `07-controller-ownership-reenable`.

## Confirmed blocker 2 — same-world respawn permits stale visibility work

The current world-state fast path treats an unchanged world name as no world transition. An outbound same-world `RESPAWN` can therefore preserve the visibility epoch, pending entity/block transitions, retries, reconciliation tasks, tracked views, replay state, and client-visible assumptions created before respawn.

A queued pre-respawn SHOW can execute after the client has reset its entity universe. If the authoritative post-respawn spawn is hidden, that stale SHOW can create a persistent client-side ghost. Every respawn must establish a new visibility generation and reject pre-respawn work, including when the world name and UUID are unchanged.

This blocker is routed to `08-respawn-visibility-state-invalidation`.

## New package sequence

- `07-controller-ownership-reenable` — release and rollback both entity-controller singleton owners; prove same-classloader reconstruction.
- `08-respawn-visibility-state-invalidation` — make every respawn invalidate pre-respawn visibility and reconciliation state.
- `09-final-integration-pr-cleanup-release-review` — independently re-review the full PR and issue a new final verdict.

## Validation status

The package-06 Build run `31063979744` and Static analysis run `31063979737` remain valid evidence for head `72489966f5c45261e61538c8725c955750fd188b`, but they do not cover the two confirmed failure mechanisms and are not release-readiness evidence for the reopened sequence.

No implementation change is included in this coordination commit. PR #11 remains open and unmerged.

## Next route

Execute package `07-controller-ownership-reenable` only on `agent/sync-upstream-clean-history`. After exact-head validation, record a new handoff on `main`, advance to package 08, and stop.
