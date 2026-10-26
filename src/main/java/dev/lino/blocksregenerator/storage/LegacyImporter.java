package dev.lino.blocksregenerator.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lino.blocksregenerator.engine.BlockSnapshot;
import dev.lino.blocksregenerator.engine.PendingRegen;
import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Bounds;
import dev.lino.blocksregenerator.model.DelayRange;
import dev.lino.blocksregenerator.model.Placeable;
import dev.lino.blocksregenerator.model.RegenChain;
import dev.lino.blocksregenerator.model.RegenRule;
import dev.lino.blocksregenerator.model.Region;
import dev.lino.blocksregenerator.model.RegionSetting;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class LegacyImporter {
    public SavedState legacyJson(Path directory, long now) throws IOException {
        Path file = directory.resolve("regions.json");
        JsonObject root = json(file);
        JsonObject entries = root.has("regions") ? root.getAsJsonObject("regions") : root;
        List<Region> regions = new ArrayList<>();
        for (var entry : entries.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                regions.add(region(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
        }
        List<PendingRegen> timers = new ArrayList<>();
        Path persistence = directory.resolve("persistence.json");
        if (Files.exists(persistence)) {
            JsonObject pending = json(persistence);
            if (pending.has("blocks")) {
                for (JsonElement element : pending.getAsJsonArray("blocks")) {
                    timers.add(timer(element.getAsJsonObject(), regions, now));
                }
            }
        }
        return new SavedState(regions, timers);
    }

    public List<Region> foreignYaml(Path directory, boolean perRegionMaterials) throws IOException {
        Path regionFile = directory.resolve("regions.yml");
        if (!Files.isRegularFile(regionFile)) {
            throw new IOException("Cannot find " + regionFile);
        }
        YamlConfiguration yaml = loadYaml(regionFile);
        ConfigurationSection entries = yaml.getConfigurationSection("Regions");
        if (entries == null) {
            throw new IOException("The source has no Regions section");
        }
        List<Region> result = new ArrayList<>();
        for (String id : entries.getKeys(false)) {
            ConfigurationSection entry = entries.getConfigurationSection(id);
            if (entry == null) {
                throw new IOException("Invalid region: " + id);
            }
            String world = entry.getString("world");
            BlockKey first = new BlockKey(world, entry.getInt("x1"), entry.getInt("y1"), entry.getInt("z1"));
            BlockKey second = new BlockKey(world, entry.getInt("x2"), entry.getInt("y2"), entry.getInt("z2"));
            Region importedRegion = Region.create(id, world, Bounds.between(first, second));
            Path materialFile = perRegionMaterials ? directory.resolve(id).resolve("materials.yml") : directory.resolve("materials.yml");
            if (perRegionMaterials && !Files.exists(materialFile)) {
                materialFile = directory.resolve("regions").resolve(id).resolve("materials.yml");
            }
            if (perRegionMaterials && !Files.exists(materialFile)) {
                materialFile = directory.resolve("materials.yml");
            }
            ConfigurationSection materials = loadYaml(materialFile).getConfigurationSection("Materials");
            if (materials == null) {
                throw new IOException("The source has no Materials section: " + materialFile);
            }
            List<RegenRule> rules = new ArrayList<>();
            for (String material : materials.getKeys(false)) {
                ConfigurationSection rule = materials.getConfigurationSection(material);
                if (rule == null || !rule.getBoolean("Enabled")) {
                    continue;
                }
                List<String> replacements = rule.getStringList("DelayBlock");
                if (replacements.isEmpty()) {
                    throw new IOException("Missing DelayBlock for " + material);
                }
                String replacement = replacements.getFirst().split("\\s*;\\s*", 2)[0];
                int delay = rule.getInt("Delay", 5);
                rules.add(new RegenRule(UUID.randomUUID(), Placeable.material(material), Placeable.material(replacement), new DelayRange(delay, delay)));
            }
            result.add(importedRegion.withRules(rules));
        }
        return result;
    }

    private Region region(String id, JsonObject data) {
        String world = data.has("worldName") ? string(data, "worldName")
                : data.has("cornerOne") ? string(data.getAsJsonObject("cornerOne"), "world") : id;
        Bounds bounds = data.has("cornerOne")
                ? Bounds.between(location(data.getAsJsonObject("cornerOne"), world), location(data.getAsJsonObject("cornerTwo"), world)) : null;
        Map<RegionSetting, Boolean> settings = new EnumMap<>(RegionSetting.class);
        String icon = "GRASS_BLOCK";
        if (data.has("settings")) {
            JsonObject source = data.getAsJsonObject("settings");
            if (source.has("region-display-icon")) {
                icon = string(source, "region-display-icon");
            }
            for (RegionSetting setting : RegionSetting.values()) {
                String legacyKey = setting.name().toLowerCase(Locale.ROOT).replace('_', '-').replace("trampling", "tramping");
                if (source.has(legacyKey)) {
                    settings.put(setting, source.get(legacyKey).getAsBoolean());
                }
            }
        }
        List<RegenRule> rules = new ArrayList<>();
        if (data.has("regennableBlocks")) {
            for (var entry : data.getAsJsonObject("regennableBlocks").entrySet()) {
                rules.add(rule(entry.getValue().getAsJsonObject(), entry.getKey()));
            }
        }
        List<RegenChain> chains = new ArrayList<>();
        if (data.has("chains")) {
            for (var entry : data.getAsJsonObject("chains").entrySet()) {
                JsonObject chain = entry.getValue().getAsJsonObject();
                List<RegenRule> stages = chain.getAsJsonObject("stages").entrySet().stream()
                        .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getKey())))
                        .map(e -> rule(e.getValue().getAsJsonObject(), UUID.randomUUID().toString())).toList();
                String style = chain.has("regenStyle") ? string(chain, "regenStyle")
                        : chain.has("steppedRegen") && !chain.get("steppedRegen").getAsBoolean() ? "DIRECT"
                        : chain.has("uniformDelay") && chain.get("uniformDelay").getAsBoolean() ? "STAGED_SAME_TIMER" : "STAGED";
                RegenChain.Style mapped = switch (style.toUpperCase(Locale.ROOT).replace('-', '_')) {
                    case "DIRECT" -> RegenChain.Style.DIRECT;
                    case "STAGED", "STAGED_PER_STAGE" -> RegenChain.Style.STAGED_PER_STAGE;
                    case "STAGED_SAME_TIMER" -> RegenChain.Style.STAGED_SAME_TIMER;
                    default -> throw new IllegalArgumentException("Unknown legacy chain style: " + style);
                };
                chains.add(new RegenChain(UUID.fromString(chain.has("uuid") ? string(chain, "uuid") : entry.getKey()),
                        chain.has("name") ? string(chain, "name") : "Imported Chain", stages, mapped,
                        chain.has("lastStageBreakable") && chain.get("lastStageBreakable").getAsBoolean()));
            }
        }
        return new Region(id, world, bounds, bounds == null ? 0 : 1, icon, settings, rules, chains);
    }

    private RegenRule rule(JsonObject source, String id) {
        Placeable original = source.has("originalPlaceable") ? placeable(source.getAsJsonObject("originalPlaceable"))
                : Placeable.material(string(source, "originalMaterial"));
        Placeable replacement = source.has("replacePlaceable") ? placeable(source.getAsJsonObject("replacePlaceable"))
                : Placeable.material(source.has("replaceMaterial") ? string(source, "replaceMaterial") : "AIR");
        int minimum = source.has("minRegenTime") ? source.get("minRegenTime").getAsInt() : source.has("regenTime") ? source.get("regenTime").getAsInt() : 5;
        int maximum = source.has("maxRegenTime") ? source.get("maxRegenTime").getAsInt() : minimum;
        return new RegenRule(UUID.fromString(source.has("uuid") ? string(source, "uuid") : id), original, replacement, new DelayRange(minimum, maximum));
    }

    private Placeable placeable(JsonObject source) {
        String type = string(source, "type");
        JsonObject data = source.getAsJsonObject("data");
        String id = type.equals("material") || type.equals("crop") ? string(data, "material")
                : string(data, type.equals("itemsadder-furniture") ? "furniture-id" : "block-id");
        return new Placeable(type, id, data.has("age") ? data.get("age").getAsInt() : -1);
    }

    private PendingRegen timer(JsonObject data, List<Region> regions, long now) {
        String regionId = string(data, "region");
        Region region = regions.stream().filter(r -> r.id().equals(regionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Legacy timer references unknown region: " + regionId));
        BlockKey key = location(data.getAsJsonObject("location"), region.world());
        long remaining = Math.max(50, data.get("regenTime").getAsLong());
        if (remaining > 31_536_000_000L) {
            throw new IllegalArgumentException("Invalid legacy timer duration");
        }
        if (string(data, "type").equals("chain")) {
            RegenChain chain = region.chain(UUID.fromString(string(data, "chain")));
            if (chain == null) {
                throw new IllegalArgumentException("Legacy timer references unknown chain");
            }
            int stage = data.get("stageIndex").getAsInt();
            Map<BlockKey, String> blocks = data.has("initialBlockData") ? Map.of(key, string(data, "initialBlockData")) : Map.of();
            Placeable replacement = stage > chain.lastIndex() ? Placeable.material("AIR") : chain.stages().get(stage).original();
            return new PendingRegen(key, regionId, null, chain, stage, new BlockSnapshot(chain.stages().getFirst().original(), blocks), replacement, now + remaining);
        }
        RegenRule rule = region.rule(UUID.fromString(string(data, "regennableBlock")));
        if (rule == null) {
            throw new IllegalArgumentException("Legacy timer references unknown rule");
        }
        Map<BlockKey, String> blocks = data.has("originalBlockData") ? Map.of(key, string(data, "originalBlockData")) : Map.of();
        return new PendingRegen(key, regionId, rule.id(), null, 0, new BlockSnapshot(rule.original(), blocks), rule.replacement(), now + remaining);
    }

    private BlockKey location(JsonObject source, String fallbackWorld) {
        String world = source.has("world") ? string(source, "world") : fallbackWorld;
        if (!world.equals(fallbackWorld)) {
            throw new IllegalArgumentException("Legacy region positions use different worlds");
        }
        return new BlockKey(world, coordinate(source, "x"), coordinate(source, "y"), coordinate(source, "z"));
    }

    private int coordinate(JsonObject source, String name) {
        double value = source.get(name).getAsDouble();
        if (!Double.isFinite(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid block coordinate");
        }
        return (int) Math.floor(value);
    }

    private String string(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Missing legacy field: " + key);
        }
        return object.get(key).getAsString();
    }

    private JsonObject json(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > 128L * 1024 * 1024) {
            throw new IOException("Missing or oversized source file: " + path);
        }
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private YamlConfiguration loadYaml(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Cannot find " + path);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            return yaml;
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IOException("Invalid YAML: " + path, error);
        }
    }
}
