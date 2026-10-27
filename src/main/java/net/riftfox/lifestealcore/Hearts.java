/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public final class Hearts {

    public static final double PER_HEART = 2.0;
    private final Attribute maxHealth;

    public Hearts(Logger logger) {
        Attribute attribute = byKey("max_health");
        String source = "minecraft:max_health";
        if (attribute == null) {
            attribute = byKey("generic.max_health");
            source = "minecraft:generic.max_health";
        }

        if (attribute == null) {
            attribute = scan();
            source = "a registry scan";
        }

        this.maxHealth = attribute;
        if (attribute == null) {
            logger.severe("This server has no max-health attribute under any known name - hearts cannot be set and the plugin will not start.");
        } else {
            logger.info("Max health resolved through " + source + ".");
        }
    }

    private static Attribute byKey(String key) {
        try {
            return (Attribute)Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Attribute scan() {
        try {
            for (Attribute attribute : Registry.ATTRIBUTE) {
                String name = attribute.getKey().getKey().toLowerCase(Locale.ROOT);
                if (name.equals("max_health") || name.endsWith(".max_health")) {
                    return attribute;
                }
            }

            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public boolean available() {
        return this.maxHealth != null;
    }

    public int of(Player player) {
        AttributeInstance instance = this.instance(player);
        return instance == null ? 0 : (int)Math.round(instance.getBaseValue() / 2.0);
    }

    public boolean apply(Player player, int amount) {
        AttributeInstance instance = this.instance(player);
        if (instance == null) {
            return false;
        }

        double target = Math.max(2.0, amount * 2.0);
        instance.setBaseValue(target);
        if (player.getHealth() > target) {
            player.setHealth(target);
        }

        return true;
    }

    private AttributeInstance instance(Player player) {
        return this.maxHealth != null && player != null && player.isOnline() ? player.getAttribute(this.maxHealth) : null;
    }
}
