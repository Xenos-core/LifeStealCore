/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public abstract class PluginModule extends JavaPlugin {

    protected final void disableSelf() {
        this.getServer().getPluginManager().disablePlugin(this);
    }

    protected final boolean owns(PluginCommand command) {
        return command != null && this.equals(command.getPlugin());
    }
}
