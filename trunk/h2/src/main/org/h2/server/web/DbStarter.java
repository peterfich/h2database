/*
 * Copyright 2004-2009 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.server.web;

import java.sql.Connection;
import java.sql.DriverManager;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.h2.tools.Server;
import org.h2.util.StringUtils;

/**
 * This class can be used to start the H2 TCP server (or other H2 servers, for
 * example the PG server) inside a web application container such as Tomcat or
 * Jetty. It can also open a database connection.
 */
public class DbStarter implements ServletContextListener {

    private Connection conn;
    private Server server;

    public void contextInitialized(ServletContextEvent servletContextEvent) {
        try {
            org.h2.Driver.load();

            // This will get the setting from a context-param in web.xml if defined:
            ServletContext servletContext = servletContextEvent.getServletContext();
            String url = getParameter(servletContext, "db.url", "jdbc:h2:~/test");
            String user = getParameter(servletContext, "db.user", "sa");
            String password = getParameter(servletContext, "db.password", "sa");

            conn = DriverManager.getConnection(url, user, password);
            servletContext.setAttribute("connection", conn);

            // Start the server if configured to do so
            String serverParams = getParameter(servletContext, "db.tcpServer", null);
            if (serverParams != null) {
                String[] params = StringUtils.arraySplit(serverParams, ' ', true);
                server = Server.createTcpServer(params);
                server.start();
            }
            // To access the database in server mode, use the database URL:
            // jdbc:h2:tcp://localhost/~/test

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getParameter(ServletContext servletContext, String key, String defaultValue) {
        String value = servletContext.getInitParameter(key);
        return value == null ? defaultValue : value;
    }

    /**
     * Get the connection.
     *
     * @return the connection
     */
    public Connection getConnection() {
        return conn;
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        if (server != null) {
            server.stop();
            server = null;
        }
        try {
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
