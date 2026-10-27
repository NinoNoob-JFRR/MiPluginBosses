package com.tunombre.bossplugin.listener;

import com.tunombre.bossplugin.Main;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aplicacion de esencias, habilidades activas, brujula y encantamientos.
 */
public class AbilityListener implements Listener {

    private final Main plugin;
    /** Evita activar dos veces el mismo libro (evento de mano principal y secundaria). */
    private final Map<UUID, Long> lastBookUse = new HashMap<>();

    public AbilityListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!rightClick) {
            return;
        }
        ItemStack held = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        if (plugin.getItems().isType(held, "tracking_compass")) {
            Block block = event.getClickedBlock();
            if (block != null && plugin.getEggManager().isEggBlock(block)) {
                return;
            }
            event.setCancelled(true);
            plugin.getEggManager().useCompass(player);
            return;
        }

        if (plugin.getItems().isType(held, "ability_essence")) {
            event.setCancelled(true);
            plugin.getAbilityManager().tryApply(player);
            return;
        }

        // Libro activo: funciona con clic derecho en el aire o en un bloque y
        // tanto en la mano principal como en la secundaria.
        ItemStack other = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (!plugin.getAbilityManager().isActiveBook(held)
                && !plugin.getAbilityManager().isActiveBook(other)) {
            return;
        }
        if (isDuplicateBookUse(player)) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            plugin.getAbilityManager().saveWaypoint(player);
        } else {
            plugin.getAbilityManager().tryUseActive(player);
        }
    }

    /**
     * El evento de interaccion se dispara una vez por mano. Sin esta guarda,
     * un libro en la mano secundaria se activaria dos veces.
     */
    private boolean isDuplicateBookUse(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastBookUse.get(player.getUniqueId());
        if (last != null && now - last < 250L) {
            return true;
        }
        lastBookUse.put(player.getUniqueId(), now);
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (plugin.getBossManager().isBoss(player)) {
            return;
        }
        plugin.getAbilityManager().onHit(player, victim, event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getAbilityManager().onBlockBreak(event.getPlayer(), event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        plugin.getAbilityManager().onItemDamage(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if ("active_ability".equals(plugin.getItems().itemType(event.getItem()))) {
            event.setCancelled(true);
        }
    }

    /**
     * Permite aplicar esencias en la mesa de crafteo con la dinamica normal:
     * un objeto compatible y una esencia en cada hueco libre que se quiera
     * gastar. Cada hueco con esencia sube 1 nivel y la mesa consume una
     * esencia por hueco, sin trucos ni sobrantes.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.isRepair()) {
            return;
        }
        CraftingInventory inventory = event.getInventory();
        ItemStack essence = null;
        ItemStack target = null;
        int targetSlots = 0;
        int essenceSlots = 0;
        for (ItemStack item : inventory.getMatrix()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (plugin.getItems().isType(item, "ability_essence")) {
                if (essence == null) {
                    essence = item;
                }
                essenceSlots++;
            } else {
                target = item;
                targetSlots++;
            }
        }
        if (essence == null || target == null || targetSlots != 1 || essenceSlots <= 0) {
            return;
        }
        // Si hay mas esencias que niveles disponibles no se muestra resultado:
        // asi la mesa nunca consume esencias que no se van a aprovechar.
        int consume = plugin.getAbilityManager().craftConsume(target, essence, essenceSlots);
        if (consume != essenceSlots) {
            inventory.setResult(null);
            return;
        }
        inventory.setResult(plugin.getAbilityManager().craftResult(target, essence, essenceSlots));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getAbilityManager().isRenaming(player)) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        org.bukkit.Bukkit.getScheduler().runTask(plugin,
                () -> plugin.getAbilityManager().handleRename(player, message));
    }
}
