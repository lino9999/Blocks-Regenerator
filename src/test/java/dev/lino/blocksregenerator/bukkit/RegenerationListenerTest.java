package dev.lino.blocksregenerator.bukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lino.blocksregenerator.engine.BlockSnapshot;
import dev.lino.blocksregenerator.engine.RegenerationEngine;
import dev.lino.blocksregenerator.integration.ClaimsService;
import dev.lino.blocksregenerator.integration.IntegrationRegistry;
import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Placeable;
import dev.lino.blocksregenerator.model.RegenRule;
import dev.lino.blocksregenerator.model.RegenChain;
import dev.lino.blocksregenerator.model.Region;
import dev.lino.blocksregenerator.model.RegionSetting;
import dev.lino.blocksregenerator.service.RegionIndex;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegenerationListenerTest {
    private final BlockKey key = new BlockKey("world", 10, 64, 20);
    private final Placeable source = Placeable.material("DIAMOND_ORE");
    private BukkitWorldAccess access;
    private IntegrationRegistry integrations;
    private RegionPermissions permissions;
    private RegionIndex regions;
    private Region region;
    private RegenRule rule;
    private RegenerationEngine engine;
    private RegenerationListener listener;
    private Player player;
    private Block block;
    private Location location;
    private BlockSnapshot snapshot;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);

        integrations = mock(IntegrationRegistry.class);
        access = mock(BukkitWorldAccess.class);
        permissions = mock(RegionPermissions.class);
        regions = new RegionIndex();
        rule = RegenRule.create(source);
        region = Region.create("mine", "world", null).withRules(List.of(rule));
        regions.put(region);
        engine = new RegenerationEngine(access, Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC), regions::get,
                (selectedRegion, selectedRule) -> 5, 20);
        listener = new RegenerationListener(plugin, regions, engine, integrations, access, permissions, mock(PlayerNotices.class), dev.lino.blocksregenerator.configuration.RuntimeSettings::defaults);
        player = mock(Player.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(key.x());
        when(block.getY()).thenReturn(key.y());
        when(block.getZ()).thenReturn(key.z());
        when(block.getType()).thenReturn(Material.DIAMOND_ORE);
        location = new Location(world, key.x(), key.y(), key.z());
        when(block.getLocation()).thenReturn(location);
        when(integrations.identify(block)).thenReturn(source);
        when(integrations.matches(source, block)).thenReturn(true);
        when(permissions.claimsAllow(region, player, location, ClaimsService.Action.BREAK)).thenReturn(true);
        snapshot = new BlockSnapshot(source, Map.of(key, "minecraft:diamond_ore"));
        when(access.capture(block, source)).thenReturn(snapshot);
    }

    @Test
    void lateCancellationPreventsRegenerationAndReleasesReservation() {
        BlockBreakEvent cancelled = new BlockBreakEvent(block, player);
        listener.onBreak(cancelled);
        assertNull(engine.at(key));
        verify(access, never()).place(any(), any());
        cancelled.setCancelled(true);
        listener.tick();
        assertNull(engine.at(key));
        verify(access, never()).place(any(), any());

        BlockBreakEvent allowed = new BlockBreakEvent(block, player);
        listener.onBreak(allowed);
        assertFalse(allowed.isCancelled());
        listener.tick();
        assertNotNull(engine.at(key));
        verify(access).place(key, rule.replacement());
    }

    @Test
    void customBreakAlsoHonorsCancellationAfterProviderCallback() {
        CustomEvent event = new CustomEvent();
        listener.onCustomBreak(player, block, source, event, ignored -> { });
        event.setCancelled(true);
        listener.tick();
        assertNull(engine.at(key));
        verify(access, never()).place(any(), any());
    }

    @Test
    void creativeFurnitureBreakCancelsPendingTimerOnlyAfterFinalApproval() {
        engine.start(region, rule, key, snapshot);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        CustomEvent event = new CustomEvent();
        listener.onCustomBreak(player, block, new Placeable("nexo-furniture", "chair"), event, ignored -> { });
        assertNotNull(engine.at(key));
        listener.tick();
        assertNull(engine.at(key));
    }

    @Test
    void lateCancelledCreativeFurnitureBreakKeepsPendingTimer() {
        engine.start(region, rule, key, snapshot);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        CustomEvent event = new CustomEvent();
        listener.onCustomBreak(player, block, new Placeable("nexo-furniture", "chair"), event, ignored -> { });
        event.setCancelled(true);
        listener.tick();
        assertNotNull(engine.at(key));
    }

    @Test
    void physicalInteractionWithNullHandStillProtectsFarmland() {
        when(block.getType()).thenReturn(Material.FARMLAND);
        when(permissions.actionAllowed(region, RegionSetting.ALLOW_BLOCK_TRAMPLING, player, location, ClaimsService.Action.TRAMPLE)).thenReturn(false);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.PHYSICAL, null, block, BlockFace.UP, null);
        assertNull(event.getHand());
        listener.onInteract(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void cancelledDropEventDoesNotGiveItems() {
        region = region.withSetting(RegionSetting.AUTO_INVENTORY, true);
        regions.put(region);
        when(permissions.claimsAllow(region, player, location, ClaimsService.Action.BREAK)).thenReturn(true);
        listener.onBreak(new BlockBreakEvent(block, player));
        Item item = mock(Item.class);
        BlockDropItemEvent drop = new BlockDropItemEvent(block, mock(BlockState.class), player, List.of(item));
        listener.onDrop(drop);
        drop.setCancelled(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            listener.tick();
        }
        verify(item, never()).remove();
        verify(player, never()).getInventory();
    }

    @Test
    void attributedDropIsCollectedOnceAfterFinalApproval() {
        region = region.withSetting(RegionSetting.AUTO_INVENTORY, true);
        regions.put(region);
        when(permissions.claimsAllow(region, player, location, ClaimsService.Action.BREAK)).thenReturn(true);
        listener.onBreak(new BlockBreakEvent(block, player));
        Item item = mock(Item.class);
        ItemStack stack = mock(ItemStack.class);
        when(stack.clone()).thenReturn(stack);
        when(item.getItemStack()).thenReturn(stack);
        when(item.isValid()).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(new java.util.HashMap<>());
        BlockDropItemEvent drop = new BlockDropItemEvent(block, mock(BlockState.class), player, List.of(item));
        listener.onDrop(drop);
        verify(item, never()).remove();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            listener.tick();
            listener.tick();
        }
        verify(item, times(1)).remove();
        verify(inventory, times(1)).addItem(any(ItemStack[].class));
    }

    @Test
    void activeChainCanAdvanceWhileOwningItsCompanionBlock() {
        RegenChain chain = new RegenChain(UUID.randomUUID(), "Paired blocks", List.of(rule,
                RegenRule.create(Placeable.material("IRON_ORE")), RegenRule.create(Placeable.material("STONE"))),
                RegenChain.Style.STAGED_PER_STAGE, false);
        region = region.withRules(List.of()).withChains(List.of(chain));
        regions.put(region);
        when(permissions.claimsAllow(region, player, location, ClaimsService.Action.BREAK)).thenReturn(true);
        BlockSnapshot paired = new BlockSnapshot(source, Map.of(key, "minecraft:diamond_ore", key.above(), "minecraft:diamond_ore"));
        engine.mineChain(region, chain, 0, key, paired);
        when(integrations.identify(block)).thenReturn(Placeable.material("IRON_ORE"));
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        listener.onBreak(event);
        assertFalse(event.isCancelled());
        listener.tick();
        assertEquals(2, engine.at(key).stage());
        assertSame(engine.at(key), engine.at(key.above()));
    }

    private static final class CustomEvent implements Cancellable {
        private boolean cancelled;

        @Override
        public boolean isCancelled() { return cancelled; }

        @Override
        public void setCancelled(boolean value) { cancelled = value; }
    }
}
