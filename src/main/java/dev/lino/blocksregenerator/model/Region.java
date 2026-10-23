package dev.lino.blocksregenerator.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Region(String id, String world, Bounds bounds, int priority, String icon,
                     Map<RegionSetting, Boolean> settings, List<RegenRule> rules, List<RegenChain> chains) {
    public Region {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Region ID must contain 1-64 letters, digits, underscores or hyphens");
        }
        Objects.requireNonNull(world);
        if (world.isBlank()) {
            throw new IllegalArgumentException("World name is required");
        }
        Objects.requireNonNull(icon);
        settings = Map.copyOf(settings);
        rules = List.copyOf(rules);
        chains = List.copyOf(chains);
        if (rules.stream().map(RegenRule::id).distinct().count() != rules.size()
                || chains.stream().map(RegenChain::id).distinct().count() != chains.size()) {
            throw new IllegalArgumentException("Duplicate rule or chain ID");
        }
    }

    public static Region create(String id, String world, Bounds bounds) {
        return new Region(id, world, bounds, bounds == null ? 0 : 1, "GRASS_BLOCK", Map.of(), List.of(), List.of());
    }

    public boolean global() {
        return bounds == null;
    }

    public boolean contains(BlockKey key) {
        return world.equals(key.world()) && (global() || bounds.contains(key));
    }

    public boolean setting(RegionSetting setting) {
        return settings.getOrDefault(setting, setting.defaultValue());
    }

    public Region withSetting(RegionSetting setting, boolean value) {
        Map<RegionSetting, Boolean> updated = new EnumMap<>(RegionSetting.class);
        updated.putAll(settings);
        updated.put(setting, value);
        return new Region(id, world, bounds, priority, icon, updated, rules, chains);
    }

    public Region withRules(List<RegenRule> values) {
        return new Region(id, world, bounds, priority, icon, settings, values, chains);
    }

    public Region withChains(List<RegenChain> values) {
        return new Region(id, world, bounds, priority, icon, settings, rules, values);
    }

    public Region withIcon(String value) {
        return new Region(id, world, bounds, priority, value, settings, rules, chains);
    }

    public Region withPriority(int value) {
        return new Region(id, world, bounds, value, icon, settings, rules, chains);
    }

    public Region withBounds(Bounds value) {
        return new Region(id, world, value, priority, icon, settings, rules, chains);
    }

    public Region copyConfigurationFrom(Region source) {
        return new Region(id, world, bounds, priority, source.icon, source.settings,
                source.rules.stream().map(RegenRule::copy).toList(), source.chains.stream().map(RegenChain::copy).toList());
    }

    public RegenRule rule(UUID ruleId) {
        return rules.stream().filter(r -> r.id().equals(ruleId)).findFirst().orElse(null);
    }

    public RegenChain chain(UUID chainId) {
        return chains.stream().filter(c -> c.id().equals(chainId)).findFirst().orElse(null);
    }
}
