package dev.lino.blocksregenerator.service;

import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Region;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RegionIndex {
    private static final int MAX_INDEXED_CHUNKS = 4096;
    private static final Comparator<Region> ORDER = Comparator.comparingInt(Region::priority).reversed()
            .thenComparing(Region::id);
    private final Map<String, Region> regions = new HashMap<>();
    private final Map<String, Map<Long, List<Region>>> chunks = new HashMap<>();
    private final Map<String, List<Region>> broad = new HashMap<>();

    public void replaceAll(Collection<Region> values) {
        Map<String, Region> unique = new HashMap<>();
        for (Region region : values) {
            if (unique.putIfAbsent(region.id(), region) != null) {
                throw new IllegalArgumentException("Duplicate region ID: " + region.id());
            }
        }
        regions.clear();
        regions.putAll(unique);
        rebuild();
    }

    public void put(Region region) {
        regions.put(region.id(), region);
        rebuild();
    }

    public Region remove(String id) {
        Region removed = regions.remove(id);
        rebuild();
        return removed;
    }

    public Region get(String id) {
        return regions.get(id);
    }

    public List<Region> all() {
        return regions.values().stream().sorted(Comparator.comparing(Region::id)).toList();
    }

    public Region at(BlockKey key) {
        Region result = null;
        for (Region region : chunks.getOrDefault(key.world(), Map.of()).getOrDefault(key.chunk(), List.of())) {
            if (region.contains(key) && (result == null || ORDER.compare(region, result) < 0)) {
                result = region;
            }
        }
        for (Region region : broad.getOrDefault(key.world(), List.of())) {
            if (region.contains(key) && (result == null || ORDER.compare(region, result) < 0)) {
                result = region;
            }
        }
        return result;
    }

    private void rebuild() {
        chunks.clear();
        broad.clear();
        for (Region region : regions.values()) {
            if (region.global()) {
                broad.computeIfAbsent(region.world(), ignored -> new ArrayList<>()).add(region);
                continue;
            }
            var bounds = region.bounds();
            long width = (long) (bounds.maxX() >> 4) - (bounds.minX() >> 4) + 1;
            long depth = (long) (bounds.maxZ() >> 4) - (bounds.minZ() >> 4) + 1;
            if (width * depth > MAX_INDEXED_CHUNKS) {
                broad.computeIfAbsent(region.world(), ignored -> new ArrayList<>()).add(region);
                continue;
            }
            var world = chunks.computeIfAbsent(region.world(), ignored -> new HashMap<>());
            for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
                for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
                    long chunk = ((long) x << 32) ^ (z & 0xffffffffL);
                    world.computeIfAbsent(chunk, ignored -> new ArrayList<>()).add(region);
                }
            }
        }
    }
}
