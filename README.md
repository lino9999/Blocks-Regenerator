# BlocksRegenerator

Block, crop and custom content regeneration for **Spigot 26.3**, written by **Lino**. Requires **Java 25**.

This is a new implementation based on the observed behavior of the supplied OreRegenerator 2.6.2 artifact. It uses the Spigot API, immutable configuration models, an indexed region lookup, and a bounded regeneration queue.

## Features

- Inclusive cuboid regions and world-wide global regions, with configurable priority and deterministic overlap resolution.
- Block replacement, exact or random delays, crop age requirements, full block data restoration, doors, tall plants, sugar cane, cactus and bamboo.
- Ordered regeneration chains with direct, staged, or shared-timer recovery and optional final-stage mining.
- Automatic inventory drops with overflow returned to the world, remaining-time messages, build restrictions, bucket restrictions and farmland protection.
- Inventory editors, selection wand, private text input, searchable lists, copy/paste, conflict merging and personal region outlines.
- Optional ItemsAdder, Oraxen, Nexo and BlockHardness adapters; direct WorldGuard, GriefPrevention, Towny, Lands and Residence protection with 23 additional protection adapters through the bundled AntiGriefLib.
- Persistent recovery snapshots, atomic JSON replacement, previous-file backup, and non-destructive legacy imports.

## Build

Set `JAVA_HOME` to a JDK 25 installation. The Maven Wrapper downloads the pinned Maven distribution on its first run.

Windows PowerShell:

```powershell
.\mvnw.cmd verify
```

Linux/macOS:

```sh
./mvnw verify
```

The plugin is `target/BlocksRegenerator-1.0.0.jar`. GitHub Actions also builds and uploads it as a workflow artifact.

## Install and configure

1. Stop the server and place the built JAR in `plugins/`.
2. Remove the original OreRegenerator JAR before enabling the replacement.
3. Start Spigot 26.3 with Java 25. Configuration is created in `plugins/BlocksRegenerator/`.
4. As an operator, run `/br wand`, select both corners with left/right click, then `/br create mine`.
5. Run `/br edit mine` to configure blocks, chains and protection settings.

A console example:

```text
br global world
br block add world DIAMOND_ORE STONE 5 10
br setting set world auto-inventory true
```

Administrative access uses `blocksregenerator.admin`, granted to operators by default. The legacy `oreregenerator.admin` permission and `/or` command alias are supported. See [all commands and menu workflows](docs/COMMANDS.md).

## Persistence and migration

`data.json` contains regions and pending snapshots in one versioned state. Successful administrative edits are saved before confirmation. Timers preserve their remaining milliseconds across clean restarts. Periodic checkpoints default to 30 seconds; an abrupt process or machine failure can lose changes since the last checkpoint.

Unloaded worlds and chunks retain pending recovery entries. Regeneration never forces chunks to load. Load all affected chunks before editing, deleting, or manually restoring a region with pending blocks. Turning `persistent-timers` off restores loaded entries at shutdown and retains unloaded entries for later recovery.

To migrate, keep the original data folder beside the new plugin folder, remove the old JAR, then run `br import oreregenerator`. The importer reads `OreRegenerator/regions.json` and optional `persistence.json`. Imports from `Ultimate_BlockRegeneration` and `MineXFarmRegen` are also available. Existing region ID collisions reject the import; source folders are preserved. The legacy converter `remove-data` options never delete source files.

Do not run both regeneration plugins together. Never delete `data.json` while blocks are pending unless you intentionally discard their recovery snapshots. Invalid state files stop startup and remain intact; `data.json.bak` contains the preceding saved version.

## Delay expressions

The default advanced expression is `rUni(%min_delay%, %max_delay%)`. Enable `use-advanced-regen-delay` per region to use it. Variables are `%min_delay%`, `%max_delay%`, and `%player_count%`. Supported operators are `+ - * / ^`, parentheses, and unary signs. Functions are `rUni`, `min`, `max`, `pow`, `abs`, `sqrt`, `floor`, `ceil`, `round`, `sin`, `cos`, `ln`, and `log`; constants are `pi` and `e`.

Example: `%min_delay% + (%max_delay% - %min_delay%) * rUni(0, 1) / (1 + %player_count%)`.

Unsupported or invalid delay expressions log a warning and fall back to the rule's validated delay range. This bounded evaluator does not implement the original library's entire expression language.

## Development

Read [docs/AGENTS.md](docs/AGENTS.md) for targeted source paths, invariants and verification commands. `.gitignore` excludes original binaries, generated output, local tools and server data. `.ignore` also keeps broad text searches focused on maintained source; tool support for ignore files varies.

[Reverse engineering evidence](docs/REVERSE_ENGINEERING.md), [original behavior](docs/ORIGINAL_CONTRACT.md), [integration contracts](docs/INTEGRATIONS.md), and [verification scope](docs/VERIFICATION.md) record what was inspected and tested. Optional commercial content providers were not available for live testing; validate their installed versions before using custom blocks or furniture in production.
