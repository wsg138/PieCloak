# PieCloak workspace manifest

This manifest maps the repository areas agents must inspect. It is not a substitute for reading the code.

## Repository and branch model

- Repository: `wsg138/PieCloak`
- Coordination branch: `main`
- Active remediation PR: `#11`
- Implementation branch: `agent/sync-upstream-clean-history`
- Recorded implementation head at coordination bootstrap: `33b615d5ef3a937dafdf461c48e9626ee7d342fc`

Always verify these live.

The agent workspace and handoffs remain on `main`. Source, tests, build files, workflows, plugin metadata, and ordinary product documentation remain on the PR branch until merge.

## Current and future platform

### Current

- Minecraft: `1.21.11`
- Server family: Leaf with Paper-compatible API behavior
- Player compatibility: Java plus Geyser/Floodgate paths
- Packet layer: PacketEvents

Package 01 must establish and document the exact API dependency, Java release/toolchain, test-server version, plugin metadata, and CI JDK needed for this current target.

### Future

The next major platform target is a stable Paper `26.2` or newer build. That upgrade is not part of the current remediation.

Future adaptation should normally be limited to:

- platform/build version properties;
- Paper-specific internals and adapters;
- PacketEvents version-specific wrappers;
- compatibility tests and metadata.

Core raycasting, visibility state, transition contracts, retry semantics, and target filtering should remain platform-neutral.

## Modules

### `core/`

Owns platform-neutral state and algorithms:

- `engine/AsyncEngine.java` and timing/fan-out logic;
- entity and block views;
- packed transition queues;
- visibility state and tracked entity/tile abstractions;
- registries and controller contracts.

Critical themes: concurrency, boundedness, state publication, queue consumption, cleanup, and scheduler rejection.

### `packetevents/`

Owns protocol interception, packet parsing, replay data, and client reconciliation:

- `PacketEventsEntityViewController`;
- `PacketEventsBlockViewController`;
- entity and tile replay data;
- chunk parsers;
- PacketEvents target filtering.

Critical themes: packet ordering, partial writes, idempotency, stale state, world epochs, entity IDs, relationships, and sensitive data.

### `platform-paper/`

Owns Paper/Leaf lifecycle and integration:

- main plugin class `RaycastedAntiESP`;
- `EventListener` and `PaperListener`;
- PacketEvents platform controller registration;
- `FancyCompatibility`;
- update checker;
- Paper scheduler/engine adapter;
- `src/main/resources/plugin.yml`.

Critical themes: optional dependency class loading, startup/disable/re-enable, scheduler shutdown, listener unregistering, Java/Paper compatibility, and metadata.

### Submodules

- `leafpile/`
- `locatables/`
- `logging/`

Treat submodule pointers as intentional dependencies. Do not modify or advance them unless the selected package proves that is required.

## Build and configuration

Inspect together:

- `/build.gradle.kts`
- `/platform-paper/build.gradle.kts`
- `/settings.gradle`
- `/gradle.properties`
- `/gradle/wrapper/gradle-wrapper.properties`
- `/.github/workflows/*.yml`
- `/platform-paper/src/main/resources/plugin.yml`

Package 01 should centralize target versions in the smallest practical number of properties and make CI use the same baseline.

## Product documentation and validation

These product documents live with the implementation PR when changed:

- `/README.md`
- `/MAINTAINING.md`
- `/TESTING.md`
- `/CONTRIBUTING.md`

The agent workspace under `/ai-agents/` lives on `main` and is updated only through coordination commits.

The existing manual checklist remains required for live behavior. Workers must extend product testing documentation when a package introduces a new failure scenario.

## Historical baseline command

The PR previously used:

```bash
cd leafpile/templates
javac --release 21 GenerateSources.java Preprocessor.java
java GenerateSources . ..
cd ../..
./gradlew clean :platform-paper:compileJava test :platform-paper:build :platform-paper:buildSnapshot --no-daemon
```

Package 01 must verify and, if needed, replace this with the exact coherent Minecraft 1.21.11 command and CI environment. Later agents must use the updated documented command rather than blindly copying this baseline.

## Static and packaging checks

Use the repository's current configured workflows and tasks. At minimum inspect:

- PMD results and whether failures are ignored;
- Semgrep results and changed-code relevance;
- Trivy results;
- shaded JAR contents and `build-properties/platform.yml`;
- plugin metadata in the produced JAR;
- exact-head workflow SHA.

## Manual live scenarios reserved for final validation

Unless a worker has access to an isolated test server and records direct evidence, keep these marked manual:

- bypass viewers across join/reconnect and permission changes;
- filtered and non-filtered entities;
- hidden block entities and standalone block-entity packets;
- teleport, respawn, dimension change, and fast movement;
- villager or mob minecart mount and dismount;
- injected packet-send failure at each SHOW/HIDE stage;
- repeated hide/show and retry convergence;
- plugin disable/re-enable or explicit unsupported behavior;
- server shutdown while an async tick is scheduling or running;
- optional Fancy plugins absent, one present, and both present;
- Java and Bedrock/Geyser clients.