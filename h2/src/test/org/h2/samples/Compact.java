/*
 * Copyright 2004-2006 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.samples;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.h2.tools.Script;
import org.h2.tools.DeleteDbFiles;
import org.h2.tools.RunScript;

public class Compact {
    public static void main(String[] args) throws Exception {
        DeleteDbFiles.execute("data", "test", true);
        Class.forName("org.h2.Driver");
        Connection conn = DriverManager.getConnection("jdbc:h2:data/test", "sa", "");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR)");
        stat.execute("INSERT INTO TEST VALUES(1, 'Hello'), (2, 'World');");
        conn.close();
        
        System.out.println("Compacting...");
        compact("data", "test", "sa", "");
        System.out.println("Done.");
        
    }
    
    public static void compact(String dir, String dbName, String user, String password) throws Exception {
        String url = "jdbc:h2:" + dir + "/" + dbName;
        String file = "data/test.sql";
        Script.execute(url, user, password, file);
        DeleteDbFiles.execute(dir, dbName, true);
        RunScript.execute(url, user, password, file, null, false);
    }
}
