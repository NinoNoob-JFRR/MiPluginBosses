package com.tunombre.bossplugin.util;

import com.tunombre.bossplugin.Main;
import org.bukkit.NamespacedKey;

/**
 * Claves persistentes compartidas.
 */
public final class Keys {

    public static NamespacedKey ITEM_TYPE;
    public static NamespacedKey ABILITY_ID;
    public static NamespacedKey ABILITY_LEVEL;
    public static NamespacedKey ABILITIES;
    public static NamespacedKey LOOT_BYTES;
    public static NamespacedKey LOOT_ROLLED;
    public static NamespacedKey LOOT_TIER;
    public static NamespacedKey MOB_TIER;
    public static NamespacedKey MOB_APPLIED;
    public static NamespacedKey MOB_BASE_NAME;
    public static NamespacedKey MOB_XP_MULT;
    public static NamespacedKey MOB_BASE_HEALTH;
    public static NamespacedKey MOB_BASE_DAMAGE;
    public static NamespacedKey MOB_BASE_ARMOR;
    public static NamespacedKey MOB_BASE_TOUGHNESS;
    public static NamespacedKey MOB_BASE_SPEED;
    public static NamespacedKey BOSS_ID;
    public static NamespacedKey EGG_MARKER;
    public static NamespacedKey MINION;
    public static NamespacedKey MENU_ACTION;

    private Keys() {
    }

    public static void init(Main plugin) {
        ITEM_TYPE = new NamespacedKey(plugin, "item_type");
        ABILITY_ID = new NamespacedKey(plugin, "ability_id");
        ABILITY_LEVEL = new NamespacedKey(plugin, "ability_level");
        ABILITIES = new NamespacedKey(plugin, "abilities");
        LOOT_BYTES = new NamespacedKey(plugin, "loot_bytes");
        LOOT_ROLLED = new NamespacedKey(plugin, "loot_rolled");
        LOOT_TIER = new NamespacedKey(plugin, "loot_tier");
        MOB_TIER = new NamespacedKey(plugin, "mob_tier");
        MOB_APPLIED = new NamespacedKey(plugin, "mob_tier_applied");
        MOB_BASE_NAME = new NamespacedKey(plugin, "mob_base_name");
        MOB_XP_MULT = new NamespacedKey(plugin, "mob_xp_mult");
        MOB_BASE_HEALTH = new NamespacedKey(plugin, "mob_base_health");
        MOB_BASE_DAMAGE = new NamespacedKey(plugin, "mob_base_damage");
        MOB_BASE_ARMOR = new NamespacedKey(plugin, "mob_base_armor");
        MOB_BASE_TOUGHNESS = new NamespacedKey(plugin, "mob_base_toughness");
        MOB_BASE_SPEED = new NamespacedKey(plugin, "mob_base_speed");
        BOSS_ID = new NamespacedKey(plugin, "boss_id");
        EGG_MARKER = new NamespacedKey(plugin, "egg_marker");
        MINION = new NamespacedKey(plugin, "boss_minion");
        MENU_ACTION = new NamespacedKey(plugin, "menu_action");
    }
}
