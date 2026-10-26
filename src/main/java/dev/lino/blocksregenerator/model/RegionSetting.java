package dev.lino.blocksregenerator.model;

import java.util.Arrays;

public enum RegionSetting {
    ALLOW_BLOCK_BREAK("mining.allow-unmatched", false),
    ALLOW_BLOCK_PLACE("construction.enabled", false),
    AUTO_INVENTORY("harvest.collect-items", false),
    ALLOW_BLOCK_TRAMPLING("soil.can-flatten", true),
    ALLOW_BUCKET_USAGE("liquids.enabled", false),
    USE_ADVANCED_REGEN_DELAY("timing.use-formula", false),
    RESPECT_CLAIMS("protection.check-access", true);

    private final String key;
    private final boolean defaultValue;

    RegionSetting(String key, boolean defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String key() {
        return key;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    public static RegionSetting parse(String key) {
        return Arrays.stream(values()).filter(v -> v.key.equalsIgnoreCase(key))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown region setting: " + key));
    }
}
