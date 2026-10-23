package dev.lino.blocksregenerator.bukkit;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Messages {
    private static final Pattern DELAY = Pattern.compile("<delay:(\\d+)>");
    private final JavaPlugin plugin;
    private final Map<UUID, Map<String, Long>> throttles = new HashMap<>();
    private YamlConfiguration messages;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        throttles.clear();
    }

    public void send(Player player, String key) {
        send(player, key, 0);
    }

    public void send(Player player, String key, long remainingMillis) {
        String message = messages.getString(key, key);
        Matcher matcher = DELAY.matcher(message);
        long delay = 0;
        if (matcher.find()) {
            String value = matcher.group(1);
            delay = value.length() > 5 ? 60_000 : Math.min(60_000, Long.parseLong(value));
        }
        Map<String, Long> previous = throttles.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long now = System.currentTimeMillis();
        if (now - previous.getOrDefault(key, 0L) < delay) {
            return;
        }
        previous.put(key, now);
        long seconds = Math.max(0, (remainingMillis + 999) / 1000);
        message = DELAY.matcher(message).replaceAll("")
                .replace("%dd%", Long.toString(seconds / 86400))
                .replace("%hh%", Long.toString(seconds / 3600 % 24))
                .replace("%mm%", Long.toString(seconds / 60 % 60))
                .replace("%ss%", Long.toString(seconds % 60))
                .replace("%ms%", Long.toString(Math.max(0, remainingMillis) % 1000));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', messages.getString("prefix", "") + message));
    }

    public void forget(UUID player) {
        throttles.remove(player);
    }
}
