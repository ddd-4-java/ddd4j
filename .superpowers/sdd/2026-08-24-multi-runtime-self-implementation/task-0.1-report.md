# Task 0.1 Report: Delete fuin dead dependencies from ddd4j-dependencies BOM

**Status: DONE_WITH_CONCERNS** (change itself complete and verified zero-impact; plan's literal verification commands cannot pass at HEAD due to two pre-existing, fuin-unrelated build issues — details below)

Commit: `4fd03ee3` `chore(deps): 删除 ddd4j-dependencies BOM 中 8 个 fuin 死依赖` (branch `feature/2.0.x`)

## What Was Implemented

Exactly per the task brief, two Edit-tool edits on `/Users/wandl/workspaces/workspace-ddd4j/workspace-ddd4j-boot/ddd4j/ddd4j-dependencies/pom.xml`:

1. **Step 1** — deleted the 2 version properties (was at lines 274-275):
   - `<fuin-ddd4j.version>0.7.0</fuin-ddd4j.version>`
   - `<fuin-cqrs4j.version>0.6.0</fuin-cqrs4j.version>`
2. **Step 2** — deleted the 8 fuin dependency blocks with their Source-URL + Chinese-description comments (was at lines 3620-3675):
   - `org.fuin.ddd4j:ddd-4-java-core / esc / jsonb / jackson / jaxb` (5)
   - `org.fuin.cqrs4j:cqrs-4-java-core / jsonb / jackson` (3)

The pre-existing blank separator line (was 3619) was preserved so the diff is strictly the 56 dependency-section lines + 2 property lines.

## Files Changed (diff stats)

- `ddd4j-dependencies/pom.xml`: 1 file changed, **58 deletions(+), 0 insertions(-)**
- No other files touched. The 2 untracked plan docs under `docs/superpowers/plans/` were left untracked and uncommitted.

## Verification

| Check | Command | Result |
|---|---|---|
| Zero fuin refs | `grep -c "fuin" ddd4j-dependencies/pom.xml` | **0** (exit 1, i.e. no matches) |
| No fuin refs in any pom | `grep -rln "org.fuin\|fuin-" --include=pom.xml .` | none |
| BOM install (plan command, wrapper Maven 4.0.0-rc-5) | `./mvnw -pl ddd4j-dependencies install -DskipTests` | **FAILURE — pre-existing** (identical at clean HEAD without my change, proven via `git stash` round-trip; also fails `-o` offline) |
| BOM install (Maven 4.0.0-rc-6) | `~/.m2/wrapper/dists/apache-maven-4.0.0-rc-6/2fa31093/bin/mvn -pl ddd4j-dependencies install -DskipTests` | **BUILD SUCCESS** |
| ddd4j-core compile (both rc-5 and rc-6, with and without my change) | `./mvnw -pl ddd4j-core compile` (+ `-am` variant) | **FAILURE — pre-existing** (identical at clean HEAD via `git stash`) |
| POM well-formed / model resolves after edit | `./mvnw -pl ddd4j-dependencies help:effective-pom` | SUCCESS (produced effective pom) |

## Pre-existing Issues Found (NOT caused by this task; escalated for orchestrator awareness)

1. **Maven wrapper pins 4.0.0-rc-5, which fails ALL dependency resolution in this repo** with `IllegalArgumentException: Invalid Collect Request: null` (thrown by `DefaultRepositorySystemValidator` during `collectDependencies`, before any compile). The junit-jupiter mix visible in the failing request (aggregator resolved 6.0.3 from an earlier-imported BOM vs explicit api/engine/params pinned 6.1.0 via `${junit-jupiter.version}`) may be related. Evidence it is unrelated to fuin:
   - Fails identically at clean HEAD (`abf539a4`) with my change stashed.
   - Fails identically in offline mode.
   - **Builds SUCCESS under Maven 4.0.0-rc-6** (already cached in `~/.m2/wrapper/dists`, and the `3.0.x` branch already migrated to rc-6 in commit `293f2071` "chore(3.0.x): migrate to Maven 4.0.0-rc-6 with POM model 4.1.0"). System Maven 3.9.16 cannot read the POMs at all (modelVersion 4.1.0 requires Maven 4).
   - Suggested follow-up: bump `.mvn/wrapper/maven-wrapper.properties` to 4.0.0-rc-6 (out of scope for Task 0.1).
   - Additionally, `HEAD~1` (`c77b6d75`) doesn't even parse (duplicate `jcaptcha-api` / `xmlschema-core` entries in dependencyManagement) — HEAD's `abf539a4` fixed parsing but left the resolution failure.
2. **ddd4j-kit source does not compile** (blocks `ddd4j-core compile` under any Maven): `ddd4j-kit/src/main/java/io/ddd4j/kit/lang/JsonKit.java:68,70` — package `com.fasterxml.jackson.databind.ext.javatime.ser/deser` 不存在. Because kit fails, ddd4j-core sees a stale installed kit SNAPSHOT and cascades errors (`无法访问 cn.hutool.core.util.StrUtil`, `CollKit.isNotEmpty` signature mismatch). Identical with/without my change (stash-verified). Needs a separate fix task (Jackson version or JsonKit source).

## Self-Review

- **Completeness:** both properties deleted; all 8 dependency blocks (incl. comments) deleted; `grep -c fuin` = 0. Done.
- **Quality:** diff is exactly 58 deleted lines, zero insertions, no whitespace-only or unrelated edits; surrounding context preserved.
- **Discipline:** pom modified only via Edit tool (no sed/scripts); committed only `ddd4j-dependencies/pom.xml`.
- **Verification:** BOM install verified BUILD SUCCESS under Maven 4.0.0-rc-6; the literal `./mvnw` (rc-5) invocation and `ddd4j-core compile` cannot pass at HEAD for pre-existing reasons proven independent of this change (stash + worktree + cross-Maven-version evidence above).
- **Residual fuin mention (informational):** `ddd4j-core/src/main/java/io/ddd4j/core/cqrs/readmodel/ProjectionService.java:7` mentions `org.fuin.*` in a javadoc comment only — no import, no pom dependency; presumably handled by later plan tasks.

## Issues / Follow-ups for Orchestrator

1. Wrapper Maven rc-5 → rc-6 bump needed (or fix the BOM import-order junit mix) before any task can use `./mvnw` verification commands.
2. ddd4j-kit Jackson `databind.ext.javatime` compile breakage blocks `ddd4j-core compile` — needs its own fix task before "compile" verifications in later tasks can pass.
