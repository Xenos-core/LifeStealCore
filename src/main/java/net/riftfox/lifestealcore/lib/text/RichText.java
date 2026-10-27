/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.text;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class RichText {

    private RichText() {
    }

    static void actionBar(Player player, String text) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(text));
    }

    static void clickable(CommandSender sender, String text, String action, String tooltip, boolean openUrl) {
        BaseComponent[] components = TextComponent.fromLegacyText(text);

        for (BaseComponent component : components) {
            if (action != null && !action.isEmpty()) {
                component.setClickEvent(new ClickEvent(
                        openUrl ? ClickEvent.Action.OPEN_URL : ClickEvent.Action.RUN_COMMAND,
                        !openUrl && !action.startsWith("/") ? "/" + action : action));
            }

            if (tooltip != null && !tooltip.isEmpty()) {
                component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(tooltip))));
            }
        }

        if (sender instanceof Player player) {
            player.spigot().sendMessage(components);
        } else {
            sender.sendMessage(TextComponent.toLegacyText(components));
        }
    }
}
