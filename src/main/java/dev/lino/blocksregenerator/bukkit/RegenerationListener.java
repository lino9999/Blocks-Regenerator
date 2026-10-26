package dev.lino.blocksregenerator.bukkit;

import dev.lino.blocksregenerator.configuration.RuntimeSettings;
import java.util.function.Supplier;
import dev.lino.blocksregenerator.engine.BlockSnapshot;
import dev.lino.blocksregenerator.engine.PendingRegen;
import dev.lino.blocksregenerator.engine.RegenerationEngine;
import dev.lino.blocksregenerator.integration.ClaimsService;
import dev.lino.blocksregenerator.integration.IntegrationRegistry;
import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Placeable;
import dev.lino.blocksregenerator.model.RegenChain;
import dev.lino.blocksregenerator.model.RegenRule;
import dev.lino.blocksregenerator.model.Region;
import dev.lino.blocksregenerator.model.RegionSetting;
import dev.lino.blocksregenerator.service.RegionIndex;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class RegenerationListener implements Listener {
    private record BreakIntent(Cancellable event, Region region, RegenRule rule, RegenChain chain, int stage,
                               BlockKey key, BlockSnapshot snapshot) {}
    private record DropTicket(UUID player, boolean auto, boolean drop, long expires, boolean spawnCapture) {}
    private record CreativeIntent(Cancellable event, BlockKey key) {}
    private record DropCommit(Cancellable event, UUID player, List<Item> items, Location location) {}
    private final JavaPlugin plugin;
    private final RegionIndex regions;
    private final RegenerationEngine engine;
    private final IntegrationRegistry integrations;
    private final BukkitWorldAccess world;
    private final RegionPermissions permissions;
    private final PlayerNotices messages;
    private final Supplier<RuntimeSettings> settings;
    private final ArrayDeque<BreakIntent> commits = new ArrayDeque<>();
    private final ArrayDeque<CreativeIntent> creative = new ArrayDeque<>();
    private final ArrayDeque<DropCommit> collections = new ArrayDeque<>();
    private final Set<BlockKey> overflowDrops = new HashSet<>();
    private final Set<BlockKey> reserved = new HashSet<>();
    private final Set<BlockKey> synthetic = new HashSet<>();
    private final Map<BlockKey, DropTicket> drops = new HashMap<>();
    private long tick;

    public RegenerationListener(JavaPlugin plugin, RegionIndex regions, RegenerationEngine engine,
                                IntegrationRegistry integrations, BukkitWorldAccess world,
                                RegionPermissions permissions, PlayerNotices messages, Supplier<RuntimeSettings> settings) {
        this.plugin = plugin;
        this.regions = regions;
        this.engine = engine;
        this.integrations = integrations;
        this.world = world;
        this.permissions = permissions;
        this.messages = messages;
        this.settings = settings;
    }

    public void tick() {
        tick++;
        int budget = settings.get().scheduler().workBudget();
        int creativeBudget = budget;
        while (creativeBudget-- > 0 && !creative.isEmpty()) {
            CreativeIntent intent = creative.remove();
            if (!intent.event().isCancelled()) {
                engine.cancel(intent.key());
            }
        }
        int commitBudget = budget;
        while (commitBudget-- > 0 && !commits.isEmpty()) {
            BreakIntent intent = commits.remove();
            reserved.remove(intent.key());
            reserved.removeAll(intent.snapshot().data().keySet());
            if (intent.event().isCancelled()) {
                drops.remove(intent.key());
                continue;
            }
            try {
                if (regions.get(intent.region().id()) == null) {
                    world.restore(intent.key(), intent.snapshot());
                    continue;
                }
                if (intent.chain() == null) {
                    engine.start(intent.region(), intent.rule(), intent.key(), intent.snapshot());
                } else {
                    engine.mineChain(intent.region(), intent.chain(), intent.stage(), intent.key(), intent.snapshot());
                }
            } catch (RuntimeException error) {
                world.failure(intent.key(), error);
            }
        }
        int count = Math.min(collections.size(), budget);
        while (count-- > 0) {
            DropCommit collection = collections.remove();
            Player player = Bukkit.getPlayer(collection.player());
            if (collection.event().isCancelled() || player == null) {
                continue;
            }
            for (Item item : collection.items()) {
                if (item.isValid() && !item.isDead()) {
                    ItemStack stack = item.getItemStack().clone();
                    item.remove();
                    give(player, stack, collection.location());
                }
            }
        }
        drops.entrySet().removeIf(entry -> entry.getValue().expires() < tick);
    }

    public void flush() {
        do {
            tick();
        } while (!commits.isEmpty() || !creative.isEmpty() || !collections.isEmpty());
    }

    public void clear() {
        commits.clear();
        creative.clear();
        collections.clear();
        reserved.clear();
        drops.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (synthetic.contains(BukkitWorldAccess.key(event.getBlock()))) {
            return;
        }
        try {
            Placeable source = integrations.identify(event.getBlock());
            if (source.type().startsWith("itemsadder") || source.type().startsWith("nexo")) {
                return;
            }
            handle(event.getPlayer(), event.getBlock(), source, event, event::setDropItems, false);
        } catch (RuntimeException error) {
            event.setCancelled(true);
            world.failure(BukkitWorldAccess.key(event.getBlock()), error);
            messages.send(event.getPlayer(), NoticeId.PROVIDER_NOT_READY);
        }
    }

    public void onCustomBreak(Player player, Block block, Placeable source, Cancellable event, Consumer<Boolean> setDrops) {
        handle(player, block, source, event, setDrops, true);
    }

    private void handle(Player player, Block block, Placeable source, Cancellable event,
                        Consumer<Boolean> setDrops, boolean custom) {
        if (event.isCancelled()) {
            return;
        }
        BlockKey key = BukkitWorldAccess.key(block);
        if (player.getGameMode() == GameMode.CREATIVE) {
            creative.add(new CreativeIntent(event, key));
            return;
        }
        Region region = regions.at(key);
        if (region == null) {
            return;
        }
        if (!permissions.claimsAllow(region, player, block.getLocation(), ClaimsService.Action.BREAK)) {
            event.setCancelled(true);
            messages.send(player, NoticeId.MINING_DENIED);
            return;
        }
        if (reserved.contains(key)) {
            event.setCancelled(true);
            return;
        }
        PendingRegen active = engine.at(key);
        if (active != null && !active.key().equals(key)) {
            event.setCancelled(true);
            messages.send(player, NoticeId.RECOVERY_PENDING);
            return;
        }
        RegenChain chain = active == null ? null : active.chain();
        int stage = active == null ? 0 : active.stage();
        RegenRule rule = null;
        if (active == null) {
            rule = match(region, block, source);
            if (rule == null) {
                chain = region.chains().stream().filter(c -> !c.stages().isEmpty()
                        && matches(c.stages().getFirst().original(), block, source)).findFirst().orElse(null);
            }
        }
        if (chain != null && stage >= chain.lastIndex() && !chain.lastStageBreakable()) {
            event.setCancelled(true);
            messages.send(player, NoticeId.RECOVERY_PENDING);
            return;
        }
        if (chain == null && rule == null) {
            if (!permissions.actionAllowed(region, RegionSetting.ALLOW_BLOCK_BREAK, player,
                    block.getLocation(), ClaimsService.Action.BREAK)) {
                event.setCancelled(true);
                messages.send(player, NoticeId.MINING_DENIED);
            }
            return;
        }
        if (rule != null) {
            integrations.validate(rule.replacement());
        } else if (chain != null && stage < chain.lastIndex()) {
            integrations.validate(chain.stages().get(chain.degradeTarget(stage)).original());
        }
        List<BreakIntent> planned = new ArrayList<>();
        Map<BlockKey, DropTicket> plannedDrops = new HashMap<>();
        BlockSnapshot snapshot = active == null ? world.capture(block, source) : active.snapshot();
        if (snapshot.data().keySet().stream().anyMatch(position -> reserved.contains(position)
                || engine.at(position) != null && engine.at(position) != active)) {
            event.setCancelled(true);
            return;
        }
        planned.add(new BreakIntent(event, region, rule, chain, stage, key, snapshot));
        if (rule != null && BukkitWorldAccess.STACKED.contains(block.getType().name())) {
            Material material = block.getType();
            for (int y = block.getY() + 1; y < block.getWorld().getMaxHeight(); y++) {
                Block upper = block.getWorld().getBlockAt(block.getX(), y, block.getZ());
                if (!sameStack(material, upper.getType())) {
                    break;
                }
                BlockKey upperKey = BukkitWorldAccess.key(upper);
                Region upperRegion = regions.at(upperKey);
                if (upperRegion == null || !upperRegion.id().equals(region.id())
                        || !permissions.claimsAllow(region, player, upper.getLocation(), ClaimsService.Action.BREAK)
                        || engine.at(upperKey) != null || reserved.contains(upperKey)) {
                    event.setCancelled(true);
                    return;
                }
                BlockBreakEvent check = new BlockBreakEvent(upper, player);
                if (settings.get().harvesting().notifyStackedBreaks()) {
                    synthetic.add(upperKey);
                    try {
                        Bukkit.getPluginManager().callEvent(check);
                    } finally {
                        synthetic.remove(upperKey);
                    }
                }
                if (check.isCancelled()) {
                    event.setCancelled(true);
                    return;
                }
                planned.add(new BreakIntent(event, region, rule, null, 0, upperKey,
                        world.capture(upper, integrations.identify(upper))));
                plannedDrops.put(upperKey, new DropTicket(player.getUniqueId(), region.setting(RegionSetting.AUTO_INVENTORY),
                        check.isDropItems(), tick + 2, true));
            }
        }
        for (BreakIntent intent : planned) {
            reserved.add(intent.key());
            reserved.addAll(intent.snapshot().data().keySet());
            commits.add(intent);
        }
        drops.putAll(plannedDrops);
        drops.put(key, new DropTicket(player.getUniqueId(), region.setting(RegionSetting.AUTO_INVENTORY), true, tick + 2, custom));
    }

    private RegenRule match(Region region, Block block, Placeable source) {
        return region.rules().stream().filter(rule -> matches(rule.original(), block, source))
                .max(Comparator.comparingInt(rule -> rule.original().specificity())).orElse(null);
    }

    private boolean matches(Placeable candidate, Block block, Placeable source) {
        if (!source.vanilla() && candidate.vanilla()) {
            return false;
        }
        if (!candidate.vanilla() && !candidate.type().equals(source.type())) {
            return false;
        }
        return integrations.matches(candidate, block);
    }

    private boolean sameStack(Material left, Material right) {
        return left == right || (left == Material.BAMBOO || left == Material.BAMBOO_SAPLING)
                && (right == Material.BAMBOO || right == Material.BAMBOO_SAPLING);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreativeBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            engine.cancel(BukkitWorldAccess.key(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        DropTicket ticket = drops.remove(BukkitWorldAccess.key(event.getBlock()));
        if (ticket == null || !ticket.auto() || !ticket.player().equals(event.getPlayer().getUniqueId())) {
            return;
        }
        collections.add(new DropCommit(event, ticket.player(), List.copyOf(event.getItems()), event.getBlock().getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        BlockKey key = BukkitWorldAccess.key(event.getLocation());
        if (overflowDrops.contains(key)) {
            return;
        }
        DropTicket ticket = drops.get(key);
        if (ticket == null || !ticket.spawnCapture() || ticket.expires() < tick) {
            return;
        }
        if (!ticket.drop()) {
            event.setCancelled(true);
        } else if (ticket.auto()) {
            collections.add(new DropCommit(event, ticket.player(), List.of(event.getEntity()), event.getLocation()));
        }
    }

    private void give(Player player, ItemStack item, Location at) {
        BlockKey key = BukkitWorldAccess.key(at);
        overflowDrops.add(key);
        try {
            player.getInventory().addItem(item.clone()).values().forEach(leftover -> at.getWorld().dropItemNaturally(at, leftover));
        } finally {
            overflowDrops.remove(key);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        BlockKey key = BukkitWorldAccess.key(event.getBlock());
        Region region = regions.at(key);
        if (region != null && (reserved.contains(key) || engine.at(key) != null
                || !permissions.actionAllowed(region, RegionSetting.ALLOW_BLOCK_PLACE, event.getPlayer(),
                event.getBlock().getLocation(), ClaimsService.Action.PLACE))) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), NoticeId.BUILDING_DENIED);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreativePlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            engine.cancel(BukkitWorldAccess.key(event.getBlock()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        bucket(event.getPlayer(), event.getBlock().getLocation(), event, ClaimsService.Action.PLACE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        bucket(event.getPlayer(), event.getBlock().getLocation(), event, ClaimsService.Action.BREAK);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        bucket(event.getPlayer(), event.getEntity().getLocation(), event, ClaimsService.Action.PLACE);
    }

    private void bucket(Player player, Location location, Cancellable event, ClaimsService.Action action) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Region region = regions.at(BukkitWorldAccess.key(location.getBlock()));
        if (region != null && (occupied(location.getBlock())
                || !permissions.actionAllowed(region, RegionSetting.ALLOW_BUCKET_USAGE, player, location, action))) {
            event.setCancelled(true);
            messages.send(player, NoticeId.BUCKET_BLOCKED);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL && event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        BlockKey key = BukkitWorldAccess.key(block);
        Region region = regions.at(key);
        if (region == null) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL && block.getType() == Material.FARMLAND
                && event.getPlayer().getGameMode() != GameMode.CREATIVE
                && !permissions.actionAllowed(region, RegionSetting.ALLOW_BLOCK_TRAMPLING, event.getPlayer(),
                block.getLocation(), ClaimsService.Action.TRAMPLE)) {
            event.setCancelled(true);
            messages.send(event.getPlayer(), NoticeId.SOIL_PROTECTED);
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && engine.at(key) != null) {
            messages.send(event.getPlayer(), NoticeId.RECOVERING, engine.remaining(key));
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && integrations.available("nexo")) {
            try {
                if (integrations.identify(block).type().equals("nexo")) {
                    event.setUseInteractedBlock(Event.Result.DENY);
                }
            } catch (RuntimeException error) {
                world.failure(key, error);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTrample(EntityChangeBlockEvent event) {
        if (occupied(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getBlock().getType() == Material.FARMLAND) {
            Region region = regions.at(BukkitWorldAccess.key(event.getBlock()));
            if (region != null && !region.setting(RegionSetting.ALLOW_BLOCK_TRAMPLING)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) {
        if (occupied(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> occupied(block) || occupied(block.getRelative(event.getDirection())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> occupied(block) || occupied(block.getRelative(event.getDirection())))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::occupied);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::occupied);
    }

    private boolean occupied(Block block) {
        BlockKey key = BukkitWorldAccess.key(block);
        return engine.at(key) != null || reserved.contains(key);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        long chunk = ((long) event.getChunk().getX() << 32) ^ (event.getChunk().getZ() & 0xffffffffL);
        engine.chunkLoaded(event.getWorld().getName(), chunk);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        engine.worldLoaded(event.getWorld().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        messages.forget(event.getPlayer().getUniqueId());
    }
}
