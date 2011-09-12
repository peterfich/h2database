/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Properties;
import org.h2.constant.ErrorCode;
import org.h2.engine.ConnectionInfo;
import org.h2.engine.Constants;
import org.h2.engine.Database;
import org.h2.engine.Session;
import org.h2.message.DbException;
import org.h2.store.fs.FileUtils;
import org.h2.store.fs.Recorder;
import org.h2.store.fs.RecordingFileSystem;
import org.h2.tools.Recover;
import org.h2.util.IOUtils;
import org.h2.util.New;
import org.h2.util.StringUtils;
import org.h2.util.Utils;

/**
 * A tool that simulates a crash while writing to the database, and then
 * verifies the database doesn't get corrupt.
 */
public class RecoverTester implements Recorder {

    private static RecoverTester instance;

    private String testDatabase = "memFS:reopen";
    private int writeCount = Utils.getProperty("h2.recoverTestOffset", 0);
    private int testEvery = Utils.getProperty("h2.recoverTest", 64);
    private long maxFileSize = Utils.getProperty("h2.recoverTestMaxFileSize", Integer.MAX_VALUE) * 1024L * 1024;
    private int verifyCount;
    private HashSet<String> knownErrors = New.hashSet();
    private volatile boolean testing;

    /**
     * Initialize the recover test.
     *
     * @param recoverTest the value of the recover test parameter
     */
    public static synchronized void init(String recoverTest) {
        RecoverTester tester = RecoverTester.getInstance();
        if (StringUtils.isNumber(recoverTest)) {
            tester.setTestEvery(Integer.parseInt(recoverTest));
        }
        RecordingFileSystem.setRecorder(tester);
    }

    public static synchronized RecoverTester getInstance() {
        if (instance == null) {
            instance = new RecoverTester();
        }
        return instance;
    }

    public void log(int op, String fileName, byte[] data, long x) {
        if (op != Recorder.WRITE && op != Recorder.SET_LENGTH) {
            return;
        }
        if (!fileName.endsWith(Constants.SUFFIX_PAGE_FILE)) {
            return;
        }
        writeCount++;
        if ((writeCount % testEvery) != 0) {
            return;
        }
        if (FileUtils.size(fileName) > maxFileSize) {
            // System.out.println(fileName + " " + IOUtils.length(fileName));
            return;
        }
        if (testing) {
            // avoid deadlocks
            return;
        }
        testing = true;
        PrintWriter out = null;
        try {
            out = new PrintWriter(
                    new OutputStreamWriter(
                    FileUtils.newOutputStream(fileName + ".log", true)));
            testDatabase(fileName, out);
        } finally {
            IOUtils.closeSilently(out);
            testing = false;
        }
    }

    private synchronized void testDatabase(String fileName, PrintWriter out) {
        out.println("+ write #" + writeCount + " verify #" + verifyCount);
        try {
            FileUtils.copy(fileName, testDatabase + Constants.SUFFIX_PAGE_FILE);
            verifyCount++;
            // avoid using the Engine class to avoid deadlocks
            Properties p = new Properties();
            p.setProperty("user", "");
            p.setProperty("password", "");
            ConnectionInfo ci = new ConnectionInfo("jdbc:h2:" + testDatabase + ";FILE_LOCK=NO;TRACE_LEVEL_FILE=0", p);
            Database database = new Database(ci, null);
            // close the database
            Session session = database.getSystemSession();
            session.prepare("script to '" + testDatabase + ".sql'").query(0);
            session.prepare("shutdown immediately").update();
            database.removeSession(null);
            // everything OK - return
            return;
        } catch (DbException e) {
            SQLException e2 = DbException.toSQLException(e);
            int errorCode = e2.getErrorCode();
            if (errorCode == ErrorCode.WRONG_USER_OR_PASSWORD) {
                return;
            } else if (errorCode == ErrorCode.FILE_ENCRYPTION_ERROR_1) {
                return;
            }
            e.printStackTrace(System.out);
        } catch (Exception e) {
            // failed
            int errorCode = 0;
            if (e instanceof SQLException) {
                errorCode = ((SQLException) e).getErrorCode();
            }
            if (errorCode == ErrorCode.WRONG_USER_OR_PASSWORD) {
                return;
            } else if (errorCode == ErrorCode.FILE_ENCRYPTION_ERROR_1) {
                return;
            }
            e.printStackTrace(System.out);
        }
        out.println("begin ------------------------------ " + writeCount);
        try {
            Recover.execute(fileName.substring(0, fileName.lastIndexOf('/')), null);
        } catch (SQLException e) {
            // ignore
        }
        testDatabase += "X";
        try {
            FileUtils.copy(fileName, testDatabase + Constants.SUFFIX_PAGE_FILE);
            // avoid using the Engine class to avoid deadlocks
            Properties p = new Properties();
            ConnectionInfo ci = new ConnectionInfo("jdbc:h2:" + testDatabase + ";FILE_LOCK=NO", p);
            Database database = new Database(ci, null);
            // close the database
            database.removeSession(null);
        } catch (Exception e) {
            int errorCode = 0;
            if (e instanceof DbException) {
                e = ((DbException) e).getSQLException();
                errorCode = ((SQLException) e).getErrorCode();
            }
            if (errorCode == ErrorCode.WRONG_USER_OR_PASSWORD) {
                return;
            } else if (errorCode == ErrorCode.FILE_ENCRYPTION_ERROR_1) {
                return;
            }
            StringBuilder buff = new StringBuilder();
            StackTraceElement[] list = e.getStackTrace();
            for (int i = 0; i < 10 && i < list.length; i++) {
                buff.append(list[i].toString()).append('\n');
            }
            String s = buff.toString();
            if (!knownErrors.contains(s)) {
                out.println(writeCount + " code: " + errorCode + " " + e.toString());
                e.printStackTrace(System.out);
                knownErrors.add(s);
            } else {
                out.println(writeCount + " code: " + errorCode);
            }
        }
    }

    public void setTestEvery(int testEvery) {
        this.testEvery = testEvery;
    }

}