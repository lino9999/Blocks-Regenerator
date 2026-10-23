# Administration

Use `/blocksregenerator` to open the inventory editor. Short aliases are `/bregen` and `/br`. The old `/or` and `/oreregenerator` command aliases were removed in 1.0.2. Commands require `blocksregenerator.admin`; the compatibility permission `oreregenerator.admin` also grants access. Console can run commands except selection, menus, manual mode, clipboard, wand, and outlines.

WorldEdit/FAWE may use `/br` for brushes. Use `/blocksregenerator` or `/bregen` to avoid that collision. `/blocksregenerator:blocksregenerator` explicitly selects this plugin's command.

All successful edits are saved. Changing a region's rules or chains restores its active regenerations before adopting the new configuration. GUI entries are read again by ID before edits, so two administrators do not overwrite each other's unrelated changes.

## Selection and regions

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

## Blocks

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

## Settings

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

## Regen chains

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

## Clipboard, imports, and maintenance

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
