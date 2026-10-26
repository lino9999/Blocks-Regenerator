package dev.lino.blocksregenerator.integration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.momirealms.antigrieflib.AntiGriefLib;
import net.momirealms.antigrieflib.Flag;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

final class AntiGriefBridge {
    static final Set<String> PROVIDERS = Set.of("Kingdoms", "IridiumSkyblock", "CrashClaim", "GriefDefender",
            "HuskClaims", "BentoBox", "HuskTowns", "PlotSquared", "SuperiorSkyblock2", "FabledSkyBlock",
            "RedProtect", "Landlord", "uSkyBlock", "XClaim", "UltimateClaims", "UltimateClans",
            "PreciousStones", "Dominion", "Factions", "NoBuildPlus", "Protect", "SimpleClaimSystem", "LandClaimPlugin");
    private final JavaPlugin plugin;
    private final Function<JavaPlugin, AntiGriefLib> factory;
    private final Set<String> reported = new HashSet<>();
    private Map<String, Plugin> loaded = Map.of();
    private AntiGriefLib library;

    AntiGriefBridge(JavaPlugin plugin) {
        this(plugin, AntiGriefBridge::createLibrary);
    }

    AntiGriefBridge(JavaPlugin plugin, Function<JavaPlugin, AntiGriefLib> factory) {
        this.plugin = plugin;
        this.factory = factory;
    }

    boolean allowed(Player player, Location location, ClaimsService.Action action) {
        Map<String, Plugin> enabled = enabledProviders();
        if (enabled.isEmpty()) {
            loaded = Map.of();
            library = null;
            return true;
        }
        try {
            if (!enabled.equals(loaded)) {
                loaded = enabled;
                library = null;
                AntiGriefLib candidate = factory.apply(plugin);
                Set<String> detected = Arrays.stream(candidate.providers()).map(provider -> provider.plugin().getName())
                        .collect(Collectors.toSet());
                Set<String> missing = new HashSet<>(enabled.keySet());
                missing.removeAll(detected);
                if (!missing.isEmpty()) throw new IllegalStateException("No compatible protection adapter loaded for " + String.join(", ", missing));
                library = candidate;
            }
            if (library == null) return false;
            Flag<Location> flag = switch (action) {
                case BREAK -> Flag.BREAK;
                case TRAMPLE -> Flag.INTERACT;
                case PLACE, BUCKET -> Flag.PLACE;
            };
            return library.test(player, flag, location);
        } catch (RuntimeException | LinkageError error) {
            if (reported.add(error.getClass().getName() + ":" + error.getMessage())) {
                plugin.getLogger().severe("Additional protection checks failed. Actions are denied until the integration is repaired: " + error.getMessage());
            }
            return false;
        }
    }

    private Map<String, Plugin> enabledProviders() {
        Map<String, Plugin> enabled = new LinkedHashMap<>();
        for (String name : PROVIDERS) {
            Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
            if (dependency != null && dependency.isEnabled()) enabled.put(name, dependency);
        }
        return Map.copyOf(enabled);
    }

    private static AntiGriefLib createLibrary(JavaPlugin plugin) {
        return AntiGriefLib.builder(plugin).ignoreOP(false).bypassPermission(null).silentLogs(true).suppressErrors(false)
                .exclude(dependency -> !dependency.isEnabled() || !PROVIDERS.contains(dependency.getName())).build();
    }
}
