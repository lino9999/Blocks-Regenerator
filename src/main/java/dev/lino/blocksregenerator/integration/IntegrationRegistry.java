package dev.lino.blocksregenerator.integration;

import dev.lino.blocksregenerator.bukkit.MaterialResolver;
import dev.lino.blocksregenerator.model.Placeable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class IntegrationRegistry {
    private final JavaPlugin plugin;
    private final Listener listener = new Listener() { };
    private final Set<String> reported = new HashSet<>();
    private boolean registered;

    public IntegrationRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Placeable identify(Block block) {
        for (String type : List.of("itemsadder-furniture", "itemsadder", "oraxen-furniture", "oraxen", "nexo-furniture", "nexo")) {
            if (!available(type)) continue;
            try {
                String id = customId(type, block);
                if (id != null) return new Placeable(type, id);
            } catch (ReflectiveOperationException | LinkageError error) {
                throw failure(type, error);
            }
        }
        Material item = Crops.item(block.getType());
        if (item != null) return new Placeable("crop", item.name());
        return Placeable.material(block.getType().name());
    }

    public Placeable identifyItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.CAVE_AIR
                || item.getType() == Material.VOID_AIR) throw new IllegalArgumentException("Select a block item");
        for (String provider : List.of("itemsadder", "oraxen", "nexo")) {
            if (!available(provider)) continue;
            try {
                Object id;
                if (provider.equals("itemsadder")) {
                    Object stack = Reflect.call(itemsApi(provider), "byItemStack", item);
                    id = stack == null ? null : Reflect.call(stack, "getNamespacedID");
                } else {
                    id = Reflect.call(itemsApi(provider), provider.equals("oraxen") ? "getIdByItem" : "idFromItem", item);
                }
                if (id == null) continue;
                Placeable block = new Placeable(provider, String.valueOf(id));
                if (definition(block) != null) return block;
                Placeable furniture = new Placeable(provider + "-furniture", String.valueOf(id));
                if (definition(furniture) != null) return furniture;
                throw new IllegalArgumentException("This custom item is not a block or furniture: " + id);
            } catch (ReflectiveOperationException | LinkageError error) {
                throw failure(provider + " item lookup", error);
            }
        }
        Material material = item.getType();
        if (Crops.BLOCKS.containsKey(material)) return new Placeable("crop", material.name());
        if (!material.isBlock()) throw new IllegalArgumentException("This item cannot be used as a regenerating block");
        return Placeable.material(material.name());
    }

    public boolean matches(Placeable placeable, Block block) {
        if (!placeable.vanilla()) {
            requireAvailable(placeable.type());
            try {
                return placeable.id().equals(customId(placeable.type(), block));
            } catch (ReflectiveOperationException | LinkageError error) {
                throw failure(placeable.type(), error);
            }
        }
        Material material = material(placeable);
        if (block.getType() != material && !(material == Material.BAMBOO && block.getType() == Material.BAMBOO_SAPLING)) return false;
        return !placeable.type().equals("crop") || placeable.age() < 0
                || block.getBlockData() instanceof Ageable ageable && ageable.getAge() == placeable.age();
    }

    public void place(Placeable placeable, Block block) {
        validate(placeable);
        clearCustom(block);
        if (placeable.vanilla()) {
            BlockData data = material(placeable).createBlockData();
            if (placeable.type().equals("crop") && placeable.age() >= 0 && data instanceof Ageable ageable) {
                ageable.setAge(placeable.age());
            }
            block.setBlockData(data, false);
            return;
        }
        Location location = block.getLocation();
        try {
            Object result = switch (placeable.type()) {
                case "itemsadder" -> Reflect.call(definition(placeable), "place", location);
                case "itemsadder-furniture" -> Reflect.call(api(placeable.type()), "spawn", placeable.id(), block);
                case "oraxen", "nexo" -> Reflect.call(api(placeable.type()), "place", placeable.id(), location);
                case "oraxen-furniture", "nexo-furniture" -> Reflect.call(api(placeable.type()), "place", placeable.id(), location, location.getYaw(), BlockFace.UP);
                default -> throw new IllegalArgumentException("Unknown block type: " + placeable.type());
            };
            if (Boolean.FALSE.equals(result)) throw new IllegalStateException("Provider rejected placement of " + placeable.display());
        } catch (ReflectiveOperationException | LinkageError error) {
            throw failure(placeable.type(), error);
        }
    }

    public ItemStack displayItem(Placeable placeable) {
        validate(placeable);
        if (placeable.vanilla()) {
            Material icon = MaterialResolver.match(placeable.id());
            return new ItemStack(icon != null && icon.isItem() && !icon.isAir() ? icon : Material.BARRIER);
        }
        try {
            Object item = placeable.type().startsWith("itemsadder") ? Reflect.call(definition(placeable), "getItemStack")
                    : Reflect.call(Reflect.call(itemsApi(placeable.type()), placeable.type().startsWith("oraxen") ? "getItemById" : "itemFromId", placeable.id()), "build");
            if (!(item instanceof ItemStack stack)) throw new IllegalStateException("No display item for " + placeable.display());
            return stack.clone();
        } catch (ReflectiveOperationException | LinkageError error) {
            throw failure(placeable.type(), error);
        }
    }

    public List<Placeable> catalog(String type) {
        String normalized = type.toLowerCase(Locale.ROOT).replace('_', '-');
        if (normalized.equals("crop")) return Crops.BLOCKS.keySet().stream().sorted(Comparator.comparing(Enum::name))
                .map(material -> new Placeable("crop", material.name())).toList();
        if (normalized.equals("material")) return Arrays.stream(Material.values()).filter(Material::isBlock)
                .filter(material -> !material.isLegacy()).map(material -> Placeable.material(material.name())).toList();
        if (!available(normalized)) return List.of();
        try {
            Object ids = normalized.startsWith("itemsadder") ? Reflect.call(api(normalized), "getNamespacedIdsInRegistry")
                    : normalized.startsWith("oraxen") ? Reflect.call(api(normalized), normalized.endsWith("furniture") ? "getFurnitureIDs" : "getBlockIDs")
                    : Reflect.call(api(normalized), normalized.endsWith("furniture") ? "furnitureIDs" : "blockIDs");
            List<Placeable> result = new ArrayList<>();
            if (ids instanceof Iterable<?> iterable) {
                for (Object id : iterable) result.add(new Placeable(normalized, String.valueOf(id)));
            } else if (ids != null && ids.getClass().isArray()) {
                for (int index = 0; index < Array.getLength(ids); index++) result.add(new Placeable(normalized, String.valueOf(Array.get(ids, index))));
            }
            return result.stream().sorted(Comparator.comparing(Placeable::id)).toList();
        } catch (ReflectiveOperationException | LinkageError error) {
            throw failure(normalized, error);
        }
    }

    public boolean available(String type) {
        if (type.equals("material") || type.equals("crop")) return true;
        String name = providerName(type);
        return name != null && plugin.getServer().getPluginManager().isPluginEnabled(name);
    }

    public void validate(Placeable placeable) {
        if (placeable.vanilla()) {
            Material material = material(placeable);
            if (!material.isBlock()) throw new IllegalArgumentException("Material is not a block: " + placeable.id());
            if (placeable.type().equals("crop") && placeable.age() >= 0) {
                BlockData data = material.createBlockData();
                if (!(data instanceof Ageable ageable) || placeable.age() > ageable.getMaximumAge()) {
                    throw new IllegalArgumentException("Invalid crop age for " + placeable.id() + ": " + placeable.age());
                }
            }
            return;
        }
        requireAvailable(placeable.type());
        try {
            if (definition(placeable) == null) throw new IllegalArgumentException("Unknown custom block: " + placeable.display());
        } catch (ReflectiveOperationException | LinkageError error) {
            throw failure(placeable.type(), error);
        }
    }

    public void registerCustomBreaks(CustomBreakHandler handler) {
        if (registered) throw new IllegalStateException("Custom break handlers are already registered");
        registered = true;
        register("ItemsAdder", "dev.lone.itemsadder.api.Events.CustomBlockBreakEvent", handler, "itemsadder", false);
        register("ItemsAdder", "dev.lone.itemsadder.api.Events.FurnitureBreakEvent", handler, "itemsadder-furniture", true);
        register("Nexo", "com.nexomc.nexo.api.events.custom_block.NexoBlockBreakEvent", handler, "nexo", false);
        register("Nexo", "com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent", handler, "nexo-furniture", true);
        register("Oraxen", "io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureBreakEvent", handler, "oraxen-furniture", true);
        register("BlockHardness", "me.ogali.blockhardness.events.CustomHardnessBlockBreakEvent", handler, null, false);
    }

    private void register(String provider, String className, CustomBreakHandler handler, String type, boolean furniture) {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(provider);
        if (dependency == null || !dependency.isEnabled()) return;
        try {
            Class<? extends Event> eventType = Reflect.type(dependency, className).asSubclass(Event.class);
            if (type == null && BlockBreakEvent.class.isAssignableFrom(eventType)
                    && eventType.getMethod("getHandlerList").invoke(null) == BlockBreakEvent.getHandlerList()) return;
            plugin.getServer().getPluginManager().registerEvent(eventType, listener, EventPriority.HIGHEST, (ignored, event) -> {
                if (!(event instanceof Cancellable cancellable) || cancellable.isCancelled()) return;
                try {
                    Player player = (Player) Reflect.call(event, "getPlayer");
                    Block block;
                    if (furniture) {
                        Object entity = type.startsWith("itemsadder") ? Reflect.call(Reflect.call(event, "getFurniture"), "getEntity") : Reflect.call(event, "getBaseEntity");
                        if (!(entity instanceof Entity base)) throw new IllegalStateException("Furniture event has no base entity");
                        block = base.getLocation().getBlock();
                    } else block = (Block) Reflect.call(event, "getBlock");
                    Placeable placeable = type == null ? identify(block) : new Placeable(type, requiredId(type, block));
                    Consumer<Boolean> drops = dropControl(event);
                    handler.handle(player, block, placeable, cancellable, drops);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    cancellable.setCancelled(true);
                    report(provider + " event", error);
                }
            }, plugin, true);
        } catch (ReflectiveOperationException | LinkageError error) {
            report(provider + " event registration", error);
        }
    }

    private Consumer<Boolean> dropControl(Event event) {
        boolean supported = Arrays.stream(event.getClass().getMethods()).anyMatch(method -> method.getName().equals("setDropItems") && Arrays.equals(method.getParameterTypes(), new Class<?>[]{boolean.class}));
        if (!supported) return value -> { };
        return value -> {
            try {
                Reflect.call(event, "setDropItems", value);
            } catch (ReflectiveOperationException error) {
                throw failure(event.getClass().getSimpleName(), error);
            }
        };
    }

    private String requiredId(String type, Block block) throws ReflectiveOperationException {
        String id = customId(type, block);
        if (id == null) throw new IllegalStateException("Cannot identify " + type + " at " + block.getLocation());
        return id;
    }

    private String customId(String type, Block block) throws ReflectiveOperationException {
        Object api = api(type);
        Object definition;
        if (type.startsWith("itemsadder")) definition = Reflect.call(api, type.endsWith("furniture") ? "byAlreadySpawned" : "byAlreadyPlaced", block);
        else if (type.startsWith("oraxen")) definition = Reflect.call(api, type.endsWith("furniture") ? "getFurnitureMechanic" : "getOraxenBlock", type.endsWith("furniture") ? block : block.getLocation());
        else definition = Reflect.call(api, type.endsWith("furniture") ? "furnitureMechanic" : "customBlockMechanic", block.getLocation());
        return definition == null ? null : String.valueOf(Reflect.call(definition, type.startsWith("itemsadder") ? "getId" : "getItemID"));
    }

    private Object definition(Placeable placeable) throws ReflectiveOperationException {
        Object api = api(placeable.type());
        if (placeable.type().startsWith("itemsadder")) return Reflect.call(api, "getInstance", placeable.id());
        if (placeable.type().startsWith("nexo")) return Reflect.call(api, placeable.furniture() ? "furnitureMechanic" : "customBlockMechanic", placeable.id());
        if (placeable.furniture()) return Reflect.call(api, "getFurnitureMechanic", placeable.id());
        Object mechanic = Reflect.call(api, "getStringMechanic", placeable.id());
        return mechanic != null ? mechanic : Reflect.call(api, "getNoteBlockMechanic", placeable.id());
    }

    private void clearCustom(Block block) {
        for (String type : List.of("itemsadder-furniture", "itemsadder", "oraxen", "nexo")) {
            if (!available(type)) continue;
            try {
                if (type.startsWith("itemsadder")) {
                    Object existing = Reflect.call(api(type), type.endsWith("furniture") ? "byAlreadySpawned" : "byAlreadyPlaced", block);
                    if (existing != null) {
                        if (type.endsWith("furniture")) Reflect.call(existing, "remove", false);
                        else Reflect.call(existing, "remove");
                    }
                } else {
                    if (customId(type, block) != null) Reflect.call(api(type), "remove", block.getLocation(), null);
                    String furnitureType = type + "-furniture";
                    Location center = block.getLocation().add(0.5, 0.5, 0.5);
                    for (Entity entity : block.getWorld().getNearbyEntities(center, 0.5, 0.5, 0.5)) {
                        if (!entity.getLocation().getBlock().equals(block) || !Boolean.TRUE.equals(Reflect.call(api(furnitureType), "isFurniture", entity))) continue;
                        if (type.equals("oraxen")) Reflect.call(api(furnitureType), "remove", entity, null);
                        else Reflect.call(api(furnitureType), "remove", entity, null, null);
                    }
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                throw failure(type + " cleanup", error);
            }
        }
    }

    private Material material(Placeable placeable) {
        Material material = MaterialResolver.match(placeable.id());
        if (placeable.type().equals("crop") && material != null) material = Crops.BLOCKS.get(material);
        if (material == null) throw new IllegalArgumentException("Unknown " + placeable.type() + ": " + placeable.id());
        return material;
    }

    private Class<?> api(String type) throws ClassNotFoundException {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(providerName(type));
        if (dependency == null || !dependency.isEnabled()) throw new IllegalStateException("Provider is unavailable: " + type);
        String name = type.startsWith("itemsadder") ? "dev.lone.itemsadder.api.Custom" + (type.endsWith("furniture") ? "Furniture" : "Block")
                : type.startsWith("oraxen") ? "io.th0rgal.oraxen.api.Oraxen" + (type.endsWith("furniture") ? "Furniture" : "Blocks")
                : "com.nexomc.nexo.api.Nexo" + (type.endsWith("furniture") ? "Furniture" : "Blocks");
        return Reflect.type(dependency, name);
    }

    private Class<?> itemsApi(String type) throws ClassNotFoundException {
        String name = type.startsWith("itemsadder") ? "dev.lone.itemsadder.api.CustomStack"
                : type.startsWith("oraxen") ? "io.th0rgal.oraxen.api.OraxenItems" : "com.nexomc.nexo.api.NexoItems";
        return Reflect.type(plugin.getServer().getPluginManager().getPlugin(providerName(type)), name);
    }

    private String providerName(String type) {
        if (type.startsWith("itemsadder")) return "ItemsAdder";
        if (type.startsWith("oraxen")) return "Oraxen";
        if (type.startsWith("nexo")) return "Nexo";
        return null;
    }

    private void requireAvailable(String type) {
        if (!available(type)) throw new IllegalStateException("Required provider is unavailable: " + type);
    }

    private IllegalStateException failure(String operation, Throwable error) {
        report(operation, error);
        return new IllegalStateException("Integration failed for " + operation + ": " + error.getMessage(), error);
    }

    private void report(String operation, Throwable error) {
        if (reported.add(operation)) plugin.getLogger().warning("Integration failed for " + operation + ": " + error.getMessage());
    }
}
