/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MenuConfig {

    private final String title;
    private final LayoutGrid layout;
    private final Map<Character, ItemSpec> bySymbol = new HashMap<>();
    private final Map<String, ItemSpec> byAction = new HashMap<>();
    private final char fillerSymbol;

    private MenuConfig(String title, LayoutGrid layout, char fillerSymbol) {
        this.title = title;
        this.layout = layout;
        this.fillerSymbol = fillerSymbol;
    }

    public MenuConfig load(ConfigurationSection section, String path, Logger logger) {
        String title = section.getString("menu.title", "Menu");
        String fillerText = section.getString("menu.filler-symbol", "-");
        char filler = fillerText != null && !fillerText.isEmpty() ? fillerText.charAt(0) : '-';
        List<String> raw = section.getStringList(path);
        if (raw.isEmpty()) {
            String single = section.getString(path, "");
            raw = new ArrayList<>();
            if (!single.isEmpty()) {
                raw.add(single);
            }
        }

        MenuConfig config = new MenuConfig(title, LayoutGrid.parse(raw, filler), filler);
        ConfigurationSection items = section.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection itemSection = items.getConfigurationSection(key);
                if (itemSection != null) {
                    ItemSpec spec = ItemSpec.from(itemSection, key, layout);
                    if (spec.symbol() != 0) {
                        config.bySymbol.put(spec.symbol(), spec);
                    }

                    config.byAction.put(spec.action(), spec);
                }
            }
        }

        return config;
    }

    public String title() {
        return this.title;
    }

    public LayoutGrid layout() {
        return this.layout;
    }

    public int rows() {
        return this.layout.rows();
    }

    public ItemSpec item(String action) {
        return this.byAction.get(action);
    }

    public List<Integer> slotsOfAction(String action) {
        ItemSpec spec = this.byAction.get(action);
        return spec == null ? List.of() : this.layout.slotsOf(spec.symbol());
    }

    public void paging(Inventory inventory, int currentPage) {
        this.paging(inventory, -1, currentPage);
    }

    public void paging(Inventory inventory, int currentPage, int totalPages) {
        if (totalPages <= 1 || (currentPage >= 0 && currentPage <= 0)) {
            this.fill(inventory, "previous");
            this.fill(inventory, "page_previous");
        }

        if (totalPages <= 1 || (currentPage >= 0 && currentPage >= totalPages - 1)) {
            this.fill(inventory, "next");
            this.fill(inventory, "page_next");
        }
    }

    public void fill(Inventory inventory, String action) {
        ItemSpec filler = this.bySymbol.get(this.fillerSymbol);

        for (int slot : this.slotsOfAction(action)) {
            if (slot >= 0 && slot < inventory.getSize()) {
                ItemStack item = filler == null ? null : filler.build();
                inventory.setItem(slot, item != null && item.getType().isAir() ? null : item);
            }
        }
    }

    public String actionAt(int slot) {
        ItemSpec spec = this.bySymbol.get(this.layout.symbolAt(slot));
        return spec == null ? null : spec.action();
    }

    public void render(Inventory inventory, Map<String, String> placeholders, char... excludeSymbols) {
        outer:
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            char symbol = this.layout.symbolAt(slot);

            for (char excluded : excludeSymbols) {
                if (symbol == excluded) {
                    continue outer;
                }
            }

            ItemSpec spec = this.bySymbol.get(symbol);
            if (spec == null) {
                inventory.setItem(slot, null);
            } else {
                ItemStack item = spec.build(placeholders);
                inventory.setItem(slot, item.getType().isAir() ? null : item);
            }
        }
    }

    public char fillerSymbol() {
        return this.fillerSymbol;
    }
}
