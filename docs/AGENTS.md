# Editing BlocksRegenerator efficiently

Author: Lino. Target: Spigot 26.3, Java 25. Keep all code, configuration, documentation, and messages in English. Do not add comments to Java, XML, YAML, or scripts.

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
| JSON format and durability | storage/StateCodec.java, storage/StateStore.java |
| Importing older plugin data | storage/LegacyImporter.java, docs/ORIGINAL_CONTRACT.md |
| Delay formulas | engine/DelayExpression.java |
| Messages and throttling | bukkit/Messages.java, src/main/resources/messages.yml |

Paths without a source prefix in the table are below src/main/java/dev/lino/blocksregenerator.

## Invariants

- Bukkit world, inventory, permission and player operations run on the server thread. Only immutable storage snapshots cross into the storage executor.
- Models are immutable. Change a region through AdminBackend.save; it restores pending blocks before changing configuration and waits for durable storage.
- A location and every companion in its snapshot have at most one active owner. Owners protect both halves of doors and tall plants. Stairs and trapdoors are single blocks.
- Respect the final cancellation state of mining events before committing regeneration. Synthetic stack events have a recursion guard.
- The active map is authoritative. Priority queue entries may be stale; identity guards discard them.
- Never force chunks to load for a timer. Park unloaded entries and resume on chunk/world load.
- Every successful regeneration removes its active owner. Queue work is bounded by the configured per-tick budget.
- Persist pending snapshots before shutdown. Turning persistent-timers off restores loaded entries and still retains unloaded recovery entries.
- Failed JSON/schema validation disables startup without overwriting the source data. Storage uses ordered writes, fsync, atomic replacement where supported, and a previous-file backup.
- Optional provider classes must never link at plugin startup. Preserve custom definitions when their provider is missing; report errors rather than substituting vanilla blocks.
- GUI identity comes from owner/session holders, never inventory titles. Cancel unsafe transfers and recheck permission when a deferred action runs.
- Legacy imports preserve source folders and refuse existing region ID collisions.

## Verification

Use JDK 25 and Maven 3.9 or newer. Run `mvn verify` for a complete build. Focus tests with `mvn -Dtest=RegenerationEngineTest test` or the test class relevant to the changed boundary.

Tests should verify meaningful behavior: cancellation, item conservation, timer ownership, unloaded worlds, migration, malformed data, permissions and reload/shutdown. Do not add tests that only repeat implementation details.

The built plugin is target/BlocksRegenerator-1.0.0.jar. Do not commit target, .tools, .work, original JARs, Minecraft worlds or logs. Read docs/VERIFICATION.md for the actual verification scope and remaining live integration gaps.

## Original behavior evidence

docs/ORIGINAL_CONTRACT.md and docs/INTEGRATIONS.md summarize the original JAR without including its implementation. docs/REVERSE_ENGINEERING.md records artifact identity and REA evidence. Read those summaries before considering any focused original-bytecode lookup; the raw analysis under .work is local-only.

Ignore patterns reduce broad search noise; they are not access controls and are not a promise that every coding tool automatically honors them.
