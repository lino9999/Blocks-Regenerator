package dev.lino.blocksregenerator.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lino.blocksregenerator.engine.BlockSnapshot;
import dev.lino.blocksregenerator.engine.PendingRegen;
import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Placeable;
import dev.lino.blocksregenerator.model.RegenChain;
import dev.lino.blocksregenerator.model.Region;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StateCodec {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String encode(SavedState state, long now) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.add("regions", JSON.toJsonTree(state.regions()));
        JsonArray timers = new JsonArray();
        for (PendingRegen entry : state.pending()) {
            JsonObject timer = new JsonObject();
            timer.add("key", JSON.toJsonTree(entry.key()));
            timer.addProperty("region", entry.regionId());
            if (entry.ruleId() != null) {
                timer.addProperty("rule", entry.ruleId().toString());
            }
            if (entry.chain() != null) {
                timer.add("chain", JSON.toJsonTree(entry.chain()));
            }
            timer.addProperty("stage", entry.stage());
            timer.add("source", JSON.toJsonTree(entry.snapshot().source()));
            timer.add("replacement", JSON.toJsonTree(entry.replacement()));
            timer.addProperty("remaining-ms", Math.max(0, entry.dueAt() - now));
            JsonArray blocks = new JsonArray();
            entry.snapshot().data().forEach((key, data) -> {
                JsonObject block = new JsonObject();
                block.add("key", JSON.toJsonTree(key));
                block.addProperty("data", data);
                blocks.add(block);
            });
            timer.add("blocks", blocks);
            timers.add(timer);
        }
        root.add("timers", timers);
        return JSON.toJson(root) + "\n";
    }

    public SavedState decode(String text, long now) {
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        if (!root.has("schema") || root.get("schema").getAsInt() != 1) {
            throw new IllegalArgumentException("Unsupported data schema");
        }
        List<Region> regions = new ArrayList<>();
        HashSet<String> ids = new HashSet<>();
        for (JsonElement element : requiredArray(root, "regions")) {
            Region region = JSON.fromJson(element, Region.class);
            if (region == null || !ids.add(region.id())) {
                throw new IllegalArgumentException("Missing or duplicate region");
            }
            regions.add(region);
        }
        List<PendingRegen> timers = new ArrayList<>();
        HashSet<BlockKey> keys = new HashSet<>();
        HashSet<BlockKey> occupied = new HashSet<>();
        for (JsonElement element : requiredArray(root, "timers")) {
            JsonObject timer = element.getAsJsonObject();
            BlockKey key = JSON.fromJson(timer.get("key"), BlockKey.class);
            if (key == null || !keys.add(key)) {
                throw new IllegalArgumentException("Missing or duplicate pending location");
            }
            String regionId = timer.get("region").getAsString();
            if (!ids.contains(regionId)) {
                throw new IllegalArgumentException("Pending timer references unknown region: " + regionId);
            }
            Map<BlockKey, String> blocks = new LinkedHashMap<>();
            for (JsonElement blockElement : requiredArray(timer, "blocks")) {
                JsonObject block = blockElement.getAsJsonObject();
                BlockKey blockKey = JSON.fromJson(block.get("key"), BlockKey.class);
                if (!key.world().equals(blockKey.world()) || Math.abs((long) key.x() - blockKey.x()) > 1
                        || Math.abs((long) key.y() - blockKey.y()) > 1 || Math.abs((long) key.z() - blockKey.z()) > 1) {
                    throw new IllegalArgumentException("Snapshot contains an unrelated block");
                }
                blocks.put(blockKey, block.get("data").getAsString());
            }
            Placeable source = JSON.fromJson(timer.get("source"), Placeable.class);
            Placeable replacement = JSON.fromJson(timer.get("replacement"), Placeable.class);
            UUID rule = timer.has("rule") ? UUID.fromString(timer.get("rule").getAsString()) : null;
            RegenChain chain = timer.has("chain") ? JSON.fromJson(timer.get("chain"), RegenChain.class) : null;
            long remaining = timer.get("remaining-ms").getAsLong();
            if (remaining < 0 || remaining > 31_536_000_000L) {
                throw new IllegalArgumentException("Invalid remaining timer duration");
            }
            HashSet<BlockKey> positions = new HashSet<>(blocks.keySet());
            positions.add(key);
            for (BlockKey position : positions) {
                if (!occupied.add(position)) {
                    throw new IllegalArgumentException("Pending regeneration snapshots overlap");
                }
            }
            timers.add(new PendingRegen(key, regionId, rule, chain, timer.get("stage").getAsInt(),
                    new BlockSnapshot(source, blocks), replacement, Math.addExact(now, Math.max(50, remaining))));
        }
        return new SavedState(regions, timers);
    }

    private JsonArray requiredArray(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            throw new IllegalArgumentException("Missing array: " + key);
        }
        return root.getAsJsonArray(key);
    }
}
