/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.gui;

import net.riftfox.lifestealcore.lib.platform.Scheduler;
import net.riftfox.lifestealcore.lib.text.Placeholders;
import net.riftfox.lifestealcore.lib.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class Menu implements InventoryHolder {

    protected final Player viewer;
    private Inventory inventory;
    private boolean creating;

    protected Menu(Player player) {
        this.viewer = player;
    }

    protected abstract String title();

    protected abstract int rows();

    protected abstract void draw();

    protected abstract void onClick(InventoryClickEvent event);

    protected void onClose(InventoryCloseEvent event) {
    }

    protected boolean cancelByDefault() {
        return true;
    }

    public Inventory getInventory() {
        if (this.inventory != null) {
            return this.inventory;
        }

        if (this.creating) {
            throw new IllegalStateException(
                    this.getClass().getSimpleName()
                            + ".title() or .rows() reads the menu's own inventory, which does not exist yet. Use inventoryOrNull() there and handle null.");
        }

        this.creating = true;

        try {
            this.inventory = Bukkit.createInventory(
                    this, this.rows() * 9, Text.chat(Placeholders.apply(this.viewer, this.title())));
            return this.inventory;
        } finally {
            this.creating = false;
        }
    }

    protected Inventory inventoryOrNull() {
        return this.inventory;
    }

    public void open() {
        Placeholders.renderFor(this.viewer, this::draw);
        this.viewer.openInventory(this.getInventory());
    }

    public void refresh() {
        Placeholders.renderFor(this.viewer, this::draw);
        this.viewer.updateInventory();
    }

    protected void later(Runnable work) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());

        try {
            Scheduler.atEntity(plugin, this.viewer, work);
        } catch (RuntimeException ignored) {
            plugin.getLogger().fine("Menu action discarded: " + ignored);
        }
    }

    final void handleClick(InventoryClickEvent event) {
        if (this.cancelByDefault()) {
            event.setCancelled(true);
        }

        this.onClick(event);
    }

    final void handleClose(InventoryCloseEvent event) {
        this.onClose(event);
    }

    protected boolean clickedTopInventory(InventoryClickEvent event) {
        return event.getClickedInventory() != null && event.getClickedInventory().equals(this.getInventory());
    }

    public final boolean isOwnedBy(Plugin plugin) {
        if (plugin == null) {
            return false;
        }

        String myPackage = this.getClass().getPackageName();
        String theirs = plugin.getClass().getPackageName();
        return myPackage.equals(theirs) || myPackage.startsWith(theirs + ".");
    }
}
