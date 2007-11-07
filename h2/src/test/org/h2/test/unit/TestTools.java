/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.unit;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.h2.test.TestBase;
import org.h2.tools.Backup;
import org.h2.tools.ChangePassword;
import org.h2.tools.ConvertTraceFile;
import org.h2.tools.DeleteDbFiles;
import org.h2.tools.Recover;
import org.h2.tools.Restore;
import org.h2.tools.RunScript;
import org.h2.tools.Script;
import org.h2.tools.Server;
import org.h2.util.Resources;

public class TestTools extends TestBase {

    public void test() throws Exception {
        deleteDb("utils");
        testRemove();
        testConvertTraceFile();
        testManagementDb();
        testResourceGenerator();
        testChangePassword();
        testServer();
        testScriptRunscript();
        testBackupRestore();
        testRecover();
    }
    
    private void testConvertTraceFile() throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:" + baseDir + "/toolsConvertTraceFile";
        Connection conn = DriverManager.getConnection(url + ";TRACE_LEVEL_FILE=3", "sa", "sa");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id int primary key, name varchar)");
        stat.execute("insert into test values(1, 'Hello')");
        conn.close();
        ConvertTraceFile.main(new String[]{"-traceFile", baseDir + "/toolsConvertTraceFile.trace.db", "-javaClass", baseDir + "/Test", "-script", baseDir + "/test.sql"});
        deleteDb("toolsConvertTraceFile");
        RunScript.main(new String[]{"-url", url, "-user", "test", "-password", "test", "-script", baseDir + "/test.sql"});
        conn = DriverManager.getConnection(url, "test", "test");
        stat = conn.createStatement();
        ResultSet rs;
        rs = stat.executeQuery("select * from test");
        rs.next();
        check(1, rs.getInt(1));
        check("Hello", rs.getString(2));
        checkFalse(rs.next());
        conn.close();
    }

    private void testRemove() throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:" + baseDir + "/toolsRemove";
        Connection conn = DriverManager.getConnection(url, "sa", "sa");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id int primary key, name varchar)");
        stat.execute("insert into test values(1, 'Hello')");
        conn.close();
        Recover.main(new String[]{"-dir", baseDir, "-db", "toolsRemove", "-removePassword"});
        conn = DriverManager.getConnection(url, "sa", "");
        stat = conn.createStatement();
        ResultSet rs;
        rs = stat.executeQuery("select * from test");
        rs.next();
        check(1, rs.getInt(1));
        check("Hello", rs.getString(2));
        conn.close();
    }
    
    private void testRecover() throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:" + baseDir + "/toolsRecover";
        Connection conn = DriverManager.getConnection(url, "sa", "sa");
        Statement stat = conn.createStatement();
        stat.execute("create table test(id int primary key, name varchar, b blob, c clob)");
        stat.execute("insert into test values(1, 'Hello', SECURE_RAND(2000), space(2000))");
        ResultSet rs;
        rs = stat.executeQuery("select * from test");
        rs.next();
        byte[] b1 = rs.getBytes(3);
        String s1 = rs.getString(4);
        
        conn.close();
        Recover.main(new String[]{"-dir", baseDir, "-db", "toolsRecover"});
        deleteDb("toolsRecover");
        conn = DriverManager.getConnection(url, "another", "another");
        stat = conn.createStatement();
        stat.execute("runscript from '" + baseDir + "/toolsRecover.data.sql'");
        rs = stat.executeQuery("select * from test");
        rs.next();
        check(1, rs.getInt(1));
        check("Hello", rs.getString(2));
        byte[] b2 = rs.getBytes(3);
        String s2 = rs.getString(4);
        check(2000, b2.length);
        check(2000, s2.length());
        check(b1, b2);
        check(s1, s2);
        checkFalse(rs.next());
        conn.close();
    }

    private void testManagementDb() throws Exception {
        int count = getSize(2, 10);
        for (int i = 0; i < count; i++) {
            Server server = Server.createTcpServer(new String[] {}).start();
            server.stop();
            server = Server.createTcpServer(new String[] { "-tcpPassword", "abc" }).start();
            server.stop();
        }
    }

    private void testScriptRunscript() throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:" + baseDir + "/utils";
        String user = "sa", password = "abc";
        String fileName = baseDir + "/b2.sql";
        Connection conn = DriverManager.getConnection(url, user, password);
        conn.createStatement().execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR)");
        conn.createStatement().execute("INSERT INTO TEST VALUES(1, 'Hello')");
        conn.close();
        Script.main(new String[] { "-url", url, "-user", user, "-password", password, "-script", fileName, "-options",
                "nodata", "compression", "lzf", "cipher", "xtea", "password", "'123'" });
        DeleteDbFiles.main(new String[] { "-dir", baseDir, "-db", "utils", "-quiet" });
        RunScript.main(new String[] { "-url", url, "-user", user, "-password", password, "-script", fileName,
                "-options", "compression", "lzf", "cipher", "xtea", "password", "'123'" });
        conn = DriverManager.getConnection("jdbc:h2:" + baseDir + "/utils", "sa", "abc");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        checkFalse(rs.next());
        conn.close();
    }

    private void testBackupRestore() throws Exception {
        Class.forName("org.h2.Driver");
        String url = "jdbc:h2:" + baseDir + "/utils";
        String user = "sa", password = "abc";
        String fileName = baseDir + "/b2.zip";
        DeleteDbFiles.main(new String[] { "-dir", baseDir, "-db", "utils", "-quiet" });
        Connection conn = DriverManager.getConnection(url, user, password);
        conn.createStatement().execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR)");
        conn.createStatement().execute("INSERT INTO TEST VALUES(1, 'Hello')");
        conn.close();
        Backup.main(new String[] { "-file", fileName, "-dir", baseDir, "-db", "utils", "-quiet" });
        DeleteDbFiles.main(new String[] { "-dir", baseDir, "-db", "utils", "-quiet" });
        Restore.main(new String[] { "-file", fileName, "-dir", baseDir, "-db", "utils", "-quiet" });
        conn = DriverManager.getConnection("jdbc:h2:" + baseDir + "/utils", "sa", "abc");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM TEST");
        check(rs.next());
        checkFalse(rs.next());
        conn.close();
        DeleteDbFiles.main(new String[] { "-dir", baseDir, "-db", "utils", "-quiet" });
    }

    private void testResourceGenerator() throws Exception {
        Resources.main(new String[] { "." });
    }

    private void testChangePassword() throws Exception {
        Class.forName("org.h2.Driver");
        Connection conn = DriverManager.getConnection("jdbc:h2:" + baseDir + "/utils;CIPHER=XTEA;STORAGE=TEXT", "sa",
                "abc 123");
        Statement stat = conn.createStatement();
        stat.execute("CREATE TABLE TEST(ID INT PRIMARY KEY, NAME VARCHAR(255))");
        conn.close();
        String[] args = new String[] { "-dir", baseDir, "-db", "utils", "-cipher", "XTEA", "-decrypt", "abc", "-quiet" };
        ChangePassword.main(args);
        args = new String[] { "-dir", baseDir, "-db", "utils", "-cipher", "AES", "-encrypt", "def", "-quiet" };
        ChangePassword.main(args);
        conn = DriverManager.getConnection("jdbc:h2:" + baseDir + "/utils;CIPHER=AES", "sa", "def 123");
        stat = conn.createStatement();
        stat.execute("SELECT * FROM TEST");
        conn.close();
        args = new String[] { "-dir", baseDir, "-db", "utils", "-quiet" };
        DeleteDbFiles.main(args);
    }

    private void testServer() throws Exception {
        Connection conn;
        Server server = Server.createTcpServer(new String[] { "-ifExists", "false", "-baseDir", baseDir }).start();
        conn = DriverManager.getConnection("jdbc:h2:tcp://localhost/test", "sa", "");
        conn.close();
        server.stop();

        server = Server.createTcpServer(
                new String[] { "-ifExists", "true", "-tcpPassword", "abc", "-baseDir", baseDir }).start();
        try {
            conn = DriverManager.getConnection("jdbc:h2:tcp://localhost/test2", "sa", "");
            error("should not be able to create new db");
        } catch (SQLException e) {
            checkNotGeneralException(e);
        }
        conn = DriverManager.getConnection("jdbc:h2:tcp://localhost/test", "sa", "");
        conn.close();
        try {
            Server.shutdownTcpServer("tcp://localhost", "", true);
            error("shouldn't work and should throw an exception");
        } catch (SQLException e) {
            // expected
        }
        conn = DriverManager.getConnection("jdbc:h2:tcp://localhost/test", "sa", "");
        conn.close();
        Server.shutdownTcpServer("tcp://localhost", "abc", true);
        try {
            conn = DriverManager.getConnection("jdbc:h2:tcp://localhost/test", "sa", "");
            error("server must have been closed");
        } catch (SQLException e) {
            checkNotGeneralException(e);
        }
    }

}
