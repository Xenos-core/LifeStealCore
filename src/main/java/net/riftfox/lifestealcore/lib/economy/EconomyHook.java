/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.economy;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyHook {

    private final Plugin plugin;
    private Economy economy;
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);

    public EconomyHook(Plugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            this.plugin.getLogger().warning("Vault not found - selling is disabled until an economy is installed.");
        } else {
            this.resolve();
        }
    }

    private void resolve() {
        if (this.economy == null) {
            RegisteredServiceProvider<Economy> provider = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (provider != null) {
                this.economy = provider.getProvider();
                this.plugin.getLogger().info("Economy hooked: " + this.economy.getName());
            }
        }
    }

    public boolean available() {
        this.resolve();
        return this.economy != null;
    }

    public double balance(OfflinePlayer player) {
        return this.available() ? this.economy.getBalance(player) : 0.0;
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return this.available() && !(amount <= 0.0) ? this.economy.depositPlayer(player, amount).transactionSuccess() : false;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (this.available() && !(amount <= 0.0)) {
            return this.economy.getBalance(player) < amount ? false : this.economy.withdrawPlayer(player, amount).transactionSuccess();
        }

        return false;
    }

    public static String format(double amount) {
        double value = Math.abs(amount);
        if (value >= 1.0E12) {
            return trim(amount / 1.0E12) + "T";
        } else if (value >= 1.0E9) {
            return trim(amount / 1.0E9) + "B";
        } else if (value >= 1000000.0) {
            return trim(amount / 1000000.0) + "M";
        } else {
            return value >= 1000.0 ? trim(amount / 1000.0) + "K" : new DecimalFormat("#,##0", SYMBOLS).format(amount);
        }
    }

    private static String trim(double value) {
        return new DecimalFormat("#,##0.##", SYMBOLS).format(value);
    }

    public static String formatExact(double amount) {
        return new DecimalFormat("#,##0", SYMBOLS).format(amount);
    }

    public static double parseAmount(String input) {
        if (input == null) {
            return 0.0;
        }

        String cleaned = input.toLowerCase().replace(",", "").replace("$", "").trim();
        double multiplier = 1.0;
        if (cleaned.endsWith("k")) {
            multiplier = 1000.0;
        } else if (cleaned.endsWith("m")) {
            multiplier = 1000000.0;
        } else if (cleaned.endsWith("b")) {
            multiplier = 1.0E9;
        } else if (cleaned.endsWith("t")) {
            multiplier = 1.0E12;
        }

        if (multiplier > 1.0) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        try {
            return Math.max(0.0, Double.parseDouble(cleaned) * multiplier);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
