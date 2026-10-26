package dev.lino.blocksregenerator.admin;

import dev.lino.blocksregenerator.model.Bounds;
import dev.lino.blocksregenerator.model.Region;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class RegionOutlines {
    private final AdminBackend backend;
    private final Set<UUID> enabled = new HashSet<>();
    private BukkitTask task;

    RegionOutlines(AdminBackend backend) {
        this.backend = backend;
    }

    boolean toggle(Player player) {
        if (enabled.remove(player.getUniqueId())) {
            stopIfEmpty();
            return false;
        }
        enabled.add(player.getUniqueId());
        if (task == null) {
            schedule(1L);
        }
        return true;
    }

    void remove(UUID player) {
        enabled.remove(player);
        stopIfEmpty();
    }

    private void stopIfEmpty() {
        if (enabled.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private void schedule(long ticks) {
        task = Bukkit.getScheduler().runTaskLater(backend.plugin(), () -> {
            task = null;
            for (UUID id : List.copyOf(enabled)) {
                Player player = Bukkit.getPlayer(id);
                if (player == null || !player.isOnline() || !AdminSessions.allowed(player)) {
                    enabled.remove(id);
                } else {
                    render(player);
                }
            }
            if (!enabled.isEmpty()) {
                schedule(backend.settings().visuals().outlineRefreshTicks());
            }
        }, ticks);
    }

    private void render(Player player) {
        double radius = backend.settings().visuals().outlineDistanceBlocks();
        Location viewer = player.getLocation();
        List<Region> regions = backend.regions().stream().filter(r -> !r.global() && r.world().equals(player.getWorld().getName())).toList();
        int budget = 600;
        for (Region region : regions) {
            if (budget <= 0) {
                break;
            }
            Bounds b = region.bounds();
            double[] min = {b.minX(), b.minY(), b.minZ()};
            double[] max = {(double) b.maxX() + 1, (double) b.maxY() + 1, (double) b.maxZ() + 1};
            double[] position = {viewer.getX(), viewer.getY(), viewer.getZ()};
            double distanceSquared = 0;
            for (int axis = 0; axis < 3; axis++) {
                double distance = position[axis] - Math.clamp(position[axis], min[axis], max[axis]);
                distanceSquared += distance * distance;
            }
            if (distanceSquared > radius * radius) {
                continue;
            }
            boolean overlap = regions.stream().anyMatch(other -> !other.id().equals(region.id()) && b.overlaps(other.bounds()));
            Color color = overlap ? Color.RED : region.contains(AdminCommands.key(viewer)) ? Color.LIME : Color.AQUA;
            Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
            for (int axis = 0; axis < 3 && budget > 0; axis++) {
                int a = (axis + 1) % 3;
                int c = (axis + 2) % 3;
                for (int edge = 0; edge < 4 && budget > 0; edge++) {
                    double[] point = new double[3];
                    point[a] = (edge & 1) == 0 ? min[a] : max[a];
                    point[c] = (edge & 2) == 0 ? min[c] : max[c];
                    double first = Math.max(min[axis], Math.ceil(position[axis] - radius));
                    double last = Math.min(max[axis], Math.floor(position[axis] + radius));
                    for (double coordinate = first; coordinate <= last && budget > 0; coordinate += 1) {
                        point[axis] = coordinate;
                        double dx = point[0] - viewer.getX();
                        double dy = point[1] - viewer.getY();
                        double dz = point[2] - viewer.getZ();
                        if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                            player.spawnParticle(Particle.DUST, point[0], point[1], point[2], 1, 0, 0, 0, 0, dust);
                            budget--;
                        }
                    }
                }
            }
        }
    }

    void close() {
        enabled.clear();
        stopIfEmpty();
    }
}
