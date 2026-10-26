package dev.lino.blocksregenerator.service;

import dev.lino.blocksregenerator.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RegionIndexTest {
    private final RegionIndex index = new RegionIndex();

    @Test
    void localWinsOverGlobalAndTiesAreDeterministic() {
        Region global = Region.create("world", "world", null);
        Region local = Region.create("beta", "world", new Bounds(-1, 1, -1, 1, 100, 1));
        Region alpha = Region.create("alpha", "world", local.bounds());
        index.replaceAll(List.of(global, local, alpha));
        assertEquals("alpha", index.at(new BlockKey("world", 0, 64, 0)).id());
        assertEquals("world", index.at(new BlockKey("world", 2, 64, 0)).id());
        assertNull(index.at(new BlockKey("nether", 0, 64, 0)));
    }

    @Test
    void inclusiveBoundsAndNegativeChunkCoordinatesAreCorrect() {
        Region region = Region.create("mine", "world", new Bounds(-17, -64, -17, -1, 320, -1));
        index.put(region);
        assertEquals(region, index.at(new BlockKey("world", -17, -64, -17)));
        assertEquals(region, index.at(new BlockKey("world", -1, 320, -1)));
        assertNull(index.at(new BlockKey("world", 0, 64, -1)));
        assertNull(index.at(new BlockKey("world", -1, 321, -1)));
    }

    @Test
    void enormousRegionsUseFallbackWithoutAllocatingEveryChunk() {
        Region broad = Region.create("huge", "world", new Bounds(-30_000_000, -64, -30_000_000, 30_000_000, 320, 30_000_000));
        index.put(broad);
        assertEquals(broad, index.at(new BlockKey("world", 29_999_999, 1, -29_999_999)));
        index.remove("huge");
        assertNull(index.at(new BlockKey("world", 0, 0, 0)));
    }

    @Test
    void replacingRegionRemovesOldSpatialEntries() {
        Region first = Region.create("mine", "world", new Bounds(0, 0, 0, 1, 1, 1));
        index.put(first);
        index.put(first.withBounds(new Bounds(100, 0, 100, 101, 1, 101)));
        assertNull(index.at(new BlockKey("world", 0, 0, 0)));
        assertNotNull(index.at(new BlockKey("world", 100, 0, 100)));
    }

    @Test
    void invalidRangesAndDuplicateIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DelayRange(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> new DelayRange(6, 5));
        Region region = Region.create("mine", "world", null);
        assertThrows(IllegalArgumentException.class, () -> index.replaceAll(List.of(region, region)));
        assertThrows(IllegalArgumentException.class, () -> Bounds.between(new BlockKey("world", 0, 0, 0), new BlockKey("nether", 0, 0, 0)));
    }
}
