/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import net.riftfox.lifestealcore.lib.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class Items {

    private static final Method PAPER_READER = findPaperReader();

    private Items() {
    }

    public static ItemStack read(ConfigurationSection section, Logger logger, Map<String, String> placeholders) {
        if (section == null) {
            return null;
        }

        String encoded = section.getString("base64", "");
        if (!encoded.isEmpty()) {
            ItemStack stack = decodeExact(encoded);
            if (stack != null) {
                if (section.isInt("amount")) {
                    stack.setAmount(Math.max(1, section.getInt("amount")));
                }

                return stack;
            }

            logger.warning("Unreadable base64 at " + section.getCurrentPath() + " - using the readable keys next to it instead.");
        }

        String materialName = section.getString("material", "");
        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material != null && !material.isAir()) {
            ItemStack stack = new ItemStack(material, Math.max(1, section.getInt("amount", 1)));
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) {
                return stack;
            }

            String name = section.getString("name", "");
            if (!name.isEmpty()) {
                meta.setDisplayName(Text.item(Text.apply(name, placeholders)));
            }

            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.setLore(Text.lore(Text.apply(lore, placeholders)));
            }

            int model = section.getInt("custom-model-data", -1);
            if (model >= 0) {
                meta.setCustomModelData(model);
            }

            if (section.getBoolean("unbreakable", false)) {
                meta.setUnbreakable(true);
            }

            applyTrim(meta, section, logger);

            for (String enchantment : section.getStringList("enchantments")) {
                applyEnchantment(meta, enchantment, section.getCurrentPath(), logger);
            }

            if (section.getBoolean("glow", false) && meta.getEnchants().isEmpty()) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
            }

            String potion = section.getString("potion", "");
            if (!potion.isEmpty() && meta instanceof PotionMeta potionMeta) {
                try {
                    potionMeta.setBasePotionType(PotionType.valueOf(potion.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    logger.warning("Unknown potion type '" + potion + "' at " + section.getCurrentPath()
                            + " - the item stays a water bottle.");
                }
            }

            int flight = section.getInt("flight", -1);
            if (flight >= 0 && meta instanceof FireworkMeta fireworkMeta) {
                int power = Math.max(0, Math.min(3, flight));
                if (power != flight) {
                    logger.warning("flight " + flight + " at " + section.getCurrentPath() + " is outside 0-3 - using " + power + ".");
                }

                fireworkMeta.setPower(power);
            }

            if (section.getBoolean("hide-attributes", false)) {
                meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
            }

            String head = section.getString("head-texture", "");
            if (meta instanceof SkullMeta skullMeta && !head.isEmpty()) {
                applyHeadTexture(skullMeta, head);
            }

            stack.setItemMeta(meta);
            return stack;
        }

        logger.warning("Unknown material '" + materialName + "' at " + section.getCurrentPath() + " - this entry was skipped.");
        return null;
    }

    public static String encodeExact(ItemStack stack) {
        if (stack != null && !stack.getType().isAir()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            try (BukkitObjectOutputStream stream = new BukkitObjectOutputStream(buffer)) {
                stream.writeObject(stack);
                return Base64.getEncoder().encodeToString(buffer.toByteArray());
            } catch (Exception problem) {
                return "";
            }
        }

        return "";
    }

    public static ItemStack decodeExact(String input) {
        if (input != null && !input.isEmpty()) {
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(input.trim());
            } catch (IllegalArgumentException ignored) {
                return null;
            }

            try (ByteArrayInputStream source = new ByteArrayInputStream(bytes)) {
                BukkitObjectInputStream stream = new BukkitObjectInputStream(source);

                ItemStack stack;
                outer:
                {
                    try {
                        if (stream.readObject() instanceof ItemStack candidate && !candidate.getType().isAir()) {
                            stack = candidate;
                            break outer;
                        }
                    } catch (Throwable problem) {
                        try {
                            stream.close();
                        } catch (Throwable suppressed) {
                            problem.addSuppressed(suppressed);
                        }

                        throw problem;
                    }

                    stream.close();
                    return paperBytes(bytes);
                }

                stream.close();
                return stack;
            } catch (Exception problem) {
                return paperBytes(bytes);
            }
        }

        return null;
    }

    private static ItemStack paperBytes(byte[] bytes) {
        try {
            return (ItemStack)PAPER_READER.invoke(null, bytes) instanceof ItemStack candidate && !candidate.getType().isAir() ? candidate : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findPaperReader() {
        try {
            return ItemStack.class.getMethod("deserializeBytes", byte[].class);
        } catch (NoSuchMethodException missing) {
            // Paper builds without ItemStack.deserializeBytes - no byte reader available.
            return null;
        }
    }


    public static List<ItemStack> readList(ConfigurationSection section, String path, Logger logger) {
        ArrayList<ItemStack> list = new ArrayList<>();
        if (section.isList(path)) {
            for (Map<?, ?> entry : section.getMapList(path)) {
                MemoryConfiguration memory = new MemoryConfiguration();
                ConfigurationSection item = memory.createSection("item", entry);
                ItemStack stack = read(item, logger, Map.of());
                if (stack != null) {
                    list.add(stack);
                }
            }

            return list;
        }

        ConfigurationSection group = section.getConfigurationSection(path);
        if (group != null) {
            for (String key : group.getKeys(false)) {
                ItemStack stack = read(group.getConfigurationSection(key), logger, Map.of());
                if (stack != null) {
                    list.add(stack);
                }
            }
        }

        return list;
    }

    private static void applyTrim(ItemMeta meta, ConfigurationSection section, Logger logger) {
        ConfigurationSection trim = section.getConfigurationSection("trim");
        if (trim != null && meta instanceof ArmorMeta armorMeta) {
            String pattern = trim.getString("pattern", "");
            String material = trim.getString("material", "");
            if (!pattern.isEmpty() && !material.isEmpty()) {
                TrimPattern trimPattern = (TrimPattern)Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(pattern.toLowerCase(Locale.ROOT)));
                TrimMaterial trimMaterial = (TrimMaterial)Registry.TRIM_MATERIAL.get(NamespacedKey.minecraft(material.toLowerCase(Locale.ROOT)));
                if (trimPattern != null && trimMaterial != null) {
                    armorMeta.setTrim(new ArmorTrim(trimMaterial, trimPattern));
                } else {
                    logger.warning("Unknown armour trim '" + pattern + "/" + material + "' at " + section.getCurrentPath() + " - the item was made without a trim.");
                }
            }
        }
    }

    private static void applyEnchantment(ItemMeta meta, String entry, String path, Logger logger) {
        String[] parts = entry.split(":");
        String name = parts[0].trim().toLowerCase();
        int level = 1;
        if (parts.length > 1) {
            try {
                level = Math.max(1, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        Enchantment enchantment = null;
        try {
            NamespacedKey key = name.contains(":") ? NamespacedKey.fromString(name) : NamespacedKey.minecraft(name);
            if (key != null) {
                enchantment = (Enchantment)Registry.ENCHANTMENT.get(key);
            }
        } catch (Exception ignored) {
        }

        if (enchantment == null) {
            logger.warning("Unknown enchantment '" + entry + "' at " + path + " - the item is given without it.");
        } else {
            meta.addEnchant(enchantment, level, true);
        }
    }

    private static void applyHeadTexture(SkullMeta meta, String texture) {
        try {
            String source = texture.startsWith("http") ? texture : "https://textures.minecraft.net/texture/" + texture;
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(source).toURL());
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (Exception ignored) {
        }
    }

    public static void give(Player player, ItemStack stack) {
        int remaining = stack.getAmount();
        int max = Math.max(1, stack.getMaxStackSize());

        while (remaining > 0) {
            ItemStack clone = stack.clone();
            clone.setAmount(Math.min(remaining, max));
            remaining -= clone.getAmount();

            for (ItemStack dropped : player.getInventory().addItem(new ItemStack[]{clone}).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), dropped);
            }
        }
    }

    public static int freeSlots(Player player) {
        int free = 0;

        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                free++;
            }
        }

        return free;
    }

    public static List<String> lines(List<String> lines, Map<String, String> placeholders) {
        return new ArrayList<>(Text.apply(lines, placeholders));
    }
}
