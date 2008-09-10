/*
 * Copyright 2004-2008 H2 Group. Multiple-Licensed under the H2 License, 
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Properties;

import org.h2.jdbc.JdbcConnection;
import org.h2.message.TraceSystem;
import org.h2.store.FileLock;
import org.h2.tools.DeleteDbFiles;

/**
 * The base class for all tests.
 */
public abstract class TestBase {

    /**
     * The base directory to write test databases.
     */
    protected static String baseDir = getTestDir("");
    private static final String BASE_TEST_DIR = "data";

    /**
     * The test configuration.
     */
    protected TestAll config;
    
    private long start;

    /**
     * Get the test directory for this test.
     * 
     * @param name the directory name suffix
     */
    public static String getTestDir(String name) {
        return BASE_TEST_DIR + "/test" + name;
    }
    
    /**
     * Start the TCP server if enabled in the configuration.
     */
    protected void startServerIfRequired() throws SQLException {
        config.beforeTest();
    }

    /**
     * Initialize the test configuration using the default settings.
     * 
     * @return itself
     */
    public TestBase init() throws Exception {
        baseDir = getTestDir("");
        this.config = new TestAll();
        return this;
    }

    /**
     * Initialize the test configuration.
     * 
     * @param conf the configuration
     * @return itself
     */
    public TestBase init(TestAll conf) throws Exception {
        baseDir = getTestDir("");
        this.config = conf;
        return this;
    }

    /**
     * Run a test case using the given seed value.
     * 
     * @param seed the random seed value
     */
    public void testCase(int seed) throws Exception {
        // do nothing
    }

    /**
     * This method is initializes the test, runs the test by calling the test()
     * method, and prints status information. It also catches exceptions so that
     * the tests can continue.
     * 
     * @param conf the test configuration
     */
    public void runTest(TestAll conf) {
        try {
            init(conf);
            start = System.currentTimeMillis();
            test();
            println("");
        } catch (Exception e) {
            println("FAIL " + e.toString());
            logError("FAIL " + e.toString(), e);
            if (config.stopOnError) {
                throw new Error("ERROR");
            }
        }
    }

    /**
     * Open a database connection in admin mode. The default user name and
     * password is used.
     * 
     * @param name the database name
     * @return the connection
     */
    public Connection getConnection(String name) throws Exception {
        return getConnectionInternal(getURL(name, true), getUser(), getPassword());
    }

    /**
     * Open a database connection.
     * 
     * @param name the database name
     * @param user the user name to use
     * @param password the password to use
     * @return the connection
     */
    protected Connection getConnection(String name, String user, String password) throws Exception {
        return getConnectionInternal(getURL(name, false), user, password);
    }

    protected String getPassword() {
        return "123";
    }

    private void deleteIndexFiles(String name) {
        if (name.indexOf(";") > 0) {
            name = name.substring(0, name.indexOf(';'));
        }
        name += ".index.db";
        if (new File(name).canWrite()) {
            new File(name).delete();
        }
    }

    /**
     * Get the database URL for the given database name using the current
     * configuration options.
     * 
     * @param name the database name
     * @param admin true if the current user is an admin
     * @return the database URL
     */
    protected String getURL(String name, boolean admin) {
        String url;
        if (name.startsWith("jdbc:")) {
            return name;
        }
        if (config.memory) {
            name = "mem:" + name;
        } else {
            if (!name.startsWith("memFS:") && !name.startsWith(baseDir + "/")) {
                name = baseDir + "/" + name;
            }
            if (config.deleteIndex) {
                deleteIndexFiles(name);
            }
        }
        if (config.networked) {
            if (config.ssl) {
                url = "ssl://localhost:9192/" + name;
            } else {
                url = "tcp://localhost:9192/" + name;
            }
        } else {
            url = name;
        }
        if (!config.memory) {
            if (config.textStorage) {
                url += ";STORAGE=TEXT";
            }
            if (admin) {
                url += ";LOG=" + config.logMode;
            }
            if (config.smallLog && admin) {
                url += ";MAX_LOG_SIZE=1";
            }
        }
        if (config.traceSystemOut) {
            url += ";TRACE_LEVEL_SYSTEM_OUT=2";
        }
        if (config.traceLevelFile > 0 && admin) {
            url += ";TRACE_LEVEL_FILE=" + config.traceLevelFile;
        }
        if (config.throttle > 0) {
            url += ";THROTTLE=" + config.throttle;
        }
        if (url.indexOf("LOCK_TIMEOUT=") < 0) {
            url += ";LOCK_TIMEOUT=50";
        }
        if (config.diskUndo && admin) {
            url += ";MAX_MEMORY_UNDO=3";
        }
        if (config.big && admin) {
            // force operations to disk
            url += ";MAX_OPERATION_MEMORY=1";
        }
        if (config.mvcc && url.indexOf("MVCC=") < 0) {
            url += ";MVCC=TRUE";
        }
        if (config.cache2Q) {
            url += ";CACHE_TYPE=TQ";
        }
        if (config.diskResult && admin) {
            url += ";MAX_MEMORY_ROWS=100;CACHE_SIZE=0";
        }
        return "jdbc:h2:" + url;
    }

    private Connection getConnectionInternal(String url, String user, String password) throws Exception {
        Class.forName("org.h2.Driver");
        // url += ";DEFAULT_TABLE_TYPE=1";
        // Class.forName("org.hsqldb.jdbcDriver");
        // return DriverManager.getConnection("jdbc:hsqldb:" + name, "sa", "");
        Connection conn;
        if (config.cipher != null) {
            url += ";cipher=" + config.cipher;
            password = "filePassword " + password;
            Properties info = new Properties();
            info.setProperty("user", user);
            info.setProperty("password", password);
            // a bug in the PostgreSQL driver: throws a NullPointerException if we do this
            // info.put("password", password.toCharArray());
            conn = DriverManager.getConnection(url, info);
        } else {
            conn = DriverManager.getConnection(url, user, password);
        }
        return conn;
    }

    /**
     * Get the small or the big value depending on the configuration.
     * 
     * @param small the value to return if the current test mode is 'small'
     * @param big the value to return if the current test mode is 'big'
     * @return small or big, depending on the configuration
     */
    protected int getSize(int small, int big) {
        return config.endless ? Integer.MAX_VALUE : config.big ? big : small;
    }

    protected String getUser() {
        return "sa";
    }

    /**
     * Write a message to system out if trace is enabled.
     * 
     * @param x the value to write
     */
    protected void trace(int x) {
        trace("" + x);
    }

    /**
     * Write a message to system out if trace is enabled.
     * 
     * @param s the message to write
     */
    public void trace(String s) {
        if (config.traceTest) {
            println(s);
        }
    }

    /**
     * Print how much memory is currently used.
     */
    protected void traceMemory() {
        if (config.traceTest) {
            trace("mem=" + getMemoryUsed());
        }
    }

    /**
     * Print the currently used memory, the message and the given time in
     * milliseconds.
     * 
     * @param s the message
     * @param time the time in millis
     */
    public void printTimeMemory(String s, long time) {
        if (config.big) {
            println(getMemoryUsed() + " MB: " + s + " ms: " + time);
        }
    }

    /**
     * Get the number of megabytes heap memory in use.
     * 
     * @return the used megabytes
     */
    public static int getMemoryUsed() {
        Runtime rt = Runtime.getRuntime();
        long memory = Long.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            rt.gc();
            long memNow = rt.totalMemory() - rt.freeMemory();
            if (memNow >= memory) {
                break;
            }
            memory = memNow;
        }
        int mb = (int) (memory / 1024 / 1024);
        return mb;
    }

    /**
     * Called if the test reached a point that was not expected.
     * 
     * @throws Exception always throws an exception
     */
    protected void fail() throws Exception {
        fail("Unexpected success");
    }

    /**
     * Called if the test reached a point that was not expected.
     * 
     * @param string the error message
     * @throws Exception always throws an exception
     */
    protected void fail(String string) throws Exception {
        println(string);
        throw new Exception(string);
    }

    /**
     * Log an error message.
     * 
     * @param s the message
     * @param e the exception
     */
    public static void logError(String s, Throwable e) {
        if (e == null) {
            e = new Exception(s);
        }
        System.out.println("ERROR: " + s + " " + e.toString() + " ------------------------------");
        e.printStackTrace();
        try {
            TraceSystem ts = new TraceSystem(null, false);
            FileLock lock = new FileLock(ts, 1000);
            lock.lock("error.lock", false);
            FileWriter fw = new FileWriter("ERROR.txt", true);
            PrintWriter pw = new PrintWriter(fw);
            e.printStackTrace(pw);
            pw.close();
            fw.close();
            lock.unlock();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Print a message to system out.
     * 
     * @param s the message
     */
    protected void println(String s) {
        long time = System.currentTimeMillis() - start;
        printlnWithTime(time, getClass().getName() + " " + s);
    }

    /**
     * Print a message, prepended with the specified time in milliseconds.
     * 
     * @param time the milliseconds
     * @param s the message
     */
    static void printlnWithTime(long time, String s) {
        String t = "0000000000" + time;
        t = t.substring(t.length() - 6);
        System.out.println(t + " " + s);
    }

    /**
     * Print the current time and a message to system out.
     * 
     * @param s the message
     */    
    protected void printTime(String s) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        println(dateFormat.format(new java.util.Date()) + " " + s);
    }

    /**
     * Delete all database files for this database.
     * 
     * @param name the database name
     */
    protected void deleteDb(String name) throws Exception {
        DeleteDbFiles.execute(baseDir, name, true);
    }

    /**
     * Delete all database files for a database.
     * 
     * @param dir the directory where the database files are located
     * @param name the database name
     */
    protected void deleteDb(String dir, String name) throws Exception {
        DeleteDbFiles.execute(dir, name, true);
    }

    /**
     * This method will be called by the test framework.
     * 
     * @throws Exception if an exception in the test occurs
     */
    public abstract void test() throws Exception;

    /**
     * Check if two values are equal, and if not throw an exception.
     * 
     * @param message the message to print in case of error
     * @param expected the expected value
     * @param actual the actual value
     * @throws Exception if the values are not equal
     */
    public void assertEquals(String message, int expected, int actual) throws Exception {
        if (expected != actual) {
            fail("Expected: " + expected + " actual: " + actual + " message: " + message);
        }
    }
    
    /**
     * Check if two values are equal, and if not throw an exception.
     * 
     * @param expected the expected value
     * @param actual the actual value
     * @throws Exception if the values are not equal
     */
    public void assertEquals(int expected, int actual) throws Exception {
        if (expected != actual) {
            fail("Expected: " + expected + " actual: " + actual);
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     * 
     * @param expected the expected value
     * @param actual the actual value
     * @throws Exception if the values are not equal
     */
    protected void assertEquals(byte[] expected, byte[] actual) throws Exception {
        assertTrue(expected.length == actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                fail("Expected[" + i + "]: a=" + (int) expected[i] + " actual=" + (int) actual[i]);
            }
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     * 
     * @param expected the expected value
     * @param actual the actual value
     * @throws Exception if the values are not equal
     */
    protected void assertEquals(String expected, String actual) throws Exception {
        if (expected == null && actual == null) {
            return;
        } else if (expected == null || actual == null) {
            fail("Expected: " + expected + " Actual: " + actual);
        }
        if (!expected.equals(actual)) {
            for (int i = 0; i < expected.length(); i++) {
                String s = expected.substring(0, i);
                if (!actual.startsWith(s)) {
                    expected = expected.substring(0, i) + "<*>" + expected.substring(i);
                    break;
                }
            }
            int al = expected.length();
            int bl = actual.length();
            if (al > 4000) {
                expected = expected.substring(0, 4000);
            }
            if (bl > 4000) {
                actual = actual.substring(0, 4000);
            }
            fail("Expected: " + expected + " (" + al + ") actual: " + actual + " (" + bl + ")");
        }
    }

    /**
     * Check if the first value is larger or equal than the second value, and if
     * not throw an exception.
     * 
     * @param a the first value
     * @param b the second value (must be smaller than the first value)
     * @throws Exception if the first value is smaller
     */    
    protected void assertSmaller(long a, long b) throws Exception {
        if (a >= b) {
            fail("a: " + a + " is not smaller than b: " + b);
        }
    }

    /**
     * Check that a result contains the given substring.
     * 
     * @param result the result value
     * @param contains the term that should appear in the result
     * @throws Exception if the term was not found
     */   
    protected void assertContains(String result, String contains) throws Exception {
        if (result.indexOf(contains) < 0) {
            fail(result + " does not contain: " + contains);
        }
    }
    
    /**
     * Check that a text starts with the expected characters..
     * 
     * @param text the text
     * @param  expectedStart the expected prefix
     * @throws Exception if the text does not start with the expected characters
     */  
    protected void assertStartsWith(String text, String expectedStart) throws Exception {
        if (!text.startsWith(expectedStart)) {
            fail(text + " does not start with: " + expectedStart);
        }
    }
    
    /**
     * Check if two values are equal, and if not throw an exception.
     * 
     * @param expected the expected value
     * @param actual the actual value
     * @throws Exception if the values are not equal
     */
    protected void assertEquals(long expected, long actual) throws Exception {
        if (expected != actual) {
            fail("Expected: " + expected + " actual: " + actual);
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     * 
     * @param expected the expected value
     * @param actual the actual value
     * @throws Exception if the values are not equal
     */
    protected void assertEquals(double expected, double actual) throws Exception {
        if (expected != actual) {
            if (Double.isNaN(expected) && Double.isNaN(actual)) {
                // if both a NaN, then there is no error
            } else {                
                fail("Expected: " + expected + " actual: " + actual);
            }
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     * 
     * @param expected the expected value
     * @param actual the actual value
     * @throws Exception if the values are not equal
     */
    protected void assertEquals(float expected, float actual) throws Exception {
        if (expected != actual) {
            if (Float.isNaN(expected) && Float.isNaN(actual)) {
                // if both a NaN, then there is no error
            } else {
                fail("Expected: " + expected + " actual: " + actual);
            }
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     * 
     * @param expected the expected value
     * @param actual the actual value
     * @throws Exception if the values are not equal
     */
    protected void assertEquals(boolean expected, boolean actual) throws Exception {
        if (expected != actual) {
            fail("Boolean expected: " + expected + " actual: " + actual);
        }
    }
    
    /**
     * Check that the passed boolean is true.
     * 
     * @param condition the condition
     * @throws Exception if the condition is false
     */
    protected void assertTrue(boolean condition) throws Exception {
        assertTrue("Expected: true got: false", condition);
    }

    /**
     * Check that the passed boolean is true.
     * 
     * @param message the message to print if the condition is false
     * @param condition the condition
     * @throws Exception if the condition is false
     */
    protected void assertTrue(String message, boolean condition) throws Exception {
        if (!condition) {
            fail(message);
        }
    }

    /**
     * Check that the passed boolean is false.
     * 
     * @param value the condition
     * @throws Exception if the condition is true
     */
    protected void assertFalse(boolean value) throws Exception {
        assertFalse("Expected: false got: true", value);
    }
    
    /**
     * Check that the passed boolean is false.
     * 
     * @param message the message to print if the condition is false
     * @param value the condition
     * @throws Exception if the condition is true
     */
    protected void assertFalse(String message, boolean value) throws Exception {
        if (value) {
            fail(message);
        }        
    }
    
    /**
     * Check that the result set row count matches.
     * 
     * @param rs the result set
     * @param expected the number of expected rows
     * @throws Exception if a different number of rows have been found
     */
    protected void assertResultRowCount(ResultSet rs, int expected) throws Exception {
        int i = 0;
        while (rs.next()) {
            i++;
        }
        assertEquals(i, expected);
    }

    /**
     * Check that the result set of a query is exactly this value.
     * 
     * @param stat the statement
     * @param sql the SQL statement to execute
     * @param expected the expected result value
     * @throws Exception if a different result value was returned
     */
    protected void assertSingleValue(Statement stat, String sql, int expected) throws Exception {
        ResultSet rs = stat.executeQuery(sql);
        assertTrue(rs.next());
        assertEquals(expected, rs.getInt(1));
        assertFalse(rs.next());
    }
    
    /**
     * Check that the result set of a query is exactly this value.
     * 
     * @param stat the statement
     * @param sql the SQL statement to execute
     * @param expected the expected result value
     * @throws Exception if a different result value was returned
     */
    protected void assertResult(Statement stat, String sql, String expected) throws Exception {
        ResultSet rs = stat.executeQuery(sql);
        if (rs.next()) {
            String actual = rs.getString(1);
            assertEquals(expected, actual);
        } else {
            assertEquals(null, expected);
        }
    }

    /**
     * Check if the result set meta data is correct.
     * 
     * @param rs the result set
     * @param columnCount the expected column count
     * @param labels the expected column labels
     * @param datatypes the expected data types
     * @param precision the expected precisions
     * @param scale the expected scales
     */
    protected void assertResultSetMeta(ResultSet rs, int columnCount, String[] labels, int[] datatypes, int[] precision,
            int[] scale) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int cc = meta.getColumnCount();
        if (cc != columnCount) {
            fail("result set contains " + cc + " columns not " + columnCount);
        }
        for (int i = 0; i < columnCount; i++) {
            if (labels != null) {
                String l = meta.getColumnLabel(i + 1);
                if (!labels[i].equals(l)) {
                    fail("column label " + i + " is " + l + " not " + labels[i]);
                }
            }
            if (datatypes != null) {
                int t = meta.getColumnType(i + 1);
                if (datatypes[i] != t) {
                    fail("column datatype " + i + " is " + t + " not " + datatypes[i] + " (prec="
                            + meta.getPrecision(i + 1) + " scale=" + meta.getScale(i + 1) + ")");
                }
                String typeName = meta.getColumnTypeName(i + 1);
                String className = meta.getColumnClassName(i + 1);
                switch (t) {
                case Types.INTEGER:
                    assertEquals(typeName, "INTEGER");
                    assertEquals(className, "java.lang.Integer");
                    break;
                case Types.VARCHAR:
                    assertEquals(typeName, "VARCHAR");
                    assertEquals(className, "java.lang.String");
                    break;
                case Types.SMALLINT:
                    assertEquals(typeName, "SMALLINT");
                    assertEquals(className, "java.lang.Short");
                    break;
                case Types.TIMESTAMP:
                    assertEquals(typeName, "TIMESTAMP");
                    assertEquals(className, "java.sql.Timestamp");
                    break;
                case Types.DECIMAL:
                    assertEquals(typeName, "DECIMAL");
                    assertEquals(className, "java.math.BigDecimal");
                    break;
                default:
                }
            }
            if (precision != null) {
                int p = meta.getPrecision(i + 1);
                if (precision[i] != p) {
                    fail("column precision " + i + " is " + p + " not " + precision[i]);
                }
            }
            if (scale != null) {
                int s = meta.getScale(i + 1);
                if (scale[i] != s) {
                    fail("column scale " + i + " is " + s + " not " + scale[i]);
                }
            }

        }
    }

    /**
     * Check if a result set contains the expected data.
     * The sort order is significant
     * 
     * @param rs the result set
     * @param data the expected data
     * @throws Exception if there is a mismatch
     */
    protected void assertResultSetOrdered(ResultSet rs, String[][] data) throws Exception {
        assertResultSet(true, rs, data);
    }

    /**
     * Check if a result set contains the expected data.
     * The sort order is not significant
     * 
     * @param rs the result set
     * @param data the expected data
     * @throws Exception if there is a mismatch
     */
//    void assertResultSetUnordered(ResultSet rs, String[][] data) 
//            throws Exception {
//        assertResultSet(false, rs, data);
//    }

    /**
     * Check if a result set contains the expected data.
     * 
     * @param ordered if the sort order is significant
     * @param rs the result set
     * @param data the expected data
     * @throws Exception if there is a mismatch
     */
    private void assertResultSet(boolean ordered, ResultSet rs, String[][] data) throws Exception {
        int len = rs.getMetaData().getColumnCount();
        int rows = data.length;
        if (rows == 0) {
            // special case: no rows
            if (rs.next()) {
                fail("testResultSet expected rowCount:" + rows + " got:0");
            }
        }
        int len2 = data[0].length;
        if (len < len2) {
            fail("testResultSet expected columnCount:" + len2 + " got:" + len);
        }
        for (int i = 0; i < rows; i++) {
            if (!rs.next()) {
                fail("testResultSet expected rowCount:" + rows + " got:" + i);
            }
            String[] row = getData(rs, len);
            if (ordered) {
                String[] good = data[i];
                if (!testRow(good, row, good.length)) {
                    fail("testResultSet row not equal, got:\n" + formatRow(row) + "\n" + formatRow(good));
                }
            } else {
                boolean found = false;
                for (int j = 0; j < rows; j++) {
                    String[] good = data[i];
                    if (testRow(good, row, good.length)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    fail("testResultSet no match for row:" + formatRow(row));
                }
            }
        }
        if (rs.next()) {
            String[] row = getData(rs, len);
            fail("testResultSet expected rowcount:" + rows + " got:>=" + (rows + 1) + " data:" + formatRow(row));
        }
    }

    private boolean testRow(String[] a, String[] b, int len) {
        for (int i = 0; i < len; i++) {
            String sa = a[i];
            String sb = b[i];
            if (sa == null || sb == null) {
                if (sa != sb) {
                    return false;
                }
            } else {
                if (!sa.equals(sb)) {
                    return false;
                }
            }
        }
        return true;
    }

    private String[] getData(ResultSet rs, int len) throws SQLException {
        String[] data = new String[len];
        for (int i = 0; i < len; i++) {
            data[i] = rs.getString(i + 1);
            // just check if it works
            rs.getObject(i + 1);
        }
        return data;
    }

    private String formatRow(String[] row) {
        String sb = "";
        for (int i = 0; i < row.length; i++) {
            sb += "{" + row[i] + "}";
        }
        return "{" + sb + "}";
    }

    /**
     * Simulate a database crash. This method will also close the database
     * files, but the files are in a state as the power was switched off. It
     * doesn't throw an exception.
     * 
     * @param conn the database connection
     */
    protected void crash(Connection conn) throws Exception {
        ((JdbcConnection) conn).setPowerOffCount(1);
        try {
            conn.createStatement().execute("SET WRITE_DELAY 0");
            conn.createStatement().execute("CREATE TABLE TEST_A(ID INT)");
            fail("should be crashed already");
        } catch (SQLException e) {
            // expected
        }
        try {
            conn.close();
        } catch (SQLException e) {
            // ignore
        }
    }

    /**
     * Read a string from the reader. This method reads until end of file.
     * 
     * @param reader the reader
     * @return the string read
     */
    protected String readString(Reader reader) throws Exception {
        if (reader == null) {
            return null;
        }
        StringBuffer buffer = new StringBuffer();
        try {
            while (true) {
                int c = reader.read();
                if (c == -1) {
                    break;
                }
                buffer.append((char) c);
            }
            return buffer.toString();
        } catch (Exception e) {
            assertTrue(false);
            return null;
        }
    }

    /**
     * Check that a given exception is not an unexpected 'general error'
     * exception.
     * 
     * @param e the error
     */
    protected void assertKnownException(SQLException e) throws Exception {
        assertKnownException("", e);
    }

    /**
     * Check that a given exception is not an unexpected 'general error'
     * exception.
     * 
     * @param message the message
     * @param e the exception
     */
    protected void assertKnownException(String message, SQLException e) throws Exception {
        if (e != null && e.getSQLState().startsWith("HY000")) {
            TestBase.logError("Unexpected General error " + message, e);
        }
    }

    /**
     * Check if two values are equal, and if not throw an exception.
     * 
     * @param expected the expected value
     * @param actual the actual value
     * @throws Exception if the values are not equal
     */
    protected void assertEquals(Integer expected, Integer actual) throws Exception {
        if (expected == null || actual == null) {
            assertTrue(expected == actual);
        } else {
            assertEquals(expected.intValue(), actual.intValue());
        }
    }
    
    /**
     * Check if two databases contain the same met data.
     * 
     * @param stat1 the connection to the first database
     * @param stat2 the connection to the second database
     * @throws Exception if the database don't match
     */
    protected void assertEqualDatabases(Statement stat1, Statement stat2) throws Exception {
        ResultSet rs1 = stat1.executeQuery("SCRIPT NOPASSWORDS");
        ResultSet rs2 = stat2.executeQuery("SCRIPT NOPASSWORDS");
        ArrayList list1 = new ArrayList();
        ArrayList list2 = new ArrayList();
        while (rs1.next()) {
            assertTrue(rs2.next());
            String s1 = rs1.getString(1);
            list1.add(s1);
            String s2 = rs2.getString(1);
            list2.add(s2);
        }
        for (int i = 0; i < list1.size(); i++) {
            String s = (String) list1.get(i);
            if (!list2.remove(s)) {
                fail("not found: " + s);
            }
        }
        assertEquals(list2.size(), 0);
        assertFalse(rs2.next());
    }

}
