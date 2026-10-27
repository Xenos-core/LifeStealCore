/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class Sounds {

    private final Map<String, Cue> cues = new HashMap<>();

    public Sounds(ConfigurationSection section) {
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry != null) {
                    String name = entry.getString("name", "").trim().toLowerCase(Locale.ROOT);
                    if (!name.isEmpty()) {
                        this.cues.put(key, new Cue(name, (float)entry.getDouble("volume", 1.0), (float)entry.getDouble("pitch", 1.0)));
                    }
                }
            }
        }
    }

    public void play(Player player, String key) {
        Cue cue = this.cues.get(key);
        if (cue != null && player != null) {
            player.playSound(player.getLocation(), cue.key(), cue.volume(), cue.pitch());
        }
    }

    private record Cue(String key, float volume, float pitch) {
    }
}
