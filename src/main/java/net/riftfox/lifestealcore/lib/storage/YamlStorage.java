/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlStorage implements Storage {

    private static final char SEPARATOR = '\u0000';

    private final File folder;
    private final Map<String, Map<String, Map<String, String>>> data = new ConcurrentHashMap<>();
    private final Map<String, Boolean> dirty = new ConcurrentHashMap<>();
    private final Map<String, Boolean> unreadable = new ConcurrentHashMap<>();

    public YamlStorage(File folder) {
        this.folder = folder;
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Could not create folder: " + folder);
        }
    }

    private Map<String, Map<String, String>> load(String namespace) {
        return this.data.computeIfAbsent(namespace, name -> {
            ConcurrentHashMap<String, Map<String, String>> entries = new ConcurrentHashMap<>();
            File file = new File(this.folder, name + ".yml");
            if (!file.exists()) {
                return entries;
            }

            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().pathSeparator(SEPARATOR);

            try {
                yaml.load(file);
            } catch (IOException | InvalidConfigurationException problem) {
                this.unreadable.put(name, Boolean.TRUE);
                Bukkit.getLogger()
                        .severe("[LifeStealCore] " + file.getName() + " could not be read - this file will NOT be overwritten. Your data is still in it: " + problem);
                return entries;
            }

            for (String key : yaml.getKeys(false)) {
                ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section != null) {
                    ConcurrentHashMap<String, String> fields = new ConcurrentHashMap<>();
                    for (String field : section.getKeys(false)) {
                        String value = section.getString(field);
                        if (value != null) {
                            fields.put(field, value);
                        }
                    }

                    entries.put(key, fields);
                }
            }

            return entries;
        });
    }

    @Override
    public Map<String, String> read(String namespace, String key) {
        Map<String, String> fields = this.load(namespace).get(key);
        return fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
    }

    @Override
    public void write(String namespace, String key, Map<String, String> fields) {
        this.load(namespace).put(key, new ConcurrentHashMap<>(fields));
        this.dirty.put(namespace, Boolean.TRUE);
    }

    @Override
    public void delete(String namespace, String key) {
        if (this.load(namespace).remove(key) != null) {
            this.dirty.put(namespace, Boolean.TRUE);
        }
    }

    @Override
    public Map<String, Map<String, String>> readAll(String namespace) {
        LinkedHashMap<String, Map<String, String>> copy = new LinkedHashMap<>();
        this.load(namespace).forEach((key, fields) -> copy.put(key, new LinkedHashMap<>(fields)));
        return copy;
    }

    @Override
    public void flush() {
        for (Map.Entry<String, Map<String, Map<String, String>>> entry : this.data.entrySet()) {
            if (Boolean.TRUE.equals(this.unreadable.get(entry.getKey())) || !Boolean.TRUE.equals(this.dirty.get(entry.getKey()))) {
                continue;
            }

            YamlConfiguration yaml = new YamlConfiguration();
            yaml.options().pathSeparator(SEPARATOR);
            entry.getValue().forEach((key, fields) -> fields.forEach((field, value) -> yaml.set(key + SEPARATOR + field, value)));
            File target = new File(this.folder, entry.getKey() + ".yml");
            File temporary = new File(this.folder, entry.getKey() + ".yml.tmp");

            try {
                yaml.save(temporary);

                try {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException fallback) {
                    Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }

                this.dirty.put(entry.getKey(), Boolean.FALSE);
            } catch (IOException problem) {
                throw new IllegalStateException("Write failed: " + target, problem);
            }
        }
    }

    @Override
    public void close() {
        this.flush();
    }
}
