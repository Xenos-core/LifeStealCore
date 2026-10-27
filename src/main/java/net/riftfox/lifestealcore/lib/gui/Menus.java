/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.gui;

import java.util.List;
import java.util.function.Predicate;
import net.riftfox.lifestealcore.lib.platform.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public final class Menus {

    private Menus() {
    }

    public static void closeAll(Plugin plugin, Predicate<InventoryHolder> filter) {
        List<Player> players = List.copyOf(Bukkit.getOnlinePlayers());
        boolean enabled = plugin.isEnabled();

        for (Player player : players) {
            if (enabled) {
                Scheduler.atEntity(plugin, player, () -> closeIfMatches(player, filter));
            } else {
                try {
                    closeIfMatches(player, filter);
                } catch (RuntimeException ignored) {
                    Bukkit.getLogger().fine("[LifeStealCore] could not close a menu while shutting down: " + ignored);
                }
            }
        }
    }

    private static void closeIfMatches(Player player, Predicate<InventoryHolder> filter) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder != null && filter.test(holder)) {
            player.closeInventory();
        }
    }
}
