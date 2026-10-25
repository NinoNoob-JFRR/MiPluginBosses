package com.tunombre.bossplugin.command;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.model.BossDefinition;
import com.tunombre.bossplugin.model.EggData;
import com.tunombre.bossplugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comando principal /boss.
 */
public class BossCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public BossCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.getMessages().send(sender, "general.only-players");
                return true;
            }
            plugin.getMenuManager().openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                return userMenu(sender, player -> plugin.getMenuManager().openMain(player));
            }
            case "stats" -> {
                return userMenu(sender, player -> plugin.getMenuManager().openStats(player));
            }
            case "progreso", "progress" -> {
                return userMenu(sender, player -> plugin.getMenuManager().openProgress(player));
            }
            case "ayuda", "help" -> {
                return userMenu(sender, player -> plugin.getMenuManager().openHelp(player));
            }
            case "recetas", "recipes" -> {
                return userMenu(sender, player -> plugin.getMenuManager().openRecipes(player, 1));
            }
            case "habilidades", "abilities" -> {
                return userMenu(sender, player -> plugin.getMenuManager().openAbilities(player, 1));
            }
            case "jefes", "bestiario" -> {
                return userMenu(sender, player -> plugin.getMenuManager().openBosses(player, 1));
            }
            case "adminmenu" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    plugin.getMessages().send(sender, "general.only-players");
                    return true;
                }
                plugin.getMenuManager().openAdmin(player);
                return true;
            }
            case "jefe", "invocar" -> {
                return spawnBoss(sender, args);
            }
            case "huevo", "egg" -> {
                return spawnEgg(sender, args);
            }
            case "brujula", "compass" -> {
                return giveCompass(sender, args);
            }
            case "dar", "give" -> {
                return give(sender, args);
            }
            case "desbloquear", "unlock" -> {
                return unlock(sender, args);
            }
            case "reset" -> {
                return reset(sender, args);
            }
            case "recargar", "reload" -> {
                if (!admin(sender)) {
                    return true;
                }
                plugin.getMessages().send(sender, "admin.reloading");
                plugin.reloadPlugin();
                plugin.getMessages().send(sender, "admin.reloaded");
                return true;
            }
            case "validar", "validate" -> {
                if (!admin(sender)) {
                    return true;
                }
                List<String> errors = plugin.validateConfigs();
                if (errors.isEmpty()) {
                    plugin.getMessages().send(sender, "admin.validation-ok");
                } else {
                    plugin.getMessages().send(sender, "admin.validation-errors",
                            "count", String.valueOf(errors.size()));
                    for (String error : errors) {
                        plugin.getLogger().warning("[Validacion] " + error);
                        sender.sendMessage(Text.c("&c- " + error));
                    }
                }
                return true;
            }
            case "listar", "list" -> {
                if (!admin(sender)) {
                    return true;
                }
                sender.sendMessage(Text.c("&e--- Jefes disponibles ---"));
                for (BossDefinition definition : plugin.getBossManager().bosses()) {
                    sender.sendMessage(Text.c(plugin.getTierManager().color(definition.tier())
                            + definition.id() + " &8- &f" + definition.name()
                            + " &7(T" + definition.tier() + ")"));
                }
                return true;
            }
            default -> {
                plugin.getMessages().send(sender, "general.unknown-command");
                return true;
            }
        }
    }

    private boolean userMenu(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!user(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.only-players");
            return true;
        }
        action.accept(player);
        return true;
    }

    private boolean spawnBoss(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.only-players");
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.unknown-command");
            return true;
        }
        BossDefinition definition = plugin.getBossManager().definition(args[1]);
        if (definition == null) {
            plugin.getMessages().send(sender, "boss.not-found", "id", args[1]);
            return true;
        }
        var boss = plugin.getBossManager().spawn(definition, player.getLocation(), player);
        if (boss == null) {
            plugin.getMessages().send(sender, "general.config-error", "error", "No se pudo invocar el jefe");
            return true;
        }
        plugin.getMessages().send(sender, "admin.boss-spawned",
                "boss", definition.name(),
                "x", String.valueOf(player.getLocation().getBlockX()),
                "y", String.valueOf(player.getLocation().getBlockY()),
                "z", String.valueOf(player.getLocation().getBlockZ()));
        return true;
    }

    private boolean spawnEgg(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "general.only-players");
            return true;
        }
        int tier = 0;
        int amount = 1;
        if (args.length >= 2) {
            try {
                tier = Math.max(0, Math.min(10, Integer.parseInt(args[1])));
            } catch (NumberFormatException e) {
                plugin.getMessages().send(sender, "general.invalid-number", "value", args[1]);
                return true;
            }
        }
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(10, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                plugin.getMessages().send(sender, "general.invalid-number", "value", args[2]);
                return true;
            }
        }
        List<EggData> eggs = plugin.getEggManager().spawnNear(player, tier, amount);
        if (eggs.isEmpty()) {
            plugin.getMessages().send(sender, "egg.max-reached",
                    "max", String.valueOf(plugin.getEggManager().maxActive()));
            return true;
        }
        for (EggData egg : eggs) {
            plugin.getMessages().send(sender, "admin.egg-spawned",
                    "x", String.valueOf(egg.x()),
                    "y", String.valueOf(egg.y()),
                    "z", String.valueOf(egg.z()));
        }
        return true;
    }

    private boolean giveCompass(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.getMessages().send(sender, "general.player-not-found", "player", args[1]);
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.getMessages().send(sender, "general.only-players");
            return true;
        }
        plugin.getEggManager().giveCompass(target, 1);
        plugin.getMessages().send(sender, "admin.compass-given", "player", target.getName());
        if (!target.equals(sender)) {
            plugin.getMessages().send(target, "egg.compass-received");
        }
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 3) {
            plugin.getMessages().send(sender, "general.unknown-command");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessages().send(sender, "general.player-not-found", "player", args[1]);
            return true;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                plugin.getMessages().send(sender, "general.invalid-number", "value", args[3]);
                return true;
            }
        }
        String kind = args[2].toLowerCase(Locale.ROOT);
        if (kind.equals("esencia") || kind.equals("legendaria")
                || kind.equals("legendary_essence") || kind.equals("legendary")) {
            ItemStack item = plugin.getItems().legendaryEssence();
            item.setAmount(amount);
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(item);
            for (ItemStack rest : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), rest);
            }
            plugin.getMessages().send(sender, "admin.essence-given", "player", target.getName());
            plugin.getMessages().send(target, "essence.received", "amount", String.valueOf(amount));
            return true;
        }
        if (plugin.getAbilityManager().giveEssence(target, kind, amount)) {
            plugin.getMessages().send(sender, "admin.essence-given", "player", target.getName());
            plugin.getMessages().send(target, "essence.received", "amount", String.valueOf(amount));
            return true;
        }
        plugin.getMessages().send(sender, "boss.not-found", "id", args[2]);
        return true;
    }

    private boolean unlock(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 3) {
            plugin.getMessages().send(sender, "general.unknown-command");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessages().send(sender, "general.player-not-found", "player", args[1]);
            return true;
        }
        int tier;
        try {
            tier = Math.max(1, Math.min(10, Integer.parseInt(args[2])));
        } catch (NumberFormatException e) {
            plugin.getMessages().send(sender, "general.invalid-number", "value", args[2]);
            return true;
        }
        plugin.getProgressionManager().unlock(target.getUniqueId(), tier);
        plugin.getProgressionManager().save();
        plugin.getMessages().send(sender, "admin.tier-unlocked",
                "tier", String.valueOf(tier), "player", target.getName());
        plugin.getMessages().send(target, "progression.unlocked", "tier", String.valueOf(tier));
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return true;
        }
        if (args.length < 2) {
            plugin.getMessages().send(sender, "general.unknown-command");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessages().send(sender, "general.player-not-found", "player", args[1]);
            return true;
        }
        if (args.length >= 3 && !args[2].equalsIgnoreCase("all") && !args[2].equalsIgnoreCase("todo")) {
            try {
                int tier = Math.max(1, Math.min(10, Integer.parseInt(args[2])));
                plugin.getProgressionManager().resetTier(target.getUniqueId(), tier);
                plugin.getProgressionManager().save();
                plugin.getMessages().send(sender, "progression.reset-tier",
                        "tier", String.valueOf(tier), "player", target.getName());
            } catch (NumberFormatException e) {
                plugin.getMessages().send(sender, "general.invalid-number", "value", args[2]);
            }
            return true;
        }
        plugin.getProgressionManager().reset(target.getUniqueId());
        plugin.getProgressionManager().save();
        plugin.getMessages().send(sender, "admin.progress-reset", "player", target.getName());
        plugin.getMessages().send(target, "progression.reset", "player", target.getName());
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("mibosses.admin") || sender.hasPermission("bossplugin.admin")) {
            return true;
        }
        plugin.getMessages().send(sender, "general.no-permission");
        return false;
    }

    private boolean user(CommandSender sender) {
        if (sender.hasPermission("mibosses.user") || sender.hasPermission("bossplugin.use")) {
            return true;
        }
        plugin.getMessages().send(sender, "general.no-permission");
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("menu", "stats", "progreso", "ayuda",
                    "recetas", "habilidades", "jefes"));
            if (sender.hasPermission("mibosses.admin") || sender.hasPermission("bossplugin.admin")) {
                options.addAll(List.of("adminmenu", "jefe", "huevo", "brujula", "dar",
                        "desbloquear", "reset", "recargar", "validar", "listar"));
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "jefe", "invocar" -> filter(new ArrayList<>(plugin.getBossManager().bosses().stream()
                        .map(BossDefinition::id).toList()), args[1]);
                case "brujula", "dar", "desbloquear", "reset" -> filter(onlinePlayers(), args[1]);
                case "huevo" -> filter(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "dar" -> filter(new ArrayList<>(plugin.getAbilityManager().abilities().keySet()), args[2]);
                case "desbloquear" -> filter(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), args[2]);
                case "reset" -> filter(List.of("all", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), args[2]);
                case "huevo" -> filter(List.of("1", "2", "3"), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private List<String> onlinePlayers() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
