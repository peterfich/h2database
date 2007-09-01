/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.table;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import org.h2.tools.SimpleResultSet;
import org.h2.util.JdbcUtils;
import org.h2.util.StringUtils;

public class LinkSchema {
    public static ResultSet linkSchema(Connection conn, String targetSchema, String driver, String url, String user,
            String password, String sourceSchema) throws SQLException {
        Connection c2 = null;
        Statement stat = null;
        ResultSet rs = null;
        SimpleResultSet result = new SimpleResultSet();
        result.addColumn("TABLE_NAME", Types.VARCHAR, 255, 0);
        try {
            c2 = JdbcUtils.getConnection(driver, url, user, password);
            stat = conn.createStatement();
            stat.execute("CREATE SCHEMA IF NOT EXISTS " + StringUtils.quoteIdentifier(targetSchema));
            rs = c2.getMetaData().getTables(null, sourceSchema, null, null);
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                StringBuffer buff = new StringBuffer();
                buff.append("DROP TABLE IF EXISTS ");
                buff.append(StringUtils.quoteIdentifier(targetSchema));
                buff.append('.');
                buff.append(StringUtils.quoteIdentifier(table));
                String sql = buff.toString();
                stat.execute(sql);
                buff = new StringBuffer();
                buff.append("CREATE LINKED TABLE ");
                buff.append(StringUtils.quoteIdentifier(targetSchema));
                buff.append('.');
                buff.append(StringUtils.quoteIdentifier(table));
                buff.append('(');
                buff.append(StringUtils.quoteStringSQL(driver));
                buff.append(", ");
                buff.append(StringUtils.quoteStringSQL(url));
                buff.append(", ");
                buff.append(StringUtils.quoteStringSQL(user));
                buff.append(", ");
                buff.append(StringUtils.quoteStringSQL(password));
                buff.append(", ");
                buff.append(StringUtils.quoteStringSQL(table));
                buff.append(')');
                sql = buff.toString();
                stat.execute(sql);
                result.addRow(new String[] { table });
            }
        } finally {
            JdbcUtils.closeSilently(rs);
            JdbcUtils.closeSilently(c2);
            JdbcUtils.closeSilently(stat);
        }
        return result;
    }
}
