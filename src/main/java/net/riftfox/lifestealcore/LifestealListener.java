/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import net.riftfox.lifestealcore.lib.platform.Scheduler;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public final class LifestealListener implements Listener {

    private final LifestealPlugin plugin;

    public LifestealListener(LifestealPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        this.plugin.onKill(victim, victim.getKiller());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Scheduler.atEntity(this.plugin, player, () -> this.plugin.onJoin(player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Scheduler.later(this.plugin, () -> Scheduler.atEntity(this.plugin, player, () -> this.plugin.onRespawn(player)), 1L);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (event.getHand() != null) {
                ItemStack item = event.getItem();
                int value = this.plugin.heartItem() == null ? 0 : this.plugin.heartItem().worthOf(item);
                if (value > 0) {
                    Player player = event.getPlayer();
                    if (action == Action.RIGHT_CLICK_BLOCK && !player.isSneaking()) {
                        Block clicked = event.getClickedBlock();
                        if (clicked != null && clicked.getType().isInteractable()) {
                            return;
                        }
                    }

                    event.setCancelled(true);
                    if (this.plugin.redeemHeart(player, value)) {
                        item.setAmount(item.getAmount() - 1);
                    }
                }
            }
        }
    }
}
