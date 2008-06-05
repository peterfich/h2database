/*
 * Copyright 2004-2008 H2 Group. Multiple-Licensed under the H2 License, 
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.h2.test.TestBase;

/**
 * Tests for overloaded user defined functions.
 * 
 * @author Gary Tong
 */
public class TestFunctionOverload extends TestBase {

    private static final String ME = TestFunctionOverload.class.getName();
    private Connection conn;
    private DatabaseMetaData meta;

    public void test() throws Exception {
        this.deleteDb("functionOverload");
        conn = getConnection("functionOverload");
        meta = conn.getMetaData();
        testControl();
        testOverload();
        testOverloadNamedArgs();
        testOverloadWithConnection();
        testOverloadError();
        conn.close();
    }
    
    private void testOverloadError() throws Exception {
        Statement stat = conn.createStatement();
        try {
            stat.execute("create alias overloadError for \"" + ME + ".overloadError\"");
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }
    }

    private void testControl() throws Exception {
        Statement stat = conn.createStatement();
        stat.execute("create alias overload0 for \"" + ME + ".overload0\"");
        ResultSet rs = stat.executeQuery("select overload0() from dual");
        assertTrue(rs.next());
        assertEquals("0 args", 0, rs.getInt(1));
        assertFalse(rs.next());
        rs = meta.getProcedures(null, null, "OVERLOAD0");
        rs.next();
        assertFalse(rs.next());
    }

    private void testOverload() throws Exception {
        Statement stat = conn.createStatement();
        stat.execute("create alias overload1or2 for \"" + ME + ".overload1or2\"");
        ResultSet rs = stat.executeQuery("select overload1or2(1) from dual");
        rs.next();
        assertEquals("1 arg", 1, rs.getInt(1));
        assertFalse(rs.next());
        rs = stat.executeQuery("select overload1or2(1, 2) from dual");
        rs.next();
        assertEquals("2 args", 3, rs.getInt(1));
        assertFalse(rs.next());
        rs = meta.getProcedures(null, null, "OVERLOAD1OR2");
        rs.next();
        assertEquals(1, rs.getInt("NUM_INPUT_PARAMS"));
        rs.next();
        assertEquals(2, rs.getInt("NUM_INPUT_PARAMS"));
        assertFalse(rs.next());
    }

    private void testOverloadNamedArgs() throws Exception {
        Statement stat = conn.createStatement();

        stat.execute("create alias overload1or2Named for \"" + ME + ".overload1or2(int)\"");

        ResultSet rs = stat.executeQuery("select overload1or2Named(1) from dual");
        assertTrue("First Row", rs.next());
        assertEquals("1 arg", 1, rs.getInt(1));
        assertFalse("Second Row", rs.next());
        rs.close();

        try {
            rs = stat.executeQuery("select overload1or2Named(1, 2) from dual");
            rs.close();
            fail();
        } catch (SQLException e) {
            assertKnownException(e);
        }

        stat.close();
    }

    private void testOverloadWithConnection() throws Exception {
        Statement stat = conn.createStatement();

        stat.execute("create alias overload1or2WithConn for \"" + ME + ".overload1or2WithConn\"");

        ResultSet rs = stat.executeQuery("select overload1or2WithConn(1) from dual");
        rs.next();
        assertEquals("1 arg", 1, rs.getInt(1));
        assertFalse(rs.next());
        rs.close();

        rs = stat.executeQuery("select overload1or2WithConn(1, 2) from dual");
        rs.next();
        assertEquals("2 args", 3, rs.getInt(1));
        assertFalse(rs.next());
        rs.close();

        stat.close();
    }

    public static int overload0() {
        return 0;
    }

    public static int overload1or2(int one) {
        return one;
    }

    public static int overload1or2(int one, int two) {
        return one + two;
    }

    public static int overload1or2WithConn(Connection conn, int one) throws SQLException {
        conn.createStatement().executeQuery("select 1 from dual");
        return one;
    }

    public static int overload1or2WithConn(int one, int two) {
        return one + two;
    }

    public static int overloadError(int one, int two) {
        return one + two;
    }

    public static int overloadError(double one, double two) {
        return (int) (one + two);
    }

}
