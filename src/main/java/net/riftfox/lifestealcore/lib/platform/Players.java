/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.platform;

import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class Players {

    private Players() {
    }

    public static void each(Plugin plugin, Consumer<Player> action) {
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            Scheduler.atEntity(plugin, player, () -> action.accept(player));
        }
    }
}
