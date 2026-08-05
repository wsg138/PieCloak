---
id: 01-current-platform-baseline
title: Establish the Minecraft 1.21.11 build and CI baseline
role: worker
expected_commits: 1-2
---

# Package 01 — Current platform baseline

## Goal

Make the repository consistently build, test, package, and describe the owner's current Minecraft 1.21.11 target while keeping the later stable Paper 26.2-or-newer upgrade contained.

This package is about build truth, not runtime visibility behavior.

## Confirmed problem

PR #11 currently mixes or replaces platform baselines. A green build against an older API does not prove compatibility with the actual 1.21.11 deployment. The previous Paper 26.2 configuration also had incompatible Java/CI settings. The repository needs one coherent current target.

## Required inspection

Inspect together:

- root and platform Gradle files;
- Gradle wrapper and properties;
- all GitHub Actions workflows;
- `plugin.yml`;
- run-server tasks;
- generated build properties and shaded JAR metadata;
- Leaf/Paper API availability for 1.21.11;
- README, maintenance, and testing claims.

Do not guess a dependency coordinate. Verify it from the configured repositories or authoritative project metadata.

## Required implementation

1. Establish the exact Java host/runtime/release/toolchain required for the current 1.21.11 Leaf/Paper-compatible build.
2. Establish the exact Paper API/dev-bundle or compatible compile dependency for 1.21.11.
3. Make the run-server/test-server task use 1.21.11.
4. Make GitHub Actions use a JDK compatible with both Gradle and the selected dependency.
5. Centralize target versions in a small, clearly named set of Gradle properties or equivalent configuration.
6. Remove stale commented alternatives and contradictory version declarations.
7. Make plugin metadata accurately represent the supported current API without unnecessarily preventing later compatible versions.
8. Preserve the existing current feature behavior and submodule pointers.
9. Document the future upgrade seam: which properties/adapters should change for a stable 26.2-or-newer move.

## Compatibility guardrails

- Do not switch the current target to Paper 26.2.
- Do not lower the target below Minecraft 1.21.11 merely to obtain a green build.
- Do not spread version conditionals through `core`.
- Do not add a broad compatibility abstraction unless a current code difference requires it.
- Do not claim Leaf-specific runtime validation without direct evidence.

## Tests and validation

At minimum:

- clean generated-source step;
- clean compile and all unit tests;
- Paper shaded JAR and snapshot build;
- inspect JAR metadata and plugin metadata;
- run configured static analysis;
- verify exact-head GitHub Actions use the same Java and platform target;
- confirm no build file still declares a conflicting current target.

Add a small build/configuration test or CI assertion when practical so future changes cannot silently downgrade the target again.

## Acceptance criteria

- One documented source of truth produces a clean Minecraft 1.21.11 build.
- CI and local instructions agree.
- Produced metadata reflects the current target.
- The future 26.2-or-newer move is described as a contained property/platform update.
- No unrelated runtime code changes.

## Intended next route

Advance to `02-block-transition-reliability`.
