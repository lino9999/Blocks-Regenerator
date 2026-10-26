package dev.lino.blocksregenerator.bukkit;

public enum NoticeId {
    MINING_DENIED("mining-denied"),
    BUILDING_DENIED("building-denied"),
    BUCKET_BLOCKED("bucket-blocked"),
    SOIL_PROTECTED("soil-protected"),
    RECOVERING("recovering"),
    PROVIDER_NOT_READY("provider-not-ready"),
    RECOVERY_PENDING("recovery-pending");

    private final String key;

    NoticeId(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
