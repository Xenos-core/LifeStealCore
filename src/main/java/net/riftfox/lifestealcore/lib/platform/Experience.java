/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.platform;

import java.lang.reflect.Method;
import org.bukkit.entity.Player;

public final class Experience {

    private static volatile Method giveExpWithMending = findGiveExpWithMending();

    private Experience() {
    }

    private static Method findGiveExpWithMending() {
        try {
            return Player.class.getMethod("giveExp", int.class, boolean.class);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    public static boolean supportsMending() {
        return giveExpWithMending != null;
    }

    public static void give(Player player, int amount) {
        if (player == null || amount <= 0) {
            return;
        }

        Method method = giveExpWithMending;
        if (method != null) {
            try {
                method.invoke(player, amount, true);
                return;
            } catch (ReflectiveOperationException failed) {
                giveExpWithMending = null;
            }
        }

        player.giveExp(amount);
    }
}
