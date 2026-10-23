package dev.lino.blocksregenerator.storage;

import dev.lino.blocksregenerator.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LegacyImporterTest {
    @TempDir Path directory;
    private final LegacyImporter importer = new LegacyImporter();

    @Test
    void importsVersionTwoRegionAndTimerWithoutRoundingMilliseconds() throws Exception {
        String rule = UUID.randomUUID().toString();
        String regions = """
                {"version":2,"regions":{"mine":{
                  "id":"mine","worldName":"world",
                  "cornerOne":{"world":"world","x":-1.2,"y":10,"z":0},
                  "cornerTwo":{"world":"world","x":5,"y":20,"z":5},
                  "settings":{"allow-block-tramping":false,"respect-claims":true},
                  "regennableBlocks":{"%s":{
                    "type":"standard","uuid":"%s",
                    "originalPlaceable":{"type":"material","data":{"material":"DIAMOND_ORE"}},
                    "replacePlaceable":{"type":"material","data":{"material":"STONE"}},
                    "minRegenTime":5,"maxRegenTime":8}},"chains":{}}}}
                """.formatted(rule, rule);
        Files.writeString(directory.resolve("regions.json"), regions);
        Files.writeString(directory.resolve("persistence.json"), """
                {"blocks":[{"type":"standard","region":"mine","regennableBlock":"%s",
                "location":{"world":"world","x":0,"y":12,"z":0},"originalBlockData":"minecraft:diamond_ore","regenTime":1234}]}
                """.formatted(rule));
        SavedState state = importer.oreRegenerator(directory, 10000);
        Region region = state.regions().getFirst();
        assertEquals(-2, region.bounds().minX());
        assertFalse(region.setting(RegionSetting.ALLOW_BLOCK_TRAMPLING));
        assertEquals(new DelayRange(5, 8), region.rules().getFirst().delay());
        assertEquals(11234, state.pending().getFirst().dueAt());
        assertEquals(regions, Files.readString(directory.resolve("regions.json")));
    }

    @Test
    void keepsGlobalCustomDefinitionsWithoutLoadingTheirWorldOrPlugin() throws Exception {
        String rule = UUID.randomUUID().toString();
        Files.writeString(directory.resolve("regions.json"), """
                {"version":2,"regions":{"custom":{"id":"custom","worldName":"missing_world",
                "settings":{},"regennableBlocks":{"%s":{"type":"itemsadder-furniture","uuid":"%s",
                "originalPlaceable":{"type":"itemsadder-furniture","data":{"furniture-id":"example:chair"}},
                "replacePlaceable":{"type":"material","data":{"material":"AIR"}},"minRegenTime":5,"maxRegenTime":5}},"chains":{}}}}
                """.formatted(rule, rule));
        Region region = importer.oreRegenerator(directory, 0).regions().getFirst();
        assertTrue(region.global());
        assertEquals("missing_world", region.world());
        assertEquals(new Placeable("itemsadder-furniture", "example:chair"), region.rules().getFirst().original());
    }

    @Test
    void importsOldChainStyleFlagsAndSortsStageNumbers() throws Exception {
        String chain = UUID.randomUUID().toString();
        String first = legacyRule("DIAMOND_ORE");
        String second = legacyRule("STONE");
        Files.writeString(directory.resolve("regions.json"), """
                {"version":2,"regions":{"mine":{"worldName":"world","settings":{},"regennableBlocks":{},
                "chains":{"%s":{"uuid":"%s","name":"ore","steppedRegen":true,"uniformDelay":true,
                "stages":{"1":%s,"0":%s}}}}}}
                """.formatted(chain, chain, second, first));
        RegenChain imported = importer.oreRegenerator(directory, 0).regions().getFirst().chains().getFirst();
        assertEquals(RegenChain.Style.STAGED_SAME_TIMER, imported.style());
        assertEquals("DIAMOND_ORE", imported.stages().getFirst().original().id());
        assertEquals("STONE", imported.stages().getLast().original().id());
    }

    @Test
    void rejectsMalformedTimerInsteadOfSilentlyDroppingIt() throws Exception {
        Files.writeString(directory.resolve("regions.json"), "{\"version\":2,\"regions\":{}}");
        Files.writeString(directory.resolve("persistence.json"), "{\"blocks\":[{\"region\":\"missing\"}]}");
        assertThrows(IllegalArgumentException.class, () -> importer.oreRegenerator(directory, 0));
        assertTrue(Files.exists(directory.resolve("persistence.json")));
    }

    @Test
    void foreignYamlImportsEnabledRulesAndPreservesSource() throws Exception {
        Files.writeString(directory.resolve("regions.yml"), """
                Regions:
                  farm:
                    world: world
                    x1: 1
                    y1: 2
                    z1: 3
                    x2: 4
                    y2: 5
                    z2: 6
                """);
        String source = """
                Materials:
                  DIAMOND_ORE:
                    Enabled: true
                    Delay: 12
                    DelayBlock: ['STONE ; 100']
                  DIRT:
                    Enabled: false
                """;
        Files.writeString(directory.resolve("materials.yml"), source);
        Region region = importer.foreignYaml(directory, false).getFirst();
        assertEquals(1, region.rules().size());
        assertEquals(Placeable.material("STONE"), region.rules().getFirst().replacement());
        assertEquals(new DelayRange(12, 12), region.rules().getFirst().delay());
        assertEquals(source, Files.readString(directory.resolve("materials.yml")));
    }

    private String legacyRule(String material) {
        return """
                {"type":"standard","uuid":"%s","originalPlaceable":{"type":"material","data":{"material":"%s"}},"minRegenTime":5,"maxRegenTime":5}
                """.formatted(UUID.randomUUID(), material);
    }
}
