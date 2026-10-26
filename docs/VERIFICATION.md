# Verification

## Version 2.0.0 independent settings and packaging verification

Verified on 26 October 2026. The Java 21 baseline passes 86 unit tests and five post-package integration tests, 91 total, with zero failures or errors. The new tests cover strict settings validation, structured notice rendering and throttling, failed notice reload preservation, packaged namespace/resource boundaries, version-1 settings/message conversion, state-file preservation, existing-file refusal, malformed migration input and active world-lock refusal.

The tested JAR has SHA-256 `885f6b92150c09f060fcd87ceb6c6133a61eadd5e76b3a1669710f3b3edbca6d`. It includes `settings.yml` and `locale/en.yml`, and excludes the former global configuration and message resources. Build-time checks inspect the shaded artifact, require the third-party license notice, reject foreign implementation namespaces and reject retired global paths, message keys and formula tokens. External import-schema identifiers are deliberately retained for interoperability.

The same JAR passed 16 live command/permission/import checks on Paper 1.21.11 build 130 with Java 21, and 45 actual block-world checks on Spigot 26.3 build 4663 with Java 25. Console checks verified new dotted region options, the menu icon option, `uniform` expressions with `players`, reload, region creation/deletion and preservation of three regions from an earlier release. Both isolated installations were upgraded using the shipped utility before starting; `data.json` remained byte-for-byte unchanged during settings conversion. The user's active Skyblock server was not interrupted or migrated.

The separate [technical reconstruction audit](INDEPENDENCE_AUDIT.md) compares archive members, class payload identity, constant-pool strings, source-file metadata and YAML mapping paths against the supplied artifact. It records zero original class/resource payload copies and explains common API/provider/import strings. Those checks are technical evidence, not a clean-room-history or publication-approval certificate.

Local evidence includes `.work/independent-build.log`, Surefire/Failsafe XML reports, `.work/independent-paper.log`, `.work/independent-spigot.log` and the retained audit evidence listed in the audit report. Third-party provider and real-player interaction limits below still apply.

## Version 1.0.3 naming and publication verification

Verified on 24 October 2026. The Java 21 baseline build passes 73 tests with zero failures or errors. Only `blocksregenerator.admin` and `blocksregenerator.bypass.claims` are declared. Administration and GUI checks use the canonical permission, and the default claim-bypass permission uses the same namespace. Regression tests verify that unrelated permissions cannot grant command access and that the canonical bypass works without an explicit configuration override.

An isolated Paper 1.21.11 build 130 on Java 21 passed 16 live checks covering enabled version, command ownership, aliases, removed legacy registrations, canonical permission declarations, import completions, console command dispatch and JSON migration. `/blocksregenerator import legacy` successfully read a fixture from `plugins/BlocksRegenerator/imports/legacy/`; source files were preserved, and the imported test region was deleted after verification. Startup and import log messages use the current naming. Conflict detection for the legacy plugin is retained internally, with a generic user-facing error message and without a product-specific soft dependency in metadata.

The current README and files under docs contain no retired product-name identifiers. The SpigotMC overview and documentation use supported BBCode tags with balanced nesting, complete command tables, explicit compatibility limits and only the two current permission nodes. The short description is 82 characters. The reconstruction record retains the source artifact's version, hash and analysis method under a generic description.

Artifact: `target/BlocksRegenerator-1.0.3.jar`. SHA-256: `8e94137d45bc194ccfad98cef7831c14906baad4e2f3ecd13f0663236949de3d`. Local evidence: `.work/branding-build.log`, `.work/branding-paper.log` and the temporary branding probe. Compatibility and real-provider verification limits below remain applicable.

## Version 1.0.2 command verification

Verified on 24 October 2026. The Java 21 baseline build passes 71 tests with zero failures or errors. The primary command is `/blocksregenerator`, with `/bregen` and `/br` aliases. Legacy command aliases are removed. Help, wand instructions and the user guides now consistently use the primary command. Existing permission nodes and importer source identifiers remain compatible.

An isolated Paper 1.21.11 build 130 on Java 21 passed 11 live checks: enabled plugin/version, primary ownership, both short aliases, namespaced primary command, absence of both legacy aliases and the old namespaced alias, and console dispatch through the primary command and `/bregen`. The console returned the expected expression result, 5.0.

The same run verified Bukkit/Paper's next-start update mechanism: a 1.0.2 JAR staged in `plugins/update/` under the installed JAR filename replaced 1.0.1 before loading, and the loaded artifact's checksum matched the staged artifact. A full server restart is required; configuration reload does not change registered commands. Existing WorldEdit/FAWE ownership of `/br` is left intact; use `/blocksregenerator` or `/bregen` on that installation.

Artifact: `target/BlocksRegenerator-1.0.2.jar`. SHA-256: `76b014d11802cc46526c1b803b061aafa550608c995da2487e5c8d70bb3705b9`. Local evidence: `.work/command-names-build.log`, `.work/command-probe/` and `.work/command-names-paper.log`. The compatibility and optional-integration limits recorded for 1.0.1 still apply.

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

The target Skyblock installation also completed startup on its existing Paper 1.21.11 build 130 with Java 25 and 39 installed plugins. BlocksRegenerator 1.0.1 enabled successfully; the previous incompatible 1.0.0 JAR was backed up outside the plugins directory. WorldEdit/FAWE owns `/br` on this installation, so the guide documents `/blocksregenerator` and the fully qualified command as alternatives. Existing WorldEdit/FAWE duplicate-plugin errors were unrelated and were left unchanged. This verifies coexistence at startup, not player-driven claims or custom-content behavior.

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
