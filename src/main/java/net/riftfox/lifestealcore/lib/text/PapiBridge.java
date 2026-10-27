/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.text;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

final class PapiBridge {

    private PapiBridge() {
    }

    static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
