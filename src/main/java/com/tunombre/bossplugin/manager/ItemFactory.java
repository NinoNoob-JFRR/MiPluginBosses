package com.tunombre.bossplugin.manager;

import com.tunombre.bossplugin.Main;
import com.tunombre.bossplugin.config.ConfigManager;
import com.tunombre.bossplugin.model.AbilityDefinition;
import com.tunombre.bossplugin.util.Keys;
import com.tunombre.bossplugin.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Crea los items personalizados del plugin a partir de items.yml.
 */
public class ItemFactory {

    private final Main plugin;
    private final ConfigManager config;

    public ItemFactory(Main plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public ItemStack legendaryEssence() {
        ConfigurationSection section = config.get("items.yml").getConfigurationSection("items.legendary_essence");
        ItemStack item = build(section, Map.of(), List.of());
        setType(item, "legendary_essence");
        return item;
    }

    public ItemStack trackingCompass() {
        ConfigurationSection section = config.get("items.yml").getConfigurationSection("items.tracking_compass");
        ItemStack item = build(section, Map.of(), List.of());
        setType(item, "tracking_compass");
        return item;
    }

    public ItemStack abilityEssence(AbilityDefinition definition, int level) {
        ConfigurationSection section = config.get("items.yml").getConfigurationSection("items.ability_essence");
        if (section == null) {
            return new ItemStack(Material.NETHER_STAR);
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ability_name", definition.name());
        values.put("ability_tier", String.valueOf(definition.tier()));
        values.put("ability_max_level", String.valueOf(definition.maxLevel()));
        values.put("level", String.valueOf(level));

        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            if (line.contains("%ability_lore%")) {
                for (String abilityLine : definition.lore()) {
                    lore.add(Text.apply(abilityLine, values));
                }
            } else {
                lore.add(Text.apply(line, values));
            }
        }

        ItemStack item = build(section, values, lore, false);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(Keys.ITEM_TYPE, PersistentDataType.STRING, "ability_essence");
            meta.getPersistentDataContainer().set(Keys.ABILITY_ID, PersistentDataType.STRING, definition.id());
            meta.getPersistentDataContainer().set(Keys.ABILITY_LEVEL, PersistentDataType.INTEGER, level);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack build(ConfigurationSection section, Map<String, String> values, List<String> extraLore) {
        return build(section, values, extraLore, true);
    }

    private ItemStack build(ConfigurationSection section, Map<String, String> values,
                            List<String> extraLore, boolean includeSectionLore) {
        Material material = Material.matchMaterial(section == null ? "STONE" : section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        String name = Text.apply(section == null ? material.name() : section.getString("name", material.name()), values);
        meta.displayName(Text.c(name));

        List<Component> lore = new ArrayList<>();
        if (includeSectionLore && section != null) {
            for (String line : section.getStringList("lore")) {
                lore.add(Text.c(Text.apply(line, values)));
            }
        }
        for (String line : extraLore) {
            lore.add(Text.c(Text.apply(line, values)));
        }
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        if (section == null || section.getBoolean("glow", false)) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (section != null && !section.getBoolean("stackable", true)) {
            // No hay una API directa para "no stackable" sin afectar la jugabilidad;
            // se deja apilable y se controla por logica.
        }
        item.setItemMeta(meta);
        return item;
    }

    private void setType(ItemStack item, String type) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(Keys.ITEM_TYPE, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
    }

    public String itemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "";
        }
        String value = meta.getPersistentDataContainer().get(Keys.ITEM_TYPE, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    public boolean isType(ItemStack item, String type) {
        return type.equalsIgnoreCase(itemType(item));
    }

    public String abilityId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return "";
        }
        String value = meta.getPersistentDataContainer().get(Keys.ABILITY_ID, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    public Map<String, Integer> abilities(ItemStack item) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (item == null || !item.hasItemMeta()) {
            return result;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return result;
        }
        String raw = meta.getPersistentDataContainer().get(Keys.ABILITIES, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            String[] pieces = part.split(":");
            if (pieces.length == 2) {
                try {
                    result.put(pieces[0].toLowerCase(Locale.ROOT), Integer.parseInt(pieces[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    public void abilities(ItemStack item, Map<String, Integer> abilities) {
        // No se usa hasItemMeta(): un item normal (pico/hacha sin meta) devuelve
        // false aunque getItemMeta() si permita escribirle datos persistentes.
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : abilities.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append(':').append(entry.getValue());
        }
        if (sb.length() == 0) {
            meta.getPersistentDataContainer().remove(Keys.ABILITIES);
        } else {
            meta.getPersistentDataContainer().set(Keys.ABILITIES, PersistentDataType.STRING, sb.toString());
        }
        item.setItemMeta(meta);
    }

    public void appendAppliedLore(ItemStack item, AbilityDefinition definition, int level) {
        setAppliedLore(item, definition, level);
    }

    /**
     * Reemplaza la linea de lore aplicada de una habilidad, evitando duplicados
     * al subir de nivel varias veces.
     */
    public void setAppliedLore(ItemStack item, AbilityDefinition definition, int level) {
        // Igual que abilities(): hasItemMeta() es false en items sin meta previa.
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> existing = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        List<Component> lore = new ArrayList<>();
        String prefix = definition.name() + " Nv.";
        for (Component line : existing) {
            if (Text.strip(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line))
                    .startsWith(prefix)) {
                continue;
            }
            lore.add(line);
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("level", String.valueOf(level));
        for (String line : definition.appliedLore()) {
            lore.add(Text.c(Text.apply(line, values)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Convierte un libro normal en un libro de habilidad activa.
     */
    public void makeAbilityBook(ItemStack item, AbilityDefinition definition, int level) {
        if (item == null) {
            return;
        }
        item.setType(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        Map<String, Integer> abilities = new LinkedHashMap<>();
        abilities.put(definition.id(), level);
        abilities(item, abilities);

        meta = item.getItemMeta();
        if (meta != null) {
            ConfigurationSection book = config.get("items.yml").getConfigurationSection("items.ability_book");
            Map<String, String> values = new LinkedHashMap<>();
            values.put("ability_name", definition.name());
            values.put("level", String.valueOf(level));
            String name = book == null ? "&b%ability_name%" : book.getString("name", "&b%ability_name%");
            meta.displayName(Text.c(Text.apply(name, values)));
            List<Component> lore = new ArrayList<>();
            for (String line : definition.lore()) {
                lore.add(Text.c(Text.apply(line, values)));
            }
            lore.add(Text.c(""));
            for (String line : definition.appliedLore()) {
                lore.add(Text.c(Text.apply(line, values)));
            }
            if (book != null) {
                String usage = book.getString("usage", "");
                if (!usage.isBlank()) {
                    lore.add(Text.c(Text.apply(usage, values)));
                }
            }
            meta.lore(lore);
            meta.setMaxStackSize(1);
            meta.setEnchantmentGlintOverride(book == null || book.getBoolean("glow", true));
            meta.getPersistentDataContainer().set(Keys.ITEM_TYPE, PersistentDataType.STRING, "active_ability");
            item.setItemMeta(meta);
        }
    }
}
