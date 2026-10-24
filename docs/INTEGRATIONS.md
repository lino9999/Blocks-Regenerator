# Integration contracts recovered from the original artifact

Evidence is static inspection of the shipped plugin, not a running server. Original evidence paths below are relative to `.work/original/`; `.work/readable/` contains the same methods with decompiler metadata removed. These files describe behavior, not reusable implementation source.

## Custom content providers

Hook detection order is ItemsAdder, Oraxen, Nexo. Provider IDs are exact strings. Custom block and furniture placeables are valid as both regeneration sources and replacement stages. Optional hooks must load without their external classes installed. An unavailable provider must not turn a custom definition into its vanilla backing block.

| Provider | Identify a block | Place a block | Item ID and display item |
| --- | --- | --- | --- |
| ItemsAdder | `dev.lone.itemsadder.api.CustomBlock.byAlreadyPlaced(Block).getId()` | `CustomBlock.getInstance(String).place(Location)` | `CustomStack.byItemStack(ItemStack).getNamespacedID()`; `CustomBlock.getInstance(id).getItemStack()` |
| Oraxen | `io.th0rgal.oraxen.api.OraxenBlocks.isOraxenBlock(Block)` and `getOraxenBlock(Location).getItemID()` | `OraxenBlocks.place(String, Location)` | `OraxenItems.getIdByItem(ItemStack)`; `getItemById(id).build()` |
| Nexo | `com.nexomc.nexo.api.NexoBlocks.isCustomBlock(Block)` and `customBlockMechanic(Location).getItemID()` | `NexoBlocks.place(String, Location)` | `NexoItems.idFromItem(ItemStack)`; `itemFromId(id).build()` |

Oraxen definition lookup tries `OraxenBlocks.getStringMechanic(id)` then `getNoteBlockMechanic(id)`. Nexo uses `customBlockMechanic(id)`; ItemsAdder uses `CustomBlock.getInstance(id)`.

| Provider | Identify furniture | Place furniture | Remove without player drops |
| --- | --- | --- | --- |
| ItemsAdder | `CustomFurniture.byAlreadySpawned(Block).getId()` | `CustomFurniture.spawn(id, Block)` then spawned entity `setRotation(yaw, 0)` | furniture `remove(false)`; custom block `remove()` |
| Oraxen | `OraxenFurniture.isFurniture(Block)` and `getFurnitureMechanic(Block).getItemID()` | `OraxenFurniture.place(id, Location, yaw, BlockFace.UP)` | `OraxenFurniture.remove(Entity, null)`; `OraxenBlocks.remove(Location, null)` |
| Nexo | `NexoFurniture.isFurniture(Location)` and `furnitureMechanic(Location).getItemID()` | `NexoFurniture.place(id, Location, yaw, BlockFace.UP)` | original calls Kotlin `remove$default(Entity, null, null, 6, null)` |

ItemsAdder furniture item identification uses `CustomStack` ID plus `CustomFurniture.getNamespacedIdsInRegistry()`. Its displayed definition comes from `CustomFurniture.getInstance(id)`. Oraxen validates `isFurniture(String)` and uses `getFurnitureMechanic(String)`; Nexo validates `isFurniture(String)` or `isFurniture(ItemStack)` and uses `furnitureMechanic(String)`.

Original serialized placeable types: `MATERIAL`, `CROP`, `ITEMSADDER`, `ITEMSADDER_FURNITURE`, `ORAXEN`, `ORAXEN_FURNITURE`, `NEXO`, `NEXO_FURNITURE`. String providers generally store `block-id`; ItemsAdder furniture stores `furniture-id`. The surrounding original serializer controls the exact YAML representation.

Evidence: `hooks/HookManager` detection methods; `hooks/impl/*Hook`; `blocks/orblocks/impl/*Placeable`.

## External event contracts

| Event class | Payload used | Original handling |
| --- | --- | --- |
| `dev.lone.itemsadder.api.Events.CustomBlockBreakEvent` | `getBlock()`, `getPlayer()`, Bukkit `Cancellable` | Common custom block pipeline |
| `dev.lone.itemsadder.api.Events.FurnitureBreakEvent` | `getFurniture().getEntity().getLocation()`, `getPlayer()`, `Cancellable` | Common furniture pipeline |
| `dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent` | No payload | Refresh furniture IDs immediately; deserialize queued blocks/chains after 5 ticks; emit original `RegionDataLoadEvent` |
| `com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent` | `getBlock()`, `getPlayer()`, `Cancellable` | Common custom block pipeline |
| `com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent` | `getBaseEntity().getLocation()`, `getPlayer()`, `Cancellable` | Common furniture pipeline |
| `io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureBreakEvent` | `getBaseEntity().getLocation()`, `getPlayer()`, `Cancellable` | Common furniture pipeline |
| `me.ogali.blockhardness.events.CustomHardnessBlockBreakEvent` | Cast to Bukkit `BlockBreakEvent` | Delegate to ordinary break pipeline |

The original registers no dedicated Oraxen block break event. Oraxen blocks reach its Bukkit listener. Custom block events support chain mining; the original furniture event path only starts normal source regeneration. Creative breaks cancel any pending regeneration at the location and otherwise continue. Claim denial cancels the event silently. A disallowed ordinary block uses the configured English denial message, but matching regenerating blocks can still be mined. Provider break events supply no Bukkit drop event to the common engine, leaving normal custom drops to the provider.

The original custom event handlers do not declare `ignoreCancelled=true`. Reconstruction should honor cancellation and avoid double-processing provider and Bukkit events. ItemsAdder reload queuing is essential: IDs can be unavailable during ordinary plugin enable.

Furniture replacement cleanup checks only provider furniture rooted in the exact same block, scanning a 0.5 radius around the block center for Oraxen/Nexo entities. Transitioning furniture into a non-furniture replacement schedules another cleanup one tick after the provider event. Cleanup must not remove the newly placed replacement. Original orientation is represented by the base event location yaw; arbitrary mounting faces are not recovered.

Evidence: `hooks/CustomBlockHook.processCustomBlockBreakEvent`, `.processCustomFurnitureBreak`; `hooks/FurnitureCleanup.clearAt`; `hooks/impl/*Hook`; `events/listeners/BlockBreakListener`.

## Claims and region permission resolution

Original config defaults enable `hooks.anti-grief.enabled` and `hooks.anti-grief.ignore-op`, with a legacy claim-bypass permission whose product-specific identifier is omitted here. Integration loads one tick after enable. The artifact shades `dev.mrshawn.shade.antigrieflib.AntiGriefLib`; this is a relocated bundled dependency, not a class supplied by another server plugin. It builds with `ignoreOP`, `bypassPermission`, `silentLogs(true)`, `suppressErrors(true)` and calls `test(player, Flag.BREAK/PLACE/INTERACT, location)`. Initialization or query failures allow the action in the original.

With region `respect-claims` true, claim denial wins first. Next, a true region action setting allows the action. A false action setting is overridden only for a global region when a granting claim contains the location. Otherwise the region denies. Explicit cuboid regions cannot receive this granting override. Claims do not disable regeneration for permitted mining.

Granting claim detectors load through each protection plugin's classloader and only when that plugin is enabled:

| Plugin | Static contract used to detect a granting claim |
| --- | --- |
| GriefPrevention | `me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(Location, false, null) != null`; third parameter class is `me.ryanhamshire.GriefPrevention.Claim` |
| Towny | `com.palmergames.bukkit.towny.TownyAPI.getInstance().isWilderness(Location) == false` |
| Lands | `me.angeschossen.lands.api.LandsIntegration.of(Plugin).getArea(Location) != null` |
| Residence | `com.bekvon.bukkit.residence.Residence.getInstance().getResidenceManager().getByLoc(Location) != null` |
| WorldGuard | `WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().getApplicableRegions(BukkitAdapter.adapt(Location))`; any `ProtectedRegion.isMember(WorldGuardPlugin.inst().wrapPlayer(Player))` |

WorldGuard requires player membership; other detectors only establish that a claim exists, with the separate action query deciding permission. WorldGuard classes live under `com.sk89q.worldguard`, `com.sk89q.worldguard.bukkit`, `com.sk89q.worldguard.protection.regions`; WorldEdit adapter/location classes are `com.sk89q.worldedit.bukkit.BukkitAdapter`, `com.sk89q.worldedit.util.Location`. Failed detector calls return false in the original.

Bucket empty and entity capture check PLACE; bucket fill checks BREAK. These handlers ignore already cancelled events and bypass creative players. Trample checks only `allow-block-tramping` (original typo), on PHYSICAL player interaction with FARMLAND; no claims or creative exemption. Right-clicking Nexo blocks inside any regeneration region sets `useInteractedBlock` to DENY.

Evidence: `hooks/impl/AntiGriefHook`; `hooks/impl/claims/*Detector`; `regions/RegionPermissions.resolve`; `events/listeners/BucketUsageListener`, `CropTrampleListener`, `PlayerInteractListener.onNexoBlockInteract`.

## Crops, stacked blocks, and restoration

Crop item-to-block mapping: WHEAT_SEEDS -> WHEAT, POTATO -> POTATOES, CARROT -> CARROTS, BEETROOT_SEEDS -> BEETROOTS, COCOA_BEANS -> COCOA, SWEET_BERRIES -> SWEET_BERRY_BUSH, MELON_SEEDS -> MELON_STEM, PUMPKIN_SEEDS -> PUMPKIN_STEM; NETHER_WART, SUGAR_CANE and CACTUS map to themselves. Crop `age: -1` matches any age; nonnegative age matches exactly when the block is Bukkit `Ageable`. Max age comes from live block data. Crop placement creates the crop block at default age, while ordinary restoration snapshots the actual block data. Bamboo material matches both BAMBOO and BAMBOO_SAPLING.

Stacked materials are SUGAR_CANE, CACTUS, BAMBOO_SAPLING and BAMBOO. Mining records each contiguous matching block above the source (bamboo variants count as equivalent), using the same regeneration definition/delay. Each upper block gets tool/player-aware natural drops and auto-inventory handling. With `advanced.stacked-blocks.fire-break-events: true` (default), an independent synthetic Bukkit BlockBreakEvent is fired for each upper block; cancellation skips that block. A recursion guard prevents processing synthetic events twice. Respect-claims also checks each upper location.

Restoration waits while a stacked block directly below has an active timer. It retries after 20 ticks; restoring a support block immediately drains ready upper blocks waiting for support. Stacked restoration sets original block data with physics disabled. Bisected blocks restore both halves with opposite half properties and physics disabled. Other standard blocks restore material and full original block data.

CFR has unresolved variable assignments in the stack loops; do not copy these loops literally. Preserve their observable intent and validate bounded upward traversal, world-height limits, cancellation, no duplicate drops, and support ordering.

Evidence: `constants/MaterialConstants`; `blocks/orblocks/impl/CropPlaceable`, `MaterialPlaceable`; `events/listeners/BlockBreakListener.getStack`, `.areSameMaterial`, `.onBlockBreak`; `blocks/active/impl/StandardActiveRegennableBlock`.

## Additional protection adapters in the reconstruction

The reconstruction bundles the independently published `net.momirealms:antigrieflib:1.0.19` from the [official Maven repository](https://repo.momirealms.net/releases/) and relocates its packages to `dev.lino.blocksregenerator.lib.antigrief`. It does not copy the original plugin's shaded classes. See [the upstream version](https://github.com/Xiao-MoMi/AntiGriefLib/blob/main/gradle.properties), [the public API](https://github.com/Xiao-MoMi/AntiGriefLib/blob/main/project/src/main/java/net/momirealms/antigrieflib/AntiGriefLib.java), and the bundled MIT notice at `META-INF/LICENSE-antigrieflib.txt`.

`AntiGriefBridge` initializes only when an additional protection plugin is enabled and an action first needs checking. It rebuilds when the enabled provider instances change. The five direct adapters above remain responsible for their own checks and granting-claim semantics, and are excluded from library detection. Operator/configured permission bypass is resolved once by `ClaimsService` before either path.

Additional recognized plugin names are Kingdoms, IridiumSkyblock, CrashClaim, GriefDefender, HuskClaims, BentoBox, HuskTowns, PlotSquared, SuperiorSkyblock2, FabledSkyBlock, RedProtect, Landlord, uSkyBlock, XClaim, UltimateClaims, UltimateClans, PreciousStones, Dominion, Factions, NoBuildPlus, Protect, SimpleClaimSystem and LandClaimPlugin. Factions covers upstream SaberFactions/FactionsUUID selection. ProtectionStones relies on the direct WorldGuard checks. These plugins do not grant the special global-region override because the original exposed that override through the five explicit detectors only.

The bridge disables library bypasses, enables `suppressErrors(false)`, and preserves BREAK/PLACE/INTERACT action mapping. Upstream `Builder.build()` catches adapter initialization errors even with suppression disabled, so the bridge separately verifies that every enabled recognized plugin appears in `providers()`. Missing adapters, unsupported plugin versions, and query exceptions deny actions and emit a diagnostic instead of silently allowing them. A changed enabled-provider set triggers another initialization attempt; unchanged failed initialization remains denied.

Unit tests cover deferred construction, provider enable changes, denial propagation, missing-adapter detection, query exceptions, and direct-adapter exclusion. Server testing with the third-party plugins is still required to validate their own consumed APIs on a particular release.

## Validation limits

This inventory establishes the shipped artifact's consumed API signatures. It does not prove compatibility with every release of third-party plugins. Reflection adapters must validate supported overloads, report unavailable providers clearly, and retain definitions when provider content is not ready. Live tests with licensed ItemsAdder/Oraxen/Nexo artifacts and a Minecraft server remain necessary for drops, furniture hitboxes/orientation, reload order and cross-plugin event cancellation. A compile against Bukkit alone cannot verify those runtime integrations.
