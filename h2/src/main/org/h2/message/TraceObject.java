/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.message;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;

import org.h2.constant.SysProperties;
import org.h2.util.FileUtils;
import org.h2.util.StringUtils;

public class TraceObject {
    public static final int CALLABLE_STATEMENT = 0, CONNECTION = 1, DATABASE_META_DATA = 2,
        PREPARED_STATEMENT = 3, RESULT_SET = 4, RESULT_SET_META_DATA = 5,
        SAVEPOINT = 6, SQL_EXCEPTION = 7, STATEMENT = 8, BLOB = 9, CLOB = 10,
        PARAMETER_META_DATA = 11;
    public static final int DATA_SOURCE = 12, XA_DATA_SOURCE = 13, XID = 14, ARRAY = 15;
    
    private static final int LAST = ARRAY + 1;  
    private Trace trace;
    private static final int[] ID = new int[LAST];
    private static final String[] PREFIX = {
        "call", "conn", "dbMeta", "prep", "rs", "rsMeta", "sp", "ex", "stat", "blob", "clob", "pMeta",
        "ds", "xads", "xid", "ar"
    };
    private int type, id;
    
    protected void setTrace(Trace trace, int type, int id) {
        this.trace = trace;
        this.type = type;
        this.id = id;
    }
    
    protected int getTraceId() {
        return id;
    }
    
    /**
     * INTERNAL
     */
    public String toString() {
        return PREFIX[type] + id ;
    }
    
    protected int getNextId(int type) {
        return ID[type]++;
    }
    
    protected boolean debug() {
        return trace.debug();
    }
    
    protected boolean info() {
        return trace.info();
    }    

    protected Trace getTrace() {
        return trace;
    }
    
    protected void debugCodeAssign(String className, int type, int id) {
        if(!trace.debug()) {
            return;
        }
        trace.debugCode(className + " " + PREFIX[type] + id + " = ");
    }
    
    protected void infoCodeAssign(String className, int type, int id) {
        if(!trace.info()) {
            return;
        }
        trace.infoCode(className + " " + PREFIX[type] + id + " = ");
    }    
    
    protected void debugCodeCall(String text) {
        if(!trace.debug()) {
            return;
        }
        trace.debugCode(toString() + "." + text + "();");
    }
    
    protected void debugCodeCall(String text, long param) {
        if(!trace.debug()) {
            return;
        }
        trace.debugCode(toString() + "." + text + "("+param+");");
    }
    
    protected void debugCodeCall(String text, String param) {
        if(!trace.debug()) {
            return;
        }
        trace.debugCode(toString() + "." + text + "("+quote(param)+");");
    }    
    
    protected void debugCode(String text) {
        if(!trace.debug()) {
            return;
        }
        trace.debugCode(toString() + "." + text);
    }
    
    protected String quote(String s) {
        return StringUtils.quoteJavaString(s);
    }
    
    protected String quoteTime(java.sql.Time x) {
        if(x == null) {
            return "null";
        }
        return "Time.valueOf(\"" + x.toString() + "\")";
    }

    protected String quoteTimestamp(java.sql.Timestamp x) {
        if(x == null) {
            return "null";
        }
        return "Timestamp.valueOf(\"" + x.toString() + "\")";
    }
    
    protected String quoteDate(java.sql.Date x) {
        if(x == null) {
            return "null";
        }
        return "Date.valueOf(\"" + x.toString() + "\")";
    }    
    
    protected String quoteBigDecimal(BigDecimal x) {
        if(x == null) {
            return "null";
        }
        return "new BigDecimal(\"" + x.toString() + "\")";
    }
    
    protected String quoteBytes(byte[] x) {
        if(x == null) {
            return "null";
        }
        return "new byte[" + x.length + "]";
    }
    
    protected String quoteArray(String[] s) {
        return StringUtils.quoteJavaStringArray(s);
    }
    
    protected String quoteIntArray(int[] s) {
        return StringUtils.quoteJavaIntArray(s);
    }
    
    protected String quoteMap(Map map) {
        if(map == null) {
            return "null";
        }
        if(map.size() == 0) {
            return "new Map()";
        }
        StringBuffer buff = new StringBuffer("new Map() /* ");
        try {
            // Map<String, Class>
            for(Iterator it = map.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                String key = (String) entry.getKey();
                buff.append(key);
                buff.append(':');
                Class clazz = (Class) entry.getValue();
                buff.append(clazz.getName());
            }
        } catch(Exception e) {
            buff.append(e.toString()+": "+map.toString());
        }
        buff.append("*/");
        return buff.toString();
    }

    protected SQLException logAndConvert(Throwable e) {
        if(SysProperties.LOG_ALL_ERRORS)  {
            synchronized(this.getClass()) {
                // e.printStackTrace();
                try {
                    Writer writer = FileUtils.openFileWriter(SysProperties.LOG_ALL_ERRORS_FILE,  true);
                    PrintWriter p = new PrintWriter(writer);
                    e.printStackTrace(p);
                    p.close();
                    writer.close();
                } catch(IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
        if(trace == null) {
            TraceSystem.traceThrowable(e);
        } else {
            if(e instanceof SQLException) {
                trace.error("SQLException", e);
                return (SQLException)e;
            } else {
                trace.error("Uncaught Exception", e);
            }
        }
        return Message.convert(e);
    }
    
}
