---
id: 06-final-integration-review
title: Independent final integration review and readiness verdict
role: coordinator
expected_commits: 0-or-focused-fixes
---

# Package 06 — Final coordinator integration review

## Role

This package is for the coordinator/final integrator, not an ordinary worker. Assume every earlier package may contain mistakes, including mistakes introduced by the coordinator's own prior code.

## Goal

Review the entire final PR #11 as one system, validate the exact current Minecraft 1.21.11 head, find cross-package defects, and return an evidence-based READY or NOT READY verdict.

Do not merge without a new explicit owner instruction in the current conversation.

## Required live reconciliation

Inspect:

- current `main` and PR #11 head/base;
- every commit added by packages 01–05;
- complete PR diff, not only recent commits;
- open/draft PRs and active branches;
- all unresolved human and bot review threads;
- exact-head Build, PMD, Semgrep, Trivy, Codacy, and any other checks;
- recent merges that could invalidate the handoffs;
- all package handoffs and claimed evidence.

## Required independent review passes

Perform at least these separate passes:

1. **Platform/build:** Minecraft 1.21.11 truth, Java/toolchain consistency, metadata, shaded artifact, future 26.2 upgrade seam.
2. **Block protocol:** chunk parsing, block-entity data, HIDE/SHOW ordering, retries, mode changes, failure injection, bounds.
3. **Entity protocol:** spawn/destroy, replay, correction, relationships, bypass, minecarts, stale retries, ID reuse.
4. **Concurrency:** tick scheduling, queue publication, partial submission, shutdown, exact-once finalization, memory visibility.
5. **Lifecycle:** partial startup, disable/re-enable, listener/controller unregistering, registries, optional plugins.
6. **Security/operability:** sensitive logs, denial-of-service bounds, malicious packet/plugin behavior, diagnostics.
7. **Test integrity:** tests that mock away production behavior, missing failure stages, flaky timing, false-positive assertions.
8. **Documentation:** README, testing checklist, PR description, and workspace state match the code.

## Cross-package scenarios

Specifically test or reason through:

- block and entity retries active while shutdown begins;
- disconnect/world change while a staged packet repair is pending;
- bypass enabled during pending HIDE and disabled during pending SHOW;
- minecart/passenger relationship failure after spawn but before metadata replay;
- optional plugin entity removal while relationship or retry state references its ID;
- scheduler rejection while packet queues still contain transitions;
- plugin disable/re-enable with optional plugins present;
- Java and Geyser/Floodgate clients on 1.21.11;
- fast movement, teleport, respawn, and dimension changes under packet failure.

## Validation

After all tracked fixes are frozen, run:

- exact current-target clean build;
- all unit tests;
- repeated concurrency-sensitive suites;
- static analysis;
- shaded JAR integrity and metadata inspection;
- exact-head GitHub workflows;
- any isolated server smoke tests available;
- the relevant manual checklist in `TESTING.md` when possible.

Do not treat skipped, cancelled, superseded, merge-ref-only, or different-head runs as exact-head evidence.

## Handling new findings

- Small, confirmed cross-package defects may be fixed in focused intentional commits, followed by a complete re-review and revalidation.
- A large architectural defect should produce `NOT_READY`, a precise handoff, and an explicitly routed follow-up package rather than a rushed patch.
- Do not invent certainty for live scenarios that were not run.

## Final outputs

Update the PR description or a final PR comment with:

- final exact head SHA;
- commit list by defect/package;
- validation run/job IDs and artifact identity;
- unresolved review-thread count;
- manual scenarios completed and still pending;
- READY or NOT READY verdict with blockers.

Update workspace state to `READY_FOR_OWNER` or `NOT_READY`, add the final handoff, and stop. Do not merge unless the owner separately instructs it after seeing the verdict.
