/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import java.util.Map;
import net.riftfox.lifestealcore.lib.item.Items;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class HeartItem {

    private final LifestealPlugin plugin;
    private final NamespacedKey key;
    private ItemStack template;
    private int worth = 1;

    public HeartItem(LifestealPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "heart");
    }

    public boolean reload(ConfigurationSection section) {
        this.template = null;
        this.worth = 1;
        if (section == null) {
            return false;
        }

        this.worth = Math.max(1, section.getInt("hearts", 1));
        ItemStack item = Items.read(section, this.plugin.getLogger(), Map.of("hearts", String.valueOf(this.worth)));
        if (item == null) {
            return false;
        }

        this.template = this.stamp(item, this.worth);
        return true;
    }

    public boolean ready() {
        return this.template != null;
    }

    public int worth() {
        return this.worth;
    }

    public ItemStack make(int amount) {
        if (this.template == null) {
            return null;
        }

        ItemStack clone = this.template.clone();
        clone.setAmount(Math.max(1, Math.min(amount, this.template.getMaxStackSize())));
        return clone;
    }

    public int worthOf(ItemStack item) {
        if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return 0;
            }

            PersistentDataContainer container = meta.getPersistentDataContainer();
            Integer value = (Integer)container.get(this.key, PersistentDataType.INTEGER);
            return value != null && value > 0 ? value : 0;
        }

        return 0;
    }

    private ItemStack stamp(ItemStack item, int value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.getPersistentDataContainer().set(this.key, PersistentDataType.INTEGER, value);
        item.setItemMeta(meta);
        return item;
    }
}
