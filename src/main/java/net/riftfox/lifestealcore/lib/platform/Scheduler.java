/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class Scheduler {

    private static final boolean FOLIA = detectFolia();

    private Scheduler() {
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    public static boolean folia() {
        return FOLIA;
    }

    public static void global(Plugin plugin, Runnable work) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, work);
    }

    public static void async(Plugin plugin, Runnable work) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> work.run());
    }

    public static void atEntity(Plugin plugin, Entity entity, Runnable work) {
        entity.getScheduler().run(plugin, scheduled -> work.run(), null);
    }

    public static void atLocation(Plugin plugin, Location location, Runnable work) {
        plugin.getServer().getRegionScheduler().execute(plugin, location, work);
    }

    public static Task later(Plugin plugin, Runnable work, long delayTicks) {
        ScheduledTask scheduled = plugin.getServer()
                .getGlobalRegionScheduler()
                .runDelayed(plugin, handle -> work.run(), Math.max(1L, delayTicks));
        return scheduled::cancel;
    }

    public static Task timer(Plugin plugin, Runnable work, long delayTicks, long periodTicks) {
        ScheduledTask scheduled = plugin.getServer()
                .getGlobalRegionScheduler()
                .runAtFixedRate(plugin, handle -> work.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        return scheduled::cancel;
    }

    public interface Task {
        void cancel();
    }
}
