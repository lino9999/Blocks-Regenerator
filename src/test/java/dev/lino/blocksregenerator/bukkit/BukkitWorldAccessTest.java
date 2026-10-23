package dev.lino.blocksregenerator.bukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lino.blocksregenerator.engine.BlockSnapshot;
import dev.lino.blocksregenerator.integration.IntegrationRegistry;
import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Placeable;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BukkitWorldAccessTest {
    private BukkitWorldAccess access;
    private World world;

    @BeforeEach
    void setUp() {
        access = new BukkitWorldAccess(mock(JavaPlugin.class), mock(IntegrationRegistry.class));
        world = mock(World.class);
        when(world.getName()).thenReturn("world");
    }

    @Test
    void oppositeHalfStairsAboveRemainIndependentBlocks() {
        Stairs lower = mock(Stairs.class);
        Stairs upper = mock(Stairs.class);
        when(lower.getHalf()).thenReturn(Bisected.Half.BOTTOM);
        when(upper.getHalf()).thenReturn(Bisected.Half.TOP);
        when(lower.getAsString()).thenReturn("minecraft:oak_stairs[half=bottom]");
        Block block = block(64, Material.OAK_STAIRS, lower);
        Block other = block(65, Material.OAK_STAIRS, upper);
        when(block.getRelative(0, 1, 0)).thenReturn(other);
        BlockSnapshot snapshot = access.capture(block, Placeable.material("OAK_STAIRS"));
        assertEquals(Map.of(new BlockKey("world", 10, 64, 20), "minecraft:oak_stairs[half=bottom]"), snapshot.data());
    }

    @Test
    void oppositeHalfTrapdoorsAboveRemainIndependentBlocks() {
        TrapDoor lower = mock(TrapDoor.class);
        TrapDoor upper = mock(TrapDoor.class);
        when(lower.getHalf()).thenReturn(Bisected.Half.BOTTOM);
        when(upper.getHalf()).thenReturn(Bisected.Half.TOP);
        when(lower.getAsString()).thenReturn("minecraft:oak_trapdoor[half=bottom]");
        Block block = block(64, Material.OAK_TRAPDOOR, lower);
        Block other = block(65, Material.OAK_TRAPDOOR, upper);
        when(block.getRelative(0, 1, 0)).thenReturn(other);
        assertEquals(1, access.capture(block, Placeable.material("OAK_TRAPDOOR")).data().size());
    }

    @Test
    void doorSnapshotContainsBothMatchingHalvesWhenMinedFromTop() {
        Door top = mock(Door.class);
        Door bottom = mock(Door.class);
        when(top.getHalf()).thenReturn(Bisected.Half.TOP);
        when(bottom.getHalf()).thenReturn(Bisected.Half.BOTTOM);
        when(top.getAsString()).thenReturn("minecraft:oak_door[half=upper]");
        when(bottom.getAsString()).thenReturn("minecraft:oak_door[half=lower]");
        Block block = block(65, Material.OAK_DOOR, top);
        Block other = block(64, Material.OAK_DOOR, bottom);
        when(block.getRelative(0, -1, 0)).thenReturn(other);
        assertEquals(Map.of(new BlockKey("world", 10, 65, 20), "minecraft:oak_door[half=upper]",
                        new BlockKey("world", 10, 64, 20), "minecraft:oak_door[half=lower]"),
                access.capture(block, Placeable.material("OAK_DOOR")).data());
    }

    @Test
    void malformedDoorDoesNotCaptureUnrelatedBlockBelow() {
        Door top = mock(Door.class);
        when(top.getHalf()).thenReturn(Bisected.Half.TOP);
        when(top.getAsString()).thenReturn("minecraft:oak_door[half=upper]");
        Block block = block(65, Material.OAK_DOOR, top);
        Block other = block(64, Material.STONE, mock(BlockData.class));
        when(block.getRelative(0, -1, 0)).thenReturn(other);
        assertEquals(1, access.capture(block, Placeable.material("OAK_DOOR")).data().size());
    }

    private Block block(int y, Material material, BlockData data) {
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(10);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(20);
        when(block.getType()).thenReturn(material);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }
}
