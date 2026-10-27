package com.tunombre.bossplugin.listener;

import com.tunombre.bossplugin.Main;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Aplicacion de esencias, habilidades activas, brujula y encantamientos.
 */
public class AbilityListener implements Listener {

    private final Main plugin;

    public AbilityListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getHand() != EquipmentSlot.HAND && event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            item = event.getHand() == EquipmentSlot.OFF_HAND
                    ? player.getInventory().getItemInOffHand()
                    : player.getInventory().getItemInMainHand();
        }
        boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (item.getType().isAir()) {
            // Libro en la otra mano: clic derecho en el aire.
            ItemStack other = event.getHand() == EquipmentSlot.OFF_HAND
                    ? player.getInventory().getItemInMainHand()
                    : player.getInventory().getItemInOffHand();
            if (event.getAction() == Action.RIGHT_CLICK_AIR
                    && plugin.getAbilityManager().isActiveBook(other)) {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    plugin.getAbilityManager().saveWaypoint(player);
                } else {
                    plugin.getAbilityManager().tryUseActive(player);
                }
            }
            return;
        }

        if (plugin.getItems().isType(item, "tracking_compass")) {
            Block block = event.getClickedBlock();
            if (block != null && plugin.getEggManager().isEggBlock(block)) {
                return;
            }
            if (rightClick) {
                event.setCancelled(true);
                plugin.getEggManager().useCompass(player);
            }
            return;
        }

        if (plugin.getItems().isType(item, "ability_essence")) {
            if (rightClick) {
                event.setCancelled(true);
                plugin.getAbilityManager().tryApply(player);
            }
            return;
        }

        if (rightClick && plugin.getAbilityManager().isActiveBook(item)) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                plugin.getAbilityManager().saveWaypoint(player);
            } else {
                plugin.getAbilityManager().tryUseActive(player);
            }
        }
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
     * Permite aplicar esencias en la mesa de crafteo:
     * una pila de esencias + un objeto compatible. Se aplican tantos
     * niveles como esencias haya en la pila (hasta el nivel maximo).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.isRepair()) {
            return;
        }
        CraftingInventory inventory = event.getInventory();
        ItemStack essence = null;
        ItemStack target = null;
        int slots = 0;
        int essenceCount = 0;
        for (ItemStack item : inventory.getMatrix()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            slots++;
            if (plugin.getItems().isType(item, "ability_essence")) {
                if (essence == null) {
                    essence = item;
                }
                essenceCount += item.getAmount();
            } else {
                target = item;
            }
        }
        if (slots != 2 || essence == null || target == null || essenceCount <= 0) {
            return;
        }
        ItemStack result = plugin.getAbilityManager().craftResult(target, essence, essenceCount);
        inventory.setResult(result);
    }

    /**
     * La mesa solo consume 1 esencia por crafteo. Si se aplicaron varias
     * de golpe, aqui se descuentan las restantes para que el coste coincida
     * con el nivel entregado.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(CraftItemEvent event) {
        if (event.isCancelled() || !(event.getInventory() instanceof CraftingInventory inventory)) {
            return;
        }
        ItemStack essence = null;
        ItemStack target = null;
        int slots = 0;
        int essenceCount = 0;
        for (ItemStack item : inventory.getMatrix()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            slots++;
            if (plugin.getItems().isType(item, "ability_essence")) {
                if (essence == null) {
                    essence = item;
                }
                essenceCount += item.getAmount();
            } else {
                target = item;
            }
        }
        if (slots != 2 || essence == null || target == null) {
            return;
        }
        int consume = plugin.getAbilityManager().craftConsume(target, essence, essenceCount);
        if (consume <= 1) {
            return;
        }
        // Con shift-click se fabricarian varias copias del mismo nivel.
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        int extra = consume - 1;
        Bukkit.getScheduler().runTask(plugin, () -> {
            int remaining = extra;
            for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack == null || !plugin.getItems().isType(stack, "ability_essence")) {
                    continue;
                }
                int take = Math.min(remaining, stack.getAmount());
                int left = stack.getAmount() - take;
                remaining -= take;
                if (left <= 0) {
                    inventory.setItem(i, null);
                } else {
                    stack.setAmount(left);
                    inventory.setItem(i, stack);
                }
            }
        });
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
