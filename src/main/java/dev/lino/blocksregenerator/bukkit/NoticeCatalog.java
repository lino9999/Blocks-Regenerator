package dev.lino.blocksregenerator.bukkit;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public record NoticeCatalog(String badge, Map<NoticeId, Template> entries) {
    public record Template(String text, long cooldownMillis) {
        public Template {
            if (text == null || text.isBlank() || text.length() > 4096 || cooldownMillis < 0 || cooldownMillis > 60_000) {
                throw new IllegalArgumentException("Invalid notice text or cooldown");
            }
        }
    }

    public NoticeCatalog {
        if (badge == null || badge.length() > 256 || !entries.keySet().equals(Set.of(NoticeId.values()))) {
            throw new IllegalArgumentException("The notice catalog is incomplete");
        }
        entries = Map.copyOf(entries);
    }

    public static NoticeCatalog parse(String text) {
        if (text == null || text.length() > 262_144) throw new IllegalArgumentException("Invalid notice document size");
        org.yaml.snakeyaml.LoaderOptions options = new org.yaml.snakeyaml.LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setCodePointLimit(262_144);
        options.setNestingDepthLimit(16);
        options.setMaxAliasesForCollections(16);
        new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(options)).load(text);
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(text);
        } catch (InvalidConfigurationException error) {
            throw new IllegalArgumentException("The notice catalog is not valid YAML", error);
        }
        if (!(yaml.get("format") instanceof Number version) || version.doubleValue() != 1
                || !yaml.getKeys(false).equals(Set.of("format", "badge", "notices")) || !(yaml.get("badge") instanceof String badge)) {
            throw new IllegalArgumentException("Expected notice format 1 with badge and notices");
        }
        ConfigurationSection notices = yaml.getConfigurationSection("notices");
        Set<String> names = Arrays.stream(NoticeId.values()).map(NoticeId::key).collect(Collectors.toSet());
        if (notices == null || !notices.getKeys(false).equals(names)) {
            throw new IllegalArgumentException("The notice catalog has missing or unknown entries");
        }
        Map<NoticeId, Template> entries = new EnumMap<>(NoticeId.class);
        for (NoticeId id : NoticeId.values()) {
            ConfigurationSection entry = notices.getConfigurationSection(id.key());
            if (entry == null || !entry.getKeys(false).equals(Set.of("text", "cooldown-ms"))
                    || !(entry.get("text") instanceof String line) || !(entry.get("cooldown-ms") instanceof Number delay)
                    || !Double.isFinite(delay.doubleValue()) || delay.doubleValue() != delay.longValue()) {
                throw new IllegalArgumentException("Invalid notice entry: " + id.key());
            }
            entries.put(id, new Template(line, delay.longValue()));
        }
        return new NoticeCatalog(badge, entries);
    }

    public String render(NoticeId id, long millis) {
        long duration = Math.max(0, millis);
        long seconds = duration / 1000 + (duration % 1000 == 0 ? 0 : 1);
        String remaining = seconds >= 86400 ? seconds / 86400 + "d " + seconds / 3600 % 24 + "h"
                : seconds >= 3600 ? seconds / 3600 + "h " + seconds / 60 % 60 + "m"
                : seconds >= 60 ? seconds / 60 + "m " + seconds % 60 + "s" : seconds + "s";
        return badge + entries.get(id).text().replace("{remaining}", remaining)
                .replace("{days}", Long.toString(seconds / 86400))
                .replace("{hours}", Long.toString(seconds / 3600 % 24))
                .replace("{minutes}", Long.toString(seconds / 60 % 60))
                .replace("{seconds}", Long.toString(seconds % 60))
                .replace("{millis}", Long.toString(duration % 1000));
    }
}
