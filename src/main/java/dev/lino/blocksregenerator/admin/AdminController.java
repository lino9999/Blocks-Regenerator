package dev.lino.blocksregenerator.admin;

import dev.lino.blocksregenerator.model.Placeable;
import dev.lino.blocksregenerator.model.RegenRule;
import dev.lino.blocksregenerator.model.Region;
import dev.lino.blocksregenerator.model.RegionSetting;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class AdminController implements CommandExecutor, TabCompleter, Listener {
    private final AdminBackend backend;
    private final AdminSessions sessions;
    private final AdminCommands commands;
    private final RegionOutlines outlines;
    private final NamespacedKey wandKey;

    public AdminController(AdminBackend backend) {
        this.backend = backend;
        sessions = new AdminSessions(backend);
        commands = new AdminCommands(backend, sessions, this);
        commands.menus(new AdminMenus(backend, sessions, commands));
        outlines = new RegionOutlines(backend);
        wandKey = new NamespacedKey(backend.plugin(), "selection_wand");
        Bukkit.getPluginManager().registerEvents(this, backend.plugin());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!allowed(sender)) {
            sender.sendMessage("You do not have permission to administer BlocksRegenerator.");
            return true;
        }
        guarded(sender, () -> commands.execute(sender, args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!allowed(sender)) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        if (args.length <= 1) {
            suggestions.addAll(List.of("menu", "help", "wand", "pos1", "pos2", "position", "create", "global", "select", "list", "delete", "edit", "regenerate", "edges", "add", "reload", "eval", "import", "copy", "paste", "region", "block", "setting", "chain", "priority"));
        } else {
            String root = args[0].toLowerCase(Locale.ROOT);
            int length = args.length;
            if (root.equals("region")) {
                if (length == 2) {
                    suggestions.addAll(List.of("save", "delete", "list", "global", "edit"));
                } else if (length == 3 && args[1].equalsIgnoreCase("edit")) {
                    suggestions.addAll(List.of("block", "setting", "chain"));
                    suggestions.addAll(regionIds());
                } else if (length == 3 && args[1].equalsIgnoreCase("global")) {
                    suggestions.add("create");
                } else if (length == 4 && args[1].equalsIgnoreCase("global")) {
                    suggestions.addAll(Bukkit.getWorlds().stream().map(w -> w.getName()).toList());
                } else if (length >= 4 && args[1].equalsIgnoreCase("edit")) {
                    return onTabComplete(sender, command, alias, Arrays.copyOfRange(args, 2, length));
                } else {
                    suggestions.addAll(regionIds());
                }
            } else if (length == 2 && List.of("delete", "edit", "regenerate", "regen", "restore", "select", "copy", "paste", "priority").contains(root)) {
                suggestions.addAll(regionIds());
            } else if (root.equals("position") || root.equals("pos")) {
                suggestions.addAll(List.of("one", "two"));
            } else if (root.equals("global") && length == 2) {
                suggestions.addAll(Bukkit.getWorlds().stream().map(w -> w.getName()).toList());
            } else if (root.equals("import") || root.equals("convert")) {
                suggestions.addAll(List.of("oreregenerator", "ultimate-regen", "minexfarm-regen"));
            } else if (root.equals("copy") && length == 3) {
                suggestions.addAll(List.of("all", "blocks", "chains"));
            } else if (List.of("block", "setting", "chain").contains(root)) {
                if (length == 2) {
                    suggestions.addAll(switch (root) {
                        case "block" -> List.of("add", "remove", "delay", "replace", "age");
                        case "setting" -> List.of("get", "set");
                        default -> List.of("add", "remove", "rename", "style", "final", "stage", "delay");
                    });
                } else if (length == 3) {
                    suggestions.addAll(regionIds());
                } else if (root.equals("setting") && length == 4) {
                    suggestions.addAll(Arrays.stream(RegionSetting.values()).map(RegionSetting::key).toList());
                    suggestions.add("region-display-icon");
                } else if (root.equals("setting") && length == 5) {
                    suggestions.addAll(List.of("true", "false"));
                } else if (root.equals("block") && (length == 4 || length == 5 && args[1].equalsIgnoreCase("add"))) {
                    suggestions.addAll(Arrays.stream(Material.values()).filter(Material::isBlock).map(Material::name).toList());
                    if (length == 4) {
                        Region region = backend.region(args[2]);
                        if (region != null) {
                            suggestions.addAll(region.rules().stream().map(r -> r.id().toString()).toList());
                        }
                    }
                } else if (root.equals("chain") && length == 4) {
                    Region region = backend.region(args[2]);
                    if (region != null) {
                        suggestions.addAll(region.chains().stream().map(c -> c.id().toString()).toList());
                    }
                } else if (root.equals("chain") && length == 5 && args[1].equalsIgnoreCase("style")) {
                    suggestions.addAll(List.of("direct", "staged", "same-timer"));
                } else if (root.equals("chain") && length == 5 && args[1].equalsIgnoreCase("final")) {
                    suggestions.addAll(List.of("true", "false"));
                } else if (root.equals("chain") && length == 5 && args[1].equalsIgnoreCase("stage")) {
                    suggestions.addAll(List.of("add", "remove", "move", "delay"));
                }
            }
        }
        String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().distinct().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(input)).sorted().limit(100).toList();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getView().getTopInventory().getHolder() instanceof AdminSessions.Menu)) {
            return;
        }
        event.setCancelled(true);
        if (!AdminSessions.allowed(player) || !sessions.owns(player, event.getView().getTopInventory())) {
            Bukkit.getScheduler().runTask(backend.plugin(), player::closeInventory);
            return;
        }
        if (event.getClick().isKeyboardClick() || event.getClick().name().equals("DOUBLE_CLICK") || event.getClick().name().equals("DROP") || event.getClick().name().equals("CONTROL_DROP")) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        Bukkit.getScheduler().runTask(backend.plugin(), () -> {
            if (player.isOnline() && AdminSessions.allowed(player) && player.getOpenInventory().getTopInventory() == top
                    && sessions.owns(player, top)) {
                guarded(player, () -> sessions.clicked(player, event));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AdminSessions.Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sessions.closed(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Object pending = sessions.promptToken(event.getPlayer().getUniqueId());
        if (pending == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String input = event.getMessage();
        Bukkit.getScheduler().runTask(backend.plugin(), () -> guarded(player, () -> sessions.input(player, input, pending)));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        boolean wand = isWand(event.getItem());
        if (!wand && !sessions.manual(player)) {
            return;
        }
        event.setCancelled(true);
        if (!AdminSessions.allowed(player)) {
            sessions.clear(player);
            return;
        }
        guarded(player, () -> {
            if (wand) {
                if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    sessions.select(player, AdminCommands.key(event.getClickedBlock().getLocation()), event.getAction() == Action.LEFT_CLICK_BLOCK);
                }
                return;
            }
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && player.isSneaking()) {
                sessions.manual(player, false);
                player.sendMessage("Manual add mode disabled.");
            } else if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                Region region = backend.regionAt(AdminCommands.key(event.getClickedBlock().getLocation()));
                if (region == null) {
                    throw new IllegalArgumentException("This block is outside a regeneration region.");
                }
                Placeable placeable = backend.integrations().identify(event.getClickedBlock());
                if (placeable.type().equals("crop")) {
                    placeable = new Placeable("crop", placeable.id(), -1);
                }
                RegenRule rule = commands.defaultRule(placeable);
                commands.addRule(region.id(), rule);
                sessions.manual(player, false);
                player.sendMessage("Added " + placeable.display() + " to region " + region.id() + ". Manual add mode disabled.");
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.clear(event.getPlayer());
        outlines.remove(event.getPlayer().getUniqueId());
    }

    void giveWand(Player player) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("BlocksRegenerator Selection Wand");
        meta.setLore(List.of("Left-click: corner one", "Right-click: corner two", "Save with /br create <region>"));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        if (player.getInventory().firstEmpty() < 0) {
            throw new IllegalArgumentException("Free an inventory slot before requesting a wand.");
        }
        player.getInventory().addItem(item);
        player.sendMessage("Selection wand added to your inventory.");
    }

    void toggleOutlines(Player player) {
        player.sendMessage(outlines.toggle(player) ? "Region outlines enabled. Green: inside; red: overlap; aqua: outside." : "Region outlines disabled.");
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private static boolean allowed(CommandSender sender) {
        return sender.hasPermission(AdminSessions.PERMISSION) || sender.hasPermission("oreregenerator.admin");
    }

    private List<String> regionIds() {
        return backend.regions().stream().map(Region::id).sorted().toList();
    }

    private void guarded(CommandSender sender, Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(exception.getMessage() == null ? "Invalid input." : exception.getMessage());
        } catch (RuntimeException exception) {
            sender.sendMessage("The operation failed. Check the server log for details.");
            backend.plugin().getLogger().log(Level.SEVERE, "Administration operation failed", exception);
        }
    }

    public void close() {
        sessions.close();
        outlines.close();
        HandlerList.unregisterAll(this);
    }
}
