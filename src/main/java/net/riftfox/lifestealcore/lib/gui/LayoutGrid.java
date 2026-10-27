/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.gui;

import java.util.ArrayList;
import java.util.List;

public final class LayoutGrid {

    private final char[] slots;
    private final int rows;

    private LayoutGrid(char[] slots, int rows) {
        this.slots = slots;
        this.rows = rows;
    }

    public static LayoutGrid parse(List<String> lines, char filler) {
        List<String> padded = new ArrayList<>();

        for (String line : lines) {
            if (line != null) {
                String paddedLine = line.length() >= 9 ? line.substring(0, 9) : line + String.valueOf(filler).repeat(9 - line.length());
                padded.add(paddedLine);
            }
        }

        if (padded.isEmpty()) {
            padded.add(String.valueOf(filler).repeat(9));
        }

        if (padded.size() > 6) {
            padded = padded.subList(0, 6);
        }

        char[] plane = new char[padded.size() * 9];

        for (int row = 0; row < padded.size(); row++) {
            for (int column = 0; column < 9; column++) {
                plane[row * 9 + column] = padded.get(row).charAt(column);
            }
        }

        return new LayoutGrid(plane, padded.size());
    }

    public int rows() {
        return this.rows;
    }

    public int size() {
        return this.slots.length;
    }

    public char symbolAt(int index) {
        return index >= 0 && index < this.slots.length ? this.slots[index] : '\u0000';
    }

    public List<Integer> slotsOf(char symbol) {
        ArrayList<Integer> positions = new ArrayList<>();

        for (int index = 0; index < this.slots.length; index++) {
            if (this.slots[index] == symbol) {
                positions.add(index);
            }
        }

        return positions;
    }
}
