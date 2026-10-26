package dev.lino.blocksregenerator.storage;

import dev.lino.blocksregenerator.engine.*;
import dev.lino.blocksregenerator.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StateStoreTest {
    @TempDir Path directory;
    private final StateCodec codec = new StateCodec();
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(10000), ZoneOffset.UTC);

    @Test
    void roundTripKeepsMillisecondsSnapshotsAndMissingWorldNames() {
        Region region = Region.create("mine", "unloaded-world", new Bounds(-1, -64, -1, 1, 320, 1));
        BlockKey key = new BlockKey(region.world(), 0, 64, 0);
        BlockSnapshot snapshot = new BlockSnapshot(Placeable.material("OAK_DOOR"),
                Map.of(key, "minecraft:oak_door[half=lower]", key.above(), "minecraft:oak_door[half=upper]"));
        RegenRule rule = RegenRule.create(snapshot.source());
        region = region.withRules(List.of(rule));
        PendingRegen pending = new PendingRegen(key, "mine", rule.id(), null, 0, snapshot, rule.replacement(), 11234);
        SavedState decoded = codec.decode(codec.encode(new SavedState(List.of(region), List.of(pending)), 10000), 50000);
        assertEquals(region, decoded.regions().getFirst());
        assertEquals(51234, decoded.pending().getFirst().dueAt());
        assertEquals(snapshot, decoded.pending().getFirst().snapshot());
    }

    @Test
    void atomicSaveRetainsPreviousValidStateAndOrdersWrites() throws Exception {
        Path file = directory.resolve("data.json");
        Region first = Region.create("first", "world", null);
        Region second = Region.create("second", "world", null);
        try (StateStore store = new StateStore(file, clock, ignored -> fail("Unexpected storage failure"))) {
            store.save(new SavedState(List.of(first), List.of()));
            store.saveBlocking(new SavedState(List.of(second), List.of()));
            assertEquals(List.of(second), store.load().regions());
            assertEquals(List.of(first), codec.decode(Files.readString(directory.resolve("data.json.bak")), 10000).regions());
        }
        assertEquals(2, Files.list(directory).count());
    }

    @Test
    void corruptInputIsNeverOverwrittenDuringLoad() throws Exception {
        Path file = directory.resolve("data.json");
        Files.writeString(file, "{ broken");
        try (StateStore store = new StateStore(file, clock, ignored -> {})) {
            assertThrows(java.io.IOException.class, store::load);
            assertEquals("{ broken", Files.readString(file));
        }
    }

    @Test
    void rejectsFutureSchemaDuplicateRegionsAndOrphanTimers() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode("{\"schema\":99,\"regions\":[],\"timers\":[]}", 0));
        Region region = Region.create("mine", "world", null);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(codec.encode(new SavedState(List.of(region, region), List.of()), 0), 0));
        BlockKey key = new BlockKey("world", 0, 0, 0);
        PendingRegen orphan = new PendingRegen(key, "missing", UUID.randomUUID(), null, 0,
                new BlockSnapshot(Placeable.material("STONE"), Map.of()), Placeable.material("AIR"), 100);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(codec.encode(new SavedState(List.of(region), List.of(orphan)), 0), 0));
    }

    @Test
    void preservesUnavailableCustomDefinitionsAndChainTimers() {
        RegenRule first = RegenRule.create(new Placeable("nexo", "ruby_ore"));
        RegenRule second = RegenRule.create(Placeable.material("STONE"));
        RegenChain chain = new RegenChain(UUID.randomUUID(), "Ruby", List.of(first, second), RegenChain.Style.STAGED_SAME_TIMER, true);
        Region region = Region.create("custom", "world", null).withChains(List.of(chain));
        BlockKey key = new BlockKey("world", 10, 64, 10);
        PendingRegen pending = new PendingRegen(key, region.id(), null, chain, 1, new BlockSnapshot(first.original(), Map.of()),
                second.original(), 15000);
        SavedState state = new SavedState(List.of(region), List.of(pending));
        SavedState decoded = codec.decode(codec.encode(state, 10000), 10000);
        assertEquals(state, decoded);
    }
}
