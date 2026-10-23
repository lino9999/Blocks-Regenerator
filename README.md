# BlocksRegenerator

Regenerate blocks, crops and custom content in configurable mining regions. Created by **Lino** for **Spigot and Paper 1.21.11 through 26.3**.

[Download](https://github.com/lino9999/Blocks-Regenerator/releases/latest) · [Commands](#commands-and-menus) · [Permissions](#permissions) · [Configuration](#configuration) · [Verification](docs/VERIFICATION.md)

## Compatibility

One JAR supports every released version in this range:

| Minecraft server | Server Java version |
| --- | --- |
| 1.21.11 | Java 21 or newer supported by your server |
| 26.1, 26.1.1, 26.1.2 | Java 25 |
| 26.2 | Java 25 |
| 26.3 | Java 25 |

The plugin targets the Spigot 1.21.11 API and Java 21 bytecode. Newer servers still require their own Java version: Minecraft 26.1 introduced the [Java 25 requirement](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1). Versions outside this range, development snapshots and Folia are not supported. Custom blocks must exist on the installed server version, and optional plugins need compatible releases of their own. See [verification scope](docs/VERIFICATION.md) for automated API checks and live server tests.

## How it works

1. Define a cuboid region or a global region covering one world.
2. Add rules describing the original block, its temporary replacement and its regeneration delay.
3. When a player mines a matching block, the plugin records its original state and applies the temporary replacement.
4. After the timer expires, the original block and its block data return. Chains can instead move through several block stages.

For example, a DIAMOND_ORE rule with STONE as its replacement and a 5–10 second delay lets players mine the ore, temporarily leaves stone, and then restores the ore. Only mined blocks with active timers regenerate; the plugin does not fill a region with ore or reset every block inside it.

Features include crop age requirements, doors and tall plants, stacked sugar cane/cactus/bamboo, automatic inventory drops with overflow dropped into the world, build and bucket restrictions, farmland protection, inventory editors, selection tools, private chat input, region outlines, copy/paste and legacy imports.

Overlapping regions use the highest priority; equal priorities use the region ID in alphabetical order. New local regions have priority 1 and global regions priority 0. The winning region's rules and settings apply; lower-priority regions are not combined with it.

## Installation and upgrades

1. Stop the server and back up its worlds and plugin data.
2. Download `BlocksRegenerator-1.0.2.jar` from [Releases](https://github.com/lino9999/Blocks-Regenerator/releases/latest) and place it in `plugins/`.
3. Remove older BlocksRegenerator JARs and the original OreRegenerator JAR from `plugins/`. Keep their data folders for migration or recovery.
4. Start the server using the Java version in the compatibility table.
5. Check the console for `Enabling BlocksRegenerator v1.0.2`, then run `/blocksregenerator` as an operator or an administrator with the permission below.

Configuration and saved state are created in `plugins/BlocksRegenerator/`. Upgrading from BlocksRegenerator 1.0.0 preserves the existing data format; keep the data folder and replace the JAR while the server is stopped. Version 1.0.0 required server API 26.3; use 1.0.2 for the wider compatibility range.

Version 1.0.2 removes `/or` and `/oreregenerator`; use `/blocksregenerator` or `/bregen`. Restart the server after replacing the JAR so Bukkit registers the new commands. `/blocksregenerator reload` reloads configuration and messages, and does not apply a new JAR.

## Quick start: a regenerating mine

Run `/blocksregenerator wand` in game. Left-click one corner and right-click the opposite corner. Both corners must be in the same world. Then run:

```text
/blocksregenerator create mine
/blocksregenerator block add mine DIAMOND_ORE STONE 5 10
/blocksregenerator block add mine IRON_ORE STONE 3
/blocksregenerator setting set mine auto-inventory true
/blocksregenerator edit mine
```

Place ore in the selected area before testing. Diamond ore regenerates after a random 5–10 seconds, and iron ore after exactly 3 seconds. New local regions deny breaking unconfigured blocks and deny block placement; configure those settings if you want other building activity.

For a mature wheat farm, select another area and run:

```text
/blocksregenerator create farm
/blocksregenerator block add farm crop:WHEAT_SEEDS@7 AIR 10
/blocksregenerator setting set farm allow-block-trampling false
```

This rule matches wheat at age 7 and restores its captured age after ten seconds. Younger wheat does not match the rule. Use `crop:WHEAT_SEEDS` without an age to match any age.

For a whole world, use the console or run in game:

```text
/blocksregenerator global world world_mines
/blocksregenerator block add world_mines DIAMOND_ORE STONE 10
```

Replace `world` with a loaded world's actual name. A global region starts with ordinary breaking and placement enabled. Console commands omit the initial `/`.

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `blocksregenerator.admin` | Operators | Use all administration commands, tools and menus |
| `oreregenerator.admin` | false | Compatibility node that grants `blocksregenerator.admin` |
| `blocksregenerator.bypass.claims` | false | Bypass this plugin's land-claim checks |
| `oreregenerator.bypass.claims` | false | Compatibility node that grants `blocksregenerator.bypass.claims` |

Ordinary players need no BlocksRegenerator permission to mine configured blocks. Region settings, other plugins' protection and claim permissions still apply. There are no separate per-command or per-region permission nodes.

With LuckPerms installed, for example:

```text
/lp group admin permission set blocksregenerator.admin true
```

The claim bypass permission affects this plugin's checks. It does not grant building rights in another protection plugin or bypass all region restrictions. By default `hooks.anti-grief.ignore-op: true` also bypasses this plugin's claim checks for operators; test ordinary-player protection with a non-operator account.

## Commands and menus

The primary command is `/blocksregenerator`, with `/bregen` and `/br` as short aliases. WorldEdit/FAWE may claim `/br` for brushes; use `/blocksregenerator` or `/bregen` on those servers. The fully qualified `/blocksregenerator:blocksregenerator` command explicitly selects this plugin. The old `/or` and `/oreregenerator` command aliases were removed in 1.0.2.

Use `/blocksregenerator` to open the inventory editor. Commands require `blocksregenerator.admin`; the compatibility permission `oreregenerator.admin` also grants access. Console can run commands except selection, menus, manual mode, clipboard, wand, and outlines.

All successful edits are saved. Changing a region's rules or chains restores its active regenerations before adopting the new configuration. GUI entries are read again by ID before edits, so two administrators do not overwrite each other's unrelated changes.

### Selection and regions

| Command | Purpose |
| --- | --- |
| `/blocksregenerator wand` | Get the selection stick; left/right click sets corners one/two |
| `/blocksregenerator pos1`, `/blocksregenerator pos2` | Select the block at your current position |
| `/blocksregenerator position one`, `/blocksregenerator position two` | Long forms; `position` also accepts `pos`, `1`, `2` |
| `/blocksregenerator create <region>` | Create an inclusive cuboid from both selected corners |
| `/blocksregenerator select <region>` | Copy an existing cuboid into your current selection |
| `/blocksregenerator global <world> [region]` | Create one global region for a loaded world |
| `/blocksregenerator list` | List region IDs |
| `/blocksregenerator edit <region>` | Open the region editor |
| `/blocksregenerator delete <region>` | Delete a region and restore its active blocks |
| `/blocksregenerator priority <region> <integer>` | Set overlap precedence; larger values win |
| `/blocksregenerator regenerate <region>` | Restore active blocks immediately; aliases `restore`, `regen` |
| `/blocksregenerator add` | Add a block type by left-clicking a world block; sneak + right-click exits |
| `/blocksregenerator edges` | Toggle personal region boundaries; aliases `borders`, `visualize` |

Region IDs contain 1-64 letters, numbers, underscores, or hyphens. Both selection corners must be in the same world. Global regions start with ordinary block breaking and placement enabled. Their other settings use the defaults in the reference contract.

Original command forms remain accepted: `/blocksregenerator region save <region>`, `/blocksregenerator region delete <region>`, `/blocksregenerator region list`, `/blocksregenerator region global create <world>`, and `/blocksregenerator region edit ...` for block/setting/chain operations. `/blocksregenerator save` aliases `create`; `/blocksregenerator remove` aliases `delete`.

### Blocks

| Command | Purpose |
| --- | --- |
| `/blocksregenerator block add <region> <original> <replacement> <minimum> [maximum]` | Add a rule with seconds of delay |
| `/blocksregenerator block remove <region> <block>` | Remove a rule; `delete` also works |
| `/blocksregenerator block delay <region> <block> <minimum> [maximum]` | Change its timer |
| `/blocksregenerator block replace <region> <block> <replacement>` | Change its temporary block |
| `/blocksregenerator block age <region> <block> <-1|age>` | Change an existing crop rule's exact required age |

`<block>` accepts the original ID, UUID, or the rule's 1-based registration number. Material names are case-insensitive. `<original>` and `<replacement>` accept these forms:

- `DIAMOND_ORE` or `material:DIAMOND_ORE`
- `crop:WHEAT_SEEDS` or `crop:WHEAT_SEEDS@7`; age -1 matches any age
- `itemsadder:namespace:block_id`, `oraxen:block_id`, `nexo:block_id`
- `itemsadder-furniture:namespace:furniture_id`, `oraxen-furniture:id`, `nexo-furniture:id`

Delay limits are `0 <= minimum <= maximum <= 31536000`. Omitting maximum uses an exact delay. Newly added GUI/manual rules default to five seconds; ordinary blocks temporarily become STONE, crops and furniture AIR.

### Settings

Use `/blocksregenerator setting get <region> <setting>` or `/blocksregenerator setting set <region> <setting> <value>`. Boolean settings require `true` or `false`:

| Setting | Default |
| --- | --- |
| `allow-block-break` | false |
| `allow-block-place` | false |
| `auto-inventory` | false |
| `allow-block-tramping` | true |
| `allow-bucket-usage` | false |
| `use-advanced-regen-delay` | false |
| `respect-claims` | true |

The corrected spelling `allow-block-trampling` is accepted. `region-display-icon` takes an item material name.

### Regen chains

Chains contain ordered block stages. Mining advances toward the last stage; regeneration returns toward the initial stage. A chain selector accepts its UUID, name, or 1-based registration number. Names containing spaces can be entered during creation/renaming; use the UUID or number to select them in later commands.

| Command | Purpose |
| --- | --- |
| `/blocksregenerator chain add <region> <name>` | Create an empty chain |
| `/blocksregenerator chain remove <region> <chain>` | Remove a chain |
| `/blocksregenerator chain rename <region> <chain> <name>` | Change its name |
| `/blocksregenerator chain style <region> <chain> <direct|staged|same-timer>` | Choose regeneration style |
| `/blocksregenerator chain final <region> <chain> <true|false>` | Allow or deny mining its final stage |
| `/blocksregenerator chain delay <region> <chain> <minimum> [maximum]` | Set every stage's delay |
| `/blocksregenerator chain stage <region> <chain> add <block> [minimum] [maximum]` | Append a stage |
| `/blocksregenerator chain stage <region> <chain> remove <stage>` | Remove a 1-based stage |
| `/blocksregenerator chain stage <region> <chain> move <from> <to>` | Reorder stages, keeping timers with them |
| `/blocksregenerator chain stage <region> <chain> delay <stage> <minimum> [maximum]` | Set one stage's delay |

`direct` restores the initial block in one step. `staged` restores one step at a time using each stage's delay. `same-timer` restores one step at a time using the first stage's delay. Crops and furniture are standalone rules, rather than chain stages.

The GUI exposes chain creation, renaming, stage selection/replacement, move earlier/later/to-position, per-stage and bulk timers, style cycling, final-stage breakability, and deletion. It shows conflicts where a standalone rule or another chain shares an entry block. Eligible replacement conflicts can merge into a chain from the rule editor.

### Clipboard, imports, and maintenance

| Command | Purpose |
| --- | --- |
| `/blocksregenerator copy <region> [all|blocks|chains]` | Copy definitions to your personal clipboard |
| `/blocksregenerator paste <region>` | Replace copied block/chain collections with independent copies |
| `/blocksregenerator import oreregenerator` | Import original plugin data from the adjacent `OreRegenerator` plugin directory |
| `/blocksregenerator import ultimate-regen` | Import Ultimate_BlockRegeneration definitions |
| `/blocksregenerator import minexfarm-regen` | Import MineXFarmRegen definitions |
| `/blocksregenerator reload` | Reload configuration/messages; alias `rl` |
| `/blocksregenerator eval <expression>` | Evaluate a supported numeric expression |
| `/blocksregenerator help` | Show command reference |

`convert` aliases `import`. A clipboard copies blocks plus chains by default; selecting only one replaces only that collection when pasted. Clipboards, selection, prompts, and manual mode are cleared on disconnect. GUI deletion, clipboard paste, conflict merge, and bulk timers require an in-game confirmation. Direct commands take effect immediately.

Lists have bounded pages and case-insensitive search. The search button opens a private chat prompt; right-click clears search. Block selectors accept a click on a compatible item in your own inventory without moving or consuming it. Numeric/private text prompts expire after two minutes; `cancel` returns without applying input. Menu actions and prompt callbacks recheck administration permissions.

### Chain example

```text
/blocksregenerator chain add mine ore_cycle
/blocksregenerator chain stage mine ore_cycle add DIAMOND_ORE 10
/blocksregenerator chain stage mine ore_cycle add GOLD_ORE 5
/blocksregenerator chain stage mine ore_cycle add STONE 3
/blocksregenerator chain style mine ore_cycle staged
/blocksregenerator chain final mine ore_cycle false
```

Use a region without standalone DIAMOND_ORE rules for this example, or remove those rules first. Mining diamond ore advances to gold ore; mining gold advances to stone. With final-stage mining disabled, stone cannot be mined. Regeneration walks back through the stages. If final-stage mining is enabled and that stage is mined out, recovery restores the final stage first before continuing toward the initial block.

### Using the menus

Open `/blocksregenerator`, select a region, then choose its block rules, chains or settings. The editors support item selection, timer changes, chain reordering, conflict merging and personal region outlines. `/blocksregenerator add` provides a manual world-click workflow.

Search buttons and numeric/name fields use private chat prompts. Prompts expire after two minutes; type `cancel` to leave without applying input. Right-click a search button to clear the filter. Selecting an item from your inventory does not consume it. Menu deletion, paste, conflict merge and bulk timer changes request confirmation; direct commands apply immediately.

## Configuration

Edit `plugins/BlocksRegenerator/config.yml`, then run `/blocksregenerator reload`. Messages are in `messages.yml`; regions and active recovery snapshots are in `data.json`.

| Configuration key | Default | Purpose |
| --- | --- | --- |
| `generate-global-regions` | false | Create missing global definitions for loaded worlds |
| `persistent-timers` | true | Save remaining regeneration time across restarts |
| `storage.autosave-seconds` | 30 | Periodic recovery checkpoint interval in seconds |
| `engine.max-operations-per-tick` | 500 | Limit regeneration work processed per server tick |
| `hooks.anti-grief.enabled` | true | Enable optional land-protection checks |
| `hooks.anti-grief.ignore-op` | true | Let operators bypass this plugin's claim checks |
| `hooks.anti-grief.bypass-permission` | `blocksregenerator.bypass.claims` | Permission used for claim bypass |
| `advanced.regen-delay.expression` | `rUni(%min_delay%, %max_delay%)` | Advanced delay expression |
| `advanced.region-edges.render-radius` | 40 | Personal outline render distance |
| `advanced.region-edges.render-interval` | 10 | Outline update interval in ticks |
| `advanced.alternatives.auto-inventory` | false | Use deferred drop collection for automatic inventory |
| `advanced.stacked-blocks.fire-break-events` | true | Fire protection-aware break events for upper stacked blocks |
| `advanced.converters.ultimate-regen.enabled` | false | Automatically import Ultimate_BlockRegeneration on startup |
| `advanced.converters.minexfarm-regen.enabled` | false | Automatically import MineXFarmRegen on startup |

Legacy converter `remove-data` settings are retained for compatibility but never delete source folders. Enable automatic conversion only when its source data is ready and does not collide with existing region IDs.

Messages support `&` color codes. A `<delay:1000>` suffix throttles that message to once per second per player. The remaining-time message supports `%dd%`, `%hh%`, `%mm%` and `%ss%` for days, hours, minutes and seconds.

### Advanced delay expressions

Enable expressions for the region with:

```text
/blocksregenerator setting set mine use-advanced-regen-delay true
```

Variables are `%min_delay%`, `%max_delay%` and `%player_count%`. Operators are `+ - * / ^`, parentheses and unary signs. Functions are `rUni`, `min`, `max`, `pow`, `abs`, `sqrt`, `floor`, `ceil`, `round`, `sin`, `cos`, `ln` and `log`; constants are `pi` and `e`.

Example `advanced.regen-delay.expression`:

```text
%min_delay% + (%max_delay% - %min_delay%) * rUni(0, 1) / (1 + %player_count%)
```

Use `/blocksregenerator eval <expression>` to check a formula. Invalid expressions log a warning and fall back to the rule's validated delay range. This bounded evaluator supports the documented grammar rather than every expression supported by the original plugin's math library.

## Optional integrations

No additional plugin is required for vanilla regeneration.

| Integration | Support |
| --- | --- |
| ItemsAdder, Oraxen, Nexo | Custom blocks, custom item selection and furniture definitions |
| BlockHardness | Custom hardness break events |
| WorldGuard, GriefPrevention, Towny, Lands, Residence | Direct land-protection checks |
| Other supported claim plugins | Additional protection through bundled AntiGriefLib |

Additional recognized protection plugins include Kingdoms, IridiumSkyblock, CrashClaim, GriefDefender, HuskClaims, BentoBox, HuskTowns, PlotSquared, SuperiorSkyblock2, FabledSkyBlock, RedProtect, Landlord, uSkyBlock, XClaim, UltimateClaims, UltimateClans, PreciousStones, Dominion, Factions, NoBuildPlus, Protect, SimpleClaimSystem and LandClaimPlugin. ProtectionStones uses WorldGuard protection.

Keep `respect-claims` enabled on regions where protection must apply. Failed protection adapters deny the affected action and report the problem in the console. Missing custom-content providers retain saved definitions and postpone affected regeneration; they do not silently replace custom content with vanilla blocks.

Third-party support depends on that provider's installed version. The automated tests cover adapter boundaries, but real custom furniture, drops and every claim-plugin version have not all been tested live. See [integration details](docs/INTEGRATIONS.md) and [verification limits](docs/VERIFICATION.md).

## Saving, restarts and recovery

Successful administrative edits are saved before confirmation. Editing or deleting a region restores its active blocks first. `/blocksregenerator regenerate <region>` restores only blocks with pending regeneration; it does not rebuild an entire mine.

Timers preserve their remaining milliseconds across a clean restart. Time does not count down while the server is stopped. Unloaded worlds/chunks keep their recovery entries; timers never force chunks to load. Load affected chunks before editing, deleting or manually restoring a region with pending blocks.

Periodic checkpoints default to 30 seconds. A crash or forced process termination can lose changes since the last checkpoint, so stop the server cleanly. Turning `persistent-timers` off restores loaded pending entries at shutdown while retaining unloaded entries for later recovery.

`data.json` stores versioned region definitions and snapshots. Writes use atomic replacement where available; `data.json.bak` holds the preceding saved version. Invalid state files stop plugin startup without overwriting the source. Back up the complete plugin folder with the matching worlds before manual recovery. Do not delete `data.json` while timers are active unless you intend to discard their recovery state.

Definitions can only use materials present on the running server. `CHAIN` and `IRON_CHAIN` are accepted as aliases when needed, but this does not make newly introduced blocks available on an older Minecraft release. Downgrading worlds or active block snapshots between Minecraft versions is not supported.

## Migrating from another regenerator

Stop the server, remove the previous regeneration plugin JAR and preserve its data directory next to `BlocksRegenerator/`. Start the server and run the corresponding command:

| Source | Import command | Source folder |
| --- | --- | --- |
| OreRegenerator | `/blocksregenerator import oreregenerator` | `plugins/OreRegenerator/` |
| Ultimate_BlockRegeneration | `/blocksregenerator import ultimate-regen` | `plugins/Ultimate_BlockRegeneration/` |
| MineXFarmRegen | `/blocksregenerator import minexfarm-regen` | `plugins/MineXFarmRegen/` |

OreRegenerator migration reads `regions.json` and optional `persistence.json`. Imports reject existing region ID collisions and preserve their source folders. Do not run two regeneration plugins over the same blocks. This plugin is a new implementation based on the observed behavior of OreRegenerator 2.6.2; see the [reconstruction record](docs/REVERSE_ENGINEERING.md).

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `Unsupported API version 26.3` on 1.21.11 | Replace BlocksRegenerator 1.0.0 with 1.0.2; remove the old JAR |
| Java is not recognized or the terminal closes immediately | Configure a valid Java executable/JAVA_HOME, run the launcher from a terminal and keep its output visible |
| `UnsupportedClassVersionError` | Check the Java version used by the actual server launcher; 26.x needs Java 25 |
| A mined block does not regenerate | Check selected world/bounds, winning region priority, rule material/crop age, other plugins' cancellation and console errors |
| A pending block has not returned | Load its chunk and verify its custom-content provider is available |
| Non-rule blocks cannot be broken or placed | Check `allow-block-break`, `allow-block-place` and the claim plugin's permissions |
| No permission for `/blocksregenerator` | Grant `blocksregenerator.admin` or use the console/operator account |
| `/br` shows brush commands | WorldEdit/FAWE owns that alias; use `/blocksregenerator`, `/bregen` or `/blocksregenerator:blocksregenerator` |
| `/or` or `/oreregenerator` no longer works | Use `/blocksregenerator` or `/bregen`; legacy command aliases were removed in 1.0.2 |
| An import is refused | Check source folder, input format and existing region ID collisions; preserve backups |

Report issues with the server version, Java version, plugin version, relevant configuration and console error. Remove passwords and private data from logs before sharing them.

## Building and contributing

Use JDK 21 or newer and the pinned Maven Wrapper:

Windows PowerShell:

```powershell
.\mvnw.cmd verify
```

Linux/macOS:

```sh
./mvnw verify
```

The result is `target/BlocksRegenerator-1.0.2.jar`. The default build uses the oldest supported API so the same artifact runs across the supported range. To verify a newer API, use JDK 25, for example:

```sh
./mvnw -Dspigot.version=26.3-R0.1-SNAPSHOT clean verify
```

GitHub Actions verifies every supported API version and both Java 21 and 25 on the baseline API, and uploads a separate artifact for each matrix entry. Use the release JAR for installation.

Read [docs/AGENTS.md](docs/AGENTS.md) for architecture, invariants and focused editing paths. All source, configuration and messages use English. `.gitignore` and `.ignore` exclude original binaries, tools, server files and generated output from version control or broad searches; ignore-file support depends on the tool.

[Command reference](docs/COMMANDS.md) · [Original behavior](docs/ORIGINAL_CONTRACT.md) · [Integration contracts](docs/INTEGRATIONS.md) · [Verification and limits](docs/VERIFICATION.md)
