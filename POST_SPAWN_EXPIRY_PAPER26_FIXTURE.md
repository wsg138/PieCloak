# PieCloak issue #17 — opt-in Paper 26.2 post-spawn expiry fixture

Status: **fixture contract only; NOT a successful runtime execution**. This document is a
test specification for a separate, security-reviewed fixture plugin and protocol-client
driver. It does not modify production behavior and must not be interpreted as a fix.

## Established implementation facts (main `4927912e`)

- An `ENTITY_TELEPORT` for a non-bypassed, unknown entity reaches
  `processTeleportPacket` and queues `PEEntityStateReconciliationTask.teleport`.
- `NettyData.evictOldPendingPostSpawnTasks` drops the queue after
  `EntitySpawnTask.TICKS_BEFORE_EVICTION` (16 ticks) and suppresses further queued
  reconciliation for that *viewer/entity ID*.
- A normal managed spawn invokes `runPendingPostSpawnTaskForEntity`; a bypassed spawn,
  destroy and viewer reset clear the affected pending/suppressed state.
- Unknown-entity teleport packets are **not automatically canceled** merely because
  PieCloak has no corresponding tracked entity. Do not change this without verifying
  both network and anti-leak behavior.

The October 23 production evidence reported 28 expiry warnings for entity ID 147
across eight viewers, each with an oldest `teleport` task. The sender and actual
reason for missing spawns are **unknown**. This fixture tests a controlled analogue,
not the original entity's identity or sender.

## Security prerequisites / immutable inputs

1. Run only on the owner's offline test PC, never Bloom/SMP. No production mounts,
   player data, credentials, external dependencies or downloaded plugin artifacts.
2. Verify the *full* image IDs before use, not tags:
   - Paper+PieCloak fixture server: `sha256:359f227c8ea6ba2b7fdd7118b01b803dd78179eb2a4736fdc25c8c306d91ec11`
   - Protocol-client image: `sha256:384c8b1ef793fa809ca4a45943eaffd489c3c00d2eca2f88bbd6693656e86470`
   - Paper target: **official 26.2 build 129**; PacketEvents target **2.14.0**.
   These values come from a prior independent fixture receipt. Check that images,
   plugin hashes and current source have not drifted.
3. Use a single disposable **network-none** container with Paper and two local
   protocol clients communicating exclusively over its private loopback. Do **not**
   use published ports, `--network container:...`, external networks or DNS.
   Run as nonroot with `--read-only`, `--cap-drop=ALL`, `--security-opt=no-new-privileges`,
   bounded `--memory`, `--cpus`, `--pids-limit`, `--log-opt max-size/max-file`,
   `--tmpfs` for ephemeral state, explicit watchdog timeout, and `--rm`.
   If the images cannot house both client and server without changing containment,
   **stop** and request a newly pinned, reviewed local image; do not relax isolation.
4. A deterministic packet-injection test plugin is **not yet approved or built**.
   Source-review its API and classpath first, compile it locally, record its SHA-256,
   and independently inspect it before execution. A test-only packet sender that
   calls a PacketEvents `writePacketSilently` API may bypass PieCloak interception:
   reject any fixture unless trace evidence proves the deliberately injected packet
   entered PieCloak's actual outbound listener path. Do not build or execute arbitrary
   downloaded JARs.

## Required packet-driven test phases

Use one real managed villager beyond the configured always-show radius behind a
solid occluder, two independent 26.2 protocol clients, and a visible unfiltered
control animal. Capture per-viewer *server listener input*, *PieCloak cancellation*,
and *client protocol* packets. A deterministic opt-in test sender must allow a
specific villager's ordinary outbound spawn to be withheld from one viewer **before
PieCloak consumes it**, while injecting an inbound-to-listener clientbound
`ENTITY_TELEPORT` for the same ID and target UUID. Never send a false negative
through PieCloak's silent-send path.

| Phase | Procedure | Required assertions |
| --- | --- | --- |
| A — missing spawn | Withhold only viewer A's villager spawn; send teleport first through the verified interceptor path | Listener records teleport for unknown ID; client A gets **no villager spawn** and never renders the hidden villager; viewer B's valid managed entity/control baseline is unaffected |
| B — expiry | Wait at least 16 server ticks after queued teleport, then send a harmless normal packet to drive deferred eviction | Exactly one targeted expired-queue event for A/ID; queued task discarded; later unknown state does not produce repeat queues/warnings; no client spawn or hidden entity leak |
| C — late managed spawn | Release a fresh, valid spawn with the same ID/UUID in a visible-safe position, then send fresh state | No stale teleport coordinates are replayed; A receives correct spawn/state/reveal; B remains correct; later occluded hide/destroy and reveal still work |
| D — prompt managed spawn (control) | Reset scenario; send teleport before spawn but release spawn **before tick 16** | Pre-spawn teleport reconciles in order; no expiry event; client position matches the final valid authoritative state |
| E — bypassed spawn | Reset scenario; use an explicitly unfiltered entity type and a before-spawn teleport, then deliver bypassed spawn | Pending task is discarded, not replayed; no phantom managed entity or quarantined ID; client sees only legitimate bypassed packets |
| F — destroy/reused ID | Reset scenario; expire missing spawn then send destroy; reintroduce the ID with a new UUID and fresh state | Old queue does not replay to new entity; new task can be queued and consumed; no cross-entity visibility leak |
| G — reconnect | Expire an unknown-entity queue, disconnect A, then join with a new connection epoch | No stale task/quarantine from A's prior session; A and B see correct hidden/revealed state and controls |
| H — plugin-free control | Repeat the same packet driver without PieCloak (keep Paper+PacketEvents) | Verifies driver delivery, packet ordering and normal client's behavior; makes accidental silent injection detectable |

## Evidence requirements and verdict

- Preflight receipt: full immutable image IDs, hashes of Paper/PieCloak/PacketEvents
  and test plugin JARs, source commit, JVM/client versions, exact command line with
  secrets removed, feature flag opt-in, `docker inspect` security configuration.
- Per phase: synthetic ID only, tick/epoch, expected upstream spawn/teleport/destroy,
  what PieCloak actually intercepted and canceled, client packet types/order,
  visible/not-visible assertion, expiry count, termination/cleanup exit codes.
  Hash/redact viewer identifiers. Do not publish production UUIDs, chat or IPs.
- A passing unit test is **not** Paper acceptance. Claim runtime PASS only after
  the 16-tick expiry, two real clients and every negative control above have
  independently executed. Any listener/bundle ordering ambiguity yields INCONCLUSIVE.
- Stop immediately on unexpected outbound connectivity, leaked hidden spawn,
  new unreviewed executable, altered host mounts or excessive resource usage;
  preserve only sanitized evidence and destroy test containers.
- Do not merge a production logic change or close #17 until root cause is
  demonstrated and this exact-head Paper fixture passes.

## Current gaps

No fixture plugin, packet injection binary, protocol trace for missing-spawn expiry
or real-client pass evidence has been produced by this PR. Unit-level regression
tests cover deterministic queue transitions only. The live entity 147 sender and
the specific Paper/PacketEvents/other-plugin interaction remain unidentified.
