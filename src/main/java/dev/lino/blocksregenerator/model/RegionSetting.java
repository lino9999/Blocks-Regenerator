package dev.lino.blocksregenerator.model;

import java.util.Arrays;

public enum RegionSetting {
    ALLOW_BLOCK_BREAK("allow-block-break", false),
    ALLOW_BLOCK_PLACE("allow-block-place", false),
    AUTO_INVENTORY("auto-inventory", false),
    ALLOW_BLOCK_TRAMPLING("allow-block-tramping", true),
    ALLOW_BUCKET_USAGE("allow-bucket-usage", false),
    USE_ADVANCED_REGEN_DELAY("use-advanced-regen-delay", false),
    RESPECT_CLAIMS("respect-claims", true);

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
        String normalized = key.replace("allow-block-trampling", "allow-block-tramping");
        return Arrays.stream(values()).filter(v -> v.key.equalsIgnoreCase(normalized) || v.name().equalsIgnoreCase(key))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown region setting: " + key));
    }
}
