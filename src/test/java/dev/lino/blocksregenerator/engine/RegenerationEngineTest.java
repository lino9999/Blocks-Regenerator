package dev.lino.blocksregenerator.engine;

import dev.lino.blocksregenerator.model.*;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RegenerationEngineTest {
    private final MutableClock clock = new MutableClock();
    private final FakeWorld world = new FakeWorld();
    private final Region region = Region.create("mine", "world", new Bounds(-20, -64, -20, 20, 300, 20));
    private final BlockKey key = new BlockKey("world", 0, 64, 0);
    private final RegenRule rule = new RegenRule(UUID.randomUUID(), Placeable.material("DIAMOND_ORE"),
            Placeable.material("STONE"), new DelayRange(1, 1));
    private final BlockSnapshot snapshot = new BlockSnapshot(rule.original(), Map.of(key, "minecraft:diamond_ore"));
    private final RegenerationEngine engine = new RegenerationEngine(world, clock,
            id -> id.equals(region.id()) ? region : null, (r, entry) -> entry.delay().min(), 100);

    @Test
    void restoresOnceAndClearsOwnership() {
        assertTrue(engine.start(region, rule, key, snapshot));
        assertFalse(engine.start(region, rule, key, snapshot));
        assertEquals("STONE", world.blocks.get(key).id());
        clock.advance(999);
        engine.tick();
        assertEquals(0, world.restored.size());
        clock.advance(1);
        engine.tick();
        assertEquals(List.of(key), world.restored);
        assertNull(engine.at(key));
        assertEquals(0, engine.count("mine"));
        engine.tick();
        assertEquals(1, world.restored.size());
    }

    @Test
    void cancelledQueueEntryCannotRestoreAReplacementEntry() {
        engine.start(region, rule, key, snapshot);
        clock.advance(500);
        engine.cancel(key);
        engine.start(region, rule, key, snapshot);
        clock.advance(500);
        engine.tick();
        assertEquals(0, world.restored.size());
        clock.advance(500);
        engine.tick();
        assertEquals(1, world.restored.size());
    }

    @Test
    void unloadedChunkIsParkedUntilItsLoadEvent() {
        engine.start(region, rule, key, snapshot);
        world.loaded = false;
        clock.advance(1000);
        engine.tick();
        assertNotNull(engine.at(key));
        world.loaded = true;
        clock.advance(1000);
        engine.tick();
        assertEquals(0, world.restored.size());
        engine.chunkLoaded("world", key.chunk());
        engine.tick();
        assertEquals(1, world.restored.size());
    }

    @Test
    void missingProviderRetainsTimerAndRetries() {
        engine.start(region, rule, key, snapshot);
        world.available = false;
        clock.advance(1000);
        engine.tick();
        assertNotNull(engine.at(key));
        world.available = true;
        clock.advance(1000);
        engine.tick();
        assertNull(engine.at(key));
    }

    @Test
    void perTickBudgetBoundsWork() {
        engine.budget(2);
        for (int x = 0; x < 5; x++) {
            BlockKey position = new BlockKey("world", x, 64, 0);
            engine.start(region, rule, position, new BlockSnapshot(rule.original(), Map.of(position, "minecraft:diamond_ore")));
        }
        clock.advance(1000);
        engine.tick();
        assertEquals(2, world.restored.size());
        engine.tick();
        assertEquals(4, world.restored.size());
        engine.tick();
        assertEquals(5, world.restored.size());
    }

    @Test
    void companionBlockSharesOwnerAndCreativeCancellation() {
        BlockKey top = key.above();
        BlockSnapshot door = new BlockSnapshot(Placeable.material("OAK_DOOR"),
                Map.of(key, "minecraft:oak_door[half=lower]", top, "minecraft:oak_door[half=upper]"));
        engine.start(region, rule, key, door);
        assertSame(engine.at(key), engine.at(top));
        assertFalse(engine.start(region, rule, top, snapshot));
        engine.cancel(top);
        assertNull(engine.at(key));
        assertNull(engine.at(top));
        clock.advance(5000);
        engine.tick();
        assertEquals(0, world.restored.size());
    }

    @Test
    void stagedChainClimbsOneStageAtATime() {
        RegenChain chain = chain(RegenChain.Style.STAGED_PER_STAGE, false);
        assertTrue(engine.mineChain(region, chain, 0, key, snapshot));
        assertEquals("IRON_ORE", world.blocks.get(key).id());
        assertTrue(engine.mineChain(region, chain, 1, key, snapshot));
        assertEquals("STONE", world.blocks.get(key).id());
        assertFalse(engine.mineChain(region, chain, 2, key, snapshot));
        clock.advance(1000);
        engine.tick();
        assertEquals("IRON_ORE", world.blocks.get(key).id());
        assertEquals(1, engine.at(key).stage());
        clock.advance(1000);
        engine.tick();
        assertEquals("DIAMOND_ORE", world.blocks.get(key).id());
        assertNull(engine.at(key));
    }

    @Test
    void directChainRestoresOriginalInOneStep() {
        RegenChain chain = chain(RegenChain.Style.DIRECT, false);
        engine.mineChain(region, chain, 0, key, snapshot);
        engine.mineChain(region, chain, 1, key, snapshot);
        clock.advance(1000);
        engine.tick();
        assertNull(engine.at(key));
        assertEquals("DIAMOND_ORE", world.blocks.get(key).id());
    }

    @Test
    void breakableFinalStagePassesThroughMinedOutState() {
        RegenChain chain = chain(RegenChain.Style.DIRECT, true);
        engine.mineChain(region, chain, 2, key, snapshot);
        assertEquals("AIR", world.blocks.get(key).id());
        assertEquals(3, engine.at(key).stage());
        clock.advance(1000);
        engine.tick();
        assertEquals("STONE", world.blocks.get(key).id());
        assertEquals(2, engine.at(key).stage());
        clock.advance(1000);
        engine.tick();
        assertNull(engine.at(key));
    }

    @Test
    void stackSupportMustRestoreBeforeUpperBlock() {
        BlockKey upper = key.above();
        Placeable bamboo = Placeable.material("BAMBOO");
        world.support = true;
        engine.start(region, rule, upper, new BlockSnapshot(bamboo, Map.of(upper, "minecraft:bamboo")));
        engine.start(region, rule, key, new BlockSnapshot(bamboo, Map.of(key, "minecraft:bamboo")));
        clock.advance(1000);
        engine.tick();
        assertTrue(world.restored.contains(key));
        if (engine.at(upper) != null) {
            clock.advance(1000);
            engine.tick();
        }
        assertEquals(2, world.restored.size());
        assertTrue(world.restored.indexOf(key) < world.restored.indexOf(upper));
    }

    @Test
    void failedPlacementStillRetainsRecoverySnapshot() {
        world.failPlacement = true;
        assertThrows(IllegalStateException.class, () -> engine.start(region, rule, key, snapshot));
        assertNotNull(engine.at(key));
        world.failPlacement = false;
        clock.advance(1000);
        engine.tick();
        assertNull(engine.at(key));
        assertEquals(List.of(key), world.restored);
    }

    @Test
    void restoreRejectsUnloadedRegionWithoutPartialChanges() {
        engine.start(region, rule, key, snapshot);
        world.loaded = false;
        assertThrows(IllegalStateException.class, () -> engine.restoreRegion("mine"));
        assertNotNull(engine.at(key));
        assertTrue(world.restored.isEmpty());
    }

    @Test
    void zeroDelayCannotRegenerateInTheSameCommit() {
        RegenRule instant = rule.withDelay(new DelayRange(0, 0));
        engine.start(region, instant, key, snapshot);
        engine.tick();
        assertNotNull(engine.at(key));
        clock.advance(50);
        engine.tick();
        assertNull(engine.at(key));
    }

    private RegenChain chain(RegenChain.Style style, boolean lastBreakable) {
        return new RegenChain(UUID.randomUUID(), "ore chain", List.of(rule,
                new RegenRule(UUID.randomUUID(), Placeable.material("IRON_ORE"), Placeable.material("AIR"), new DelayRange(1, 1)),
                new RegenRule(UUID.randomUUID(), Placeable.material("STONE"), Placeable.material("AIR"), new DelayRange(1, 1))), style, lastBreakable);
    }

    static final class MutableClock extends Clock {
        long now = 10000;
        void advance(long millis) { now += millis; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
        @Override public long millis() { return now; }
    }

    static final class FakeWorld implements WorldAccess {
        final Map<BlockKey, Placeable> blocks = new HashMap<>();
        final List<BlockKey> restored = new ArrayList<>();
        boolean loaded = true;
        boolean available = true;
        boolean support;
        boolean failPlacement;
        @Override public boolean loaded(BlockKey key) { return loaded; }
        @Override public boolean available(Placeable placeable) { return available; }
        @Override public void place(BlockKey key, Placeable block) {
            if (failPlacement) throw new IllegalStateException("Provider failed");
            blocks.put(key, block);
        }
        @Override public void restore(BlockKey key, BlockSnapshot snapshot) { restored.add(key); blocks.put(key, snapshot.source()); }
        @Override public boolean needsSupport(BlockKey key, BlockSnapshot snapshot) { return support; }
        @Override public void failure(BlockKey key, RuntimeException error) { }
    }
}
