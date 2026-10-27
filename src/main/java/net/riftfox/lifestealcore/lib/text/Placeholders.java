/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Placeholders {

    private static final String MENUS_PATH = "menus.placeholderapi";
    private static final ThreadLocal<Player> VIEWER = new ThreadLocal<>();
    private static volatile Boolean configured;
    private static volatile Boolean active;
    private static volatile boolean failed;

    private Placeholders() {
    }

    public static void load(ConfigurationSection section) {
        configured = section == null || section.getBoolean(MENUS_PATH, true);
        active = null;
    }

    public static boolean active() {
        Boolean value = active;
        if (value == null) {
            value = decide();
            active = value;
        }

        return value;
    }

    private static boolean decide() {
        if (failed) {
            return false;
        }

        Boolean value = configured;
        if (value == null) {
            value = fromOwnFile();
            configured = value;
        }

        return value && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    private static boolean fromOwnFile() {
        try {
            JavaPlugin plugin = JavaPlugin.getProvidingPlugin(Placeholders.class);
            File file = new File(plugin.getDataFolder(), "config.yml");
            return !file.isFile() || YamlConfiguration.loadConfiguration(file).getBoolean(MENUS_PATH, true);
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    public static void renderFor(Player player, Runnable work) {
        Player previous = VIEWER.get();
        VIEWER.set(player);

        try {
            work.run();
        } finally {
            if (previous == null) {
                VIEWER.remove();
            } else {
                VIEWER.set(previous);
            }
        }
    }

    public static String inMenu(String text) {
        if (text != null && text.indexOf('%') >= 0 && active()) {
            Player viewer = VIEWER.get();
            return viewer == null ? text : resolve(viewer, text);
        }

        return text;
    }

    public static List<String> inMenu(List<String> lines) {
        if (lines != null && !lines.isEmpty() && active()) {
            ArrayList<String> result = null;

            for (int index = 0; index < lines.size(); index++) {
                String text = lines.get(index);
                String resolved = inMenu(text);
                if (resolved != text) {
                    if (result == null) {
                        result = new ArrayList<>(lines);
                    }

                    result.set(index, resolved);
                }
            }

            return result == null ? lines : result;
        }

        return lines;
    }

    public static String apply(Player player, String text) {
        return text != null && text.indexOf('%') >= 0 && active() ? resolve(player, text) : text;
    }

    private static String resolve(Player player, String text) {
        try {
            return PapiBridge.apply(player, text);
        } catch (RuntimeException | LinkageError problem) {
            failed = true;
            active = false;
            Bukkit.getLogger()
                    .warning(
                            "[LifeStealCore] PlaceholderAPI aborted placeholder resolution (" + problem
                                    + ") - unknown placeholders stay as written. Disable menus.placeholderapi: false in "
                                    + "the core config.yml to switch off this bridge permanently.");
            return text;
        }
    }
}
