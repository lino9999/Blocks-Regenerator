package dev.lino.blocksregenerator.bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerNotices {
    private final Path file;
    private final Supplier<InputStream> resource;
    private final Clock clock;
    private final Map<UUID, Map<NoticeId, Long>> sent = new HashMap<>();
    private NoticeCatalog catalog;

    public PlayerNotices(JavaPlugin plugin) {
        this(plugin.getDataFolder().toPath().resolve("locale/en.yml"), () -> plugin.getResource("locale/en.yml"), Clock.systemUTC());
    }

    public PlayerNotices(Path file, Supplier<InputStream> resource, Clock clock) {
        this.file = file;
        this.resource = resource;
        this.clock = clock;
        replace(read());
    }

    public NoticeCatalog read() {
        try {
            if (!Files.exists(file)) {
                try (InputStream input = resource.get()) {
                    if (input == null) throw new IllegalStateException("The default notice catalog is missing");
                    Files.createDirectories(file.toAbsolutePath().getParent());
                    Files.copy(input, file);
                }
            }
            if (Files.size(file) > 262_144) throw new IllegalArgumentException("The notice catalog exceeds the supported size");
            return NoticeCatalog.parse(Files.readString(file));
        } catch (IOException error) {
            throw new IllegalArgumentException("The notice catalog could not be read", error);
        }
    }

    public void replace(NoticeCatalog candidate) {
        catalog = candidate;
        sent.clear();
    }

    public void send(Player player, NoticeId id) {
        send(player, id, 0);
    }

    public void send(Player player, NoticeId id, long millis) {
        long now = clock.millis();
        Map<NoticeId, Long> previous = sent.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(NoticeId.class));
        Long last = previous.get(id);
        if (last != null && now - last < catalog.entries().get(id).cooldownMillis()) return;
        previous.put(id, now);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', catalog.render(id, millis)));
    }

    public void forget(UUID player) {
        sent.remove(player);
    }
}
