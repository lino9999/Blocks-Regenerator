# Verification

## Version 1.0.1 compatibility verification

Verified on 24 October 2026. The release is built against Spigot 1.21.11 with Java 21 bytecode and declares `api-version: '1.21.11'`. Minecraft 26.x servers still require Java 25.

| API version | Java | Automated result |
| --- | --- | --- |
| 1.21.11 | 21 | 71 tests, zero failures or errors |
| 1.21.11 | 25 | 71 tests, zero failures or errors |
| 26.1 | 25 | 71 tests, zero failures or errors |
| 26.1.1 | 25 | 71 tests, zero failures or errors |
| 26.1.2 | 25 | 71 tests, zero failures or errors |
| 26.2 | 25 | 71 tests, zero failures or errors |
| 26.3 | 25 | 71 tests, zero failures or errors |

The additional tests exercise material aliases in both directions, namespaced legacy input, native lookup precedence and unknown material rejection. GitHub Actions uses the same API/Java matrix. The shipped artifact comes from the baseline build, rather than one of the newer-API builds.

Live endpoint checks use the identical shaded JAR on isolated loopback servers: Paper 1.21.11 build 130 on Temurin Java 21.0.12.1, and Spigot 26.3 build 4663 on Microsoft Java 25.0.1. Both passed 45 Bukkit checks: enabled plugin/version, legacy/native chain material lookup, ore and crop snapshots, stairs, trapdoors, both door halves, timer ownership, exact restoration and all chain styles. The Spigot run also preserved three region definitions created by 1.0.0.

Paper console checks verified global creation, legacy material input, mature crop registration, the corrected trampling setting name, chain creation/stages/style/final-stage settings, expressions, reload, listing and deletion. Both isolated servers shut down cleanly. These probes are temporary verification plugins; no real player connected.

The first Java 21 Paper startup exposed a service-provider failure in `RandomGenerator.getDefault()` before plugin enable. The release uses `java.util.Random`, which implements the same consumed interface without service-provider discovery. The successful live runs above were performed after this correction; a passing world probe is counted only when it also asserts that BlocksRegenerator 1.0.1 is enabled.

The target Skyblock installation also completed startup on its existing Paper 1.21.11 build 130 with Java 25 and 39 installed plugins. BlocksRegenerator 1.0.1 enabled successfully; the previous incompatible 1.0.0 JAR was backed up outside the plugins directory. Existing WorldEdit/FAWE duplicate-plugin errors were unrelated and were left unchanged. This verifies coexistence at startup, not player-driven claims or custom-content behavior.

API compilation/testing covers intermediate releases; separate live servers were not started for 26.1, 26.1.1, 26.1.2 or 26.2. Optional provider versions and real-player interactions retain the limits described below. Material aliases do not provide newer game content on older servers, and Minecraft world/snapshot downgrades are outside the supported workflow.

Local evidence: `.work/compat-final-java21.log`, `.work/compat-api-<version>.log`, `.work/compat-paper-1.21.11-java21.log` and `.work/compat-spigot-26.3-java25.log`. Initial failed attempts are retained separately and are not counted as passing verification.

Release artifact: `target/BlocksRegenerator-1.0.1.jar`. SHA-256: `0e5d05f01c9680176958be6161772d89bd0ada24f958a7c807217eafc3392b31`.

## Historical version 1.0.0 verification

The sections below record the original release verified on 23 October 2026 for Spigot 26.3 and Java 25. Its artifact identity applies to 1.0.0 only.

## Automated build

`mvnw.cmd -B -q clean verify` completed successfully: **68 tests, zero failures, zero errors**. The tests cover region precedence and boundaries, delay validation and evaluation, queue ownership, stale timer cancellation, loaded/unloaded chunks, retry behavior, all chain styles, stack support, snapshot companions, durable storage, malformed data, legacy imports, administration permissions and sessions, inventory transfer cancellation, provider item lookup, reflection overload resolution, and protection failure handling.

Bukkit listener tests additionally cover final mining cancellation, final drop cancellation, deferred item collection, creative furniture cancellation, paired chain ownership, and farmland events whose hand is null. Expected error logs in protection tests deliberately exercise failed providers; they do not indicate a failed test.

The packaged JAR contains no original plugin classes, test classes, JUnit, or Mockito. AntiGriefLib is bundled under a private relocated namespace with its MIT notice.

## Live Spigot checks

The final release was tested in an isolated, loopback-only server built using official BuildTools for **Spigot 26.3, build 4663**, on **Java 25**. Existing server files and worlds were left untouched. The existing accepted Minecraft EULA was reused for this temporary server.

Both initial startup and restart passed **40 actual Bukkit world probe checks**. These verify ore data, stairs, trapdoors, crop ages, duplicate timer ownership, both door halves and companion ownership, exact restoration, and direct, staged, and shared-timer chains. The probe is a temporary verification plugin rather than a simulated player.

Console checks verified global creation, rule addition/removal, boolean settings, priority, chain stages/reordering/style/final breakability, expression validation, reload, deletion, and clean shutdown. All three supported import formats succeeded. Negative delays and invalid crop ages were rejected. Re-importing a colliding region refused to overwrite it.

A migration/restart check preserved four regions and one pending timer, verified the temporary STONE block after restart, and then restored the actual world block to DIAMOND_ORE. The final shaded release was separately restarted and retained saved regions and settings. No BlocksRegenerator warnings or errors occurred on either final release run.

All temporary server/build processes were stopped and the loopback test port was closed.

## Artifact identity

Final tested artifact: `target/BlocksRegenerator-1.0.0.jar`.

SHA-256: `75185617267f366b73b7e6139c4dfe583754ee43cb157e4002708143003a85bf`.

Local-only evidence includes `.work/release-verify.log`, Surefire reports under `target/surefire-reports`, and the isolated server's `release-first.log`, `release-restart.log`, `smoke-first.log`, `smoke-restart.log`, and temporary RuntimeProbe source. These generated artifacts are intentionally excluded from Git.

## Limits

No real player connected, so visual GUI layout, player-side mining/drop interaction, and client particle rendering have not been exercised end to end. Their session, cancellation, transfer and ownership boundaries have automated tests.

ItemsAdder, Oraxen, Nexo, BlockHardness, and the actual land-claim plugins were not installed in the temporary server. Their adapters are based on original consumed APIs and official library contracts, with focused unit tests. Real furniture models, orientation, hitboxes, custom drop timing, provider reload order, and every provider version still need validation in the intended server installation.

Unloaded-chunk queue behavior is covered by engine tests. An immediate chunk-unload assertion was unreliable in the temporary live probe and was excluded from the passing live checks.

The advanced evaluator deliberately supports the documented bounded expression language, rather than the original math library's entire grammar. Source-folder deletion by legacy converters is intentionally omitted. These differences are documented in README.md and the reconstruction ledger.

Passing these checks does not establish absence of every bug or full runtime equivalence with every optional integration.
