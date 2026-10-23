package com.tunombre.bossplugin.command;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.boss.Boss;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Comando /boss <invocar|listar|recargar> [tipo]
 */
public class BossCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;

    public BossCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getConfig().getString("messages.only-players", "Solo jugadores"));
            return true;
        }

        if (!player.hasPermission("bossplugin.use")) {
            player.sendMessage(Component.text(plugin.getConfig().getString("messages.no-permission", "Sin permiso"))
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Uso: /boss <invocar|listar|recargar> [tipo]")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "invocar":
                if (args.length < 2) {
                    listBosses(player);
                    return true;
                }
                plugin.getBossManager().spawnBoss(player, args[1]);
                break;

            case "listar":
            case "list":
                listBosses(player);
                break;

            case "recargar":
            case "reload":
                if (!player.hasPermission("bossplugin.admin")) {
                    player.sendMessage(Component.text("Sin permiso de administrador").color(NamedTextColor.RED));
                    return true;
                }
                plugin.reloadConfig();
                plugin.getBossManager().cleanup();
                plugin.getBossManager().registerDefaultBosses();
                player.sendMessage(Component.text(plugin.getConfig().getString("messages.reloaded", "Recargado"))
                        .color(NamedTextColor.GREEN));
                break;

            default:
                player.sendMessage(Component.text("Subcomando desconocido: " + args[0]).color(NamedTextColor.RED));
                break;
        }

        return true;
    }

    private void listBosses(Player player) {
        player.sendMessage(Component.text(plugin.getConfig().getString("messages.boss-list-header", "Jefes disponibles"))
                .color(NamedTextColor.GOLD));

        for (Boss boss : plugin.getBossManager().getBosses().values()) {
            player.sendMessage(Component.text("- ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(boss.getKey()).color(NamedTextColor.WHITE))
                    .append(Component.text(" | Vida: ").color(NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.0f", boss.getMaxHealth())).color(NamedTextColor.RED))
                    .append(Component.text(" | Daño: ").color(NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.1f", boss.getAttackDamage())).color(NamedTextColor.RED)));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("invocar", "listar", "recargar"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("invocar")) {
            return filter(plugin.getBossManager().getBosses().keySet(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(Collection<String> options, String prefix) {
        String p = prefix.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(p))
                .sorted()
                .toList();
    }
}
