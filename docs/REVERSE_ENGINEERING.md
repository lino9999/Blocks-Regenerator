# Reverse engineering evidence

The target is the user-supplied OreRegenerator-2.6.2.jar. Its SHA-256 is `c4590429b2a6f5a54253822d758de31e949949a3eb160504d69ea94ccccd84cf`.

REA 6.7.0 was installed with `npx --yes rea-agents@latest setup --client codex --yes --json`, following a dry-run review. The Codex registration and matching workflow installation passed setup's scoped readiness check. Setup modified the user-level Codex configuration with a backup; no personal configuration belongs in this repository. The running coding session did not hot-load the new MCP server, so the REA CLI supplied artifact evidence. Restart Codex to load the registration.

REA `inspect-artifact` generated evidence `ev_7294aab8ae3c64ff47667b60b53b8fbd699c2970997341396d1b8bca6af48a35`. The underlying inventory evidence is `ev_d3dd5f988c2a16fc7d508167705a081375fc54a403eb963c5b77c0b2d45556c2`. The artifact is a ZIP/JAR with 2,254 graph nodes and 2,256 member occurrences.

REA's observations cover container entries. They do not establish JVM semantics or runtime behavior. Method-level behavior was inspected separately with CFR 0.152 and packaged resources; it must not be represented as REA decompilation. The original JAR was not executed as part of this static analysis.

The local-only .work directory retains the full REA artifact JSON, a concise summary, and focused JVM decompilation. The local-only .tools directory contains CFR. Neither is included in Git or broad text search.

## Reconstruction ledger

| Behavior | Evidence | New implementation |
| --- | --- | --- |
| Cuboids, global regions, precedence | Original RegenRegion, GlobalRegenRegion, RegionManager | Region, Bounds, RegionIndex |
| Rules, crop age and replacements | Original RegennableBlock and Placeable implementations | RegenRule, Placeable, IntegrationRegistry |
| Timers, full block data, stacked support | Original active implementations and BlockBreakListener | RegenerationEngine, BukkitWorldAccess, RegenerationListener |
| Chains and mined-out state | Original RegenChain and ChainActiveRegennableBlock | RegenChain, PendingRegen, RegenerationEngine |
| Inventory drops and remaining time | Original drop/interaction listeners and Messages | RegenerationListener, Messages |
| Administration and GUI | Original command/menu/selection classes | admin package; docs/COMMANDS.md |
| Claims and custom blocks/furniture | Original hook classes | integration package; docs/INTEGRATIONS.md |
| Persistence and conversion | Original RegionFile, PersistenceFile, converters | StateCodec, StateStore, LegacyImporter |

## Deliberate corrections

Region ties resolve deterministically by ID after priority. Negative or overflowing delays are rejected. Successful edits are persisted immediately. Timer completion clears state; stale queued work cannot resurrect a cancelled entry. Full millisecond remaining durations survive restart. Invalid data is preserved instead of silently loaded as empty. Doors and tall plants own both snapshot positions, while stairs and trapdoors remain independent.

Stack mining is aborted when an upper block is protected or vetoed, preventing natural stack physics from destroying a denied block. Existing nearby items are never swept into an inventory; auto-inventory handles attributed drop events and newly spawned provider/stack drops. Inventory UI callbacks recheck ownership and permissions.

The math evaluator supports bounded arithmetic, constants, documented functions, and delay variables rather than carrying the original bundled general-purpose expression dependency. Unsupported expressions report an error or use the configured rule range. Foreign converter remove-data options are intentionally non-destructive; imported source folders are retained.

Live feature parity is bounded by the checks recorded in docs/VERIFICATION.md. Optional third-party APIs require runtime validation with the actual provider versions.
