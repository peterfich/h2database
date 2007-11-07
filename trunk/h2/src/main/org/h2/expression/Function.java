/*
 * Copyright 2004-2007 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.expression;

import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import org.h2.constant.ErrorCode;
import org.h2.engine.Database;
import org.h2.engine.Mode;
import org.h2.engine.Session;
import org.h2.message.Message;
import org.h2.schema.Sequence;
import org.h2.security.BlockCipher;
import org.h2.security.CipherFactory;
import org.h2.security.SHA256;
import org.h2.table.Column;
import org.h2.table.ColumnResolver;
import org.h2.table.LinkSchema;
import org.h2.table.TableFilter;
import org.h2.tools.CompressTool;
import org.h2.tools.Csv;
import org.h2.tools.SimpleResultSet;
import org.h2.util.ObjectUtils;
import org.h2.util.MathUtils;
import org.h2.util.MemoryUtils;
import org.h2.util.ObjectArray;
import org.h2.util.RandomUtils;
import org.h2.util.StringUtils;
import org.h2.value.DataType;
import org.h2.value.Value;
import org.h2.value.ValueArray;
import org.h2.value.ValueBoolean;
import org.h2.value.ValueBytes;
import org.h2.value.ValueDate;
import org.h2.value.ValueDouble;
import org.h2.value.ValueInt;
import org.h2.value.ValueLong;
import org.h2.value.ValueNull;
import org.h2.value.ValueResultSet;
import org.h2.value.ValueString;
import org.h2.value.ValueTime;
import org.h2.value.ValueTimestamp;
import org.h2.value.ValueUuid;

/**
 * @author Thomas
 */

public class Function extends Expression implements FunctionCall {
    // TODO functions: add function hashcode(value)

    public static final int ABS = 0, ACOS = 1, ASIN = 2, ATAN = 3, ATAN2 = 4, BITAND = 5, BITOR = 6, BITXOR = 7,
            CEILING = 8, COS = 9, COT = 10, DEGREES = 11, EXP = 12, FLOOR = 13, LOG = 14, LOG10 = 15, MOD = 16,
            PI = 17, POWER = 18, RADIANS = 19, RAND = 20, ROUND = 21, ROUNDMAGIC = 22, SIGN = 23, SIN = 24, SQRT = 25,
            TAN = 26, TRUNCATE = 27, SECURE_RAND = 28, HASH = 29, ENCRYPT = 30, DECRYPT = 31, COMPRESS = 32,
            EXPAND = 33, ZERO = 34, RANDOM_UUID = 35;

    public static final int ASCII = 50, BIT_LENGTH = 51, CHAR = 52, CHAR_LENGTH = 53, CONCAT = 54, DIFFERENCE = 55,
            HEXTORAW = 56, INSERT = 57, INSTR = 58, LCASE = 59, LEFT = 60, LENGTH = 61, LOCATE = 62, LTRIM = 63,
            OCTET_LENGTH = 64, RAWTOHEX = 65, REPEAT = 66, REPLACE = 67, RIGHT = 68, RTRIM = 69, SOUNDEX = 70,
            SPACE = 71, SUBSTR = 72, SUBSTRING = 73, UCASE = 74, LOWER = 75, UPPER = 76, POSITION = 77, TRIM = 78,
            STRINGENCODE = 79, STRINGDECODE = 80, STRINGTOUTF8 = 81, UTF8TOSTRING = 82, XMLATTR = 83, XMLNODE = 84,
            XMLCOMMENT = 85, XMLCDATA = 86, XMLSTARTDOC = 87, XMLTEXT = 88, REGEXP_REPLACE = 89;

    public static final int CURDATE = 100, CURTIME = 101, DATEADD = 102, DATEDIFF = 103, DAYNAME = 104,
            DAYOFMONTH = 105, DAYOFWEEK = 106, DAYOFYEAR = 107, HOUR = 108, MINUTE = 109, MONTH = 110, MONTHNAME = 111,
            NOW = 112, QUARTER = 113, SECOND = 114, WEEK = 115, YEAR = 116, CURRENT_DATE = 117, CURRENT_TIME = 118,
            CURRENT_TIMESTAMP = 119, EXTRACT = 120, FORMATDATETIME = 121, PARSEDATETIME = 122;

    public static final int DATABASE = 150, USER = 151, CURRENT_USER = 152, IDENTITY = 153, AUTOCOMMIT = 154,
            READONLY = 155, DATABASE_PATH = 156, LOCK_TIMEOUT = 157;

    public static final int IFNULL = 200, CASEWHEN = 201, CONVERT = 202, CAST = 203, COALESCE = 204, NULLIF = 205,
            CASE = 206, NEXTVAL = 207, CURRVAL = 208, ARRAY_GET = 209, CSVREAD = 210, CSVWRITE = 211,
            MEMORY_FREE = 212, MEMORY_USED = 213, LOCK_MODE = 214, SCHEMA = 215, SESSION_ID = 216, ARRAY_LENGTH = 217,
            LINK_SCHEMA = 218, TABLE = 219, LEAST = 220, GREATEST = 221;

    private static final int VAR_ARGS = -1;

    private static HashMap functions;

    private FunctionInfo info;
    private Expression[] args;
    private ObjectArray varArgs;
    private int dataType, scale;
    private long precision;
    private int displaySize;
    private Column[] columnList;
    private Database database;

    private static HashMap datePart;
    private static final SimpleDateFormat FORMAT_DAYNAME = new SimpleDateFormat("EEEE", Locale.ENGLISH);
    private static final SimpleDateFormat FORMAT_MONTHNAME = new SimpleDateFormat("MMMM", Locale.ENGLISH);
    private static final char[] SOUNDEX_INDEX = new char[128];

    static {
        datePart = new HashMap();
        datePart.put("YY", ObjectUtils.getInteger(Calendar.YEAR));
        datePart.put("YEAR", ObjectUtils.getInteger(Calendar.YEAR));
        datePart.put("MM", ObjectUtils.getInteger(Calendar.MONTH));
        datePart.put("MONTH", ObjectUtils.getInteger(Calendar.MONTH));
        datePart.put("DD", ObjectUtils.getInteger(Calendar.DATE));
        datePart.put("DAY", ObjectUtils.getInteger(Calendar.DATE));
        datePart.put("HH", ObjectUtils.getInteger(Calendar.HOUR));
        datePart.put("HOUR", ObjectUtils.getInteger(Calendar.HOUR));
        datePart.put("MI", ObjectUtils.getInteger(Calendar.MINUTE));
        datePart.put("MINUTE", ObjectUtils.getInteger(Calendar.MINUTE));
        datePart.put("SS", ObjectUtils.getInteger(Calendar.SECOND));
        datePart.put("SECOND", ObjectUtils.getInteger(Calendar.SECOND));
        datePart.put("MS", ObjectUtils.getInteger(Calendar.MILLISECOND));
        datePart.put("MILLISECOND", ObjectUtils.getInteger(Calendar.MILLISECOND));
    }

    static {
        String index = "7AEIOUY8HW1BFPV2CGJKQSXZ3DT4L5MN6R";
        char number = 0;
        for (int i = 0; i < index.length(); i++) {
            char c = index.charAt(i);
            if (c < '9') {
                number = c;
            } else {
                SOUNDEX_INDEX[c] = number;
                SOUNDEX_INDEX[Character.toLowerCase(c)] = number;
            }
        }
    }

    static {
        functions = new HashMap();
        addFunction("ABS", ABS, 1, Value.NULL);
        addFunction("ACOS", ACOS, 1, Value.DOUBLE);
        addFunction("ASIN", ASIN, 1, Value.DOUBLE);
        addFunction("ATAN", ATAN, 1, Value.DOUBLE);
        addFunction("ATAN2", ATAN2, 2, Value.DOUBLE);
        addFunction("BITAND", BITAND, 2, Value.INT);
        addFunction("BITOR", BITOR, 2, Value.INT);
        addFunction("BITXOR", BITXOR, 2, Value.INT);
        addFunction("CEILING", CEILING, 1, Value.DOUBLE);
        addFunction("COS", COS, 1, Value.DOUBLE);
        addFunction("COT", COT, 1, Value.DOUBLE);
        addFunction("DEGREES", DEGREES, 1, Value.DOUBLE);
        addFunction("EXP", EXP, 1, Value.DOUBLE);
        addFunction("FLOOR", FLOOR, 1, Value.DOUBLE);
        addFunction("LOG", LOG, 1, Value.DOUBLE);
        addFunction("LOG10", LOG10, 1, Value.DOUBLE);
        addFunction("MOD", MOD, 2, Value.INT);
        addFunction("PI", PI, 0, Value.DOUBLE);
        addFunction("POWER", POWER, 2, Value.DOUBLE);
        addFunction("RADIANS", RADIANS, 1, Value.DOUBLE);
        addFunctionNotConst("RAND", RAND, VAR_ARGS, Value.DOUBLE); // no args:
                                                                    // regular
                                                                    // rand;
                                                                    // with one
                                                                    // arg: seed
                                                                    // random
                                                                    // generator
        addFunction("ROUND", ROUND, 2, Value.DOUBLE);
        addFunction("ROUNDMAGIC", ROUNDMAGIC, 1, Value.DOUBLE);
        addFunction("SIGN", SIGN, 1, Value.INT);
        addFunction("SIN", SIN, 1, Value.DOUBLE);
        addFunction("SQRT", SQRT, 1, Value.DOUBLE);
        addFunction("TAN", TAN, 1, Value.DOUBLE);
        addFunction("TRUNCATE", TRUNCATE, 2, Value.DOUBLE);
        addFunction("HASH", HASH, 3, Value.BYTES);
        addFunction("ENCRYPT", ENCRYPT, 3, Value.BYTES);
        addFunction("DECRYPT", DECRYPT, 3, Value.BYTES);
        addFunctionNotConst("SECURE_RAND", SECURE_RAND, 1, Value.BYTES);
        addFunction("COMPRESS", COMPRESS, VAR_ARGS, Value.BYTES);
        addFunction("EXPAND", EXPAND, 1, Value.BYTES);
        addFunction("ZERO", ZERO, 0, Value.INT);
        addFunctionNotConst("RANDOM_UUID", RANDOM_UUID, 0, Value.UUID);
        addFunctionNotConst("SYS_GUID", RANDOM_UUID, 0, Value.UUID);
        // string
        addFunction("ASCII", ASCII, 1, Value.INT);
        addFunction("BIT_LENGTH", BIT_LENGTH, 1, Value.INT);
        addFunction("CHAR", CHAR, 1, Value.STRING);
        addFunction("CHR", CHAR, 1, Value.STRING);
        addFunction("CHAR_LENGTH", CHAR_LENGTH, 1, Value.INT);
        addFunction("CHARACTER_LENGTH", CHAR_LENGTH, 1, Value.INT); // same as
                                                                    // CHAR_LENGTH
        addFunctionWithNull("CONCAT", CONCAT, VAR_ARGS, Value.STRING);
        addFunction("DIFFERENCE", DIFFERENCE, 2, Value.INT);
        addFunction("HEXTORAW", HEXTORAW, 1, Value.STRING);
        addFunctionWithNull("INSERT", INSERT, 4, Value.STRING);
        addFunction("LCASE", LCASE, 1, Value.STRING);
        addFunction("LEFT", LEFT, 2, Value.STRING);
        addFunction("LENGTH", LENGTH, 1, Value.INT);
        addFunction("LOCATE", LOCATE, VAR_ARGS, Value.INT); // 2 or 3 arguments
        addFunction("POSITION", LOCATE, 2, Value.INT); // same as LOCATE with 2
                                                        // arguments
        addFunction("INSTR", INSTR, VAR_ARGS, Value.INT);
        addFunction("LTRIM", LTRIM, VAR_ARGS, Value.STRING);
        addFunction("OCTET_LENGTH", OCTET_LENGTH, 1, Value.INT);
        addFunction("RAWTOHEX", RAWTOHEX, 1, Value.STRING);
        addFunction("REPEAT", REPEAT, 2, Value.STRING);
        addFunctionWithNull("REPLACE", REPLACE, VAR_ARGS, Value.STRING);
        addFunction("RIGHT", RIGHT, 2, Value.STRING);
        addFunction("RTRIM", RTRIM, VAR_ARGS, Value.STRING);
        addFunction("SOUNDEX", SOUNDEX, 1, Value.STRING);
        addFunction("SPACE", SPACE, 1, Value.STRING);
        addFunction("SUBSTR", SUBSTR, VAR_ARGS, Value.STRING);
        addFunction("SUBSTRING", SUBSTRING, VAR_ARGS, Value.STRING);
        addFunction("UCASE", UCASE, 1, Value.STRING);
        addFunction("LOWER", LOWER, 1, Value.STRING);
        addFunction("UPPER", UPPER, 1, Value.STRING);
        addFunction("POSITION", POSITION, 2, Value.INT);
        addFunction("TRIM", TRIM, VAR_ARGS, Value.STRING);
        addFunction("STRINGENCODE", STRINGENCODE, 1, Value.STRING);
        addFunction("STRINGDECODE", STRINGDECODE, 1, Value.STRING);
        addFunction("STRINGTOUTF8", STRINGTOUTF8, 1, Value.BYTES);
        addFunction("UTF8TOSTRING", UTF8TOSTRING, 1, Value.STRING);
        addFunction("XMLATTR", XMLATTR, 2, Value.STRING);
        addFunctionWithNull("XMLNODE", XMLNODE, VAR_ARGS, Value.STRING);
        addFunction("XMLCOMMENT", XMLCOMMENT, 1, Value.STRING);
        addFunction("XMLCDATA", XMLCDATA, 1, Value.STRING);
        addFunction("XMLSTARTDOC", XMLSTARTDOC, 0, Value.STRING);
        addFunction("XMLTEXT", XMLTEXT, 1, Value.STRING);
        addFunction("REGEXP_REPLACE", REGEXP_REPLACE, 3, Value.STRING);

        // date
        addFunctionNotConst("CURRENT_DATE", CURRENT_DATE, 0, Value.DATE);
        addFunctionNotConst("CURDATE", CURDATE, 0, Value.DATE);
        addFunctionNotConst("CURRENT_TIME", CURRENT_TIME, 0, Value.TIME);
        addFunctionNotConst("CURTIME", CURTIME, 0, Value.TIME);
        addFunctionNotConst("CURRENT_TIMESTAMP", CURRENT_TIMESTAMP, VAR_ARGS, Value.TIMESTAMP);
        addFunctionNotConst("NOW", NOW, VAR_ARGS, Value.TIMESTAMP);
        addFunction("DATEADD", DATEADD, 3, Value.TIMESTAMP);
        addFunction("DATEDIFF", DATEDIFF, 3, Value.LONG);
        addFunction("DAYNAME", DAYNAME, 1, Value.STRING);
        addFunction("DAY", DAYOFMONTH, 1, Value.INT);
        addFunction("DAYOFMONTH", DAYOFMONTH, 1, Value.INT);
        addFunction("DAYOFWEEK", DAYOFWEEK, 1, Value.INT);
        addFunction("DAYOFYEAR", DAYOFYEAR, 1, Value.INT);
        addFunction("HOUR", HOUR, 1, Value.INT);
        addFunction("MINUTE", MINUTE, 1, Value.INT);
        addFunction("MONTH", MONTH, 1, Value.INT);
        addFunction("MONTHNAME", MONTHNAME, 1, Value.STRING);
        addFunction("QUARTER", QUARTER, 1, Value.INT);
        addFunction("SECOND", SECOND, 1, Value.INT);
        addFunction("WEEK", WEEK, 1, Value.INT);
        addFunction("YEAR", YEAR, 1, Value.INT);
        addFunction("EXTRACT", EXTRACT, 2, Value.INT);
        addFunctionWithNull("FORMATDATETIME", FORMATDATETIME, VAR_ARGS, Value.STRING);
        addFunctionWithNull("PARSEDATETIME", PARSEDATETIME, VAR_ARGS, Value.TIMESTAMP);
        // system
        addFunctionNotConst("DATABASE", DATABASE, 0, Value.STRING);
        addFunctionNotConst("USER", USER, 0, Value.STRING);
        addFunctionNotConst("CURRENT_USER", CURRENT_USER, 0, Value.STRING);
        addFunctionNotConst("IDENTITY", IDENTITY, 0, Value.LONG);
        addFunctionNotConst("IDENTITY_VAL_LOCAL", IDENTITY, 0, Value.LONG);
        addFunctionNotConst("LAST_INSERT_ID", IDENTITY, 0, Value.LONG);
        addFunctionNotConst("AUTOCOMMIT", AUTOCOMMIT, 0, Value.BOOLEAN);
        addFunctionNotConst("READONLY", READONLY, 0, Value.BOOLEAN);
        addFunction("DATABASE_PATH", DATABASE_PATH, 0, Value.STRING);
        addFunction("LOCK_TIMEOUT", LOCK_TIMEOUT, 0, Value.INT);
        addFunctionWithNull("IFNULL", IFNULL, 2, Value.NULL);
        addFunctionWithNull("CASEWHEN", CASEWHEN, 3, Value.NULL);
        addFunctionWithNull("CONVERT", CONVERT, 1, Value.NULL);
        addFunctionWithNull("CAST", CAST, 1, Value.NULL);
        addFunctionWithNull("COALESCE", COALESCE, VAR_ARGS, Value.NULL);
        addFunctionWithNull("NVL", COALESCE, VAR_ARGS, Value.NULL);
        addFunctionWithNull("NULLIF", NULLIF, 2, Value.NULL);
        addFunctionWithNull("CASE", CASE, VAR_ARGS, Value.NULL);
        addFunctionNotConst("NEXTVAL", NEXTVAL, VAR_ARGS, Value.LONG);
        addFunctionNotConst("CURRVAL", CURRVAL, VAR_ARGS, Value.LONG);
        addFunction("ARRAY_GET", ARRAY_GET, 2, Value.NULL);
        addFunction("CSVREAD", CSVREAD, VAR_ARGS, Value.RESULT_SET, false, false);
        addFunction("CSVWRITE", CSVWRITE, VAR_ARGS, Value.INT, false, false);
        addFunctionNotConst("MEMORY_FREE", MEMORY_FREE, 0, Value.INT);
        addFunctionNotConst("MEMORY_USED", MEMORY_USED, 0, Value.INT);
        addFunctionNotConst("LOCK_MODE", LOCK_MODE, 0, Value.INT);
        addFunctionNotConst("SCHEMA", SCHEMA, 0, Value.STRING);
        addFunctionNotConst("SESSION_ID", SESSION_ID, 0, Value.INT);
        addFunction("ARRAY_LENGTH", ARRAY_LENGTH, 1, Value.INT);
        addFunction("LINK_SCHEMA", LINK_SCHEMA, 6, Value.RESULT_SET);
        addFunctionWithNull("TABLE", TABLE, VAR_ARGS, Value.RESULT_SET);
        addFunctionWithNull("LEAST", LEAST, VAR_ARGS, Value.NULL);
        addFunctionWithNull("GREATEST", GREATEST, VAR_ARGS, Value.NULL);
    }

    private static void addFunction(String name, int type, int parameterCount, int dataType,
            boolean nullIfParameterIsNull, boolean isDeterm) {
        FunctionInfo info = new FunctionInfo();
        info.name = name;
        info.type = type;
        info.parameterCount = parameterCount;
        info.dataType = dataType;
        info.nullIfParameterIsNull = nullIfParameterIsNull;
        info.isDeterministic = isDeterm;
        functions.put(name, info);
    }

    private static void addFunctionNotConst(String name, int type, int parameterCount, int dataType) {
        addFunction(name, type, parameterCount, dataType, true, false);
    }

    private static void addFunction(String name, int type, int parameterCount, int dataType) {
        addFunction(name, type, parameterCount, dataType, true, true);
    }

    private static void addFunctionWithNull(String name, int type, int parameterCount, int dataType) {
        addFunction(name, type, parameterCount, dataType, false, true);
    }

    public static Function getFunction(Database database, String name) throws SQLException {
        FunctionInfo info = (FunctionInfo) functions.get(name);
        if (info == null) {
            return null;
        }
        return new Function(database, info);
    }

    private Function(Database database, FunctionInfo info) {
        this.database = database;
        this.info = info;
        if (info.parameterCount == VAR_ARGS) {
            varArgs = new ObjectArray();
        } else {
            args = new Expression[info.parameterCount];
        }
    }

    public void setParameter(int index, Expression param) throws SQLException {
        if (varArgs != null) {
            varArgs.add(param);
        } else {
            if (index >= args.length) {
                throw Message.getSQLException(ErrorCode.INVALID_PARAMETER_COUNT_2, new String[] { info.name,
                        "" + args.length });
            }
            args[index] = param;
        }
    }

    private strictfp double log10(double value) {
        return roundmagic(StrictMath.log(value) / StrictMath.log(10));
    }

    public Value getValue(Session session) throws SQLException {
        return getValueWithArgs(session, args);
    }

    private Value getNullOrValue(Session session, Expression[] x, int i) throws SQLException {
        if (i < x.length) {
            Expression e = x[i];
            if (e != null) {
                return e.getValue(session);
            }
        }
        return null;
    }

    public Value getValueWithArgs(Session session, Expression[] args) throws SQLException {
        if (info.nullIfParameterIsNull) {
            for (int i = 0; i < args.length; i++) {
                if (getNullOrValue(session, args, i) == ValueNull.INSTANCE) {
                    return ValueNull.INSTANCE;
                }
            }
        }
        Value v0 = getNullOrValue(session, args, 0);
        switch (info.type) {
        case IFNULL:
            return v0 == ValueNull.INSTANCE ? args[1].getValue(session) : v0;
        case CASEWHEN: {
            Expression result;
            if (v0 == ValueNull.INSTANCE || !v0.getBoolean().booleanValue()) {
                result = args[2];
            } else {
                result = args[1];
            }
            Value v = result.getValue(session);
            v = v.convertTo(dataType);
            return v;
        }
        case COALESCE: {
            for (int i = 0; i < args.length; i++) {
                Value v = i == 0 ? v0 : args[i].getValue(session);
                if (!(v == ValueNull.INSTANCE)) {
                    return v.convertTo(dataType);
                }
            }
            return v0;
        }
        case GREATEST:
        case LEAST: {
            Value result = ValueNull.INSTANCE;
            for (int i = 0; i < args.length; i++) {
                Value v = i == 0 ? v0 : args[i].getValue(session);
                if (!(v == ValueNull.INSTANCE)) {
                    v = v.convertTo(dataType);
                    if (result == ValueNull.INSTANCE) {
                        result = v;
                    } else {
                        int comp = database.compareTypeSave(result, v);
                        if (info.type == GREATEST && comp < 0) {
                            result = v;
                        } else if (info.type == LEAST && comp > 0) {
                            result = v;
                        }
                    }
                }
            }
            return result;
        }
        case CASE: {
            // TODO function CASE: document & implement functionality
            int i = 0;
            for (; i < args.length; i++) {
                Value when = args[i++].getValue(session);
                if (Boolean.TRUE.equals(when)) {
                    return args[i].getValue(session);
                }
            }
            return i < args.length ? args[i].getValue(session) : ValueNull.INSTANCE;
        }
        case ARRAY_GET: {
            if (v0.getType() == Value.ARRAY) {
                Value v1 = args[1].getValue(session);
                int element = v1.getInt();
                Value[] list = ((ValueArray) v0).getList();
                if (element < 1 || element > list.length) {
                    return ValueNull.INSTANCE;
                }
                return list[element - 1];
            }
            return ValueNull.INSTANCE;
        }
        case ARRAY_LENGTH: {
            if (v0.getType() == Value.ARRAY) {
                Value[] list = ((ValueArray) v0).getList();
                return ValueInt.get(list.length);
            }
            return ValueNull.INSTANCE;
        }
        default:
            // ok
        }
        Value v1 = getNullOrValue(session, args, 1);
        Value v2 = getNullOrValue(session, args, 2);
        Value v3 = getNullOrValue(session, args, 3);
        Value v4 = getNullOrValue(session, args, 4);
        Value v5 = getNullOrValue(session, args, 5);
        switch (info.type) {
        case ABS:
            return v0.getSignum() > 0 ? v0 : v0.negate();
        case ACOS:
            return ValueDouble.get(Math.acos(v0.getDouble()));
        case ASIN:
            return ValueDouble.get(Math.asin(v0.getDouble()));
        case ATAN:
            return ValueDouble.get(Math.atan(v0.getDouble()));
        case ATAN2:
            return ValueDouble.get(Math.atan2(v0.getDouble(), v1.getDouble()));
        case BITAND:
            return ValueInt.get(v0.getInt() & v1.getInt());
        case BITOR:
            return ValueInt.get(v0.getInt() | v1.getInt());
        case BITXOR:
            return ValueInt.get(v0.getInt() ^ v1.getInt());
        case CEILING:
            return ValueDouble.get(Math.ceil(v0.getDouble()));
        case COS:
            return ValueDouble.get(Math.cos(v0.getDouble()));
        case COT: {
            double d = Math.tan(v0.getDouble());
            if (d == 0.0) {
                throw Message.getSQLException(ErrorCode.DIVISION_BY_ZERO_1, getSQL());
            }
            return ValueDouble.get(1. / d);
        }
        case DEGREES:
            return ValueDouble.get(Math.toDegrees(v0.getDouble()));
        case EXP:
            return ValueDouble.get(Math.exp(v0.getDouble()));
        case FLOOR:
            return ValueDouble.get(Math.floor(v0.getDouble()));
        case LOG:
            return ValueDouble.get(Math.log(v0.getDouble()));
        case LOG10:
            return ValueDouble.get(log10(v0.getDouble()));
        case MOD: {
            int x = v1.getInt();
            if (x == 0.0) {
                throw Message.getSQLException(ErrorCode.DIVISION_BY_ZERO_1, getSQL());
            }
            return ValueInt.get(v0.getInt() % x);
        }
        case PI:
            return ValueDouble.get(Math.PI);
        case POWER:
            return ValueDouble.get(Math.pow(v0.getDouble(), v1.getDouble()));
        case RADIANS:
            return ValueDouble.get(Math.toRadians(v0.getDouble()));
        case RAND: {
            if (v0 != null) {
                session.getRandom().setSeed(v0.getInt());
            }
            // TODO function rand: if seed value is set, return a random value?
            // probably yes
            return ValueDouble.get(session.getRandom().nextDouble());
        }
        case ROUND: {
            double f = Math.pow(10., v1.getDouble());
            return ValueDouble.get(Math.round(v0.getDouble() * f) / f);
        }
        case ROUNDMAGIC:
            return ValueDouble.get(roundmagic(v0.getDouble()));
        case SIGN:
            return ValueInt.get(v0.getSignum());
        case SIN:
            return ValueDouble.get(Math.sin(v0.getDouble()));
        case SQRT:
            return ValueDouble.get(Math.sqrt(v0.getDouble()));
        case TAN:
            return ValueDouble.get(Math.tan(v0.getDouble()));
        case TRUNCATE: {
            double d = v0.getDouble();
            int p = v1.getInt();
            double f = Math.pow(10., p);
            double g = d * f;
            return ValueDouble.get(((d < 0) ? Math.ceil(g) : Math.floor(g)) / f);
        }
        case SECURE_RAND:
            return ValueBytes.getNoCopy(RandomUtils.getSecureBytes(v0.getInt()));
        case HASH:
            return ValueBytes.getNoCopy(getHash(v0.getString(), v1.getBytesNoCopy(), v2.getInt()));
        case ENCRYPT:
            return ValueBytes.getNoCopy(encrypt(v0.getString(), v1.getBytesNoCopy(), v2.getBytesNoCopy()));
        case DECRYPT:
            return ValueBytes.getNoCopy(decrypt(v0.getString(), v1.getBytesNoCopy(), v2.getBytesNoCopy()));
        case COMPRESS: {
            String algorithm = null;
            if (v1 != null) {
                algorithm = v1.getString();
            }
            return ValueBytes.getNoCopy(CompressTool.getInstance().compress(v0.getBytesNoCopy(), algorithm));
        }
        case EXPAND:
            return ValueBytes.getNoCopy(CompressTool.getInstance().expand(v0.getBytesNoCopy()));
        case ZERO:
            return ValueInt.get(0);
        case RANDOM_UUID:
            return ValueUuid.getNewRandom();
            // string
        case ASCII: {
            String s = v0.getString();
            if (s.length() == 0) {
                return ValueNull.INSTANCE;
            }
            return ValueInt.get(s.charAt(0));
        }
        case BIT_LENGTH:
            return ValueInt.get(16 * length(v0));
        case CHAR:
            return ValueString.get(String.valueOf((char) v0.getInt()));
        case CHAR_LENGTH:
        case LENGTH:
            return ValueInt.get(length(v0));
        case OCTET_LENGTH:
            return ValueInt.get(2 * length(v0));
        case CONCAT: {
            Value concat = ValueNull.INSTANCE;
            for (int i = 0; i < args.length; i++) {
                Value v = args[i].getValue(session);
                if (v == ValueNull.INSTANCE) {
                    continue;
                }
                if (concat == ValueNull.INSTANCE) {
                    concat = v;
                } else {
                    concat = ValueString.get(concat.getString().concat(v.getString()));
                }
            }
            return concat;
        }
        case DIFFERENCE:
            return ValueInt.get(getDifference(v0.getString(), v1.getString()));
        case HEXTORAW:
            return ValueString.get(hexToRaw(v0.getString()));
        case INSERT: {
            if (v1 == ValueNull.INSTANCE || v2 == ValueNull.INSTANCE) {
                return v1;
            }
            return ValueString.get(insert(v0.getString(), v1.getInt(), v2.getInt(), v3.getString()));
        }
        case LOWER:
        case LCASE:
            // TODO this is locale specific, need to document or provide a way
            // to set the locale
            return ValueString.get(v0.getString().toLowerCase());
        case LEFT:
            return ValueString.get(left(v0.getString(), v1.getInt()));
        case LOCATE: {
            int start = v2 == null ? 0 : v2.getInt();
            return ValueInt.get(locate(v0.getString(), v1.getString(), start));
        }
        case INSTR: {
            int start = v2 == null ? 0 : v2.getInt();
            return ValueInt.get(locate(v1.getString(), v0.getString(), start));
        }
        case RAWTOHEX:
            return ValueString.get(rawToHex(v0.getString()));
        case REPEAT: {
            // TODO DOS attacks: limit len?
            int count = Math.max(0, v1.getInt());
            return ValueString.get(repeat(v0.getString(), count));
        }
        case REPLACE: {
            String s0 = v0 == ValueNull.INSTANCE ? "" : v0.getString();
            String s1 = v1 == ValueNull.INSTANCE ? "" : v1.getString();
            String s2 = (v2 == null || v2 == ValueNull.INSTANCE) ? "" : v2.getString();
            return ValueString.get(replace(s0, s1, s2));
        }
        case RIGHT:
            return ValueString.get(right(v0.getString(), v1.getInt()));
        case LTRIM:
            return ValueString.get(trim(v0.getString(), true, false, v1 == null ? " " : v1.getString()));
        case TRIM:
            return ValueString.get(trim(v0.getString(), true, true, v1 == null ? " " : v1.getString()));
        case RTRIM:
            return ValueString.get(trim(v0.getString(), false, true, v1 == null ? " " : v1.getString()));
        case SOUNDEX:
            return ValueString.get(getSoundex(v0.getString()));
        case SPACE: {
            // TODO DOS attacks: limit len?
            int len = Math.max(0, v0.getInt());
            char[] chars = new char[len];
            for (int i = len - 1; i >= 0; i--) {
                chars[i] = ' ';
            }
            return ValueString.get(new String(chars));
        }
        case SUBSTR:
        case SUBSTRING: {
            String s = v0.getString();
            int length = v2 == null ? s.length() : v2.getInt();
            return ValueString.get(substring(s, v1.getInt(), length));
        }
        case POSITION:
            return ValueInt.get(locate(v0.getString(), v1.getString(), 0));
        case UPPER:
        case UCASE:
            // TODO this is locale specific, need to document or provide a way
            // to set the locale
            return ValueString.get(v0.getString().toUpperCase());
        case STRINGENCODE:
            return ValueString.get(StringUtils.javaEncode(v0.getString()));
        case STRINGDECODE:
            return ValueString.get(StringUtils.javaDecode(v0.getString()));
        case STRINGTOUTF8:
            return ValueBytes.getNoCopy(StringUtils.utf8Encode(v0.getString()));
        case UTF8TOSTRING:
            return ValueString.get(StringUtils.utf8Decode(v0.getBytesNoCopy()));
        case XMLATTR:
            return ValueString.get(StringUtils.xmlAttr(v0.getString(), v1.getString()));
        case XMLNODE: {
            String attr = v1 == null ? null : v1 == ValueNull.INSTANCE ? null : v1.getString();
            String content = v2 == null ? null : v2 == ValueNull.INSTANCE ? null : v2.getString();
            return ValueString.get(StringUtils.xmlNode(v0.getString(), attr, content));
        }
        case XMLCOMMENT:
            return ValueString.get(StringUtils.xmlComment(v0.getString()));
        case XMLCDATA:
            return ValueString.get(StringUtils.xmlCData(v0.getString()));
        case XMLSTARTDOC:
            return ValueString.get(StringUtils.xmlStartDoc());
        case XMLTEXT:
            return ValueString.get(StringUtils.xmlText(v0.getString()));
        case REGEXP_REPLACE:
            return ValueString.get(v0.getString().replaceAll(v1.getString(), v2.getString()));
            // date
        case DATEADD:
            return ValueTimestamp.getNoCopy(dateadd(v0.getString(), v1.getInt(), v2.getTimestampNoCopy()));
        case DATEDIFF:
            return ValueLong.get(datediff(v0.getString(), v1.getTimestampNoCopy(), v2.getTimestampNoCopy()));
        case DAYNAME: {
            Value result;
            synchronized (FORMAT_DAYNAME) {
                result = ValueString.get(FORMAT_DAYNAME.format(v0.getDateNoCopy()));
            }
            return result;
        }
        case DAYOFMONTH:
            return ValueInt.get(getDatePart(v0.getTimestampNoCopy(), Calendar.DAY_OF_MONTH));
        case DAYOFWEEK:
            return ValueInt.get(getDatePart(v0.getTimestampNoCopy(), Calendar.DAY_OF_WEEK));
        case DAYOFYEAR:
            return ValueInt.get(getDatePart(v0.getTimestampNoCopy(), Calendar.DAY_OF_YEAR));
        case HOUR:
            return ValueInt.get(getDatePart(v0.getTimestampNoCopy(), Calendar.HOUR_OF_DAY));
        case MINUTE:
            return ValueInt.get(getDatePart(v0.getTimestampNoCopy(), Calendar.MINUTE));
        case MONTH:
            return ValueInt.get(getDatePart(v0.getTimestampNoCopy(), Calendar.MONTH));
        case MONTHNAME: {
            Value v;
            synchronized (FORMAT_MONTHNAME) {
                v = ValueString.get(FORMAT_MONTHNAME.format(v0.getDateNoCopy()));
            }
            return v;
        }
        case QUARTER:
            return ValueInt.get((getDatePart(v0.getTimestamp(), Calendar.MONTH) - 1) / 3 + 1);
        case SECOND:
            return ValueInt.get(getDatePart(v0.getTimestamp(), Calendar.SECOND));
        case WEEK:
            return ValueInt.get(getDatePart(v0.getTimestamp(), Calendar.WEEK_OF_YEAR));
        case YEAR:
            return ValueInt.get(getDatePart(v0.getTimestamp(), Calendar.YEAR));
        case CURDATE:
        case CURRENT_DATE:
            // need to normalize
            return ValueDate.get(new Date(System.currentTimeMillis()));
        case CURTIME:
        case CURRENT_TIME:
            // need to normalize
            return ValueTime.get(new Time(System.currentTimeMillis()));
        case NOW:
        case CURRENT_TIMESTAMP: {
            ValueTimestamp vt = ValueTimestamp.getNoCopy(new Timestamp(System.currentTimeMillis()));
            if (v0 != null) {
                Mode mode = database.getMode();
                vt = (ValueTimestamp) vt.convertScale(mode.convertOnlyToSmallerScale, v0.getInt());
            }
            return vt;
        }
        case EXTRACT: {
            int field = getDatePart(v0.getString());
            return ValueInt.get(getDatePart(v1.getTimestamp(), field));
        }
        case FORMATDATETIME: {
            if (v0 == ValueNull.INSTANCE || v1 == ValueNull.INSTANCE) {
                return ValueNull.INSTANCE;
            }
            String locale = v2 == null ? null : v2 == ValueNull.INSTANCE ? null : v2.getString();
            String tz = v3 == null ? null : v3 == ValueNull.INSTANCE ? null : v3.getString();
            return ValueString.get(StringUtils.formatDateTime(v0.getTimestamp(), v1.getString(), locale, tz));
        }
        case PARSEDATETIME: {
            if (v0 == ValueNull.INSTANCE || v1 == ValueNull.INSTANCE) {
                return ValueNull.INSTANCE;
            }
            String locale = v2 == null ? null : v2 == ValueNull.INSTANCE ? null : v2.getString();
            String tz = v3 == null ? null : v3 == ValueNull.INSTANCE ? null : v3.getString();
            java.util.Date d = StringUtils.parseDateTime(v0.getString(), v1.getString(), locale, tz);
            return ValueTimestamp.getNoCopy(new Timestamp(d.getTime()));
        }
            // system
        case DATABASE:
            return ValueString.get(database.getShortName());
        case USER:
        case CURRENT_USER:
            return ValueString.get(session.getUser().getName());
        case IDENTITY:
            return session.getLastIdentity();
        case AUTOCOMMIT:
            return ValueBoolean.get(session.getAutoCommit());
        case READONLY:
            return ValueBoolean.get(database.getReadOnly());
        case DATABASE_PATH: {
            String path = database.getDatabasePath();
            return path == null ? (Value) ValueNull.INSTANCE : ValueString.get(path);
        }
        case LOCK_TIMEOUT:
            return ValueInt.get(session.getLockTimeout());
        case NULLIF:
            return database.areEqual(v0, v1) ? ValueNull.INSTANCE : v0;
        case CAST:
            // TODO function convert compatibility with MS SQL Server:
            // convert(varchar(255), name)
        case CONVERT: {
            v0 = v0.convertTo(dataType);
            Mode mode = database.getMode();
            v0 = v0.convertScale(mode.convertOnlyToSmallerScale, scale);
            v0 = v0.convertPrecision(getPrecision());
            return v0;
        }
        case NEXTVAL: {
            Sequence sequence = getSequence(session, v0, v1);
            SequenceValue value = new SequenceValue(sequence);
            return value.getValue(session);
        }
        case CURRVAL: {
            Sequence sequence = getSequence(session, v0, v1);
            return ValueLong.get(sequence.getCurrentValue());
        }
        case CSVREAD: {
            String fileName = v0.getString();
            String columnList = v1 == null ? null : v1.getString();
            String charset = v2 == null ? null : v2.getString();
            String fieldSeparatorRead = v3 == null ? null : v3.getString();
            String fieldDelimiter = v4 == null ? null : v4.getString();
            String escapeCharacter = v5 == null ? null : v5.getString();
            Csv csv = Csv.getInstance();
            char fieldSeparator = csv.getFieldSeparatorRead();
            if (fieldSeparatorRead != null && fieldSeparatorRead.length() > 0) {
                fieldSeparator = fieldSeparatorRead.charAt(0);
                csv.setFieldSeparatorRead(fieldSeparator);
            }
            if (fieldDelimiter != null && fieldDelimiter.length() > 0) {
                csv.setFieldDelimiter(fieldDelimiter.charAt(0));
            }
            if (escapeCharacter != null && escapeCharacter.length() > 0) {
                csv.setEscapeCharacter(escapeCharacter.charAt(0));
            }
            String[] columns = StringUtils.arraySplit(columnList, fieldSeparator, true);
            ValueResultSet vr = ValueResultSet.get(csv.read(fileName, columns, charset));
            return vr;
        }
        case LINK_SCHEMA: {
            session.getUser().checkAdmin();
            Connection conn = session.createConnection(false);
            ResultSet rs = LinkSchema.linkSchema(conn, v0.getString(), v1.getString(), v2.getString(), v3.getString(),
                    v4.getString(), v5.getString());
            return ValueResultSet.get(rs);
        }
        case TABLE:
            return getTable(session, args, false);
        case CSVWRITE: {
            session.getUser().checkAdmin();
            Connection conn = session.createConnection(false);
            String charset = v2 == null ? null : v2.getString();
            String fieldSeparatorWrite = v3 == null ? null : v3.getString();
            String fieldDelimiter = v4 == null ? null : v4.getString();
            String escapeCharacter = v5 == null ? null : v5.getString();            
            Csv csv = Csv.getInstance();
            if (fieldSeparatorWrite != null) {
                csv.setFieldSeparatorWrite(fieldSeparatorWrite);
            }
            if (fieldDelimiter != null && fieldDelimiter.length() > 0) {
                csv.setFieldDelimiter(fieldDelimiter.charAt(0));
            }
            if (escapeCharacter != null && escapeCharacter.length() > 0) {
                csv.setEscapeCharacter(escapeCharacter.charAt(0));
            }            
            int rows = csv.write(conn, v0.getString(), v1.getString(), charset);
            return ValueInt.get(rows);
        }
        case MEMORY_FREE:
            session.getUser().checkAdmin();
            return ValueInt.get(MemoryUtils.getMemoryFree());
        case MEMORY_USED:
            session.getUser().checkAdmin();
            return ValueInt.get(MemoryUtils.getMemoryUsed());
        case LOCK_MODE:
            return ValueInt.get(database.getLockMode());
        case SCHEMA:
            return ValueString.get(session.getCurrentSchemaName());
        case SESSION_ID:
            return ValueInt.get(session.getId());
        default:
            throw Message.getInternalError("type=" + info.type);
        }
    }

    private Sequence getSequence(Session session, Value v0, Value v1) throws SQLException {
        String schemaName, sequenceName;
        if (v1 == null) {
            schemaName = session.getCurrentSchemaName();
            sequenceName = StringUtils.toUpperEnglish(v0.getString());
        } else {
            schemaName = v0.getString();
            sequenceName = v1.getString();
        }
        return database.getSchema(schemaName).getSequence(sequenceName);
    }

    private int length(Value v) throws SQLException {
        switch (v.getType()) {
        case Value.BLOB:
        case Value.CLOB:
        case Value.BYTES:
        case Value.JAVA_OBJECT:
            return (int) v.getPrecision();
        default:
            return v.getString().length();
        }
    }

    private byte[] getPaddedArrayCopy(byte[] data, int blockSize) {
        int size = MathUtils.roundUp(data.length, blockSize);
        byte[] newData = new byte[size];
        System.arraycopy(data, 0, newData, 0, data.length);
        return newData;
    }

    private byte[] decrypt(String algorithm, byte[] key, byte[] data) throws SQLException {
        BlockCipher cipher = CipherFactory.getBlockCipher(algorithm);
        byte[] newKey = getPaddedArrayCopy(key, cipher.getKeyLength());
        cipher.setKey(newKey);
        byte[] newData = getPaddedArrayCopy(data, BlockCipher.ALIGN);
        cipher.decrypt(newData, 0, newData.length);
        return newData;
    }

    private byte[] encrypt(String algorithm, byte[] key, byte[] data) throws SQLException {
        BlockCipher cipher = CipherFactory.getBlockCipher(algorithm);
        byte[] newKey = getPaddedArrayCopy(key, cipher.getKeyLength());
        cipher.setKey(newKey);
        byte[] newData = getPaddedArrayCopy(data, BlockCipher.ALIGN);
        cipher.encrypt(newData, 0, newData.length);
        return newData;
    }

    private byte[] getHash(String algorithm, byte[] bytes, int iterations) throws SQLException {
        SHA256 hash = CipherFactory.getHash(algorithm);
        for (int i = 0; i < iterations; i++) {
            bytes = hash.getHash(bytes);
        }
        return bytes;
    }

    private static int getDatePart(Timestamp d, int field) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        int value = c.get(field);
        if (field == Calendar.MONTH) {
            value++;
        }
        return value;
    }

//     private static long datediffRound(String part, Date d1, Date d2) throws SQLException {
//        // diff (yy, 31.12.2004, 1.1.2005) = 0
//        Integer p = (Integer) datePart.get(StringUtils.toUpperEnglish(part));
//        if (p == null) {
//            throw Message.getSQLException(ErrorCode.INVALID_VALUE_2, new String[] { "part", part }, null);
//        }
//        int field = p.intValue();
//        long t1 = d1.getTime(), t2 = d2.getTime();
//        switch (field) {
//        case Calendar.MILLISECOND:
//            return t2 - t1;
//        case Calendar.SECOND:
//            return (t2 - t1) / 1000;
//        case Calendar.MINUTE:
//            return (t2 - t1) / 1000 / 60;
//        case Calendar.HOUR:
//            return (t2 - t1) / 1000 / 60 / 60;
//        case Calendar.DATE:
//            return (t2 - t1) / 1000 / 60 / 60 / 24;
//        }
//        Calendar g1 = Calendar.getInstance();
//        g1.setTimeInMillis(t1);
//        int year1 = g1.get(Calendar.YEAR);
//        Calendar g2 = Calendar.getInstance();
//        g2.setTimeInMillis(t2);
//        int year2 = g2.get(Calendar.YEAR);
//        int result = year2 - year1;
//        if (field == Calendar.MONTH) {
//            int month1 = g1.get(Calendar.MONTH);
//            int month2 = g2.get(Calendar.MONTH);
//            result = 12 * result + (month2 - month1);
//            g2.set(Calendar.MONTH, month1);
//        }
//        g2.set(Calendar.YEAR, year1);
//        if (result > 0 && g1.after(g2)) {
//            result--;
//        } else if (result < 0 && g1.before(g2)) {
//            result++;
//        }
//        return result;
//    }

    private static int getDatePart(String part) throws SQLException {
        Integer p = (Integer) datePart.get(StringUtils.toUpperEnglish(part));
        if (p == null) {
            throw Message.getSQLException(ErrorCode.INVALID_VALUE_2, new String[] { "date part", part });
        }
        return p.intValue();
    }

    private static Timestamp dateadd(String part, int count, Timestamp d) throws SQLException {
        int field = getDatePart(part);
        Calendar calendar = Calendar.getInstance();
        int nanos = d.getNanos() % 1000000;
        calendar.setTime(d);
        calendar.add(field, count);
        long t = calendar.getTime().getTime();
        Timestamp ts = new Timestamp(t);
        ts.setNanos(ts.getNanos() + nanos);
        return ts;
    }

    private static long datediff(String part, Timestamp d1, Timestamp d2) throws SQLException {
        // diff (yy, 31.12.2004, 1.1.2005) = 1
        int field = getDatePart(part);
        Calendar calendar = Calendar.getInstance();
        long t1 = d1.getTime(), t2 = d2.getTime();
        // need to convert to UTC, otherwise we get inconsistent results with
        // certain timezones (those that are 30 minutes off)
        TimeZone zone = calendar.getTimeZone();
        calendar.setTime(d1);
        t1 += zone.getOffset(calendar.get(Calendar.ERA), calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.DAY_OF_WEEK), calendar
                        .get(Calendar.MILLISECOND));
        calendar.setTime(d2);
        t2 += zone.getOffset(calendar.get(Calendar.ERA), calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.DAY_OF_WEEK), calendar
                        .get(Calendar.MILLISECOND));
        switch (field) {
        case Calendar.MILLISECOND:
            return t2 - t1;
        case Calendar.SECOND:
        case Calendar.MINUTE:
        case Calendar.HOUR: {
            // first 'normalize' the numbers so both are not negative
            long hour = 60 * 60 * 1000;
            long add = Math.min(t1 / hour * hour, t2 / hour * hour);
            t1 -= add;
            t2 -= add;
            switch (field) {
            case Calendar.SECOND:
                return t2 / 1000 - t1 / 1000;
            case Calendar.MINUTE:
                return t2 / (60 * 1000) - t1 / (60 * 1000);
            case Calendar.HOUR:
                return t2 / hour - t1 / hour;
            default:
                throw Message.getInternalError("field:" + field);
            }
        }
        case Calendar.DATE:
            return t2 / (24 * 60 * 60 * 1000) - t1 / (24 * 60 * 60 * 1000);
        default:
            break;
        }
        calendar.setTime(new Timestamp(t1));
        int year1 = calendar.get(Calendar.YEAR);
        int month1 = calendar.get(Calendar.MONTH);
        calendar.setTime(new Timestamp(t2));
        int year2 = calendar.get(Calendar.YEAR);
        int month2 = calendar.get(Calendar.MONTH);
        int result = year2 - year1;
        if (field == Calendar.MONTH) {
            result = 12 * result + (month2 - month1);
        }
        return result;
    }

    private static String substring(String s, int start, int length) {
        int len = s.length();
        start--;
        if (start < 0) {
            start = 0;
        }
        if (length < 0) {
            length = 0;
        }
        start = (start > len) ? len : start;
        if (start + length > len) {
            length = len - start;
        }
        return s.substring(start, start + length);
    }

    private static String trim(String s, boolean leading, boolean trailing, String sp) {
        char space = (sp == null || sp.length() < 1) ? ' ' : sp.charAt(0);
        // TODO function trim: HSQLDB says 'tabs are not removed', but they are.
        // check what other databases do
        if (leading) {
            int len = s.length(), i = 0;
            while (i < len && s.charAt(i) == space) {
                i++;
            }
            s = (i == 0) ? s : s.substring(i);
        }
        if (trailing) {
            int endIndex = s.length() - 1;
            int i = endIndex;
            while (i >= 0 && s.charAt(i) == space) {
                i--;
            }
            s = i == endIndex ? s : s.substring(0, i + 1);
        }
        return s;
    }

    private static String replace(String s, String replace, String with) {
        if (replace == null || replace.length() == 0) {
            // avoid out of memory
            return s;
        }
        StringBuffer buff = new StringBuffer(s.length());
        int start = 0;
        int len = replace.length();
        while (true) {
            int i = s.indexOf(replace, start);
            if (i == -1) {
                break;
            }
            buff.append(s.substring(start, i));
            buff.append(with);
            start = i + len;
        }
        buff.append(s.substring(start));
        return buff.toString();
    }

    private static String repeat(String s, int count) {
        StringBuffer buff = new StringBuffer(s.length() * count);
        while (count-- > 0) {
            buff.append(s);
        }
        return buff.toString();
    }

    private static String rawToHex(String s) {
        StringBuffer buff = new StringBuffer(4 * s.length());
        for (int i = 0; i < s.length(); i++) {
            String hex = Integer.toHexString(s.charAt(i) & 0xffff);
            for (int j = hex.length(); j < 4; j++) {
                buff.append('0');
            }
            buff.append(hex);
        }
        return buff.toString();
    }

    private static int locate(String search, String s, int start) {
        if (start < 0) {
            int i = s.length() + start;
            return s.lastIndexOf(search, i) + 1;
        } else {
            int i = (start == 0) ? 0 : start - 1;
            return s.indexOf(search, i) + 1;
        }
    }

    private static String right(String s, int count) {
        if (count < 0) {
            count = 0;
        } else if (count > s.length()) {
            count = s.length();
        }
        return s.substring(s.length() - count);
    }

    private static String left(String s, int count) {
        if (count < 0) {
            count = 0;
        } else if (count > s.length()) {
            count = s.length();
        }
        return s.substring(0, count);
    }

    private static String insert(String s1, int start, int length, String s2) {
        if (s1 == null) {
            return s2;
        }
        if (s2 == null) {
            return s1;
        }
        int len1 = s1.length();
        int len2 = s2.length();
        start--;
        if (start < 0 || length <= 0 || len2 == 0 || start > len1) {
            return s1;
        }
        if (start + length > len1) {
            length = len1 - start;
        }
        return s1.substring(0, start) + s2 + s1.substring(start + length);
    }

    private static String hexToRaw(String s) throws SQLException {
        // TODO function hextoraw compatibility with oracle
        int len = s.length();
        if (len % 4 != 0) {
            throw Message.getSQLException(ErrorCode.DATA_CONVERSION_ERROR_1, s);
        }
        StringBuffer buff = new StringBuffer(len / 4);
        for (int i = 0; i < len; i += 4) {
            try {
                char raw = (char) Integer.parseInt(s.substring(i, i + 4), 16);
                buff.append(raw);
            } catch (NumberFormatException e) {
                throw Message.getSQLException(ErrorCode.DATA_CONVERSION_ERROR_1, s);
            }
        }
        return buff.toString();
    }

    private static int getDifference(String s1, String s2) {
        // TODO function difference: compatibility with SQL Server and HSQLDB
        s1 = getSoundex(s1);
        s2 = getSoundex(s2);
        int e = 0;
        for (int i = 0; i < 4; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                e++;
            }
        }
        return e;
    }

    private double roundmagic(double d) {
        if ((d < 0.0000000000001) && (d > -0.0000000000001)) {
            return 0.0;
        }
        if ((d > 1000000000000.) || (d < -1000000000000.)) {
            return d;
        }
        StringBuffer s = new StringBuffer();
        s.append(d);
        if (s.toString().indexOf("E") >= 0) {
            return d;
        }
        int len = s.length();
        if (len < 16) {
            return d;
        }
        if (s.toString().indexOf(".") > len - 3) {
            return d;
        }
        s.delete(len - 2, len);
        len -= 2;
        char c1 = s.charAt(len - 2);
        char c2 = s.charAt(len - 3);
        char c3 = s.charAt(len - 4);
        if ((c1 == '0') && (c2 == '0') && (c3 == '0')) {
            s.setCharAt(len - 1, '0');
        } else if ((c1 == '9') && (c2 == '9') && (c3 == '9')) {
            s.setCharAt(len - 1, '9');
            s.append('9');
            s.append('9');
            s.append('9');
        }
        return Double.valueOf(s.toString()).doubleValue();
    }

    private static String getSoundex(String s) {
        int len = s.length();
        char[] chars = new char[] { '0', '0', '0', '0' };
        char lastDigit = '0';
        for (int i = 0, j = 0; i < len && j < 4; i++) {
            char c = s.charAt(i);
            char newDigit = c > SOUNDEX_INDEX.length ? 0 : SOUNDEX_INDEX[c];
            if (newDigit != 0) {
                if (j == 0) {
                    chars[j++] = c;
                    lastDigit = newDigit;
                } else if (newDigit <= '6') {
                    if (newDigit != lastDigit) {
                        chars[j++] = newDigit;
                        lastDigit = newDigit;
                    }
                } else if (newDigit == '7') {
                    lastDigit = newDigit;
                }
            }
        }
        return new String(chars);
    }

    public int getType() {
        return dataType;
    }

    public void mapColumns(ColumnResolver resolver, int level) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            args[i].mapColumns(resolver, level);
        }
    }

    public void doneWithParameters() throws SQLException {
        if (info.parameterCount == VAR_ARGS) {
            int len = varArgs.size();
            int min = 0, max = Integer.MAX_VALUE;
            switch (info.type) {
            case COALESCE:
            case CSVREAD:
            case TABLE:
            case LEAST:
            case GREATEST:
                min = 1;
                break;
            case NOW:
            case CURRENT_TIMESTAMP:
            case RAND:
                max = 1;
                break;
            case COMPRESS:
            case LTRIM:
            case RTRIM:
            case TRIM:
                max = 2;
                break;
            case REPLACE:
            case LOCATE:
            case INSTR:
            case SUBSTR:
            case SUBSTRING:
                min = 2;
                max = 3;
                break;
            case CASE:
            case CONCAT:
            case CSVWRITE:
                min = 2;
                break;
            case XMLNODE:
                min = 1;
                max = 3;
                break;
            case FORMATDATETIME:
            case PARSEDATETIME:
                min = 2;
                max = 4;
                break;
            case CURRVAL:
            case NEXTVAL:
                min = 1;
                max = 2;
                break;
            default:
                throw Message.getInternalError("type=" + info.type);
            }
            boolean ok = (len >= min) && (len <= max);
            if (!ok) {
                throw Message.getSQLException(ErrorCode.INVALID_PARAMETER_COUNT_2, new String[] { info.name,
                        min + ".." + max });
            }
            args = new Expression[len];
            varArgs.toArray(args);
            varArgs = null;
        } else {
            int len = args.length;
            if (len > 0 && args[len - 1] == null) {
                throw Message
                        .getSQLException(ErrorCode.INVALID_PARAMETER_COUNT_2, new String[] { info.name, "" + len });
            }
        }
    }

    public void setDataType(int dataType, long precision, int scale, int displaySize) {
        this.dataType = dataType;
        this.precision = precision;
        this.displaySize = displaySize;
        this.scale = scale;
    }

    public void setDataType(Column col) {
        dataType = col.getType();
        precision = col.getPrecision();
        displaySize = col.getDisplaySize();
        scale = col.getScale();
    }

    public Expression optimize(Session session) throws SQLException {
        boolean allConst = info.isDeterministic;
        for (int i = 0; i < args.length; i++) {
            Expression e = args[i].optimize(session);
            args[i] = e;
            if (!e.isConstant()) {
                allConst = false;
            }
        }
        Expression p0 = args.length < 1 ? null : args[0];
        switch (info.type) {
        case IFNULL:
        case NULLIF:
        case COALESCE:
        case LEAST:
        case GREATEST: {
            dataType = Value.UNKNOWN;
            scale = 0;
            precision = 0;
            displaySize = 0;
            for (int i = 0; i < args.length; i++) {
                Expression e = args[i];
                if (e != ValueExpression.NULL && e.getType() != Value.UNKNOWN) {
                    dataType = Value.getHigherOrder(dataType, e.getType());
                    scale = Math.max(scale, e.getScale());
                    precision = Math.max(precision, e.getPrecision());
                    displaySize = Math.max(displaySize, e.getDisplaySize());
                }
            }
            if (dataType == Value.UNKNOWN) {
                dataType = Value.STRING;
                scale = 0;
                precision = 255;
                displaySize = 255;
            }
            break;
        }
        case CASEWHEN:
            dataType = Value.getHigherOrder(args[1].getType(), args[2].getType());
            precision = Math.max(args[1].getPrecision(), args[2].getPrecision());
            displaySize = Math.max(args[1].getDisplaySize(), args[2].getDisplaySize());
            scale = Math.max(args[1].getScale(), args[2].getScale());
            break;
        case CAST:
        case CONVERT:
            // data type, precision and scale is already set
            break;
        case ABS:
        case FLOOR:
        case RADIANS:
        case ROUND:
        case TRUNCATE:
        case POWER:
        case ARRAY_GET:
            dataType = p0.getType();
            scale = p0.getScale();
            precision = p0.getPrecision();
            displaySize = p0.getDisplaySize();
            if (dataType == Value.NULL) {
                dataType = Value.INT;
                precision = ValueInt.PRECISION;
                displaySize = ValueInt.DISPLAY_SIZE;
                scale = 0;
            }
            break;
        default:
            dataType = info.dataType;
            precision = 0;
            displaySize = 0;
            scale = 0;
        }
        if (allConst) {
            return ValueExpression.get(getValue(session));
        }
        return this;
    }

    public void setEvaluatable(TableFilter tableFilter, boolean b) {
        for (int i = 0; i < args.length; i++) {
            Expression e = args[i];
            if (e != null) {
                e.setEvaluatable(tableFilter, b);
            }
        }
    }

    public int getScale() {
        return scale;
    }

    public long getPrecision() {
        if (precision == 0) {
            calculatePrecisionAndDisplaySize();
        }
        return precision;
    }
    
    public int getDisplaySize() {
        if (precision == 0) {
            calculatePrecisionAndDisplaySize();
        }
        return displaySize;
    }
    
    private void calculatePrecisionAndDisplaySize() {
        switch (info.type) {
        case ENCRYPT:
        case DECRYPT:
            precision = args[2].getPrecision();
            displaySize = args[2].getDisplaySize();
            break;
        case COMPRESS:
            precision = args[0].getPrecision();
            displaySize = args[0].getDisplaySize();
            break;
        case CHAR:
            precision = 1;
            displaySize = 1;
            break;
        case CONCAT:
            precision = 0;
            displaySize = 0;
            for (int i = 0; i < args.length; i++) {
                precision += args[i].getPrecision();
                displaySize = MathUtils.convertLongToInt((long) displaySize + args[i].getDisplaySize());
                if (precision < 0) {
                    precision = Long.MAX_VALUE;
                }
            }
            break;
        case HEXTORAW:
            precision = (args[0].getPrecision() + 3) / 4;
            displaySize = MathUtils.convertLongToInt(precision);
            break;
        case LCASE:
        case LTRIM:
        case RIGHT:
        case RTRIM:
        case UCASE:
        case LOWER:
        case UPPER:
        case TRIM:
        case STRINGDECODE:
        case UTF8TOSTRING:
            precision = args[0].getPrecision();
            displaySize = args[0].getDisplaySize();
            break;
        case RAWTOHEX:
            precision = args[0].getPrecision() * 4;
            displaySize = MathUtils.convertLongToInt(precision);
            break;
        case SOUNDEX:
            precision = 4;
            displaySize = (int) precision;
            break;
        case DAYNAME:
        case MONTHNAME:
            precision = 20; // day and month names may be long in some languages
            displaySize = (int) precision;
            break;
        }
    }

    public String getSQL() {
        StringBuffer buff = new StringBuffer();
        buff.append(info.name);
        buff.append('(');
        switch (info.type) {
        case CAST: {
            buff.append(args[0].getSQL());
            buff.append(" AS ");
            buff.append(new Column(null, dataType, precision, scale, displaySize).getCreateSQL());
            break;
        }
        case CONVERT: {
            buff.append(args[0].getSQL());
            buff.append(",");
            buff.append(new Column(null, dataType, precision, scale, displaySize).getCreateSQL());
            break;
        }
        case EXTRACT: {
            ValueString v = (ValueString) ((ValueExpression) args[0]).getValue(null);
            buff.append(v.getString());
            buff.append(" FROM ");
            buff.append(args[1].getSQL());
            break;
        }
        case TABLE: {
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    buff.append(", ");
                }
                buff.append(columnList[i].getCreateSQL());
                buff.append("=");
                Expression e = args[i];
                buff.append(e.getSQL());
            }
            break;
        }
        default: {
            for (int i = 0; i < args.length; i++) {
                if (i > 0) {
                    buff.append(", ");
                }
                Expression e = args[i];
                buff.append(e.getSQL());
            }
        }
        }
        buff.append(')');
        return buff.toString();
    }

    public void updateAggregate(Session session) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Expression e = args[i];
            if (e != null) {
                e.updateAggregate(session);
            }
        }
    }

    public int getFunctionType() {
        return info.type;
    }

    public String getName() {
        return info.name;
    }

    public int getParameterCount() {
        return args.length;
    }

    public ValueResultSet getValueForColumnList(Session session, Expression[] args) throws SQLException {
        switch (info.type) {
        case CSVREAD: {
            String fileName = args[0].getValue(session).getString();
            if (fileName == null) {
                throw Message.getSQLException(ErrorCode.PARAMETER_NOT_SET_1, "fileName");
            }
            String columnList = args.length < 2 ? null : args[1].getValue(session).getString();
            String charset = args.length < 3 ? null : args[2].getValue(session).getString();
            String fieldSeparatorRead = args.length < 4 ? null : args[3].getValue(session).getString();
            String fieldDelimiter = args.length < 5 ? null : args[4].getValue(session).getString();
            String escapeCharacter = args.length < 6 ? null : args[5].getValue(session).getString();
            Csv csv = Csv.getInstance();
            char fieldSeparator = ',';
            if (fieldSeparatorRead != null && fieldSeparatorRead.length() > 0) {
                fieldSeparator = fieldSeparatorRead.charAt(0);
                csv.setFieldSeparatorRead(fieldSeparator);
            }
            if (fieldDelimiter != null && fieldDelimiter.length() > 0) {
                csv.setFieldDelimiter(fieldDelimiter.charAt(0));
            }
            if (escapeCharacter != null && escapeCharacter.length() > 0) {
                csv.setEscapeCharacter(escapeCharacter.charAt(0));
            }
            String[] columns = StringUtils.arraySplit(columnList, fieldSeparator, true);
            ResultSet rs = csv.read(fileName, columns, charset);
            ValueResultSet vr = ValueResultSet.getCopy(rs, 0);
            return vr;
        }
        case TABLE: {
            return getTable(session, args, true);
        }
        default:
            break;
        }
        return (ValueResultSet) getValueWithArgs(session, args);
    }

    public Expression[] getArgs() {
        return args;
    }

    public boolean isEverything(ExpressionVisitor visitor) {
        if (visitor.type == ExpressionVisitor.DETERMINISTIC && !info.isDeterministic) {
            return false;
        }
        for (int i = 0; i < args.length; i++) {
            Expression e = args[i];
            if (e != null && !e.isEverything(visitor)) {
                return false;
            }
        }
        return true;
    }

    public int getCost() {
        int cost = 3;
        for (int i = 0; i < args.length; i++) {
            cost += args[i].getCost();
        }
        return cost;
    }

    public ValueResultSet getTable(Session session, Expression[] args, boolean onlyColumnList) throws SQLException {
        SimpleResultSet rs = new SimpleResultSet();
        int len = columnList.length;
        for (int i = 0; i < len; i++) {
            Column c = columnList[i];
            String columnName = c.getName();
            int dataType = DataType.convertTypeToSQLType(c.getType());
            int precision = MathUtils.convertLongToInt(c.getPrecision());
            int scale = c.getScale();
            rs.addColumn(columnName, dataType, precision, scale);
        }
        if (!onlyColumnList) {
            Value[][] list = new Value[args.length][];
            int rowCount = 0;
            for (int i = 0; i < len; i++) {
                Value v = args[i].getValue(session);
                if (v == ValueNull.INSTANCE) {
                    list[i] = new Value[0];
                } else {
                    ValueArray array = (ValueArray) v.convertTo(Value.ARRAY);
                    Value[] l = array.getList();
                    list[i] = l;
                    rowCount = Math.max(rowCount, l.length);
                }
            }
            for (int row = 0; row < rowCount; row++) {
                Object[] r = new Object[len];
                for (int j = 0; j < len; j++) {
                    Value[] l = list[j];
                    Value v;
                    if (l.length <= row) {
                        v = ValueNull.INSTANCE;
                    } else {
                        Column c = columnList[j];
                        v = l[row];
                        v = v.convertTo(c.getType());
                        v = v.convertPrecision(c.getPrecision());
                        v = v.convertScale(true, c.getScale());
                    }
                    r[j] = v.getObject();
                }
                rs.addRow(r);
            }
        }
        ValueResultSet vr = ValueResultSet.get(rs);
        return vr;
    }

    public void setColumns(ObjectArray columns) {
        this.columnList = new Column[columns.size()];
        columns.toArray(columnList);
    }

}
