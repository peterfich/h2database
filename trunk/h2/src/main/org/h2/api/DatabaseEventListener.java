/*
 * Copyright 2004-2006 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.api;

import java.sql.SQLException;
import java.util.EventListener;

/**
 * A class that implements this interface can get notified about exceptions and other events.
 * A database event listener can be registered when connecting to a database.
 * Example database URL: jdbc:h2:test;DATABASE_EVENT_LISTENER='com.acme.DbListener' *
 */

public interface DatabaseEventListener extends EventListener {

    int STATE_SCAN_FILE = 0, STATE_CREATE_INDEX = 1, STATE_RECOVER = 2, STATE_BACKUP_FILE = 3;

    /**
     * This method is called just after creating the object.
     * This is done when opening the database if the listener is specified in the database URL,
     * but may be later if the listener is set at runtime with the SET SQL statement.
     *
     * @param url - the database URL
     */
    void init(String url);

    /**
     * This method is called if the disk space is very low.
     *
     * @param stillAvailable the estimated space that is still available, in bytes
     * @throws SQLException if the operation should be cancelled
     */
    void diskSpaceIsLow(long stillAvailable) throws SQLException;

    /**
     * This method is called if an exception occurred.
     *
     * @param e the exception
     */
    void exceptionThrown(SQLException e);

    /**
     * This method is called for long running events, such as recovering, scanning a file or building an index.
     *
     * @param state - the state
     * @param name - the object name
     * @param x - the current position
     * @param max - the highest value
     */
    void setProgress(int state, String name, int x, int max);

    /**
     * This method is called before the database is closed normally.
     */
    void closingDatabase();

}
