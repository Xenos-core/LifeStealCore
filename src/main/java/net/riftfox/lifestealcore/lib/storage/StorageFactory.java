/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.storage;

import java.io.File;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static Storage create(Plugin plugin, ConfigurationSection section) {
        File dataFolder = new File(plugin.getDataFolder(), "data");
        if (section == null) {
            return new YamlStorage(dataFolder);
        }

        String type = section.getString("type", "yaml").trim().toLowerCase(Locale.ROOT);
        String table = section.getString("mysql.table", "lifestealcore_data");

        try {
            switch (type) {
                case "sqlite": {
                    File database = new File(dataFolder, "data.db");
                    if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                        throw new IllegalStateException("Could not create folder: " + dataFolder);
                    }

                    plugin.getLogger().info("Storage: SQLite (" + database.getName() + ")");
                    return new SqlStorage("jdbc:sqlite:" + database.getAbsolutePath(), null, null, table);
                }
                case "mysql": {
                    String host = section.getString("mysql.host", "localhost");
                    int port = section.getInt("mysql.port", 3306);
                    String database = section.getString("mysql.database", "minecraft");
                    String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
                    plugin.getLogger().info("Storage: MySQL (" + host + ":" + port + "/" + database + ")");
                    return new SqlStorage(url, section.getString("mysql.user", "root"), section.getString("mysql.password", ""), table);
                }
                default:
                    return new YamlStorage(dataFolder);
            }
        } catch (RuntimeException problem) {
            plugin.getLogger()
                    .log(
                            Level.SEVERE,
                            "Storage '" + type + "' is unreachable - falling back to YAML. Check the storage block in the configuration.",
                            problem);
            return new YamlStorage(dataFolder);
        }
    }
}
