/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import java.net.InetSocketAddress;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.riftfox.lifestealcore.lib.item.Items;
import net.riftfox.lifestealcore.lib.platform.Players;
import net.riftfox.lifestealcore.lib.platform.Scheduler;
import net.riftfox.lifestealcore.lib.storage.Storage;
import net.riftfox.lifestealcore.lib.storage.StorageFactory;
import net.riftfox.lifestealcore.lib.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public final class LifestealPlugin extends LifestealBase {

    private Storage storage;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "LifeStealCore-Save"));
    private LifestealStore store;
    private Hearts hearts;
    private HeartItem heartItem;
    private Sounds sounds;
    private Scheduler.Task saveTask;
    private boolean enabled;
    private String usePermission = "";
    private String stealPermission = "";
    private String adminPermission = "";
    private String mutePermission = "";
    private String exemptPermission = "";
    private boolean worldWhitelist;
    private Set<String> worldList = Set.of();
    private int startHearts;
    private int minimumHearts;
    private int maximumHearts;
    private int heartsPerKill;
    private boolean victimLosesWhenKillerIsFull;
    private Elimination elimination = Elimination.BAN;
    private long banMinutes;
    private String banCommand = "";
    private String banReason = "";
    private GameMode spectatorReturn = GameMode.SURVIVAL;
    private long cooldownMillis;
    private long pairCooldownMillis;
    private int pairLimitPerDay;
    private int stealsPerDay;
    private int minimumPlaytimeMinutes;
    private boolean blockSameAddress;
    private boolean broadcastSteal;
    private boolean broadcastElimination;
    private boolean tellVictim;
    private boolean logToConsole;
    private ZoneId zone = ZoneId.systemDefault();
    private long forgetAfterMillis;

    @Override
    protected void start() throws Exception {
        this.hearts = new Hearts(this.getLogger());
        if (!this.hearts.available()) {
            throw new IllegalStateException("no max-health attribute on this server");
        }

        this.applySettings();
        this.storage = StorageFactory.create(this, this.config().yaml().getConfigurationSection("storage"));
        this.store = new LifestealStore(this, this.storage, this.writer);
        this.store.load();
        this.store.forget(System.currentTimeMillis(), this.forgetAfterMillis, this.startHearts);
        this.getServer().getPluginManager().registerEvents(new LifestealListener(this), this);

        LifestealCommand command = new LifestealCommand(this);
        if (this.getCommand("lifesteal") != null) {
            this.getCommand("lifesteal").setExecutor(command);
            this.getCommand("lifesteal").setTabCompleter(command);
        } else {
            this.getLogger().warning("Command /lifesteal is missing from plugin.yml.");
        }

        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            if (LifestealPlaceholders.register(this)) {
                this.getLogger().info("PlaceholderAPI hooked: %lifestealcore_...% is available.");
            } else {
                this.getLogger().warning("PlaceholderAPI is installed but refused the expansion.");
            }
        }

        for (Player player : this.getServer().getOnlinePlayers()) {
            Scheduler.atEntity(this, player, () -> this.onJoin(player));
        }

        long interval = Math.max(30, this.config().yaml().getInt("data.save-interval-seconds", 300)) * 20L;
        this.saveTask = Scheduler.timer(this, () -> {
            this.store.forget(System.currentTimeMillis(), this.forgetAfterMillis, this.startHearts);
            this.store.saveAll();
        }, interval, interval);
        this.getLogger()
                .info(
                        "LifeStealCore "
                                + (this.enabled ? "armed" : "disabled")
                                + " - "
                                + this.store.size()
                                + " player(s) with a heart count"
                                + (Scheduler.folia() ? " (Folia)" : "")
                                + ".");
    }

    @Override
    protected void stop() {
        if (this.saveTask != null) {
            this.saveTask.cancel();
            this.saveTask = null;
        }

        if (this.store != null) {
            this.store.shutdown();
        }

        HandlerList.unregisterAll(this);
    }

    public void reload() {
        this.loadSettings();
        this.applySettings();
    }

    private void applySettings() {
        YamlConfiguration yaml = this.config().yaml();
        this.enabled = yaml.getBoolean("enabled", true);
        this.usePermission = trimmed(yaml.getString("permissions.use", "lifestealcore.use"));
        this.stealPermission = trimmed(yaml.getString("permissions.steal", "lifestealcore.steal"));
        this.adminPermission = trimmed(yaml.getString("permissions.admin", "lifestealcore.admin"));
        this.mutePermission = trimmed(yaml.getString("permissions.mute", "lifestealcore.mute"));
        this.exemptPermission = trimmed(yaml.getString("permissions.exempt", "lifestealcore.exempt"));
        this.worldWhitelist = "whitelist".equalsIgnoreCase(trimmed(yaml.getString("worlds.mode", "blacklist")));

        HashSet<String> collected = new HashSet<>();
        for (String world : yaml.getStringList("worlds.list")) {
            if (world != null && !world.isBlank()) {
                collected.add(world.trim().toLowerCase(Locale.ROOT));
            }
        }

        this.worldList = Set.copyOf(collected);
        this.maximumHearts = Math.max(1, yaml.getInt("hearts.maximum", 20));
        this.minimumHearts = clampInt(yaml.getInt("hearts.minimum", 1), 0, this.maximumHearts);
        this.startHearts = clampInt(yaml.getInt("hearts.default", 10), this.minimumHearts, this.maximumHearts);
        this.heartsPerKill = Math.max(1, yaml.getInt("hearts.per-kill", 1));
        this.victimLosesWhenKillerIsFull = yaml.getBoolean("hearts.victim-loses-when-killer-is-full", true);

        if (yaml.getInt("hearts.minimum", 1) > yaml.getInt("hearts.maximum", 20)) {
            this.getLogger()
                    .warning("hearts.minimum is above hearts.maximum in " + this.config().realPath()
                            + " - the minimum was lowered to " + this.minimumHearts + ".");
        }

        this.elimination = this.readElimination(yaml.getString("elimination.mode", "ban"));
        this.banMinutes = Math.max(0L, yaml.getLong("elimination.ban.minutes", 1440L));
        this.banCommand = trimmed(yaml.getString("elimination.ban.command", ""));
        this.banReason = trimmed(yaml.getString("elimination.ban.reason", "You ran out of hearts."));
        this.spectatorReturn = this.readGameMode(yaml.getString("elimination.spectator.return-gamemode", "SURVIVAL"));
        this.cooldownMillis = yaml.getBoolean("cooldown.enabled", true)
                ? (long)(Math.max(0.0, yaml.getDouble("cooldown.minutes", 1.0)) * 60000.0)
                : 0L;
        this.pairCooldownMillis = (long)(Math.max(0.0, yaml.getDouble("abuse.pair-cooldown-minutes", 720.0)) * 60000.0);
        this.pairLimitPerDay = Math.max(0, yaml.getInt("abuse.pair-limit-per-day", 1));
        this.stealsPerDay = Math.max(0, yaml.getInt("abuse.steals-per-day", 0));
        this.minimumPlaytimeMinutes = Math.max(0, yaml.getInt("abuse.minimum-victim-playtime-minutes", 30));
        this.blockSameAddress = yaml.getBoolean("abuse.block-same-address", true);
        this.broadcastSteal = yaml.getBoolean("announce.broadcast-steal", false);
        this.broadcastElimination = yaml.getBoolean("announce.broadcast-elimination", true);
        this.tellVictim = yaml.getBoolean("announce.tell-victim", true);
        this.logToConsole = yaml.getBoolean("announce.log-to-console", true);
        this.zone = this.readZone(yaml.getString("data.timezone", ""));
        this.forgetAfterMillis = Math.max(0L, yaml.getLong("data.forget-after-days", 60L)) * 86400000L;
        this.sounds = new Sounds(yaml.getConfigurationSection("sounds"));

        if (this.heartItem == null) {
            this.heartItem = new HeartItem(this);
        }

        if (!this.heartItem.reload(yaml.getConfigurationSection("heart-item"))) {
            this.getLogger().warning("The heart item could not be built from " + this.config().realPath()
                    + " - /lifesteal give will refuse until it is fixed.");
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clampInt(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private Elimination readElimination(String mode) {
        String normalized = trimmed(mode).toUpperCase(Locale.ROOT);

        try {
            return Elimination.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            this.getLogger().warning("elimination.mode '" + mode + "' is not ban, spectator or kick - using ban.");
            return Elimination.BAN;
        }
    }

    private GameMode readGameMode(String mode) {
        String normalized = trimmed(mode).toUpperCase(Locale.ROOT);

        try {
            return GameMode.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            this.getLogger().warning("elimination.spectator.return-gamemode '" + mode + "' is not a game mode - using SURVIVAL.");
            return GameMode.SURVIVAL;
        }
    }

    private ZoneId readZone(String value) {
        if (value != null && !value.isBlank()) {
            try {
                return ZoneId.of(value.trim());
            } catch (DateTimeException ignored) {
                this.getLogger().warning("data.timezone '" + value + "' is not a known zone - using " + ZoneId.systemDefault() + ".");
                return ZoneId.systemDefault();
            }
        }

        return ZoneId.systemDefault();
    }

    public void onKill(Player killer, Player victim) {
        if (this.enabled && victim != null && this.store != null) {
            if (!killer.getUniqueId().equals(victim.getUniqueId())) {
                if (this.worldAllowed(victim.getWorld().getName())) {
                    if (Perms.permits(victim, this.stealPermission)) {
                        if (!Perms.has(killer, this.exemptPermission, false)) {
                            HashMap<String, String> values = new HashMap<>(4);
                            values.put("victim", killer.getName());
                            values.put("killer", victim.getName());
                            if (this.minimumPlaytimeMinutes > 0 && playtimeMinutes(victim) < this.minimumPlaytimeMinutes) {
                                values.put("minutes", String.valueOf(this.minimumPlaytimeMinutes));
                                this.refuse(victim, "refused-playtime", values);
                            } else if (this.blockSameAddress && sameAddress(victim, killer)) {
                                this.refuse(victim, "refused-address", values);
                            } else {
                                long timestamp = System.currentTimeMillis();
                                LifestealStore.Ticket ticket = this.store.reserve(victim.getUniqueId(), victim.getName(), killer.getUniqueId(), timestamp, this.limits());
                                if (!ticket.ok()) {
                                    values.put("seconds", String.valueOf(ticket.waitSeconds()));
                                    values.put("time", describe(ticket.waitMillis()));
                                    values.put("limit", String.valueOf(this.stealsPerDay));

                                    this.refuse(victim, switch (ticket.why()) {
                                        case COOLDOWN -> "refused-cooldown";
                                        case PAIR_COOLDOWN -> "refused-pair";
                                        case PAIR_LIMIT -> "refused-pair-limit";
                                        case DAILY_LIMIT -> "refused-daily";
                                        default -> "";
                                    }, values);
                                } else {
                                    this.transfer(ticket, victim, killer);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void transfer(LifestealStore.Ticket ticket, Player killer, Player victim) {
        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();
        int killerCount = this.storedHearts(killerId);
        int victimCount = this.storedHearts(victimId);
        int toTake = Math.min(this.heartsPerKill, this.maximumHearts - killerCount);
        if (toTake <= 0 && !this.victimLosesWhenKillerIsFull) {
            this.store.release(ticket);
            this.refuse(victim, "refused-full", Map.of("victim", victim.getName(), "hearts", String.valueOf(killerCount), "maximum", String.valueOf(this.maximumHearts)));
        } else {
            toTake = Math.max(0, toTake);
            int dropped = victimCount - this.heartsPerKill;
            boolean eliminated = dropped < this.minimumHearts;
            int gained = killerCount + toTake;
            this.store.setHearts(killerId, killer.getName(), gained);
            if (!eliminated) {
                this.store.setHearts(victimId, victim.getName(), dropped);
            }

            this.store.confirm(ticket);
            HashMap<String, String> values = new HashMap<>(10);
            values.put("killer", killer.getName());
            values.put("player", killer.getName());
            values.put("victim", victim.getName());
            values.put("world", victim.getWorld().getName());
            values.put("hearts", String.valueOf(gained));
            values.put("gained", String.valueOf(toTake));
            values.put("lost", String.valueOf(this.heartsPerKill));
            values.put("victim-hearts", String.valueOf(eliminated ? 0 : dropped));
            values.put("maximum", String.valueOf(this.maximumHearts));
            int finalGained = gained;
            int finalToTake = toTake;
            Scheduler.atEntity(this, killer, () -> {
                this.hearts.apply(killer, finalGained);
                if (finalToTake > 0) {
                    this.message(killer, "stolen", values);
                    this.sounds.play(killer, "stolen");
                } else {
                    this.message(killer, "stolen-full", values);
                }
            });
            if (eliminated) {
                this.eliminate(victim, values);
            } else {
                this.hearts.apply(victim, dropped);
                if (this.tellVictim) {
                    this.message(victim, "lost", values);
                    this.sounds.play(victim, "lost");
                }
            }

            if (this.broadcastSteal) {
                this.broadcast("broadcast-steal", values, killer);
            }

            if (this.logToConsole) {
                this.getLogger().info(killer.getName() + " took " + this.heartsPerKill + " heart(s) from " + victim.getName() + (eliminated ? " - eliminated." : "."));
            }
        }
    }

    private void eliminate(Player player, Map<String, String> values) {
        UUID uuid = player.getUniqueId();
        this.store.setHearts(uuid, player.getName(), this.startHearts);
        if (this.elimination != Elimination.KICK) {
            this.store.setEliminated(uuid, System.currentTimeMillis());
        }

        this.hearts.apply(player, this.startHearts);
        if (this.broadcastElimination) {
            this.broadcast("broadcast-eliminated", values, null);
        }

        if (this.logToConsole) {
            this.getLogger().info(player.getName() + " ran out of hearts (" + this.elimination + ").");
        }

        switch (this.elimination) {
            case BAN: {
                this.banPlayer(player, values);
                this.disconnect(player, "ban-screen", values);
                break;
            }
            case SPECTATOR: {
                this.message(player, "eliminated-spectator", values);
                break;
            }
            case KICK: {
                this.disconnect(player, "kick-screen", values);
                break;
            }
        }
    }

    private void banPlayer(Player player, Map<String, String> values) {
        String name = player.getName();
        HashMap<String, String> data = new HashMap<>(values);
        data.put("minutes", String.valueOf(this.banMinutes));
        data.put("duration", this.banMinutes > 0L ? describe(this.banMinutes * 60000L) : "");
        data.put("reason", this.banReason);

        if (!this.banCommand.isEmpty()) {
            String expanded = Text.apply(this.banCommand.startsWith("/") ? this.banCommand.substring(1) : this.banCommand, data);
            Scheduler.global(this, () -> {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), expanded);
                } catch (RuntimeException failed) {
                    this.getLogger().warning("elimination.ban.command '" + expanded + "' failed: " + failed);
                }
            });
        } else {
            Date expires = this.banMinutes > 0L ? new Date(System.currentTimeMillis() + this.banMinutes * 60000L) : null;
            String reason = Text.strip(Text.apply(this.banReason, data));
            Scheduler.global(this, () -> {
                try {
                    player.ban(reason, expires, this.getName());
                } catch (RuntimeException failed) {
                    this.getLogger().warning("Could not ban " + name + ": " + failed);
                }
            });
        }
    }

    private void disconnect(Player player, String messageKey, Map<String, String> values) {
        List<String> lines = this.config().yaml().getStringList("messages." + messageKey);
        String text = lines.isEmpty() ? this.config().yaml().getString("messages." + messageKey, "") : String.join("\n", lines);
        if (text != null && !text.isEmpty()) {
            String interpreted = Text.chat(Text.apply(text, values));
            Scheduler.later(this, () -> Scheduler.atEntity(this, player, () -> {
                if (player.isOnline()) {
                    player.kickPlayer(interpreted);
                }
            }), 2L);
        }
    }

    public void onJoin(Player player) {
        if (this.store != null) {
            UUID uuid = player.getUniqueId();
            int stored = this.store.hearts(uuid);
            int amount = stored == -1 ? this.startHearts : clampInt(stored, this.minimumHearts, this.maximumHearts);
            if (stored != amount) {
                this.store.setHearts(uuid, player.getName(), amount);
            } else {
                this.store.remember(uuid, player.getName());
            }

            this.hearts.apply(player, amount);
            long eliminated = this.store.eliminated(uuid);
            if (eliminated > 0L) {
                if (this.elimination == Elimination.SPECTATOR) {
                    player.setGameMode(GameMode.SPECTATOR);
                    this.message(player, "still-eliminated", Map.of("player", player.getName()));
                } else {
                    this.store.setEliminated(uuid, 0L);
                }
            }
        }
    }

    public void onRespawn(Player player) {
        if (this.store != null) {
            int amount = this.storedHearts(player.getUniqueId());
            this.hearts.apply(player, amount);
            if (this.elimination == Elimination.SPECTATOR && this.store.eliminated(player.getUniqueId()) > 0L) {
                player.setGameMode(GameMode.SPECTATOR);
                this.message(player, "eliminated-spectator", Map.of("player", player.getName()));
            }
        }
    }

    public boolean redeemHeart(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int current = this.storedHearts(uuid);
        Map<String, String> values = Map.of(
                "player", player.getName(),
                "hearts", String.valueOf(current),
                "gained", String.valueOf(amount),
                "maximum", String.valueOf(this.maximumHearts));
        if (current + amount > this.maximumHearts) {
            this.message(player, "heart-too-many", values);
            this.sounds.play(player, "denied");
            return false;
        }

        int next = current + amount;
        this.store.setHearts(uuid, player.getName(), next);
        this.hearts.apply(player, next);
        this.message(player, "heart-used", values);
        this.sounds.play(player, "heart");
        return true;
    }

    private boolean worldAllowed(String world) {
        if (this.worldList.isEmpty()) {
            return true;
        }

        boolean matches = this.worldList.contains(world.toLowerCase(Locale.ROOT));
        return this.worldWhitelist == matches;
    }

    private static int playtimeMinutes(Player player) {
        try {
            return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200;
        } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static boolean sameAddress(Player first, Player second) {
        String left = address(first);
        String right = address(second);
        return !left.isEmpty() && left.equals(right);
    }

    private static String address(Player player) {
        InetSocketAddress socket = player.getAddress();
        return socket != null && socket.getAddress() != null ? socket.getAddress().getHostAddress() : "";
    }

    private void refuse(Player player, String key, Map<String, String> values) {
        if (!key.isEmpty() && !this.silentMessage(key)) {
            Scheduler.atEntity(this, player, () -> {
                this.message(player, key, values);
                this.sounds.play(player, "denied");
            });
        }
    }

    private boolean silentMessage(String key) {
        String text = this.config().yaml().getString("messages." + key, "");
        return text == null || text.isEmpty();
    }

    public void broadcast(String key, Map<String, String> values, Player sender) {
        String template = this.config().yaml().getString("messages." + key, "");
        if (template != null && !template.isEmpty()) {
            String interpreted = Text.chat(Text.apply(this.prefix() + template, values));
            Players.each(this, player -> {
                if (player.equals(sender) || this.mutePermission.isEmpty() || !player.hasPermission(this.mutePermission)) {
                    player.sendMessage(interpreted);
                }
            });
        }
    }

    @Override
    public void message(CommandSender sender, String key, Map<String, String> values) {
        super.message(sender, key, values);
    }

    @Override
    public void message(CommandSender sender, String key) {
        super.message(sender, key);
    }

    public static String describe(long seconds) {
        long value = Math.max(0L, (seconds + 999L) / 1000L);
        long days = value / 86400L;
        long hours = value % 86400L / 3600L;
        long minutes = value % 3600L / 60L;
        long remaining = value % 60L;
        StringBuilder text = new StringBuilder();

        if (days > 0L) {
            text.append(days).append("d ");
        }

        if (hours > 0L) {
            text.append(hours).append("h ");
        }

        if (minutes > 0L) {
            text.append(minutes).append("m ");
        }

        if (text.length() == 0 || remaining > 0L) {
            text.append(remaining).append('s');
        }

        return text.toString().trim();
    }

    public LifestealStore store() {
        return this.store;
    }

    public HeartItem heartItem() {
        return this.heartItem;
    }

    public Sounds sounds() {
        return this.sounds;
    }

    public LifestealStore.Limits limits() {
        return new LifestealStore.Limits(this.cooldownMillis, this.pairCooldownMillis, this.pairLimitPerDay, this.stealsPerDay, this.zone);
    }

    public int storedHearts(UUID uuid) {
        int stored = this.store.hearts(uuid);
        return stored == -1 ? this.startHearts : clampInt(stored, this.minimumHearts, this.maximumHearts);
    }

    public void setHearts(UUID uuid, String name, Player player, int amount) {
        int clamped = clampInt(amount, this.minimumHearts, this.maximumHearts);
        this.store.setHearts(uuid, name, clamped);
        boolean restored = this.store.eliminated(uuid) > 0L && clamped >= this.minimumHearts;
        if (restored) {
            this.store.setEliminated(uuid, 0L);
        }

        if (player != null) {
            Scheduler.atEntity(
                    this, player, () -> {
                        this.hearts.apply(player, clamped);
                        if (restored && player.getGameMode() == GameMode.SPECTATOR) {
                            player.setGameMode(this.spectatorReturn);
                        }

                        this.message(
                                player,
                                "hearts-changed",
                                Map.of("player", player.getName(), "hearts", String.valueOf(clamped), "maximum", String.valueOf(this.maximumHearts)));
                    });
        }
    }

    public int giveHearts(Player player, int count) {
        if (this.heartItem != null && this.heartItem.ready() && count > 0) {
            int remaining = count;

            while (remaining > 0) {
                ItemStack item = this.heartItem.make(remaining);
                if (item == null) {
                    return count - remaining;
                }

                Items.give(player, item);
                remaining -= item.getAmount();
            }

            return count;
        }

        return 0;
    }

    public boolean lifestealEnabled() {
        return this.enabled;
    }

    public int heartsPerKill() {
        return this.heartsPerKill;
    }

    public int maximumHearts() {
        return this.maximumHearts;
    }

    public int minimumHearts() {
        return this.minimumHearts;
    }

    public int startHearts() {
        return this.startHearts;
    }

    public int stealsPerDay() {
        return this.stealsPerDay;
    }

    public String usePermission() {
        return this.usePermission;
    }

    public String adminPermission() {
        return this.adminPermission;
    }

    public Map<String, String> statusValues(UUID uuid, String name) {
        LifestealStore.Status status = this.store.status(uuid, System.currentTimeMillis(), this.limits());
        int current = status.hearts() == -1 ? this.startHearts : clampInt(status.hearts(), this.minimumHearts, this.maximumHearts);
        HashMap<String, String> values = new HashMap<>(12);
        values.put("player", name);
        values.put("hearts", String.valueOf(current));
        values.put("health", String.valueOf(current * 2));
        values.put("minimum", String.valueOf(this.minimumHearts));
        values.put("maximum", String.valueOf(this.maximumHearts));
        values.put("per-kill", String.valueOf(this.heartsPerKill));
        values.put("today", String.valueOf(status.today()));
        values.put("total", String.valueOf(status.total()));
        values.put("limit", this.stealsPerDay > 0 ? String.valueOf(this.stealsPerDay) : "-");
        values.put("remaining", this.stealsPerDay > 0 ? String.valueOf(Math.max(0, this.stealsPerDay - status.today())) : "-");
        values.put("seconds", String.valueOf((status.cooldownMillis() + 999L) / 1000L));
        values.put("time", describe(status.cooldownMillis()));
        values.put("state", status.eliminated() ? "eliminated" : "alive");
        return values;
    }

    private enum Elimination {
        BAN,
        SPECTATOR,
        KICK
    }
}
