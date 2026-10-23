package dev.lino.blocksregenerator.bukkit;

import dev.lino.blocksregenerator.engine.BlockSnapshot;
import dev.lino.blocksregenerator.engine.PendingRegen;
import dev.lino.blocksregenerator.engine.WorldAccess;
import dev.lino.blocksregenerator.integration.IntegrationRegistry;
import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Placeable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class BukkitWorldAccess implements WorldAccess {
    public static final Set<String> STACKED = Set.of("SUGAR_CANE", "CACTUS", "BAMBOO", "BAMBOO_SAPLING");
    private final JavaPlugin plugin;
    private final IntegrationRegistry integrations;
    private final Map<String, Long> reported = new HashMap<>();

    public BukkitWorldAccess(JavaPlugin plugin, IntegrationRegistry integrations) {
        this.plugin = plugin;
        this.integrations = integrations;
    }

    public static BlockKey key(Block block) {
        return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static BlockKey key(Location location) {
        return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public BlockSnapshot capture(Block block, Placeable source) {
        Map<BlockKey, String> data = new LinkedHashMap<>();
        if (source.vanilla()) {
            data.put(key(block), block.getBlockData().getAsString());
            if (block.getBlockData() instanceof Bisected bisected
                    && !(bisected instanceof Stairs) && !(bisected instanceof TrapDoor)) {
                Block other = block.getRelative(0, bisected.getHalf() == Bisected.Half.TOP ? -1 : 1, 0);
                if (other.getType() == block.getType() && other.getBlockData() instanceof Bisected counterpart
                        && counterpart.getHalf() != bisected.getHalf()) {
                    data.put(key(other), other.getBlockData().getAsString());
                }
            }
        }
        return new BlockSnapshot(source, data);
    }

    public PendingRegen normalize(PendingRegen entry) {
        if (!entry.snapshot().source().vanilla() || entry.snapshot().data().size() != 1) {
            return entry;
        }
        String text = entry.snapshot().data().get(entry.key());
        if (text == null) {
            return entry;
        }
        BlockData data = Bukkit.createBlockData(text);
        if (!(data instanceof Bisected bisected) || bisected instanceof Stairs || bisected instanceof TrapDoor) {
            return entry;
        }
        BlockData other = data.clone();
        ((Bisected) other).setHalf(bisected.getHalf() == Bisected.Half.TOP ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
        Map<BlockKey, String> blocks = new LinkedHashMap<>(entry.snapshot().data());
        blocks.put(bisected.getHalf() == Bisected.Half.TOP ? entry.key().below() : entry.key().above(), other.getAsString());
        return new PendingRegen(entry.key(), entry.regionId(), entry.ruleId(), entry.chain(), entry.stage(),
                new BlockSnapshot(entry.snapshot().source(), blocks), entry.replacement(), entry.dueAt());
    }

    @Override
    public boolean loaded(BlockKey key) {
        World world = Bukkit.getWorld(key.world());
        return world != null && world.isChunkLoaded(key.x() >> 4, key.z() >> 4);
    }

    @Override
    public boolean available(Placeable placeable) {
        if (!integrations.available(placeable.type())) {
            return false;
        }
        try {
            integrations.validate(placeable);
            return true;
        } catch (IllegalArgumentException | IllegalStateException error) {
            failure(new BlockKey("content-provider", 0, 0, 0), error);
            return false;
        }
    }

    @Override
    public void place(BlockKey key, Placeable placeable) {
        integrations.place(placeable, block(key));
    }

    @Override
    public void restore(BlockKey key, BlockSnapshot snapshot) {
        if (!snapshot.source().vanilla() || snapshot.data().isEmpty()) {
            integrations.place(snapshot.source(), block(key));
            return;
        }
        Map<BlockKey, BlockData> parsed = new LinkedHashMap<>();
        snapshot.data().forEach((position, data) -> {
            if (!loaded(position)) {
                throw new IllegalStateException("A snapshot chunk is not loaded");
            }
            parsed.put(position, Bukkit.createBlockData(data));
        });
        integrations.place(Placeable.material("AIR"), block(key));
        parsed.forEach((position, data) -> block(position).setBlockData(data, false));
    }

    @Override
    public boolean needsSupport(BlockKey key, BlockSnapshot snapshot) {
        return STACKED.contains(snapshot.source().id());
    }

    @Override
    public void failure(BlockKey key, RuntimeException error) {
        String identity = key.world() + ":" + error.getMessage();
        long now = System.currentTimeMillis();
        if (now - reported.getOrDefault(identity, 0L) > 60_000) {
            plugin.getLogger().warning("Regeneration at " + key + " was postponed: " + error.getMessage());
            reported.put(identity, now);
        }
    }

    public static Block block(BlockKey key) {
        World world = Bukkit.getWorld(key.world());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
            throw new IllegalStateException("The block's world or chunk is not loaded");
        }
        if (key.y() < world.getMinHeight() || key.y() >= world.getMaxHeight()) {
            throw new IllegalArgumentException("Block Y is outside the world");
        }
        return world.getBlockAt(key.x(), key.y(), key.z());
    }
}
