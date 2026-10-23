package dev.lino.blocksregenerator.admin;

import dev.lino.blocksregenerator.bukkit.MaterialResolver;
import dev.lino.blocksregenerator.model.BlockKey;
import dev.lino.blocksregenerator.model.Bounds;
import dev.lino.blocksregenerator.model.DelayRange;
import dev.lino.blocksregenerator.model.Placeable;
import dev.lino.blocksregenerator.model.RegenChain;
import dev.lino.blocksregenerator.model.RegenRule;
import dev.lino.blocksregenerator.model.Region;
import dev.lino.blocksregenerator.model.RegionSetting;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.UnaryOperator;

final class AdminCommands {
    private final AdminBackend backend;
    private final AdminSessions sessions;
    private final AdminController controller;
    private AdminMenus menus;

    AdminCommands(AdminBackend backend, AdminSessions sessions, AdminController controller) {
        this.backend = backend;
        this.sessions = sessions;
        this.controller = controller;
    }

    void menus(AdminMenus menus) {
        this.menus = menus;
    }

    void execute(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "menu" -> menus.regions(player(sender), 0, "");
            case "help" -> help(sender);
            case "wand" -> controller.giveWand(player(sender));
            case "pos1" -> position(player(sender), true);
            case "pos2" -> position(player(sender), false);
            case "position", "pos" -> {
                require(args, 2, "position <one|two>");
                if (!List.of("one", "1", "two", "2").contains(args[1].toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("Choose position one or two.");
                }
                position(player(sender), args[1].equalsIgnoreCase("one") || args[1].equals("1"));
            }
            case "create", "save" -> {
                require(args, 2, "create <region>");
                create(player(sender), args[1]);
            }
            case "global" -> {
                require(args, 2, "global <world> [region]");
                global(args[1], args.length > 2 ? args[2] : args[1]);
                sender.sendMessage("Global region created.");
            }
            case "select" -> {
                require(args, 2, "select <region>");
                Region region = region(args[1]);
                if (region.global()) {
                    throw new IllegalArgumentException("Global regions have no selection bounds.");
                }
                Player player = player(sender);
                Bounds bounds = region.bounds();
                sessions.select(player, new BlockKey(region.world(), bounds.minX(), bounds.minY(), bounds.minZ()), true);
                sessions.select(player, new BlockKey(region.world(), bounds.maxX(), bounds.maxY(), bounds.maxZ()), false);
            }
            case "list" -> sender.sendMessage("Regions: " + String.join(", ", backend.regions().stream().map(Region::id).sorted().toList()));
            case "delete", "remove" -> {
                require(args, 2, "delete <region>");
                region(args[1]);
                backend.delete(args[1]);
                sender.sendMessage("Region deleted.");
            }
            case "regenerate", "restore", "regen" -> {
                require(args, 2, "regenerate <region>");
                backend.restore(region(args[1]).id());
                sender.sendMessage("Active blocks restored.");
            }
            case "edit" -> {
                require(args, 2, "edit <region>");
                menus.region(player(sender), region(args[1]).id());
            }
            case "add" -> {
                Player player = player(sender);
                sessions.manual(player, true);
                player.sendMessage("Manual add mode enabled. Left-click a block to add its type; sneak + right-click to exit.");
            }
            case "edges", "borders", "visualize" -> controller.toggleOutlines(player(sender));
            case "reload", "rl" -> {
                backend.reload();
                sender.sendMessage("BlocksRegenerator reloaded.");
            }
            case "eval" -> {
                require(args, 2, "eval <expression>");
                sender.sendMessage("Result: " + backend.evaluate(String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
            }
            case "import", "convert" -> {
                require(args, 2, "import <oreregenerator|ultimate-regen|minexfarm-regen>");
                backend.importData(args[1]);
                sender.sendMessage("Import completed.");
            }
            case "copy" -> {
                require(args, 2, "copy <region> [all|blocks|chains]");
                copy(player(sender), args[1], args.length > 2 ? args[2] : "all");
            }
            case "paste" -> {
                require(args, 2, "paste <region>");
                paste(player(sender), args[1]);
            }
            case "region" -> regionCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            case "block" -> blockCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            case "setting" -> settingCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            case "chain" -> chainCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            case "priority" -> {
                require(args, 3, "priority <region> <number>");
                backend.save(region(args[1]).withPriority(number(args[2])));
                sender.sendMessage("Region priority saved.");
            }
            default -> throw new IllegalArgumentException("Unknown command. Use /blocksregenerator help.");
        }
    }

    private void regionCommand(CommandSender sender, String[] args) {
        require(args, 1, "region <save|delete|list|global|edit>");
        if (args[0].equalsIgnoreCase("global")) {
            require(args, 3, "region global create <world>");
            if (!args[1].equalsIgnoreCase("create")) {
                throw new IllegalArgumentException("Usage: /blocksregenerator region global create <world>");
            }
            global(args[2], args[2]);
            sender.sendMessage("Global region created.");
        } else if (args[0].equalsIgnoreCase("edit") && args.length > 1
                && List.of("block", "setting", "chain").contains(args[1].toLowerCase(Locale.ROOT))) {
            execute(sender, Arrays.copyOfRange(args, 1, args.length));
        } else {
            execute(sender, args);
        }
    }

    private void blockCommand(CommandSender sender, String[] args) {
        require(args, 3, "block <add|remove|delay|replace|age> <region> <block> [values]");
        Region region = region(args[1]);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                require(args, 5, "block add <region> <original> <replacement> <min> [max]");
                addRule(region.id(), new RegenRule(UUID.randomUUID(), parse(args[2]), parse(args[3]),
                        delay(args[4], args.length > 5 ? args[5] : args[4])));
            }
            case "remove", "delete" -> removeRule(region.id(), rule(region, args[2]).id());
            case "delay" -> {
                require(args, 4, "block delay <region> <block> <min> [max]");
                updateRule(region.id(), rule(region, args[2]).id(), r -> r.withDelay(delay(args[3], args.length > 4 ? args[4] : args[3])));
            }
            case "replace" -> {
                require(args, 4, "block replace <region> <block> <replacement>");
                updateRule(region.id(), rule(region, args[2]).id(), r -> r.withReplacement(parse(args[3])));
            }
            case "age" -> {
                require(args, 4, "block age <region> <block> <-1|age>");
                updateRule(region.id(), rule(region, args[2]).id(), r -> r.withOriginal(new Placeable("crop", r.original().id(), number(args[3]))));
            }
            default -> throw new IllegalArgumentException("Unknown block operation.");
        }
        sender.sendMessage("Block rules saved.");
    }

    private void settingCommand(CommandSender sender, String[] args) {
        require(args, 3, "setting <get|set> <region> <setting> [true|false]");
        Region region = region(args[1]);
        if (args[0].equalsIgnoreCase("get")) {
            sender.sendMessage(args[2] + " = " + (args[2].equalsIgnoreCase("region-display-icon") ? region.icon() : region.setting(RegionSetting.parse(args[2]))));
        } else if (args[0].equalsIgnoreCase("set")) {
            require(args, 4, "setting set <region> <setting> <value>");
            if (args[2].equalsIgnoreCase("region-display-icon")) {
                Material material = MaterialResolver.match(args[3]);
                if (material == null || !material.isItem()) {
                    throw new IllegalArgumentException("Choose an item material for the icon.");
                }
                backend.save(region.withIcon(material.name()));
            } else {
                backend.save(region.withSetting(RegionSetting.parse(args[2]), bool(args[3])));
            }
            sender.sendMessage("Region setting saved.");
        } else {
            throw new IllegalArgumentException("Choose setting get or set.");
        }
    }

    private void chainCommand(CommandSender sender, String[] args) {
        require(args, 3, "chain <add|remove|rename|style|final|stage|delay> <region> <chain> [values]");
        Region region = region(args[1]);
        if (args[0].equalsIgnoreCase("add")) {
            addChain(region.id(), String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        } else {
            RegenChain chain = chain(region, args[2]);
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "remove", "delete" -> removeChain(region.id(), chain.id());
                case "rename" -> {
                    require(args, 4, "chain rename <region> <chain> <name>");
                    String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                    updateChain(region.id(), chain.id(), c -> new RegenChain(c.id(), name, c.stages(), c.style(), c.lastStageBreakable()));
                }
                case "style" -> {
                    require(args, 4, "chain style <region> <chain> <direct|staged|same-timer>");
                    RegenChain.Style style = switch (args[3].toLowerCase(Locale.ROOT)) {
                        case "direct" -> RegenChain.Style.DIRECT;
                        case "staged", "staged_per_stage" -> RegenChain.Style.STAGED_PER_STAGE;
                        case "same-timer", "staged_same_timer" -> RegenChain.Style.STAGED_SAME_TIMER;
                        default -> throw new IllegalArgumentException("Unknown regeneration style.");
                    };
                    updateChain(region.id(), chain.id(), c -> new RegenChain(c.id(), c.name(), c.stages(), style, c.lastStageBreakable()));
                }
                case "final" -> {
                    require(args, 4, "chain final <region> <chain> <true|false>");
                    updateChain(region.id(), chain.id(), c -> new RegenChain(c.id(), c.name(), c.stages(), c.style(), bool(args[3])));
                }
                case "delay" -> {
                    require(args, 4, "chain delay <region> <chain> <min> [max]");
                    DelayRange delay = delay(args[3], args.length > 4 ? args[4] : args[3]);
                    updateChain(region.id(), chain.id(), c -> new RegenChain(c.id(), c.name(), c.stages().stream().map(s -> s.withDelay(delay)).toList(), c.style(), c.lastStageBreakable()));
                }
                case "stage" -> stageCommand(region.id(), chain.id(), Arrays.copyOfRange(args, 3, args.length));
                default -> throw new IllegalArgumentException("Unknown chain operation.");
            }
        }
        sender.sendMessage("Regen chains saved.");
    }

    private void stageCommand(String regionId, UUID chainId, String[] args) {
        require(args, 2, "chain stage <region> <chain> <add|remove|move|delay> <block|stage> [values]");
        if (args[0].equalsIgnoreCase("add")) {
            Placeable original = parse(args[1]);
            DelayRange delay = args.length > 2 ? delay(args[2], args.length > 3 ? args[3] : args[2]) : new DelayRange(5, 5);
            addStage(regionId, chainId, defaultRule(original).withDelay(delay));
            return;
        }
        int index = number(args[1]) - 1;
        updateChain(regionId, chainId, c -> {
            List<RegenRule> stages = new ArrayList<>(c.stages());
            if (index < 0 || index >= stages.size()) {
                throw new IllegalArgumentException("Unknown stage number.");
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "remove", "delete" -> stages.remove(index);
                case "move" -> {
                    require(args, 3, "chain stage <region> <chain> move <from> <to>");
                    int target = number(args[2]) - 1;
                    if (target < 0 || target >= stages.size()) {
                        throw new IllegalArgumentException("Unknown target stage.");
                    }
                    stages.add(target, stages.remove(index));
                }
                case "delay" -> {
                    require(args, 3, "chain stage <region> <chain> delay <stage> <min> [max]");
                    stages.set(index, stages.get(index).withDelay(delay(args[2], args.length > 3 ? args[3] : args[2])));
                }
                default -> throw new IllegalArgumentException("Unknown stage operation.");
            }
            return new RegenChain(c.id(), c.name(), stages, c.style(), c.lastStageBreakable());
        });
    }

    void create(Player player, String id) {
        if (backend.region(id) != null) {
            throw new IllegalArgumentException("A region with that ID already exists.");
        }
        AdminSessions.Selection selection = sessions.selection(player);
        if (selection.first() == null || selection.second() == null) {
            throw new IllegalArgumentException("Select both corners with /blocksregenerator wand or /blocksregenerator pos1 and /blocksregenerator pos2.");
        }
        backend.save(Region.create(id, selection.first().world(), Bounds.between(selection.first(), selection.second())));
        player.sendMessage("Region " + id + " created.");
    }

    void global(String worldName, String id) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalArgumentException("That world is not loaded.");
        }
        if (backend.region(id) != null || backend.regions().stream().anyMatch(r -> r.global() && r.world().equals(worldName))) {
            throw new IllegalArgumentException("A region with that ID or a global for that world already exists.");
        }
        backend.save(Region.create(id, worldName, null)
                .withSetting(RegionSetting.ALLOW_BLOCK_BREAK, true)
                .withSetting(RegionSetting.ALLOW_BLOCK_PLACE, true));
    }

    void copy(Player player, String regionId, String mode) {
        Region region = region(regionId);
        if (!List.of("all", "blocks", "chains").contains(mode.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Choose all, blocks, or chains.");
        }
        sessions.clipboard(player, new AdminSessions.Clipboard(mode.equalsIgnoreCase("chains") ? null : region.rules(),
                mode.equalsIgnoreCase("blocks") ? null : region.chains()));
        player.sendMessage("Copied " + mode + " to your clipboard.");
    }

    void paste(Player player, String regionId) {
        AdminSessions.Clipboard clipboard = sessions.clipboard(player);
        if (clipboard == null) {
            throw new IllegalArgumentException("Your clipboard is empty.");
        }
        Region region = region(regionId);
        if (clipboard.rules() != null) {
            region = region.withRules(clipboard.rules().stream().map(RegenRule::copy).toList());
        }
        if (clipboard.chains() != null) {
            region = region.withChains(clipboard.chains().stream().map(RegenChain::copy).toList());
        }
        backend.save(region);
        player.sendMessage("Clipboard pasted and saved.");
    }

    Region region(String id) {
        Region region = backend.region(id);
        if (region == null) {
            throw new IllegalArgumentException("Region no longer exists: " + id);
        }
        return region;
    }

    RegenRule rule(Region region, String selector) {
        for (int i = 0; i < region.rules().size(); i++) {
            RegenRule rule = region.rules().get(i);
            if (rule.id().toString().equalsIgnoreCase(selector) || rule.original().display().equalsIgnoreCase(selector)
                    || rule.original().id().equalsIgnoreCase(selector) || Integer.toString(i + 1).equals(selector)) {
                return rule;
            }
        }
        throw new IllegalArgumentException("Unknown block rule. Use its material, UUID, or list number.");
    }

    RegenChain chain(Region region, String selector) {
        for (int i = 0; i < region.chains().size(); i++) {
            RegenChain chain = region.chains().get(i);
            if (chain.id().toString().equalsIgnoreCase(selector) || chain.name().equalsIgnoreCase(selector) || Integer.toString(i + 1).equals(selector)) {
                return chain;
            }
        }
        throw new IllegalArgumentException("Unknown chain. Use its UUID, name, or list number.");
    }

    void addRule(String regionId, RegenRule rule) {
        Region region = region(regionId);
        if (region.rules().stream().anyMatch(r -> r.original().equals(rule.original()))) {
            throw new IllegalArgumentException("That block already has a rule in this region.");
        }
        List<RegenRule> rules = new ArrayList<>(region.rules());
        rules.add(rule);
        backend.save(region.withRules(rules));
    }

    void updateRule(String regionId, UUID ruleId, UnaryOperator<RegenRule> update) {
        Region region = region(regionId);
        RegenRule current = region.rule(ruleId);
        if (current == null) {
            throw new IllegalArgumentException("This rule no longer exists.");
        }
        RegenRule changed = update.apply(current);
        if (region.rules().stream().anyMatch(r -> !r.id().equals(ruleId) && r.original().equals(changed.original()))) {
            throw new IllegalArgumentException("That block already has a rule in this region.");
        }
        backend.save(region.withRules(region.rules().stream().map(r -> r.id().equals(ruleId) ? changed : r).toList()));
    }

    void removeRule(String regionId, UUID ruleId) {
        Region region = region(regionId);
        backend.save(region.withRules(region.rules().stream().filter(r -> !r.id().equals(ruleId)).toList()));
    }

    UUID addChain(String regionId, String name) {
        RegenChain chain = new RegenChain(UUID.randomUUID(), name, List.of(), RegenChain.Style.DIRECT, false);
        Region region = region(regionId);
        List<RegenChain> chains = new ArrayList<>(region.chains());
        chains.add(chain);
        backend.save(region.withChains(chains));
        return chain.id();
    }

    void updateChain(String regionId, UUID chainId, UnaryOperator<RegenChain> update) {
        Region region = region(regionId);
        if (region.chain(chainId) == null) {
            throw new IllegalArgumentException("This chain no longer exists.");
        }
        backend.save(region.withChains(region.chains().stream().map(c -> c.id().equals(chainId) ? update.apply(c) : c).toList()));
    }

    void removeChain(String regionId, UUID chainId) {
        Region region = region(regionId);
        backend.save(region.withChains(region.chains().stream().filter(c -> !c.id().equals(chainId)).toList()));
    }

    void addStage(String regionId, UUID chainId, RegenRule rule) {
        if (rule.original().furniture() || rule.original().type().equals("crop")) {
            throw new IllegalArgumentException("Crops and furniture cannot be chain stages.");
        }
        updateChain(regionId, chainId, c -> {
            List<RegenRule> stages = new ArrayList<>(c.stages());
            stages.add(rule.withReplacement(Placeable.material("AIR")));
            return new RegenChain(c.id(), c.name(), stages, c.style(), c.lastStageBreakable());
        });
    }

    RegenRule defaultRule(Placeable original) {
        return new RegenRule(UUID.randomUUID(), original,
                Placeable.material(original.furniture() || original.type().equals("crop") ? "AIR" : "STONE"), new DelayRange(5, 5));
    }

    Placeable parse(String text) {
        String type = "material";
        String id = text;
        int colon = text.indexOf(':');
        if (colon > 0 && Placeable.TYPES.contains(text.substring(0, colon).toLowerCase(Locale.ROOT))) {
            type = text.substring(0, colon).toLowerCase(Locale.ROOT);
            id = text.substring(colon + 1);
        }
        int age = -1;
        int marker = id.lastIndexOf('@');
        if (marker >= 0) {
            age = number(id.substring(marker + 1));
            id = id.substring(0, marker);
        }
        Placeable placeable = new Placeable(type, id, age);
        backend.integrations().validate(placeable);
        return placeable;
    }

    Placeable fromItem(ItemStack item) {
        return backend.integrations().identifyItem(item);
    }

    static DelayRange delay(String minimum, String maximum) {
        return new DelayRange(number(minimum), number(maximum));
    }

    static int number(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid whole number.");
        }
    }

    static boolean bool(String input) {
        if (!input.equalsIgnoreCase("true") && !input.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Enter true or false.");
        }
        return Boolean.parseBoolean(input);
    }

    static BlockKey key(Location location) {
        return new BlockKey(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private void position(Player player, boolean first) {
        sessions.select(player, key(player.getLocation()), first);
    }

    private static Player player(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            throw new IllegalArgumentException("This command requires a player.");
        }
        return player;
    }

    private static void require(String[] args, int length, String usage) {
        if (args.length < length) {
            throw new IllegalArgumentException("Usage: /blocksregenerator " + usage);
        }
    }

    private void help(CommandSender sender) {
        sender.sendMessage(new String[]{"BlocksRegenerator by Lino", "/blocksregenerator menu | wand | pos1 | pos2 | edges | add",
                "/blocksregenerator create <region> | global <world> [region] | select <region>",
                "/blocksregenerator list | edit <region> | delete <region> | regenerate <region>",
                "/blocksregenerator block add <region> <original> <replacement> <min> [max]",
                "/blocksregenerator block remove <region> <material|number|uuid>",
                "/blocksregenerator block delay|replace|age <region> <block> <values>",
                "/blocksregenerator setting get|set <region> <setting> [value]",
                "/blocksregenerator chain add|remove|rename|style|final|stage|delay <region> <chain> [values]",
                "/blocksregenerator copy <region> [all|blocks|chains] | paste <region>",
                "/blocksregenerator priority <region> <number> | reload | eval <expression>",
                "/blocksregenerator import <oreregenerator|ultimate-regen|minexfarm-regen>",
                "Custom blocks: type:id. Crop age: crop:WHEAT_SEEDS@7. See docs/COMMANDS.md."});
    }
}
