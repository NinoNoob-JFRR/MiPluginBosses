package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Scoreboard lateral por jugador con los atributos basicos y la vida actual.
 * Usa equipos con entradas invisibles para actualizar el texto sin parpadeos.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE = "mibosses_stats";
    private static final String ENTRY_CHARS = "0123456789abcdef";

    private final Main plugin;
    private final ConfigManager config;
    private final Map<UUID, Board> boards = new HashMap<>();

    private boolean enabled = true;
    private String title = "&e&lATRIBUTOS";
    private List<String> lines = List.of();
    private int updateIntervalTicks = 20;
    private int counter;

    public ScoreboardManager(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        FileConfiguration cfg = config.get("config.yml");
        enabled = cfg.getBoolean("scoreboard.enabled", true);
        title = cfg.getString("scoreboard.title", "&e&lATRIBUTOS");
        lines = new ArrayList<>(cfg.getStringList("scoreboard.lines"));
        if (lines.isEmpty()) {
            lines = defaultLines();
        }
        if (lines.size() > 15) {
            lines = new ArrayList<>(lines.subList(0, 15));
        }
        updateIntervalTicks = Math.max(1, cfg.getInt("scoreboard.update-interval-ticks", 20));

        boards.clear();
        if (!enabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                reset(player);
            }
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            setup(player);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public int updateIntervalTicks() {
        return updateIntervalTicks;
    }

    /** Refresco periodico: equipo, efectos, nivel de Evolution, etc. */
    public void tick() {
        if (!enabled) {
            return;
        }
        if (++counter < updateIntervalTicks) {
            return;
        }
        counter = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    /** Fuerza un refresco inmediato (por ejemplo al recibir dano o curarse). */
    public void refresh(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            return;
        }
        update(player);
    }

    public void setup(Player player) {
        if (!enabled || player == null || !player.isOnline()) {
            return;
        }
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE, "dummy",
                Text.color(title));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<Team> teams = new ArrayList<>();
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String entry = "\u00a7" + ENTRY_CHARS.charAt(i % ENTRY_CHARS.length()) + "\u00a7r";
            Team team = scoreboard.registerNewTeam("sb_" + i);
            team.addEntry(entry);
            objective.getScore(entry).setScore(lines.size() - i);
            teams.add(team);
            entries.add(entry);
        }

        Board board = new Board(scoreboard, objective, teams, entries);
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(scoreboard);
        render(player, board);
    }

    public void remove(Player player) {
        if (player == null) {
            return;
        }
        boards.remove(player.getUniqueId());
    }

    /** Devuelve al jugador a la scoreboard principal del servidor. */
    public void reset(Player player) {
        if (player == null) {
            return;
        }
        boards.remove(player.getUniqueId());
        org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null && player.isOnline()) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    /** Quita la scoreboard del plugin a todos los jugadores. */
    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            reset(player);
        }
        boards.clear();
    }

    private void update(Player player) {
        Board board = boards.get(player.getUniqueId());
        if (board == null || board.scoreboard() != player.getScoreboard()) {
            setup(player);
            return;
        }
        render(player, board);
    }

    private void render(Player player, Board board) {
        Map<String, String> values = placeholders(player);
        for (int i = 0; i < board.teams().size(); i++) {
            String text = Text.apply(lines.get(i), values);
            text = Text.color(text);
            String[] parts = splitLine(text);
            Team team = board.teams().get(i);
            team.setPrefix(parts[0]);
            team.setSuffix(parts[1]);
        }
    }

    private Map<String, String> placeholders(Player player) {
        Map<String, String> values = new HashMap<>();
        double health = player.getHealth();
        double maxHealth = attribute(player, Attribute.MAX_HEALTH, 20.0);
        double current = Math.max(0.0, Math.min(health, maxHealth));

        values.put("player", player.getName());
        values.put("health_current", format(current));
        values.put("health_max", format(maxHealth));
        values.put("armor", format(attribute(player, Attribute.ARMOR, 0.0)));
        values.put("toughness", format(attribute(player, Attribute.ARMOR_TOUGHNESS, 0.0)));
        values.put("attack_damage", format(attribute(player, Attribute.ATTACK_DAMAGE, 1.0)));
        values.put("attack_speed", format(attribute(player, Attribute.ATTACK_SPEED, 4.0)));
        values.put("movement_speed", format(attribute(player, Attribute.MOVEMENT_SPEED, 0.1)));
        values.put("knockback_resistance", format(attribute(player, Attribute.KNOCKBACK_RESISTANCE, 0.0)));
        values.put("luck", format(attribute(player, Attribute.LUCK, 0.0)));
        values.put("evolution_level", String.valueOf(plugin.getStatsManager().evolutionLevel(player)));
        return values;
    }

    private double attribute(Player player, Attribute attribute, double def) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance == null ? def : instance.getValue();
    }

    private String format(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
            return String.valueOf((long) Math.rint(rounded));
        }
        return String.format(Locale.US, "%.1f", rounded);
    }

    /**
     * Separa una linea en prefijo y sufijo. El prefijo se corta sin romper
     * codigos de color y el sufijo hereda el ultimo color activo.
     */
    private String[] splitLine(String text) {
        final int maxPrefix = 48;
        if (text.length() <= maxPrefix) {
            return new String[]{text, ""};
        }
        int cut = maxPrefix;
        if (cut > 0 && text.charAt(cut - 1) == '\u00a7') {
            cut--;
        }
        if (cut > 0 && Character.isHighSurrogate(text.charAt(cut - 1))) {
            cut--;
        }
        String prefix = text.substring(0, cut);
        String suffix = lastColor(prefix) + text.substring(cut);
        if (suffix.length() > 64) {
            suffix = suffix.substring(0, 64);
        }
        return new String[]{prefix, suffix};
    }

    private String lastColor(String text) {
        String color = "";
        StringBuilder formats = new StringBuilder();
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '\u00a7') {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            if ("0123456789abcdef".indexOf(code) >= 0) {
                color = "\u00a7" + code;
                formats.setLength(0);
                i++;
            } else if ("klmno".indexOf(code) >= 0) {
                formats.append('\u00a7').append(code);
                i++;
            } else if (code == 'r') {
                color = "";
                formats.setLength(0);
                i++;
            }
        }
        return color + formats;
    }

    private List<String> defaultLines() {
        return List.of(
                "&c\u2764 %health_current%/%health_max%",
                "&7Armadura: &f%armor%",
                "&7Daño: &f%attack_damage%",
                "&7Dureza: &f%toughness%",
                "&7Velocidad: &f%movement_speed%",
                "&7Suerte: &f%luck%");
    }

    private record Board(Scoreboard scoreboard, Objective objective,
                         List<Team> teams, List<String> entries) {
    }
}
