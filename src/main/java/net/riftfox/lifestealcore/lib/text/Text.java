/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final boolean RICH = detectRich();

    private Text() {
    }

    public static String toLegacy(String input) {
        if (input == null) {
            return "";
        }

        Matcher matcher = HEX.matcher(input);
        StringBuilder result = new StringBuilder(input.length() + 16);

        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("§x");

            for (char hex : matcher.group(1).toCharArray()) {
                replacement.append('§').append(hex);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }

        matcher.appendTail(result);
        return result.toString().replaceAll("&([0-9a-fA-Fk-orK-OR])", "§$1");
    }

    public static String chat(String input) {
        return toLegacy(input);
    }

    public static String item(String input) {
        return "§r" + toLegacy(input);
    }

    public static List<String> lore(List<String> lines) {
        ArrayList<String> result = new ArrayList<>(lines.size());

        for (String line : lines) {
            result.add(item(line));
        }

        return result;
    }

    public static String apply(String input, Map<String, String> placeholders) {
        if (input != null && !input.isEmpty() && !placeholders.isEmpty()) {
            String output = input;

            for (Entry<String, String> entry : placeholders.entrySet()) {
                String marker = "%" + entry.getKey() + "%";
                if (output.contains(marker)) {
                    output = output.replace(marker, entry.getValue() == null ? "" : entry.getValue());
                }
            }

            return output;
        }

        return input == null ? "" : input;
    }

    public static List<String> apply(List<String> lines, Map<String, String> placeholders) {
        ArrayList<String> result = new ArrayList<>(lines.size());

        for (String line : lines) {
            result.add(apply(line, placeholders));
        }

        return result;
    }

    public static String strip(String input) {
        return toLegacy(input).replaceAll("§[0-9a-fA-Fk-orK-ORx]", "");
    }

    private static boolean detectRich() {
        try {
            Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            CommandSender.class.getMethod("spigot");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            return false;
        }
    }

    public static boolean rich() {
        return RICH;
    }

    public static void actionBar(Player player, String text) {
        if (player != null) {
            if (RICH) {
                RichText.actionBar(player, toLegacy(text));
            } else {
                player.sendMessage(toLegacy(text));
            }
        }
    }

    public static void sendClickable(CommandSender sender, String text, String action, String tooltip) {
        send(sender, text, action, tooltip, false);
    }

    public static void sendLink(CommandSender sender, String text, String action, String tooltip) {
        send(sender, text, action, tooltip, true);
    }

    private static void send(CommandSender sender, String text, String action, String tooltip, boolean openUrl) {
        if (sender != null) {
            if (RICH) {
                RichText.clickable(sender, toLegacy(text), action, tooltip == null ? null : toLegacy(tooltip), openUrl);
            } else {
                sender.sendMessage(toLegacy(text));
            }
        }
    }
}
