/*
 * Copyright 2004-2008 H2 Group. Multiple-Licensed under the H2 License, 
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;

import org.h2.util.JdbcDriverUtils;

/**
 * A simple wrapper around the JDBC API.
 * Currently used for testing.
 * Features:
 * <ul>
 * <li>No checked exceptions
 * </li><li>Easy to use, fluent API
 * </li></ul>
 */
public class Db {

    private Connection conn;
    private Statement stat;
    private HashMap prepared = new HashMap();

    private Db(Connection conn) {
        try {
            this.conn = conn;
            stat = conn.createStatement();
        } catch (Exception e) {
            throw convert(e);
        }
    }

    /**
     * Open the database connection. For most databases, it is not required to
     * load the driver before calling this method.
     * 
     * @param url the database URL
     * @param user the user name
     * @param password the password
     * @return the database
     */
    public static Db open(String url, String user, String password) {
        try {
            JdbcDriverUtils.load(url);
            return new Db(DriverManager.getConnection(url, user, password));
        } catch (Exception e) {
            throw convert(e);
        }
    }

    /**
     * Prepare a SQL statement.
     * 
     * @param sql the SQL statement
     * @return the prepared statement
     */
    public Prepared prepare(String sql) {
        try {
            PreparedStatement prep = (PreparedStatement) prepared.get(sql);
            if (prep == null) {
                prep = conn.prepareStatement(sql);
                prepared.put(sql, prep);
            }
            return new Prepared(conn.prepareStatement(sql));
        } catch (Exception e) {
            throw convert(e);
        }
    }

    /**
     * Execute a SQL statement.
     * 
     * @param sql the SQL statement
     */
    public void execute(String sql) {
        try {
            stat.execute(sql);
        } catch (Exception e) {
            throw convert(e);
        }
    }

    /**
     * Close the database connection.
     */
    public void close() {
        try {
            conn.close();
        } catch (Exception e) {
            throw convert(e);
        }
    }

    /**
     * This class represents a prepared statement.
     */
    public class Prepared {
        private PreparedStatement prep;
        private int index;

        Prepared(PreparedStatement prep) {
            this.prep = prep;
        }

        /**
         * Set the value of the current parameter.
         * 
         * @param x the value
         */
        public Prepared set(int x) {
            try {
                prep.setInt(++index, x);
                return this;
            } catch (Exception e) {
                throw convert(e);
            }
        }

        /**
         * Set the value of the current parameter.
         * 
         * @param x the value
         */
        public Prepared set(String x) {
            try {
                prep.setString(++index, x);
                return this;
            } catch (Exception e) {
                throw convert(e);
            }
        }

        /**
         * Set the value of the current parameter.
         * 
         * @param x the value
         */
        public Prepared set(byte[] x) {
            try {
                prep.setBytes(++index, x);
                return this;
            } catch (Exception e) {
                throw convert(e);
            }
        }

        /**
         * Set the value of the current parameter.
         * 
         * @param x the value
         */
        public Prepared set(InputStream x) {
            try {
                prep.setBinaryStream(++index, x, -1);
                return this;
            } catch (Exception e) {
                throw convert(e);
            }
        }

        /**
         * Execute the prepared statement.
         */
        public void execute() {
            try {
                prep.execute();
            } catch (Exception e) {
                throw convert(e);
            }
        }
    }

    static Error convert(Exception e) {
        return new Error("Error: " + e.toString(), e);
    }

    public void setAutoCommit(boolean autoCommit) {
        try {
            conn.setAutoCommit(autoCommit);
        } catch (Exception e) {
            throw convert(e);
        }
    }

    /**
     * Commit a pending transaction.
     */
    public void commit() {
        try {
            conn.commit();
        } catch (Exception e) {
            throw convert(e);
        }
    }

}
