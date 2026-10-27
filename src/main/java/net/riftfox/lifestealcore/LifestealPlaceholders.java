/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import java.util.Locale;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

final class LifestealPlaceholders extends PlaceholderExpansion {

    private final LifestealPlugin plugin;

    private LifestealPlaceholders(LifestealPlugin plugin) {
        this.plugin = plugin;
    }

    static boolean register(LifestealPlugin plugin) {
        return new LifestealPlaceholders(plugin).register();
    }

    @Override
    public String getIdentifier() {
        return "lifestealcore";
    }

    @Override
    public String getAuthor() {
        return "RiftFox";
    }

    @Override
    public String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String identifier) {
        String key = identifier.toLowerCase(Locale.ROOT);
        switch (key) {
            case "enabled":
                return String.valueOf(this.plugin.lifestealEnabled());
            case "minimum":
                return String.valueOf(this.plugin.minimumHearts());
            case "maximum":
                return String.valueOf(this.plugin.maximumHearts());
            case "per_kill":
                return String.valueOf(this.plugin.heartsPerKill());
            case "start":
                return String.valueOf(this.plugin.startHearts());
            case "limit":
                return String.valueOf(this.plugin.stealsPerDay());
            case "players":
                return String.valueOf(this.plugin.store().size());
            default:
                if (player == null) {
                    return "";
                }

                UUID uuid = player.getUniqueId();
                LifestealStore.Status status = this.plugin.store().status(uuid, System.currentTimeMillis(), this.plugin.limits());
                int hearts = status.hearts() == -1 ? this.plugin.startHearts()
                        : Math.max(this.plugin.minimumHearts(), Math.min(this.plugin.maximumHearts(), status.hearts()));
                switch (key) {
                    case "hearts":
                        return String.valueOf(hearts);
                    case "health":
                        return String.valueOf(hearts * 2);
                    case "today":
                        return String.valueOf(status.today());
                    case "total":
                        return String.valueOf(status.total());
                    case "cooldown":
                        return String.valueOf((status.cooldownMillis() + 999L) / 1000L);
                    case "cooldown_text":
                        return status.cooldownMillis() <= 0L ? noCooldownText() : LifestealPlugin.describe(status.cooldownMillis());
                    case "eliminated":
                        return String.valueOf(status.eliminated());
                    case "alive":
                        return String.valueOf(!status.eliminated());
                    default:
                        return null;
                }
        }
    }

    private String noCooldownText() {
        String text = this.plugin.config().yaml().getString("placeholders.no-cooldown", "");
        return text == null ? "" : text;
    }
}
