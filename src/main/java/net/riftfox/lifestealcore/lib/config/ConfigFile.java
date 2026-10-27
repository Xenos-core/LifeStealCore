/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class ConfigFile {

    public static final String DEFAULT_LANGUAGE = "EN";
    private static final String TOGGLE = "config.yml";
    private static final Map<String, String> LANGUAGES = new ConcurrentHashMap<>();
    private static final Set<String> warned = ConcurrentHashMap.newKeySet();
    private static final Set<String> ALREADY_REPORTED = ConcurrentHashMap.newKeySet();
    private static final Set<String> ALREADY_WRITTEN = ConcurrentHashMap.newKeySet();

    private final Plugin plugin;
    private final String path;
    private final String realPath;
    private final File file;
    private boolean broken;
    private YamlConfiguration config;
    private String[] ownSections = new String[0];

    public ConfigFile(Plugin plugin, String path) {
        this(plugin, path, true);
    }

    private ConfigFile(Plugin plugin, String path, boolean useTranslation) {
        this.plugin = plugin;
        this.path = path;
        this.realPath = useTranslation ? "lang/" + language(plugin) + "/" + path : path;
        this.file = new File(plugin.getDataFolder(), this.realPath);
    }

    public static ConfigFile shared(Plugin plugin, String path) {
        return new ConfigFile(plugin, path, false);
    }

    public static String language(Plugin plugin) {
        return LANGUAGES.computeIfAbsent(plugin.getName(), name -> readLanguage(name, plugin));
    }

    private static String readLanguage(String name, Plugin plugin) {
        File file = new File(LANGUAGES.get(name), TOGGLE);
        if (!file.exists()) {
            plugin.getDataFolder().mkdirs();
            String defaultLanguage = oldLanguage(plugin);
            if (plugin.getResource(TOGGLE) != null) {
                plugin.saveResource(TOGGLE, false);
                if (defaultLanguage != null) {
                    setLanguage(plugin, file, defaultLanguage);
                }
            } else {
                writeDefaultToggle(plugin, file);
                if (defaultLanguage != null) {
                    setLanguage(plugin, file, defaultLanguage);
                }
            }
        }

        String language = YamlConfiguration.loadConfiguration(file).getString("language", "EN");
        return (language != null && !language.isBlank()) ? language.trim().toUpperCase(Locale.ROOT) : "EN";
    }

    private static String oldLanguage(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "settings.yml");
        if (!file.isFile()) {
            return null;
        }

        String language = YamlConfiguration.loadConfiguration(file).getString("language");
        if (language != null && !language.isBlank()) {
            String trimmed = language.trim().toUpperCase(Locale.ROOT);
            return trimmed.equals("EN") ? null : trimmed;
        }

        return null;
    }

    private static void setLanguage(Plugin plugin, File file, String language) {
        try {
            String current = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Files.writeString(file.toPath(), current.replace("language: EN", "language: " + language), StandardCharsets.UTF_8);
            plugin.getLogger().info("Carried your old language setting (" + language + ") over into config.yml.");
        } catch (IOException problem) {
            plugin.getLogger().warning("Could not carry the old language setting over: " + problem + " - set language: " + language + " in config.yml yourself.");
        }
    }

    private static void writeDefaultToggle(Plugin plugin, File file) {
        String content = "# =====================================================================\n"
                + "#  Language / Sprache\n"
                + "# =====================================================================\n"
                + "#  EN  everything this plugin writes, and every comment in its\n"
                + "#      files, is English.  ->  lang/EN/\n"
                + "#  DE  alles, was dieses Plugin schreibt, und jeder Kommentar in\n"
                + "#      seinen Konfigurationsdateien ist deutsch.  ->  lang/DE/\n"
                + "#\n"
                + "#  Change this BEFORE you configure anything. Switching later creates\n"
                + "#  the other folder from scratch - your settings do not move across.\n"
                + "#\n"
                + "#  Stelle das um, BEVOR du etwas einrichtest. Ein spaeterer Wechsel legt\n"
                + "#  den anderen Ordner neu an - deine Einstellungen wandern nicht mit.\n"
                + "# =====================================================================\n"
                + "language: EN\n"
                + "\n"
                + "# =====================================================================\n"
                + "#  Menus / Menues\n"
                + "# =====================================================================\n"
                + "menus:\n"
                + "  #  Hand every menu name, lore line and title to PlaceholderAPI once\n"
                + "  #  our own %placeholders% are filled in. That is what makes another\n"
                + "  #  plugin's values - %nexo_ruby%, %vault_eco_balance%,\n"
                + "  #  %luckperms_prefix% - work in any menu file of this plugin.\n"
                + "  #  Needs PlaceholderAPI installed; without it this does nothing.\n"
                + "  #  Costs nothing on lines that hold no percent sign.\n"
                + "  #\n"
                + "  #  Uebergibt jeden Menuenamen, jede Lorezeile und jeden Titel an\n"
                + "  #  PlaceholderAPI, nachdem unsere eigenen %Platzhalter% eingesetzt\n"
                + "  #  sind. Damit funktionieren fremde Werte in jeder Menuedatei\n"
                + "  #  dieses Plugins. Ohne PlaceholderAPI passiert nichts.\n"
                + "  placeholderapi: true\n"
                + "\n";

        try {
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException problem) {
            plugin.getLogger().warning("config.yml could not be created: " + problem + " - defaulting to EN.");
        }
    }

    public ConfigFile load(boolean appendDefaults) {
        this.ownSections = appendDefaults ? new String[0] : new String[0];
        return load();
    }

    private static boolean belongsToOwner(String section, Set<String> owned) {
        for (String ownedSection : owned) {
            if (section.equals(ownedSection) || section.startsWith(ownedSection + ".")) {
                return true;
            }
        }

        return false;
    }

    private Set<String> presentOwnSections() {
        HashSet<String> present = new HashSet<>();

        for (String section : this.ownSections) {
            if (this.config.contains(section)) {
                present.add(section);
            }
        }

        return present;
    }

    public ConfigFile load() {
        this.fetchOldFile();
        if (!this.file.exists()) {
            this.file.getParentFile().mkdirs();
            String source = this.source();
            if (source != null) {
                write(source);
            }
        }

        this.config = new YamlConfiguration();

        try {
            if (this.file.isFile()) {
                this.config.load(this.file);
            }
        } catch (InvalidConfigurationException problem) {
            this.broken = true;
            this.plugin.getLogger().severe(this.realPath + " has a YAML error and was NOT loaded:");
            this.plugin.getLogger().severe("  " + problem.getMessage());
            this.plugin.getLogger().severe("Your file is left untouched - the defaults are NOT written over it. Fix the error and reload.");
            String source = this.source();
            if (source != null) {
                try (InputStream stream = this.plugin.getResource(source)) {
                    if (stream != null) {
                        this.config.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    }
                } catch (IOException | InvalidConfigurationException ignored) {
                }
            }
        } catch (IOException problem) {
            this.broken = true;
            this.plugin.getLogger().severe("Could not read " + this.realPath + ": " + problem);
        }

        if (!this.broken) {
            this.addMissing();
        }

        return this;
    }

    private String source() {
        if (this.plugin.getResource(this.realPath) != null) {
            return this.realPath;
        }

        String translated = "lang/EN/" + this.path;
        if (!this.realPath.equals(translated) && this.plugin.getResource(translated) != null) {
            String key = this.plugin.getName() + "/" + language(this.plugin);
            if (warned.add(key)) {
                this.plugin.getLogger().warning(
                        "No " + language(this.plugin) + " translation shipped with this plugin - using English. Set language: EN in config.yml to silence this.");
            }

            return translated;
        }

        return this.plugin.getResource(this.path) != null ? this.path : null;
    }

    private void write(String source) {
        if (source.equals(this.realPath)) {
            this.plugin.saveResource(this.realPath, false);
        } else {
            try (InputStream stream = this.plugin.getResource(source)) {
                if (stream != null) {
                    Files.copy(stream, this.file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException problem) {
                this.plugin.getLogger().warning(this.realPath + " could not be created: " + problem);
            }
        }
    }

    private void addMissing() {
        boolean fromSource = this.addMissingFrom(this.source());
        String translated = "lang/EN/" + this.path;
        if (!translated.equals(this.source())) {
            fromSource |= this.addMissingFrom(translated);
        }

        boolean reported = ALREADY_WRITTEN.add(this.plugin.getName() + "/" + this.realPath);
        if (fromSource && reported) {
            this.plugin.getLogger().info("Added new options to " + this.realPath + " (your values were kept).");
            this.save();
        }
    }

    private boolean addMissingFrom(String source) {
        if (source == null) {
            return false;
        }

        InputStream stream = this.plugin.getResource(source);
        if (stream == null) {
            return false;
        }

        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        Set<String> owned = this.presentOwnSections();
        boolean changed = false;

        for (String section : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(section) && !this.config.contains(section) && !belongsToOwner(section, owned)) {
                this.config.set(section, defaults.get(section));
                this.config.setComments(section, defaults.getComments(section));
                changed = true;
            }
        }

        return changed;
    }

    private void fetchOldFile() {
        if (!this.realPath.equals(this.path)) {
            File legacy = new File(this.plugin.getDataFolder(), this.path);
            if (legacy.isFile()) {
                File translation = new File(this.plugin.getDataFolder(), "lang/EN/" + this.path);
                if (translation.exists()) {
                    if (ALREADY_REPORTED.add(this.plugin.getName() + "/" + this.path)) {
                        this.plugin.getLogger().warning(
                                this.path + " in the plugin folder is NOT read any more. Edit " + this.realPath
                                        + " instead - your changes there are ignored.");
                    }
                } else {
                    try {
                        translation.getParentFile().mkdirs();
                        Files.move(legacy.toPath(), translation.toPath(), StandardCopyOption.ATOMIC_MOVE);
                        this.plugin.getLogger().info("Moved " + this.path + " to lang/EN/" + this.path + " - that is where anything with text in it lives now.");

                        if (!"EN".equals(language(this.plugin))) {
                            this.plugin.getLogger().warning(
                                    "You run language: " + language(this.plugin)
                                            + ", so " + this.realPath
                                            + " was created fresh from the plugin. Anything you had changed is kept in lang/EN/"
                                            + this.path
                                            + " - copy it across if you need it.");
                        }
                    } catch (IOException problem) {
                        this.plugin.getLogger().warning("Could not move " + this.path + ": " + problem + " - starting from the packaged defaults.");
                    }
                }
            }
        }
    }

    public YamlConfiguration yaml() {
        if (this.config == null) {
            throw new IllegalStateException("Call load() first: " + this.realPath);
        }

        return this.config;
    }

    public void save() {
        if (this.broken) {
            this.plugin.getLogger().warning("Not writing " + this.realPath + " - it has a YAML error and your version would be lost.");
        } else {
            try {
                this.file.getParentFile().mkdirs();
                this.config.save(this.file);
            } catch (IOException problem) {
                this.plugin.getLogger().warning("Could not write " + this.realPath + ": " + problem);
            }
        }
    }

    public File file() {
        return this.file;
    }

    public String path() {
        return this.path;
    }

    public String realPath() {
        return this.realPath;
    }
}
