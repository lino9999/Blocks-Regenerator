package dev.lino.blocksregenerator.engine;

import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.RegenChain;
import dev.lino.blocksregenerator.model.RegenRule;
import dev.lino.blocksregenerator.model.Region;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class RegenerationEngine {
    private record Chunk(String world, long coordinates) {}
    private final WorldAccess world;
    private final Clock clock;
    private final Function<String, Region> regions;
    private final BiFunction<Region, RegenRule, Integer> delays;
    private final Map<BlockKey, PendingRegen> active = new HashMap<>();
    private final Map<BlockKey, BlockKey> owners = new HashMap<>();
    private final Map<Chunk, Set<BlockKey>> waiting = new HashMap<>();
    private final PriorityQueue<PendingRegen> queue = new PriorityQueue<>(Comparator.comparingLong(PendingRegen::dueAt));
    private int budget;
    private long revision;

    public RegenerationEngine(WorldAccess world, Clock clock, Function<String, Region> regions,
                              BiFunction<Region, RegenRule, Integer> delays, int budget) {
        this.world = world;
        this.clock = clock;
        this.regions = regions;
        this.delays = delays;
        budget(budget);
    }

    public void budget(int value) {
        budget = Math.max(1, Math.min(10_000, value));
    }

    public PendingRegen at(BlockKey key) {
        return active.get(owners.getOrDefault(key, key));
    }

    public int count(String regionId) {
        return (int) active.values().stream().filter(p -> p.regionId().equals(regionId)).count();
    }

    public Collection<PendingRegen> snapshot() {
        return List.copyOf(active.values());
    }

    public long revision() {
        return revision;
    }

    public long remaining(BlockKey key) {
        PendingRegen pending = at(key);
        return pending == null ? 0 : Math.max(0, pending.dueAt() - clock.millis());
    }

    public boolean start(Region region, RegenRule rule, BlockKey key, BlockSnapshot snapshot) {
        if (at(key) != null) {
            return false;
        }
        long due = due(region, rule);
        put(new PendingRegen(key, region.id(), rule.id(), null, 0, snapshot, rule.replacement(), due));
        world.place(key, rule.replacement());
        return true;
    }

    public boolean mineChain(Region region, RegenChain chain, int current, BlockKey key, BlockSnapshot initial) {
        if (chain.stages().isEmpty() || current < 0 || current > chain.lastIndex()) {
            return false;
        }
        if (current == chain.lastIndex() && !chain.lastStageBreakable()) {
            return false;
        }
        int next = current == chain.lastIndex() ? chain.stages().size() : chain.degradeTarget(current);
        var placeable = next > chain.lastIndex()
                ? dev.lino.blocksregenerator.model.Placeable.material("AIR") : chain.stages().get(next).original();
        long due = due(region, chain.timerStage(next));
        put(new PendingRegen(key, region.id(), null, chain, next, initial, placeable, due));
        world.place(key, placeable);
        return true;
    }

    public void resume(Collection<PendingRegen> entries) {
        for (PendingRegen entry : entries) {
            if (at(entry.key()) == null) {
                bind(entry);
                active.put(entry.key(), entry);
                queue.add(entry);
            }
        }
        revision++;
    }

    public void tick() {
        long now = clock.millis();
        int examined = 0;
        while (examined++ < budget && !queue.isEmpty() && queue.peek().dueAt() <= now) {
            PendingRegen entry = queue.remove();
            if (active.get(entry.key()) != entry) {
                continue;
            }
            if (!world.loaded(entry.key())) {
                park(entry.key());
                continue;
            }
            try {
                regenerate(entry, now);
            } catch (RuntimeException error) {
                world.failure(entry.key(), error);
                put(entry.withDueAt(now + 5000));
            }
        }
        if (queue.size() > active.size() * 2 + 1024) {
            queue.clear();
            Set<BlockKey> parked = new HashSet<>();
            waiting.values().forEach(parked::addAll);
            active.values().stream().filter(p -> !parked.contains(p.key())).forEach(queue::add);
        }
    }

    public void chunkLoaded(String worldName, long chunk) {
        Set<BlockKey> keys = waiting.remove(new Chunk(worldName, chunk));
        if (keys != null) {
            for (BlockKey key : keys) {
                PendingRegen entry = active.get(key);
                if (entry != null) {
                    queue.add(entry);
                }
            }
        }
    }

    public void worldLoaded(String worldName) {
        var chunks = waiting.keySet().stream().filter(k -> k.world.equals(worldName)).toList();
        chunks.forEach(k -> chunkLoaded(k.world, k.coordinates));
    }

    public void cancel(BlockKey key) {
        key = owners.getOrDefault(key, key);
        PendingRegen removed = active.remove(key);
        if (removed != null) {
            owners.remove(key);
            removed.snapshot().data().keySet().forEach(owners::remove);
            forgetWaiting(key);
            revision++;
        }
    }

    public void restoreRegion(String regionId) {
        for (PendingRegen entry : new ArrayList<>(active.values())) {
            if (entry.regionId().equals(regionId)) {
                if (!world.loaded(entry.key())) {
                    throw new IllegalStateException("Load every affected chunk before restoring or editing this region");
                }
            }
        }
        for (PendingRegen entry : new ArrayList<>(active.values())) {
            if (entry.regionId().equals(regionId)) {
                restore(entry);
            }
        }
    }

    public void restoreAllLoaded() {
        for (PendingRegen entry : new ArrayList<>(active.values())) {
            if (world.loaded(entry.key())) {
                try {
                    restore(entry);
                } catch (RuntimeException error) {
                    world.failure(entry.key(), error);
                }
            }
        }
    }

    private void regenerate(PendingRegen entry, long now) {
        if (!world.available(entry.snapshot().source())) {
            put(entry.withDueAt(now + 1000));
            return;
        }
        if (world.needsSupport(entry.key(), entry.snapshot()) && active.containsKey(entry.key().below())) {
            put(entry.withDueAt(now + 1000));
            return;
        }
        if (entry.chain() == null) {
            restore(entry);
            return;
        }
        RegenChain chain = entry.chain();
        int next = chain.regenTarget(entry.stage());
        if (next == 0) {
            restore(entry);
            return;
        }
        Region region = regions.apply(entry.regionId());
        if (region == null) {
            restore(entry);
            return;
        }
        var target = chain.stages().get(next).original();
        if (!world.available(target)) {
            put(entry.withDueAt(now + 1000));
            return;
        }
        world.place(entry.key(), target);
        put(entry.withStage(next, target, due(region, chain.timerStage(next))));
    }

    private long due(Region region, RegenRule rule) {
        return clock.millis() + Math.max(50, (long) delays.apply(region, rule) * 1000);
    }

    private void restore(PendingRegen entry) {
        world.restore(entry.key(), entry.snapshot());
        cancel(entry.key());
    }

    private void put(PendingRegen entry) {
        bind(entry);
        forgetWaiting(entry.key());
        active.put(entry.key(), entry);
        queue.add(entry);
        revision++;
    }

    private void bind(PendingRegen entry) {
        Set<BlockKey> positions = new HashSet<>(entry.snapshot().data().keySet());
        positions.add(entry.key());
        for (BlockKey position : positions) {
            BlockKey existing = owners.get(position);
            if (existing != null && !existing.equals(entry.key())) {
                throw new IllegalStateException("A snapshot overlaps another pending regeneration");
            }
        }
        positions.forEach(position -> owners.put(position, entry.key()));
    }

    private void park(BlockKey key) {
        waiting.computeIfAbsent(new Chunk(key.world(), key.chunk()), ignored -> new HashSet<>()).add(key);
    }

    private void forgetWaiting(BlockKey key) {
        Chunk chunk = new Chunk(key.world(), key.chunk());
        Set<BlockKey> keys = waiting.get(chunk);
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) {
                waiting.remove(chunk);
            }
        }
    }
}
