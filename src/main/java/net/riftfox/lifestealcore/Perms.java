/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import org.bukkit.permissions.Permissible;

public final class Perms {

    private Perms() {
    }

    public static boolean has(Permissible target, String permission, boolean checkActive) {
        if (target == null || permission == null || permission.isEmpty()) {
            return false;
        }

        return checkActive && !target.isPermissionSet(permission) ? false : target.hasPermission(permission);
    }

    public static boolean permits(Permissible target, String permission) {
        return permission == null || permission.isEmpty() || target != null && target.hasPermission(permission);
    }
}
