package dev.lino.blocksregenerator.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.lino.blocksregenerator.model.Placeable;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class IdentifyItemTest {
    @Test
    void recognizesCustomBlocksAndFurnitureThroughEachProviderItemApi() throws Exception {
        for (String provider : new String[]{"itemsadder", "oraxen", "nexo"}) {
            String pluginName = provider.equals("itemsadder") ? "ItemsAdder" : provider.equals("oraxen") ? "Oraxen" : "Nexo";
            Plugin dependency = mock(Plugin.class);
            when(dependency.isEnabled()).thenReturn(true);
            IntegrationRegistry registry = registry(pluginName, dependency);
            String root = provider.equals("itemsadder") ? "dev.lone.itemsadder.api."
                    : provider.equals("oraxen") ? "io.th0rgal.oraxen.api." : "com.nexomc.nexo.api.";
            String itemApi = root + (provider.equals("itemsadder") ? "CustomStack" : pluginName + "Items");
            String blockApi = root + (provider.equals("itemsadder") ? "CustomBlock" : pluginName + "Blocks");
            String furnitureApi = root + (provider.equals("itemsadder") ? "CustomFurniture" : pluginName + "Furniture");
            try (MockedStatic<Reflect> reflection = mockStatic(Reflect.class, CALLS_REAL_METHODS)) {
                reflection.when(() -> Reflect.type(dependency, itemApi)).thenReturn(ItemsApi.class);
                reflection.when(() -> Reflect.type(dependency, blockApi)).thenReturn(BlocksApi.class);
                reflection.when(() -> Reflect.type(dependency, furnitureApi)).thenReturn(FurnitureApi.class);
                assertEquals(new Placeable(provider, "example:ore"), registry.identifyItem(item(Material.DIAMOND_ORE)));
                assertEquals(new Placeable(provider + "-furniture", "example:chair"), registry.identifyItem(item(Material.OAK_PLANKS)));
                assertThrows(IllegalArgumentException.class, () -> registry.identifyItem(item(Material.PAPER)));
            }
        }
    }

    @Test
    void acceptsSeedItemsWithoutScanningCustomCatalogs() {
        IntegrationRegistry registry = registry(null, null);
        assertEquals(new Placeable("crop", "WHEAT_SEEDS"), registry.identifyItem(item(Material.WHEAT_SEEDS)));
        assertEquals(new Placeable("crop", "CARROT"), registry.identifyItem(item(Material.CARROT)));
        assertThrows(IllegalArgumentException.class, () -> registry.identifyItem(null));
        assertThrows(IllegalArgumentException.class, () -> registry.identifyItem(item(Material.AIR)));
    }

    private IntegrationRegistry registry(String provider, Plugin dependency) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        if (provider != null) {
            when(manager.getPlugin(provider)).thenReturn(dependency);
            when(manager.isPluginEnabled(provider)).thenReturn(true);
        }
        return new IntegrationRegistry(plugin);
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    public static final class ItemsApi {
        public static Stack byItemStack(ItemStack item) { return new Stack(id(item)); }
        public static String getIdByItem(ItemStack item) { return id(item); }
        public static String idFromItem(ItemStack item) { return id(item); }
        private static String id(ItemStack item) {
            return item.getType() == Material.DIAMOND_ORE ? "example:ore"
                    : item.getType() == Material.OAK_PLANKS ? "example:chair" : "example:unsupported";
        }
    }

    public record Stack(String id) {
        public String getNamespacedID() { return id; }
    }

    public static final class BlocksApi {
        public static Object getInstance(String id) { return definition(id); }
        public static Object getStringMechanic(String id) { return definition(id); }
        public static Object getNoteBlockMechanic(String id) { return definition(id); }
        public static Object customBlockMechanic(String id) { return definition(id); }
        private static Object definition(String id) { return id.equals("example:ore") ? id : null; }
    }

    public static final class FurnitureApi {
        public static Object getInstance(String id) { return definition(id); }
        public static Object getFurnitureMechanic(String id) { return definition(id); }
        public static Object furnitureMechanic(String id) { return definition(id); }
        private static Object definition(String id) { return id.equals("example:chair") ? id : null; }
    }
}
