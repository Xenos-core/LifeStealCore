/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.gui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.riftfox.lifestealcore.lib.sound.Sounds;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class MenuListener implements Listener {

    private static final Map<String, Sounds> sounds = new ConcurrentHashMap<>();
    private final Plugin plugin;

    public MenuListener(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("MenuListener needs the plugin whose menus it serves");
        }

        this.plugin = plugin;
    }

    public static void useSounds(Plugin plugin, Sounds soundHandler) {
        if (plugin != null) {
            if (soundHandler == null) {
                sounds.remove(plugin.getName());
            } else {
                sounds.put(plugin.getName(), soundHandler);
            }
        }
    }

    private Menu ourMenu(InventoryHolder holder) {
        return holder instanceof Menu menu && menu.isOwnedBy(this.plugin) ? menu : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        Menu menu = this.ourMenu(event.getView().getTopInventory().getHolder());
        if (menu != null) {
            this.playClick(event);
            menu.handleClick(event);
        }
    }

    private void playClick(InventoryClickEvent event) {
        Sounds soundHandler = sounds.get(this.plugin.getName());
        if (soundHandler != null && event.getWhoClicked() instanceof Player player) {
            int size = event.getView().getTopInventory().getSize();
            if (event.getRawSlot() >= 0 && event.getRawSlot() < size) {
                ItemStack item = event.getCurrentItem();
                if (item != null && !item.getType().isAir()) {
                    soundHandler.play(player, "click");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        Menu menu = this.ourMenu(event.getView().getTopInventory().getHolder());
        if (menu != null) {
            if (!menu.cancelByDefault()) {
                int size = event.getView().getTopInventory().getSize();

                for (int slot : event.getRawSlots()) {
                    if (slot < size) {
                        event.setCancelled(true);
                        return;
                    }
                }
            } else {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onClickFinal(InventoryClickEvent event) {
        Menu menu = this.ourMenu(event.getView().getTopInventory().getHolder());
        if (menu != null && menu.cancelByDefault() && !event.isCancelled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Menu menu = this.ourMenu(event.getView().getTopInventory().getHolder());
        if (menu != null) {
            menu.handleClose(event);
        }
    }
}
