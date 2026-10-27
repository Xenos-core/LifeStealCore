/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) RiftFox and the LifeStealCore contributors
 */

package net.riftfox.lifestealcore.lib.storage;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SqlStorage implements Storage {

    private final String url;
    private final String user;
    private final String password;
    private final boolean mysql;
    private final String table;
    private Connection connection;

    public SqlStorage(String url, String user, String password, String table) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.mysql = url.startsWith("jdbc:mysql");
        this.table = table;
        this.createTable();
    }

    private Connection connection() {
        try {
            if (this.connection == null || this.connection.isClosed() || !this.connection.isValid(2)) {
                this.connection = this.user != null && !this.user.isEmpty()
                        ? DriverManager.getConnection(this.url, this.user, this.password)
                        : DriverManager.getConnection(this.url);
            }

            return this.connection;
        } catch (SQLException problem) {
            throw new IllegalStateException("No connection to " + this.url, problem);
        }
    }

    private void createTable() {
        String column = this.mysql ? "VARCHAR(190)" : "TEXT";
        String statement = "CREATE TABLE IF NOT EXISTS "
                + this.table
                + " (ns "
                + column
                + " NOT NULL,k "
                + column
                + " NOT NULL,feld "
                + column
                + " NOT NULL,wert TEXT,PRIMARY KEY (ns, k, feld))";

        try (Statement handle = this.connection().createStatement()) {
            handle.executeUpdate(statement);
        } catch (SQLException problem) {
            throw new IllegalStateException("Could not create table " + this.table, problem);
        }
    }

    @Override
    public Map<String, String> read(String namespace, String key) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        String query = "SELECT feld, wert FROM " + this.table + " WHERE ns=? AND k=?";

        try (PreparedStatement statement = this.connection().prepareStatement(query)) {
            statement.setString(1, namespace);
            statement.setString(2, key);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    fields.put(rows.getString(1), rows.getString(2));
                }
            }

            return fields;
        } catch (SQLException problem) {
            throw new IllegalStateException("Read failed: " + namespace + "/" + key, problem);
        }
    }

    @Override
    public void write(String namespace, String key, Map<String, String> fields) {
        String delete = "DELETE FROM " + this.table + " WHERE ns=? AND k=?";
        String insert = "INSERT INTO " + this.table + " (ns,k,feld,wert) VALUES (?,?,?,?)";
        Connection connection = this.connection();

        try {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(delete)) {
                statement.setString(1, namespace);
                statement.setString(2, key);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                for (Map.Entry<String, String> field : fields.entrySet()) {
                    statement.setString(1, namespace);
                    statement.setString(2, key);
                    statement.setString(3, field.getKey());
                    statement.setString(4, field.getValue());
                    statement.addBatch();
                }

                statement.executeBatch();
            }

            connection.commit();
            connection.setAutoCommit(autoCommit);
        } catch (SQLException problem) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }

            throw new IllegalStateException("Write failed: " + namespace + "/" + key, problem);
        }
    }

    @Override
    public void delete(String namespace, String key) {
        try (PreparedStatement statement = this.connection().prepareStatement("DELETE FROM " + this.table + " WHERE ns=? AND k=?")) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            statement.executeUpdate();
        } catch (SQLException problem) {
            throw new IllegalStateException("Delete failed: " + namespace + "/" + key, problem);
        }
    }

    @Override
    public Map<String, Map<String, String>> readAll(String namespace) {
        LinkedHashMap<String, Map<String, String>> all = new LinkedHashMap<>();
        String query = "SELECT k, feld, wert FROM " + this.table + " WHERE ns=? ORDER BY k";

        try (PreparedStatement statement = this.connection().prepareStatement(query)) {
            statement.setString(1, namespace);

            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    all.computeIfAbsent(rows.getString(1), key -> new HashMap<>()).put(rows.getString(2), rows.getString(3));
                }
            }

            return all;
        } catch (SQLException problem) {
            throw new IllegalStateException("Read failed: " + namespace, problem);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
            }
        } catch (SQLException ignored) {
        }
    }
}
