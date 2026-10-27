/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.riftfox.lifestealcore.lib.storage.Storage;

import org.bukkit.plugin.Plugin;
public final class LifestealStore {

    private static final String NAMESPACE = "lifesteal-players";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_HEARTS = "hearts";
    private static final String FIELD_ELIMINATED = "eliminated";
    private static final String FIELD_DAY = "day";
    private static final String FIELD_DAY_COUNT = "day-count";
    private static final String FIELD_TOTAL = "total-steals";
    private static final String FIELD_LAST = "last-steal";
    private static final String FIELD_TOUCHED = "touched";
    private static final String FIELD_PAIR = "pair.";
    public static final int UNSET = -1;
    private static final long DAY_MILLIS = 86400000L;

    private final Plugin plugin;
    private final Storage storage;
    private final ExecutorService writer;
    private final Map<UUID, Entry> players = new LinkedHashMap<>();
    private boolean dirty;

    public LifestealStore(Plugin plugin, Storage storage, ExecutorService writer) {
        this.plugin = plugin;
        this.storage = storage;
        this.writer = writer;
    }

    public synchronized void load() {
        int unreadable = 0;

        for (Map.Entry<String, Map<String, String>> entry : this.storage.readAll(NAMESPACE).entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException ignored) {
                unreadable++;
                continue;
            }

            Map<String, String> fields = entry.getValue();
            Entry record = new Entry();
            record.name = fields.getOrDefault(FIELD_NAME, "");
            record.hearts = fields.containsKey(FIELD_HEARTS) ? (int)number(fields.get(FIELD_HEARTS)) : -1;
            record.eliminated = number(fields.get(FIELD_ELIMINATED));
            record.day = fields.getOrDefault(FIELD_DAY, "");
            record.dayCount = (int)number(fields.get(FIELD_DAY_COUNT));
            record.total = number(fields.get(FIELD_TOTAL));
            record.lastSteal = number(fields.get(FIELD_LAST));
            record.touched = number(fields.get(FIELD_TOUCHED));

            for (Map.Entry<String, String> field : fields.entrySet()) {
                if (field.getKey().startsWith(FIELD_PAIR)) {
                    String[] pair = String.valueOf(field.getValue()).split("/", 2);
                    long stamp = number(pair[0]);
                    int count = pair.length > 1 ? (int)number(pair[1]) : 0;
                    if (stamp > 0L) {
                        record.pairs.put(field.getKey().substring(FIELD_PAIR.length()), new long[]{stamp, count});
                    }
                }
            }

            this.players.put(uuid, record);
        }

        if (!this.players.isEmpty() || unreadable > 0) {
            this.plugin.getLogger()
                    .info(this.players.size() + " player(s) have a heart count" + (unreadable > 0 ? " (" + unreadable + " unreadable entries skipped)" : "") + ".");
        }
    }

    private static long number(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public synchronized Ticket reserve(UUID killer, String name, UUID victim, long timestamp, Limits limits) {
        Entry record = this.players.get(killer);
        String today = LocalDate.now(limits.zone()).toString();
        String previousDay = record == null ? "" : record.day;
        int previousCount = record == null ? 0 : record.dayCount;
        long previousLast = record == null ? 0L : record.lastSteal;
        boolean crossedDay = !today.equals(previousDay);
        int todayCount = crossedDay ? 0 : previousCount;
        String victimKey = victim.toString();
        long[] pair = record == null ? null : record.pairs.get(victimKey);
        boolean knownPair = pair != null;
        long pairLast = knownPair ? pair[0] : 0L;
        int pairCount = knownPair ? (int)pair[1] : 0;
        int pairToday = crossedDay ? 0 : pairCount;
        Refusal refusal = Refusal.OK;
        long wait = 0L;

        if (limits.cooldownMillis() > 0L && previousLast > 0L && timestamp - previousLast < limits.cooldownMillis()) {
            refusal = Refusal.COOLDOWN;
            wait = limits.cooldownMillis() - (timestamp - previousLast);
        } else if (limits.pairCooldownMillis() > 0L && pairLast > 0L && timestamp - pairLast < limits.pairCooldownMillis()) {
            refusal = Refusal.PAIR_COOLDOWN;
            wait = limits.pairCooldownMillis() - (timestamp - pairLast);
        } else if (limits.pairLimitPerDay() > 0 && pairToday >= limits.pairLimitPerDay()) {
            refusal = Refusal.PAIR_LIMIT;
            wait = untilMidnight(timestamp, limits.zone());
        } else if (limits.stealsPerDay() > 0 && todayCount >= limits.stealsPerDay()) {
            refusal = Refusal.DAILY_LIMIT;
            wait = untilMidnight(timestamp, limits.zone());
        }

        if (refusal != Refusal.OK) {
            return new Ticket(killer, victim, refusal, wait, previousDay, previousCount, previousLast, pairLast, pairCount, knownPair, timestamp);
        }

        Entry target = record == null ? this.players.computeIfAbsent(killer, key -> new Entry()) : record;
        if (name != null && !name.isEmpty()) {
            target.name = name;
        }

        target.day = today;
        target.dayCount = todayCount + 1;
        target.lastSteal = timestamp;
        target.touched = timestamp;
        target.pairs.put(victimKey, new long[]{timestamp, pairToday + 1});
        prune(target, timestamp, limits);
        return new Ticket(killer, victim, Refusal.OK, 0L, previousDay, previousCount, previousLast, pairLast, pairCount, knownPair, timestamp);
    }

    public synchronized void confirm(Ticket ticket) {
        Entry target = this.players.get(ticket.killer());
        if (target != null) {
            target.total++;
            this.dirty = true;
            this.queue(ticket.killer(), fields(target));
        }
    }

    public synchronized void release(Ticket ticket) {
        if (ticket.ok()) {
            Entry target = this.players.get(ticket.killer());
            if (target != null && target.lastSteal == ticket.stampedAt()) {
                target.day = ticket.prevDay();
                target.dayCount = ticket.prevDayCount();
                target.lastSteal = ticket.prevLast();
                if (ticket.prevPairKnown()) {
                    target.pairs.put(ticket.victim().toString(), new long[]{ticket.prevPairLast(), ticket.prevPairCount()});
                } else {
                    target.pairs.remove(ticket.victim().toString());
                }

                if (target.hearts == -1 && target.total == 0L && target.lastSteal == 0L && target.pairs.isEmpty()) {
                    this.players.remove(ticket.killer());
                }
            }
        }
    }

    private static void prune(Entry entry, long timestamp, Limits limits) {
        long cutoff = Math.max(limits.pairCooldownMillis(), 86400000L);
        Iterator<Map.Entry<String, long[]>> iterator = entry.pairs.entrySet().iterator();

        while (iterator.hasNext()) {
            long[] pair = iterator.next().getValue();
            if (timestamp - pair[0] > cutoff) {
                iterator.remove();
            }
        }
    }

    private static long untilMidnight(long timestamp, ZoneId zone) {
        long next = LocalDate.now(zone).plusDays(1L).atStartOfDay(zone).toInstant().toEpochMilli();
        return Math.max(0L, next - timestamp);
    }

    public synchronized int hearts(UUID uuid) {
        Entry target = this.players.get(uuid);
        return target == null ? -1 : target.hearts;
    }

    public synchronized void setHearts(UUID uuid, String name, int amount) {
        Entry record = this.players.computeIfAbsent(uuid, key -> new Entry());
        if (name != null && !name.isEmpty()) {
            record.name = name;
        }

        record.hearts = amount;
        record.touched = System.currentTimeMillis();
        this.dirty = true;
        this.queue(uuid, fields(record));
    }

    public synchronized long eliminated(UUID uuid) {
        Entry target = this.players.get(uuid);
        return target == null ? 0L : target.eliminated;
    }

    public synchronized void setEliminated(UUID uuid, long timestamp) {
        Entry record = this.players.computeIfAbsent(uuid, key -> new Entry());
        record.eliminated = timestamp;
        record.touched = System.currentTimeMillis();
        this.dirty = true;
        this.queue(uuid, fields(record));
    }

    public synchronized Status status(UUID uuid, long timestamp, Limits limits) {
        Entry record = this.players.get(uuid);
        if (record == null) {
            return new Status(-1, 0, 0L, 0L, false);
        }

        boolean today = LocalDate.now(limits.zone()).toString().equals(record.day);
        long remaining = 0L;
        if (limits.cooldownMillis() > 0L && record.lastSteal > 0L) {
            long elapsed = timestamp - record.lastSteal;
            remaining = elapsed >= limits.cooldownMillis() ? 0L : limits.cooldownMillis() - elapsed;
        }

        return new Status(record.hearts, today ? record.dayCount : 0, record.total, remaining, record.eliminated > 0L);
    }

    public synchronized String nameOf(UUID uuid) {
        Entry target = this.players.get(uuid);
        return target == null ? "" : target.name;
    }

    public synchronized int size() {
        return this.players.size();
    }

    public synchronized void remember(UUID uuid, String name) {
        Entry record = this.players.get(uuid);
        if (record != null && !name.equals(record.name)) {
            record.name = name;
            this.dirty = true;
        }
    }

    public synchronized UUID find(String name) {
        for (Map.Entry<UUID, Entry> entry : this.players.entrySet()) {
            if (entry.getValue().name.equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }

        return null;
    }

    public synchronized int forget(long timestamp, long days, int defaultHearts) {
        if (days <= 0L) {
            return 0;
        }

        ArrayList<UUID> candidates = new ArrayList<>();

        for (Map.Entry<UUID, Entry> entry : this.players.entrySet()) {
            Entry record = entry.getValue();
            long lastSeen = Math.max(record.touched, record.lastSteal);
            if (timestamp - lastSeen > days && record.eliminated <= 0L && (record.hearts == -1 || record.hearts == defaultHearts)) {
                candidates.add(entry.getKey());
            }
        }

        for (UUID uuid : candidates) {
            this.players.remove(uuid);
            this.writer.execute(() -> {
                try {
                    this.storage.delete(NAMESPACE, uuid.toString());
                } catch (RuntimeException ignored) {
                    this.plugin.getLogger().log(Level.WARNING, "Could not drop an old record", ignored);
                }
            });
        }

        if (!candidates.isEmpty()) {
            this.writer.execute(this::flushQuietly);
        }

        return candidates.size();
    }

    private static Map<String, String> fields(Entry entry) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_NAME, entry.name);
        fields.put(FIELD_HEARTS, String.valueOf(entry.hearts));
        fields.put(FIELD_ELIMINATED, String.valueOf(entry.eliminated));
        fields.put(FIELD_DAY, entry.day);
        fields.put(FIELD_DAY_COUNT, String.valueOf(entry.dayCount));
        fields.put(FIELD_TOTAL, String.valueOf(entry.total));
        fields.put(FIELD_LAST, String.valueOf(entry.lastSteal));
        fields.put(FIELD_TOUCHED, String.valueOf(entry.touched));
        entry.pairs.forEach((key, pair) -> fields.put("pair." + key, pair[0] + "/" + pair[1]));
        return fields;
    }

    private void queue(UUID uuid, Map<String, String> fields) {
        this.writer.execute(() -> {
            try {
                this.storage.write(NAMESPACE, uuid.toString(), fields);
                this.storage.flush();
            } catch (RuntimeException problem) {
                this.plugin.getLogger().log(Level.SEVERE, "Could not save a heart count - it is rewritten on shutdown", problem);
            }
        });
    }

    private void flushQuietly() {
        try {
            this.storage.flush();
        } catch (RuntimeException problem) {
            this.plugin.getLogger().log(Level.WARNING, "Could not flush the heart counts", problem);
        }
    }

    public void shutdown() {
        this.writer.shutdown();

        try {
            if (!this.writer.awaitTermination(10L, TimeUnit.SECONDS)) {
                this.plugin.getLogger().warning("A pending heart save did not finish within 10s.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        try {
            synchronized (this) {
                this.players.forEach((uuid, record) -> this.storage.write(NAMESPACE, uuid.toString(), fields(record)));
            }

            this.storage.flush();
        } catch (RuntimeException problem) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save the heart counts on shutdown", problem);
        }

        this.storage.close();
    }

    public void saveAll() {
        LinkedHashMap<UUID, Map<String, String>> batch = new LinkedHashMap<>();
        synchronized (this) {
            if (!this.dirty) {
                return;
            }

            this.dirty = false;
            this.players.forEach((uuid, record) -> batch.put(uuid, fields(record)));
        }

        this.writer.execute(() -> {
            try {
                batch.forEach((uuid, fields) -> this.storage.write(NAMESPACE, uuid.toString(), fields));
                this.storage.flush();
            } catch (RuntimeException problem) {
                synchronized (this) {
                    this.dirty = true;
                }

                this.plugin.getLogger().log(Level.SEVERE, "Could not save the heart counts", problem);
            }
        });
    }

    private static final class Entry {
        String name = "";
        int hearts = -1;
        long eliminated;
        String day = "";
        int dayCount;
        long total;
        long lastSteal;
        long touched;
        final Map<String, long[]> pairs = new LinkedHashMap<>();
    }

    public record Limits(long cooldownMillis, long pairCooldownMillis, int pairLimitPerDay, int stealsPerDay, ZoneId zone) {
    }

    public enum Refusal {
        OK,
        COOLDOWN,
        PAIR_COOLDOWN,
        PAIR_LIMIT,
        DAILY_LIMIT
    }

    public record Status(int hearts, int today, long total, long cooldownMillis, boolean eliminated) {
    }

    public record Ticket(
            UUID killer,
            UUID victim,
            Refusal why,
            long waitMillis,
            String prevDay,
            int prevDayCount,
            long prevLast,
            long prevPairLast,
            int prevPairCount,
            boolean prevPairKnown,
            long stampedAt) {

        public boolean ok() {
            return this.why == Refusal.OK;
        }

        public long waitSeconds() {
            return (this.waitMillis + 999L) / 1000L;
        }
    }
}
