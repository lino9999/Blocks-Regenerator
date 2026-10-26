package dev.lino.blocksregenerator.bukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MaterialResolverTest {
    @Test
    void resolvesNewChainNameOnOlderServers() {
        Map<String, String> materials = Map.of("CHAIN", "older-chain");
        assertEquals("older-chain", MaterialResolver.resolve("IRON_CHAIN", materials::get));
        assertEquals("older-chain", MaterialResolver.resolve("minecraft:iron_chain", materials::get));
    }

    @Test
    void resolvesOldChainNameOnNewerServers() {
        Map<String, String> materials = Map.of("IRON_CHAIN", "newer-chain");
        assertEquals("newer-chain", MaterialResolver.resolve("CHAIN", materials::get));
        assertEquals("newer-chain", MaterialResolver.resolve("minecraft:chain", materials::get));
    }

    @Test
    void preservesNativeLookupAndRejectsUnknownMaterials() {
        Map<String, String> materials = Map.of("CHAIN", "native-chain", "IRON_CHAIN", "native-iron-chain");
        assertEquals("native-chain", MaterialResolver.resolve("CHAIN", materials::get));
        assertEquals("native-iron-chain", MaterialResolver.resolve("IRON_CHAIN", materials::get));
        assertNull(MaterialResolver.resolve("FUTURE_BLOCK", materials::get));
        assertNull(MaterialResolver.resolve("", materials::get));
        assertNull(MaterialResolver.resolve(null, materials::get));
    }
}
