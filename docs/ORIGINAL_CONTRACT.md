# Original plugin contract: administration and storage

This is a static analysis of the supplied OreRegenerator artifact, decompiled with CFR. It records observed method bodies and data keys; it is not a claim that any feature has been exercised on a server. Evidence paths below are relative to `dev.mrshawn.oreregenerator` in the original artifact. Core regeneration and external hook behavior are analyzed separately.

## Regions and selection

- Local regions are inclusive cuboids in one world. Containment uses block coordinates, inclusive minimum and maximum X/Y/Z, and the recorded world name. Evidence: `regions.RegenRegion.contains`.
- Global regions cover their entire world; their ID is the world name. Local priority is 1, global priority is 0. Lookup selects a containing region with greatest priority. Same-priority overlaps have no explicit ordering because the backing collection is a `HashMap`; the menu warns about overlap. Evidence: `regions.GlobalRegenRegion`, `regions.RegionManager.getRegion(Location)`, `menus.regions.RegionEditorMenu.createGui`.
- `/or wand` supplies a named stick. Left-click selects corner 1; right-click selects corner 2. `/or position|pos one|1` and `two|2` select the player's current location. Saving requires both corners and a unique ID. Evidence: `wands.RegionSelectorWand`, `commands.OreRegeneratorCMD.PositionCommand`, `RegionCommand.onSave`.
- Normal local creation reconciles settings defaults, registers the region, then saves it. Global creation is also exposed as a command. Auto-generated globals are optional. `GlobalRegenRegion.Companion.create` explicitly enables block break, block place, and farmland trampling; the global creation command instead directly constructs a global with ordinary defaults. This inconsistency is an observed defect, not a recommended requirement. Evidence: `regions.RegionManager.createRegion`, `regions.GlobalRegenRegion.Companion.create`, `RegionCommand.GlobalCommand.onGlobalCreate`, `files.RegionFile.loadRegions`.

## Commands

All commands are under `/or` or `/oreregenerator` and inherit `oreregenerator.admin`. Player-only methods use the Bukkit `Player` argument. Evidence: `commands.OreRegeneratorCMD` annotations and nested command classes.

| Command suffix | Sender | Observed behavior |
| --- | --- | --- |
| empty, `menu`, unknown suffix | Player | Opens main menu |
| `help` | Any | Shows command help |
| `wand` | Player | Adds selection wand |
| `position one`, `position two` | Player | Sets selection corners; aliases above |
| `edges`, `borders`, `visualize` | Player | Toggles personal region outline |
| `reload`, `rl` | Any | Reloads config and messages only |
| `eval <expression>` | Any | Evaluates mathematical expression |
| `add` | Player | Enters manual add mode |
| `region save <name>` | Player | Creates selected local region |
| `region delete <name>` | Player | Deletes existing region; completion excludes globals, method does not |
| `region list` | Any | Prints region IDs |
| `region global create <world>` | Any | Creates a global for a loaded world |
| `region edit block add <region> <original> <replacement> <delay>` | Any | Validates Bukkit materials and integer delay, adds a standard rule, explicitly saves |
| `region edit setting set <region> <setting> <value>` | Any | Validates known setting and existing primitive type, changes memory |
| `region edit setting get <region> <setting>` | Any | Reports value |
| `testchat` | Player | Developer chat-format diagnostics |

Help advertises `region edit block remove`, but no corresponding method is present in this command class. Negative delays are not explicitly rejected by block-add. Setting name validation ignores case, but raw settings lookup/write is case-sensitive. The rewrite should validate inputs and implement removal instead of reproducing these defects.

Manual add mode cancels block interaction, requires a region at the clicked block, rejects an existing matching rule, adds a detected standard/custom rule, then exits. Sneak + right-click exits without adding. Re-entering displays instructions. Evidence: `events.listeners.PlayerInteractListener.handleManualAdd`. No manual-mode quit cleanup was found in `events.listeners.PlayerQuitListener`.

## Region settings

Evidence: `regions.settings.RegionSettingsContainer.Settings` initialization and `Companion.reconcile`. Missing settings are filled during create/deserialize. The exact legacy spelling `allow-block-tramping` matters for imports.

| Serialized key | Type | Default | Purpose |
| --- | --- | --- | --- |
| `region-display-icon` | String | `GRASS_BLOCK` | Menu icon; invalid material falls back to STONE |
| `allow-block-break` | Boolean | false | Allows non-regennable block breaking |
| `allow-block-place` | Boolean | false | Allows placing blocks |
| `auto-inventory` | Boolean | false | Routes drops to player inventory |
| `allow-block-tramping` | Boolean | true | Allows trampling farmland |
| `allow-bucket-usage` | Boolean | false | Allows water/lava/fish and other buckets |
| `use-advanced-regen-delay` | Boolean | false | Uses configured delay expression |
| `respect-claims` | Boolean | true | Applies claim checks |

Permission resolution first denies an action if claims are respected and the claim adapter denies it; otherwise an enabled region action allows it. A global region also allows an action inside an accessible claim even if its setting is false. A local region has no such exemption. Evidence: `regions.RegionPermissions.resolve` and `check`.

## Config and messages

Evidence: `files.Config.CValues`.

| YAML path | Default |
| --- | --- |
| `generate-global-regions` | false |
| `persistent-timers` | true |
| `advanced.alternatives.auto-inventory` | false |
| `advanced.regen-delay.expression` | `rUni(%min_delay%, %max_delay%)` |
| `advanced.region-edges.render-radius` | 40 |
| `advanced.region-edges.render-interval` | 10 |
| `advanced.converters.ultimate-regen.enabled` | false |
| `advanced.converters.ultimate-regen.remove-data` | false |
| `advanced.converters.minexfarm-regen.enabled` | false |
| `advanced.converters.minexfarm-regen.remove-data` | false |
| `hooks.anti-grief.enabled` | true |
| `hooks.anti-grief.ignore-op` | true |
| `hooks.anti-grief.bypass-permission` | `oreregenerator.bypass.claims` |
| `advanced.stacked-blocks.fire-break-events` | true |

`messages.yml` exposes `cant-break-block`, `cant-place-block`, `cant-use-bucket`, and `regen-time-remaining`. Defaults are English with legacy color codes. A trailing `<delay:N>` is stripped from displayed text and supplies a per-message delay. Default delays are 5000, 1000, 1000, and 1000 respectively. Remaining-time replacements include `%dd%`, `%hh%`, `%mm%`, `%ss%`, `%ms%`. Right-clicking an active block reports its remaining time. Evidence: `files.Messages`, `events.listeners.PlayerInteractListener.handleGetRegenTime`, `extensions.ChatExtensionsKt`.

## GUI workflows

The artifact exposes these observable administration capabilities; exact slot decoration is not required to preserve the workflow:

- Main menu opens a searchable, paginated region list sorted by ID. Entries show global status and overlap warning. Region editor offers icon selection, block rules, chains, settings, teleport to center, and confirmed deletion. Evidence: `menus.MainMenu`, `menus.regions.RegionListMenu`, `RegionEditorMenu`.
- Rule list is sorted by identifying name and supports adding, editing, deleting, bulk timer editing, and copy/paste. Copy left-click selects blocks plus chains; shift-left selects blocks only; right-click selects chains only. Paste replaces the selected target containers with clones. Clipboard is per player and removed on quit. Evidence: `RegennableBlocksListMenu.decorate`, `clipboards.Clipboard`.
- Rule editor selects replacement, min/max seconds, crop age if applicable, and deletion. Adding an existing block opens the existing rule. Default vanilla replacement is STONE, crop replacement AIR, min/max 5 seconds; custom blocks use STONE and furniture AIR, also 5 seconds. Evidence: `RegennableBlockEditorMenu`, `RegennableBlocksListMenu`, `blocks.RegennableBlock.RegennableType.Companion`.
- Delay editor has increments of 1, 15, and 60 seconds, numeric chat input, and confirmation. It enforces nonnegative values and min <= max. Equalizing sets max to min. Bulk apply requires confirmation. Crop age editor allows -1 through maximum age; -1 means any age and an explicit age requires exact equality. Evidence: `RegennableRegenTimeEditorMenu`, `RegennableTimeTypeMenu`, `RegennableAgeConfigurationMenu`, `StepperMenu`, `blocks.orblocks.impl.CropPlaceable.matches`.
- Chain list supports creation and editing. Chain editor offers stages, add, drag/reorder/remove, rename, timer editing, final-stage breakability, regeneration style, and deletion. Styles are Direct, Staged, and Staged (same timer). Reordering follows left-to-right, top-to-bottom; existing stage timers follow stage identity. Evidence: `RegenChainsListMenu`, `RegenChainEditorMenu`, `ChainBlocksReorderMenu`, `RegenChainStageEditorMenu`.
- Rule replacement conflicts are visible. Eligible standalone rules can merge into a chain, or fold into an existing entry chain; crop/furniture conflicts require manual conversion. Entry conflict and standalone-priority warnings appear in chain editors. Evidence: `RegennableBlockEditorMenu.createConflictResolutionItem`, `addStandaloneConflictItem`, `addChainFoldInItem`, `RegenChainEditorMenu.createGui`.
- Material/custom selectors support pagination, case-insensitive search, inventory item selection where resolvable, and optional expanded lists. Search is entered with an anvil; right-click clears it, shift-left toggles filtered-out entries. Custom choices are conditional on loaded hooks. Evidence: `menus.materials.PaginatedListMenu`, `menus.regions.regennables.AbstractTypePickerMenu`, type picker subclasses.

Numeric chat input accepts integers and `cancel`, suppresses that chat event, and returns to the menu on the main thread. The manager retains prior numeric input; cancellation can therefore reapply stale input. Preserve the workflow, not this bug. Evidence: `conversations.ConversationManager`, `menus.StepperMenu.promptForInt`.

## Personal region outlines

`edges` enables dust particles sent only to the player. Local region edges within the configured radius render green inside, aqua outside, red for any overlapping cuboid. Globals are excluded. Radius and interval clamp to at least 1; interval is ticks. There is a budget of 600 particles per player per render, nominal edge spacing one block. Evidence: `regions.RegionVisualizationManager`.

## Legacy serialization

`regions.json` has root `version` (latest observed 2) and `regions`, an object keyed by ID. Local region fields are `id`, `worldName`, `cornerOne`, `cornerTwo`, `regennableBlocks`, `settings`, `chains`. Corners use Bukkit Location serialization. Globals omit corners; their ID equals world name. Missing `chains` loads empty. Evidence: `files.RegionFile`, `regions.RegenRegion.serialize/deserialize`, `GlobalRegenRegion.serialize/deserialize`, `updaters.regions_file.RegionsFilePatcher`.

`regennableBlocks` is an object keyed by UUID. Each entry stores `type`, `originalPlaceable`, `replacePlaceable`, `minRegenTime`, `maxRegenTime`, `uuid`; absent replacement loads AIR. Rule types are `standard`, `itemsadder`, `itemsadder-furniture`, `oraxen`, `oraxen-furniture`, `nexo`, `nexo-furniture`. Evidence: `blocks.RegennableBlock.serialize/deserialize`, `RegennableBlocksContainer.serialize`.

A placeable is `{ "type": "...", "data": { ... } }`. Placeable types are `material`, `crop`, and the six custom types above. Material data uses `material`; crop adds `age`. Custom data uses `block-id`, except ItemsAdder furniture uses `furniture-id`. Evidence: `blocks.orblocks.Placeable`, `StringIdPlaceable`, and each `impl.*.generateData` or constructor.

`chains` is an object keyed by chain UUID. A chain stores `uuid`, `name`, `regenStyle`, `lastStageBreakable`, and `stages`. Stage keys are numeric strings in ascending order, each holding rule fields without replacement. Absent final breakability is false. Evidence: `blocks.chains.RegenChain.serialize/deserialize`, `RegenChainsContainer.serialize`.

`persistence.json` root `blocks` is a list of serialized active regenerations. Save collects active entries from all regions. Load deserializes entries, restarts their timers, and registers them with their associated region. Precise active-entry fields and timer semantics belong to the core contract. Evidence: `files.PersistenceFile`.

Regions failing to load trigger a backup under `backups`. Custom definitions may queue until their integration is ready. A region-data-loaded event fires once queued block data is empty. Region loading also identifies global regions by matching the ID to a loaded world or world-container entry; this can misclassify a local region and should be avoided in the rewrite. Evidence: `files.RegionFile.loadRegions/isWorldName`, `blocks.RegennableBlocksContainer`.

Legacy region migrations wrap old root entries under `regions`; change `replaceMaterial` into a material placeable; split `regenTime` into min/max; introduce `worldName` from corner world; change `originalMaterial` into an original placeable. A backup precedes patching. Evidence: `updaters.regions_file.patches.v1.*`, `v2.OriginalMaterialPlaceablePatch`.

## External converters

Both converters read foreign `regions.yml` under `Regions.<id>` with `world`, `x1/y1/z1`, `x2/y2/z2`. `Ultimate_BlockRegeneration` reads a region's `materials.yml`; `MineXFarmRegen` uses one shared `materials.yml`. Under `Materials.<material>`, only `Enabled: true` entries are imported; replacement is the first `DelayBlock` list entry before ` ; ` and `Delay` supplies both seconds. Imported rules are standard. Each imported region is saved. Optional remove-data flags recursively remove the foreign plugin's folder. Evidence: `files.converters.impl.UltimateRegenConverter`, `MineXFarmRegenConverter`; folder scanning in `files.converters.PluginConverter`.

## Boundaries and uncertainties

- These statements are static observations only. No Minecraft server, interactive menu, plugin conversion, or external integration was exercised for this document.
- Decompiled locals sometimes contain CFR reconstruction artifacts. Serialized key literals and direct branch behavior are stronger evidence than reconstructed temporary variables.
- Menu operations often mutate state without an immediate save call; the original's wider lifecycle must be consulted for durability. The rewrite should persist successful administrative edits explicitly.
- Original paid/free restrictions exist around local-region/rule creation. They are distribution checks, not user-requested behavior for the new plugin.
- Verify supported server versions, claims, custom content adapters, persistence restoration, and GUI item safety in the rebuilt plugin's actual test matrix. Static discovery establishes intent and import compatibility, not runtime parity.
