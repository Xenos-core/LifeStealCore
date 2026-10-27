/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.platform;

import java.util.Locale;
import net.riftfox.lifestealcore.lib.config.ConfigFile;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

public final class Startup {

    private static final String BANNER_FLAG = "riftfox.lifestealcore.banner";

    private Startup() {
    }

    public static void announce(Plugin plugin) {
        try {
            if (System.getProperty(BANNER_FLAG) == null) {
                System.setProperty(BANNER_FLAG, "1");
                banner();
            }

            line(plugin);
        } catch (Throwable ignored) {
        }
    }

    private static void banner() {
        say("&c&lLifeStealCore &7by &fRiftFox");
        say("&7Take a heart on every kill. Keep it through death and restart.");
    }

    private static void line(Plugin plugin) {
        String name = plugin.getName();
        String version = plugin.getPluginMeta().getVersion();
        Bukkit.getLogger()
                .info("  >> " + name + " v" + version + "   |   " + language(plugin) + "   |   " + platform() + (Scheduler.folia() ? "   |   Folia" : ""));
    }

    private static String language(Plugin plugin) {
        try {
            return ConfigFile.language(plugin);
        } catch (Throwable ignored) {
            return "?";
        }
    }

    private static String platform() {
        String full = Bukkit.getVersion();
        String version = Bukkit.getBukkitVersion();
        int separator = version.indexOf(45);
        if (separator > 0) {
            version = version.substring(0, separator);
        }

        String name = "Bukkit";
        String lowered = full.toLowerCase(Locale.ROOT);
        if (lowered.contains("folia")) {
            name = "Folia";
        } else if (lowered.contains("canvas")) {
            name = "Canvas";
        } else if (lowered.contains("purpur")) {
            name = "Purpur";
        } else if (lowered.contains("paper")) {
            name = "Paper";
        } else if (lowered.contains("spigot")) {
            name = "Spigot";
        }

        return name + " " + version;
    }

    private static void say(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
