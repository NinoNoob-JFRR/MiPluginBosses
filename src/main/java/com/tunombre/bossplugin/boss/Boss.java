package com.tunombre.bossplugin.boss;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Representa la definición de un jefe personalizado.
 */
public class Boss {

    private final String key;
    private final Component displayName;
    private final EntityType entityType;
    private final double maxHealth;
    private final double attackDamage;
    private final double movementSpeed;
    private final double armor;
    private final double armorToughness;
    private final double dropChance;
    private final List<ItemStack> drops;

    public Boss(String key, String displayName, EntityType entityType,
                double maxHealth, double attackDamage, double movementSpeed,
                double armor, double armorToughness, double dropChance) {
        this.key = key;
        this.displayName = Component.text(displayName);
        this.entityType = entityType;
        this.maxHealth = maxHealth;
        this.attackDamage = attackDamage;
        this.movementSpeed = movementSpeed;
        this.armor = armor;
        this.armorToughness = armorToughness;
        this.dropChance = dropChance;
        this.drops = new ArrayList<>();
    }

    public Boss addDrop(Material material, String name, int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.GOLD));
            item.setItemMeta(meta);
        }
        drops.add(item);
        return this;
    }

    public Boss addRareDrop(Material material, String name, String lore, int amount, boolean glowing) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text(lore).color(NamedTextColor.GRAY)));
            if (glowing) {
                item.setGlowing(true);
            }
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        drops.add(item);
        return this;
    }

    // Getters

    public String getKey() {
        return key;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public double getAttackDamage() {
        return attackDamage;
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public double getArmor() {
        return armor;
    }

    public double getArmorToughness() {
        return armorToughness;
    }

    public double getDropChance() {
        return dropChance;
    }

    public List<ItemStack> getDrops() {
        return drops;
    }

    public boolean hasAttribute(Attribute attribute) {
        return switch (attribute) {
            case GENERIC_MAX_HEALTH -> maxHealth > 0;
            case GENERIC_ATTACK_DAMAGE -> attackDamage > 0;
            case GENERIC_MOVEMENT_SPEED -> movementSpeed > 0;
            case GENERIC_ARMOR -> armor > 0;
            case GENERIC_ARMOR_TOUGHNESS -> armorToughness > 0;
            default -> false;
        };
    }
}
