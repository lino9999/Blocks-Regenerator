package dev.lino.blocksregenerator.integration;

import dev.lino.blocksregenerator.configuration.RuntimeSettings;
import java.util.function.Supplier;
import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClaimsService {
    public enum Action { BREAK, PLACE, TRAMPLE, BUCKET }

    private static final List<String> PROVIDERS = List.of("WorldGuard", "GriefPrevention", "Towny", "Lands", "Residence");
    private final JavaPlugin plugin;
    private final Supplier<RuntimeSettings> settings;
    private final AntiGriefBridge additional;
    private final Set<String> reported = new HashSet<>();
    private Object landsIntegration;

    public ClaimsService(JavaPlugin plugin, Supplier<RuntimeSettings> settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.additional = new AntiGriefBridge(plugin);
    }

    public boolean allowed(Player player, Location location, Action action) {
        if (!enabled() || bypass(player)) return true;
        for (String name : PROVIDERS) {
            Plugin dependency = dependency(name);
            if (dependency == null) continue;
            try {
                boolean allowed = switch (name) {
                    case "WorldGuard" -> worldGuard(dependency, player, location, action);
                    case "GriefPrevention" -> griefPrevention(dependency, player, location, action);
                    case "Towny" -> towny(dependency, player, location, action);
                    case "Lands" -> lands(dependency, player, location, action);
                    case "Residence" -> residence(dependency, player, location, action);
                    default -> throw new IllegalStateException("Unsupported protection provider: " + name);
                };
                if (!allowed) return false;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                report(name, error);
                return false;
            }
        }
        return additional.allowed(player, location, action);
    }

    public boolean grants(Player player, Location location) {
        if (!enabled()) return false;
        for (String name : PROVIDERS) {
            Plugin dependency = dependency(name);
            if (dependency == null) continue;
            try {
                boolean claimed = switch (name) {
                    case "WorldGuard" -> worldGuardMember(dependency, player, location);
                    case "GriefPrevention" -> claim(dependency, location) != null;
                    case "Towny" -> !Boolean.TRUE.equals(Reflect.call(Reflect.call(type(dependency, "com.palmergames.bukkit.towny.TownyAPI"), "getInstance"), "isWilderness", location));
                    case "Lands" -> area(dependency, location) != null;
                    case "Residence" -> residenceAt(dependency, location) != null;
                    default -> false;
                };
                if (claimed) return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                report(name, error);
                return false;
            }
        }
        return false;
    }

    private boolean worldGuard(Plugin dependency, Player player, Location location, Action action) throws ReflectiveOperationException {
        Object worldGuard = Reflect.call(type(dependency, "com.sk89q.worldguard.WorldGuard"), "getInstance");
        Object platform = Reflect.call(worldGuard, "getPlatform");
        Object localPlayer = localPlayer(dependency, player);
        Object adaptedWorld = Reflect.call(type(dependency, "com.sk89q.worldedit.bukkit.BukkitAdapter"), "adapt", location.getWorld());
        Object sessions = Reflect.call(platform, "getSessionManager");
        if (Boolean.TRUE.equals(Reflect.call(sessions, "hasBypass", localPlayer, adaptedWorld))) return true;
        Object query = Reflect.call(Reflect.call(platform, "getRegionContainer"), "createQuery");
        Object adapted = Reflect.call(type(dependency, "com.sk89q.worldedit.bukkit.BukkitAdapter"), "adapt", location);
        String flagName = action == Action.BREAK ? "BLOCK_BREAK" : action == Action.TRAMPLE ? "INTERACT" : "BLOCK_PLACE";
        Object flag = Reflect.field(type(dependency, "com.sk89q.worldguard.protection.flags.Flags"), flagName);
        Object flags = Array.newInstance(type(dependency, "com.sk89q.worldguard.protection.flags.StateFlag"), 1);
        Array.set(flags, 0, flag);
        return Boolean.TRUE.equals(Reflect.call(query, "testBuild", adapted, localPlayer, flags));
    }

    private boolean worldGuardMember(Plugin dependency, Player player, Location location) throws ReflectiveOperationException {
        Object worldGuard = Reflect.call(type(dependency, "com.sk89q.worldguard.WorldGuard"), "getInstance");
        Object query = Reflect.call(Reflect.call(Reflect.call(worldGuard, "getPlatform"), "getRegionContainer"), "createQuery");
        Object adapted = Reflect.call(type(dependency, "com.sk89q.worldedit.bukkit.BukkitAdapter"), "adapt", location);
        Object regions = Reflect.call(query, "getApplicableRegions", adapted);
        Object localPlayer = localPlayer(dependency, player);
        if (!(regions instanceof Iterable<?> iterable)) return false;
        for (Object region : iterable) if (Boolean.TRUE.equals(Reflect.call(region, "isMember", localPlayer))) return true;
        return false;
    }

    private Object localPlayer(Plugin dependency, Player player) throws ReflectiveOperationException {
        return Reflect.call(Reflect.call(type(dependency, "com.sk89q.worldguard.bukkit.WorldGuardPlugin"), "inst"), "wrapPlayer", player);
    }

    private boolean griefPrevention(Plugin dependency, Player player, Location location, Action action) throws ReflectiveOperationException {
        Object claim = claim(dependency, location);
        if (claim == null) return true;
        Object instance = Reflect.field(type(dependency, "me.ryanhamshire.GriefPrevention.GriefPrevention"), "instance");
        Object playerData = Reflect.call(Reflect.field(instance, "dataStore"), "getPlayerData", player.getUniqueId());
        if (Boolean.TRUE.equals(Reflect.field(playerData, "ignoreClaims"))) return true;
        Object permission = Reflect.field(type(dependency, "me.ryanhamshire.GriefPrevention.ClaimPermission"), "Build");
        return Reflect.call(claim, "checkPermission", player, permission, null) == null;
    }

    private Object claim(Plugin dependency, Location location) throws ReflectiveOperationException {
        Object instance = Reflect.field(type(dependency, "me.ryanhamshire.GriefPrevention.GriefPrevention"), "instance");
        return Reflect.call(Reflect.field(instance, "dataStore"), "getClaimAt", location, false, null);
    }

    private boolean towny(Plugin dependency, Player player, Location location, Action action) throws ReflectiveOperationException {
        Class<?> actionType = type(dependency, "com.palmergames.bukkit.towny.object.TownyPermission$ActionType");
        String name = action == Action.BREAK ? "DESTROY" : action == Action.TRAMPLE ? "SWITCH" : "BUILD";
        Object permission = Reflect.field(actionType, name);
        return Boolean.TRUE.equals(Reflect.call(type(dependency, "com.palmergames.bukkit.towny.utils.PlayerCacheUtil"), "getCachePermission", player, location, location.getBlock().getType(), permission));
    }

    private boolean lands(Plugin dependency, Player player, Location location, Action action) throws ReflectiveOperationException {
        integration(dependency);
        Object world = Reflect.call(landsIntegration, "getWorld", location.getWorld());
        if (world == null) return true;
        String flagName = action == Action.BREAK ? "BLOCK_BREAK" : action == Action.TRAMPLE ? "TRAMPLE_FARMLAND" : "BLOCK_PLACE";
        try {
            Object flag = Reflect.field(type(dependency, "me.angeschossen.lands.api.flags.type.Flags"), flagName);
            return Boolean.TRUE.equals(Reflect.call(world, "hasRoleFlag", player.getUniqueId(), location, flag));
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException missing) {
            Object flag = Reflect.field(type(dependency, "me.angeschossen.lands.api.flags.Flags"), flagName);
            return Boolean.TRUE.equals(Reflect.call(world, "hasFlag", player.getUniqueId(), location, flag));
        }
    }

    private Object area(Plugin dependency, Location location) throws ReflectiveOperationException {
        integration(dependency);
        return Reflect.call(landsIntegration, "getArea", location);
    }

    private void integration(Plugin dependency) throws ReflectiveOperationException {
        if (landsIntegration == null) landsIntegration = Reflect.call(type(dependency, "me.angeschossen.lands.api.LandsIntegration"), "of", plugin);
    }

    private boolean residence(Plugin dependency, Player player, Location location, Action action) throws ReflectiveOperationException {
        Object residence = residenceAt(dependency, location);
        if (residence == null) return true;
        Object permissions = Reflect.call(residence, "getPermissions");
        String flag = action == Action.BREAK ? "destroy" : action == Action.TRAMPLE ? "use" : "place";
        Class<?> flags = type(dependency, "com.bekvon.bukkit.residence.containers.Flags");
        boolean build = Boolean.TRUE.equals(Reflect.call(permissions, "playerHas", player, Reflect.field(flags, "build"), false));
        return Boolean.TRUE.equals(Reflect.call(permissions, "playerHas", player, Reflect.field(flags, flag), build));
    }

    private Object residenceAt(Plugin dependency, Location location) throws ReflectiveOperationException {
        Object instance = Reflect.call(type(dependency, "com.bekvon.bukkit.residence.Residence"), "getInstance");
        return Reflect.call(Reflect.call(instance, "getResidenceManager"), "getByLoc", location);
    }

    private boolean enabled() {
        return settings.get().protection().consultProviders();
    }

    private boolean bypass(Player player) {
        if (settings.get().protection().operatorsExempt() && player.isOp()) return true;
        String permission = settings.get().protection().exemptionNode();
        return permission != null && !permission.isBlank() && player.hasPermission(permission);
    }

    private Plugin dependency(String name) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(name);
        return dependency != null && dependency.isEnabled() ? dependency : null;
    }

    private Class<?> type(Plugin dependency, String name) throws ClassNotFoundException {
        return Reflect.type(dependency, name);
    }

    private void report(String name, Throwable error) {
        if (reported.add(name)) plugin.getLogger().severe("Protection checks for " + name + " failed. Actions are denied until the integration is repaired: " + error.getMessage());
    }
}
