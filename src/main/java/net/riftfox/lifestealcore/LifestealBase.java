/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import java.util.Map;
import net.riftfox.lifestealcore.lib.PluginModule;
import net.riftfox.lifestealcore.lib.config.ConfigFile;
import net.riftfox.lifestealcore.lib.platform.Startup;
import net.riftfox.lifestealcore.lib.text.Text;
import org.bukkit.command.CommandSender;

public abstract class LifestealBase extends PluginModule {


    private ConfigFile config;

    protected abstract void start() throws Exception;

    protected void stop() {
    }

    public final void onEnable() {
        this.config = new ConfigFile(this, "settings.yml");
        this.loadSettings();

        try {
            this.start();
            Startup.announce(this);
        } catch (Exception problem) {
            this.getLogger().severe("Startup failed - check plugins/" + this.getName() + "/" + this.config.realPath());
            problem.printStackTrace();
            this.disableSelf();
        }
    }

    public final void onDisable() {
        this.stop();
    }

    protected final void loadSettings() {
        this.config.load(true);
    }

    protected ConfigFile config() {
        return this.config;
    }

    protected String prefix() {
        String prefix = this.config.yaml().getString("prefix", "");
        return prefix == null ? "" : prefix;
    }

    protected void message(CommandSender sender, String key, Map<String, String> placeholders) {
        String template = this.config.yaml().getString("messages." + key, "");
        if (template != null && !template.isEmpty()) {
            sender.sendMessage(Text.chat(Text.apply(this.prefix() + template, placeholders)));
        }
    }

    protected void message(CommandSender sender, String key) {
        this.message(sender, key, Map.of());
    }
}
