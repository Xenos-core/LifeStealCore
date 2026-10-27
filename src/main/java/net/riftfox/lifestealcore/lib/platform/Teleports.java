/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.platform;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class Teleports {

    private Teleports() {
    }

    public static void to(Plugin plugin, Entity entity, Location destination, Consumer<Boolean> result) {
        if (entity == null || destination == null) {
            report(result, false);
            return;
        }

        try {
            entity.teleportAsync(destination).thenAccept(success -> report(result, Boolean.TRUE.equals(success)));
        } catch (RuntimeException failed) {
            plugin.getLogger().warning("teleportAsync failed (" + failed + ") - teleporting synchronously.");
            try {
                report(result, entity.teleport(destination));
            } catch (RuntimeException alsoFailed) {
                plugin.getLogger().warning("Teleport failed: " + alsoFailed);
                report(result, false);
            }
        }
    }

    private static void report(Consumer<Boolean> callback, boolean value) {
        if (callback != null) {
            callback.accept(value);
        }
    }
}
