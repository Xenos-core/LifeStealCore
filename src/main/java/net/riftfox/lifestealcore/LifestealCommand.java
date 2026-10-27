/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.riftfox.lifestealcore.lib.platform.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class LifestealCommand implements CommandExecutor, TabCompleter {

    private final LifestealPlugin plugin;

    public LifestealCommand(LifestealPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            this.status(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status":
            case "me":
                this.status(sender);
                break;
            case "check":
            case "info":
                this.check(sender, args.length > 1 ? args[1] : "");
                break;
            case "give":
                this.give(sender, args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : "1");
                break;
            case "set":
                this.set(sender, args.length > 1 ? args[1] : "", args.length > 2 ? args[2] : "");
                break;
            case "reload":
                this.reload(sender);
                break;
            case "help":
            case "?":
                this.help(sender);
                break;
            default:
                this.plugin.message(sender, "unknown-subcommand", Map.of("input", args[0]));
        }

        return true;
    }

    private void status(CommandSender sender) {
        if (sender instanceof Player player) {
            if (this.allowed(sender, this.plugin.usePermission())) {
                this.show(sender, this.plugin.statusValues(player.getUniqueId(), player.getName()));
            }
        } else {
            this.plugin.message(sender, "players-only");
        }
    }

    private void check(CommandSender sender, String target) {
        if (this.allowed(sender, this.plugin.adminPermission())) {
            if (target.isEmpty()) {
                this.plugin.message(sender, "usage-check");
            } else {
                UUID uuid = this.resolve(target);
                if (uuid == null) {
                    this.plugin.message(sender, "unknown-player", Map.of("player", target));
                } else {
                    this.show(sender, this.plugin.statusValues(uuid, this.displayName(uuid, target)));
                }
            }
        }
    }

    private void show(CommandSender sender, Map<String, String> values) {
        this.plugin.message(sender, "status-header", values);
        this.plugin.message(sender, "status-hearts", values);
        this.plugin.message(sender, "status-today", values);
        this.plugin.message(sender, "status-total", values);
        this.plugin.message(sender, "0".equals(values.get("seconds")) ? "status-ready" : "status-cooldown", values);
    }

    private void give(CommandSender sender, String name, String amount) {
        if (this.allowed(sender, this.plugin.adminPermission())) {
            if (name.isEmpty()) {
                this.plugin.message(sender, "usage-give");
            } else {
                int count = number(amount, 1);
                if (count <= 0) {
                    this.plugin.message(sender, "bad-number", Map.of("input", amount));
                } else {
                    Player player = Bukkit.getPlayerExact(name);
                    if (player == null) {
                        this.plugin.message(sender, "player-offline", Map.of("player", name));
                    } else if (this.plugin.heartItem() != null && this.plugin.heartItem().ready()) {
                        Map<String, String> values = Map.of(
                                "player", player.getName(),
                                "amount", String.valueOf(count),
                                "hearts", String.valueOf(this.plugin.heartItem().worth()));
                        Scheduler.atEntity(
                                this.plugin, player, () -> {
                                    this.plugin.giveHearts(player, count);
                                    this.plugin.message(player, "heart-received", values);
                                    this.plugin.sounds().play(player, "heart");
                                });
                        this.plugin.message(sender, "give-done", values);
                    } else {
                        this.plugin.message(sender, "item-unavailable");
                    }
                }
            }
        }
    }

    private void set(CommandSender sender, String name, String amount) {
        if (this.allowed(sender, this.plugin.adminPermission())) {
            if (!name.isEmpty() && !amount.isEmpty()) {
                int value = number(amount, -1);
                if (value < 0) {
                    this.plugin.message(sender, "bad-number", Map.of("input", amount));
                } else {
                    UUID uuid = this.resolve(name);
                    if (uuid == null) {
                        this.plugin.message(sender, "unknown-player", Map.of("player", name));
                    } else {
                        Player player = Bukkit.getPlayer(uuid);
                        String display = this.displayName(uuid, name);
                        this.plugin.setHearts(uuid, display, player, value);
                        this.plugin.message(
                                sender,
                                "set-done",
                                Map.of(
                                        "player", display,
                                        "hearts", String.valueOf(this.plugin.storedHearts(uuid)),
                                        "minimum", String.valueOf(this.plugin.minimumHearts()),
                                        "maximum", String.valueOf(this.plugin.maximumHearts())));
                    }
                }
            } else {
                this.plugin.message(sender, "usage-set");
            }
        }
    }

    private void reload(CommandSender sender) {
        if (this.allowed(sender, this.plugin.adminPermission())) {
            this.plugin.reload();
            this.plugin.message(sender, "reloaded");
        }
    }

    private void help(CommandSender sender) {
        this.plugin.message(sender, "usage-status");
        if (Perms.permits(sender, this.plugin.adminPermission())) {
            this.plugin.message(sender, "usage-check");
            this.plugin.message(sender, "usage-give");
            this.plugin.message(sender, "usage-set");
            this.plugin.message(sender, "usage-reload");
        }
    }

    private static int number(String input, int fallback) {
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException | NullPointerException ignored) {
            return fallback;
        }
    }

    private UUID resolve(String name) {
        Player player = Bukkit.getPlayerExact(name);
        return player != null ? player.getUniqueId() : this.plugin.store().find(name);
    }

    private String displayName(UUID uuid, String name) {
        String stored = this.plugin.store().nameOf(uuid);
        return stored.isEmpty() ? name : stored;
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (Perms.permits(sender, permission)) {
            return true;
        }

        this.plugin.message(sender, "no-permission", Map.of("permission", permission));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        ArrayList<String> suggestions = new ArrayList<>();
        boolean admin = Perms.permits(sender, this.plugin.adminPermission());

        if (args.length == 1) {
            suggestions.add("status");
            if (admin) {
                suggestions.add("check");
                suggestions.add("give");
                suggestions.add("set");
                suggestions.add("reload");
            }
        } else if (args.length == 2 && admin) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (subcommand.equals("check") || subcommand.equals("info") || subcommand.equals("give") || subcommand.equals("set")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    suggestions.add(player.getName());
                }
            }
        } else if (args.length == 3 && admin) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (subcommand.equals("give")) {
                suggestions.add("1");
            } else if (subcommand.equals("set")) {
                suggestions.add(String.valueOf(this.plugin.startHearts()));
                suggestions.add(String.valueOf(this.plugin.maximumHearts()));
                suggestions.add(String.valueOf(this.plugin.minimumHearts()));
            }
        }

        if (suggestions.isEmpty()) {
            return Collections.emptyList();
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        ArrayList<String> filtered = new ArrayList<>();

        for (String suggestion : suggestions) {
            if (suggestion.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                filtered.add(suggestion);
            }
        }

        return filtered;
    }
}
