/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.storage;

import java.util.Map;

public interface Storage extends AutoCloseable {

    Map<String, String> read(String namespace, String key);

    void write(String namespace, String key, Map<String, String> fields);

    void delete(String namespace, String key);

    Map<String, Map<String, String>> readAll(String namespace);

    void flush();

    @Override
    void close();
}
