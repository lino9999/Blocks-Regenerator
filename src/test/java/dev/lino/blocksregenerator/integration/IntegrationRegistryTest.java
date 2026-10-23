package dev.lino.blocksregenerator.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lino.blocksregenerator.model.Placeable;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntegrationRegistryTest {
    private IntegrationRegistry registry;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        registry = new IntegrationRegistry(plugin);
    }

    @Test
    void matchesCropAgeAndMapsSeedItemToBlock() {
        Block block = mock(Block.class);
        Ageable data = mock(Ageable.class);
        when(block.getType()).thenReturn(Material.WHEAT);
        when(block.getBlockData()).thenReturn(data);
        when(data.getAge()).thenReturn(7);
        assertTrue(registry.matches(new Placeable("crop", "WHEAT_SEEDS", 7), block));
        assertFalse(registry.matches(new Placeable("crop", "WHEAT_SEEDS", 6), block));
        assertTrue(registry.matches(new Placeable("crop", "WHEAT_SEEDS"), block));
        assertEquals(new Placeable("crop", "WHEAT_SEEDS"), registry.identify(block));
    }

    @Test
    void matchesBambooSaplingAsBamboo() {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.BAMBOO_SAPLING);
        assertTrue(registry.matches(Placeable.material("BAMBOO"), block));
    }

    @Test
    void unavailableProviderCannotBecomeVanillaBackingBlock() {
        Placeable custom = new Placeable("itemsadder", "example:ore");
        assertFalse(registry.available(custom.type()));
        assertThrows(IllegalStateException.class, () -> registry.validate(custom));
        assertThrows(IllegalStateException.class, () -> registry.matches(custom, mock(Block.class)));
        assertTrue(registry.catalog("itemsadder").isEmpty());
    }

    @Test
    void rejectsUnknownMaterialAndCrop() {
        assertThrows(IllegalArgumentException.class, () -> registry.validate(Placeable.material("INVALID_MATERIAL")));
        assertThrows(IllegalArgumentException.class, () -> registry.validate(new Placeable("crop", "STONE")));
    }
}
