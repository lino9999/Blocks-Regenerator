# Editing BlocksRegenerator efficiently

Author: Lino. Target: Spigot/Paper 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. Compile against Spigot 1.21.11 with Java 21 bytecode; Minecraft 26.x servers require Java 25. Keep all code, configuration, documentation, and messages in English. Do not add comments to Java, XML, YAML, or scripts.

Vendored Apache Maven Wrapper scripts retain their upstream license headers. Preserve bundled third-party license notices.

## Read only the relevant path

Start with README.md and this file. Use rg for targeted searches. Do not recursively open ignored binary, tool, decompilation, server, or build directories. Root AGENTS.md redirects here because instructions inside docs do not automatically apply to src.

| Change | First files |
| --- | --- |
| Plugin startup, reload, saving | src/main/java/dev/lino/blocksregenerator/BlocksRegeneratorPlugin.java |
| Region lookup and overlap precedence | model/Region.java, model/Bounds.java, service/RegionIndex.java |
| Regeneration, timers, chains | engine/RegenerationEngine.java, engine/PendingRegen.java, model/RegenChain.java |
| Mining, drops, protection, stacks | bukkit/RegenerationListener.java, bukkit/BukkitWorldAccess.java |
| Commands, wand, GUI, conversations | admin/AdminController.java, admin/AdminCommands.java, admin/AdminMenus.java, admin/AdminSessions.java |
| Custom content and land claims | integration/IntegrationRegistry.java, integration/ClaimsService.java, docs/INTEGRATIONS.md |
| Minecraft/API compatibility and material aliases | pom.xml, src/main/resources/plugin.yml, bukkit/MaterialResolver.java, .github/workflows/build.yml |
| JSON format and durability | storage/StateCodec.java, storage/StateStore.java |
| Importing older plugin data | storage/LegacyImporter.java, docs/ORIGINAL_CONTRACT.md |
| Delay formulas | engine/DelayExpression.java |
| Typed settings and YAML validation | configuration/RuntimeSettings.java, configuration/SettingsRepository.java, src/main/resources/settings.yml |
| Player notices and cooldowns | bukkit/PlayerNotices.java, src/main/resources/locale/en.yml |

Paths without a source prefix in the table are below src/main/java/dev/lino/blocksregenerator.

## Invariants

- The runtime reads only the versioned `settings.yml` and `locale/en.yml` schemas. Keep typed settings immutable, reject malformed or unsupported documents, and preserve invalid source files. Reload must not partially adopt invalid settings.
- Public region command keys are grouped names such as `mining.allow-unmatched` and `harvest.collect-items`. Keep enum identities and schema-1 data compatibility intact; do not rename persisted enum values for a cosmetic key change.
- Delay formulas use bare `lower`, `upper`, `players` variables and `uniform(a, b)`. Do not reintroduce retired expression or inline notice syntax into the runtime. Use the stopped-server upgrade script for 1.x configuration conversion.

- Bukkit world, inventory, permission and player operations run on the server thread. Only immutable storage snapshots cross into the storage executor.
- Models are immutable. Change a region through AdminBackend.save; it restores pending blocks before changing configuration and waits for durable storage.
- A location and every companion in its snapshot have at most one active owner. Owners protect both halves of doors and tall plants. Stairs and trapdoors are single blocks.
- Respect the final cancellation state of mining events before committing regeneration. Synthetic stack events have a recursion guard.
- The active map is authoritative. Priority queue entries may be stale; identity guards discard them.
- Never force chunks to load for a timer. Park unloaded entries and resume on chunk/world load.
- Every successful regeneration removes its active owner. Queue work is bounded by the configured per-tick budget.
- Persist pending snapshots before shutdown. Turning `recovery.remember-on-restart` off restores loaded entries and still retains unloaded recovery entries.
- Failed JSON/schema validation disables startup without overwriting the source data. Storage uses ordered writes, fsync, atomic replacement where supported, and a previous-file backup.
- Optional provider classes must never link at plugin startup. Preserve custom definitions when their provider is missing; report errors rather than substituting vanilla blocks.
- GUI identity comes from owner/session holders, never inventory titles. Cancel unsafe transfers and recheck permission when a deferred action runs.
- Legacy imports preserve source folders and refuse existing region ID collisions.

## Verification

Use JDK 21 or newer and the pinned Maven Wrapper: `./mvnw verify` or `mvnw.cmd verify`. Focus tests with `./mvnw -Dtest=RegenerationEngineTest test` or the test class relevant to the changed boundary. For compatibility changes, verify all supported APIs with JDK 25 and `-Dspigot.version=<version>-R0.1-SNAPSHOT clean verify`, plus the default build on JDK 21. Publish the baseline build rather than an artifact compiled against a newer API. Keep the minimum API in plugin.yml aligned with pom.xml and do not introduce newer static API links without a compatibility path.

Tests should verify meaningful behavior: cancellation, item conservation, timer ownership, unloaded worlds, migration, malformed data, permissions and reload/shutdown. Do not add tests that only repeat implementation details.

The built plugin is target/BlocksRegenerator-2.0.0.jar. Do not commit target, .tools, .work, original JARs, Minecraft worlds or logs. Read docs/VERIFICATION.md for the actual verification scope and remaining live integration gaps. Keep the README command and permission tables aligned with docs/COMMANDS.md and plugin.yml when behavior changes. The primary command is /blocksregenerator, with /bregen and /br aliases; keep command names and permission nodes under BlocksRegenerator.

## Configuration upgrades

Use `tools/Upgrade-BlocksRegenerator.ps1 -PluginDirectory <absolute plugin data folder>` only while the target server is stopped. Keep its adjacent `tools/UpgradeSettings.java` helper. The utility requires JDK 21 or newer and the server's SnakeYAML JAR; `-JavaExecutable` and `-YamlLibrary` override discovery. Preserve backups and unchanged `data.json`; do not overwrite an existing 2.x configuration. Changes to global settings, notice structure, formula syntax or region command keys must update README, docs/COMMANDS.md and the SpigotMC BBCode pages together. Do not claim a clean-room development history or legal publication clearance from a technical rewrite; preserve the reconstruction record and third-party notices.

## Original behavior evidence

docs/ORIGINAL_CONTRACT.md and docs/INTEGRATIONS.md summarize the original JAR without including its implementation. docs/REVERSE_ENGINEERING.md records artifact identity and REA evidence. Read those summaries before considering any focused original-bytecode lookup; the raw analysis under .work is local-only.

Ignore patterns reduce broad search noise; they are not access controls and are not a promise that every coding tool automatically honors them.
