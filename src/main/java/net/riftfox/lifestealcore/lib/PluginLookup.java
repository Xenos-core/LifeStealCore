/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class PluginLookup {

    private PluginLookup() {
    }

    public static Plugin find(String name) {
        return Bukkit.getPluginManager().getPlugin(name);
    }
}
