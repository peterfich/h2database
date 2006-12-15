/*
 * Copyright 2004-2006 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.value;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.util.Calendar;

import org.h2.message.Message;

public class ValueTime extends Value {
    public static final int PRECISION = 6;
    private Time value;

    private ValueTime(Time value) {
        // this class is mutable - must copy the object
        Calendar cal = Calendar.getInstance();
        cal.setTime(value);
        // TODO gcj: required so that the millis are calculated?
        cal.get(Calendar.HOUR_OF_DAY);
        cal.set(1970, 0, 1);
        this.value = new Time(cal.getTime().getTime());        
    }

    public static Time parseTime(String s) throws SQLException {
        return (Time) DataType.parseDateTime(s, Value.TIME, Message.TIME_CONSTANT_1);
    }

    public Time getTime() {
        // this class is mutable - must copy the object
        return (Time)value.clone();
    }

    public String getSQL() {
        return "TIME '" + getString() + "'";
    }

    public int getType() {
        return Value.TIME;
    }

    protected int compareSecure(Value o, CompareMode mode) {
        ValueTime v = (ValueTime) o;
        int c = value.compareTo(v.value);
        return c == 0 ? 0 : (c < 0 ? -1 : 1);
    }

    public String getString() {
        return value.toString();
    }

    public long getPrecision() {
        return PRECISION;
    }

    public int hashCode() {
        return value.hashCode();
    }

    public Object getObject() {
        return getTime();
    }

    public void set(PreparedStatement prep, int parameterIndex) throws SQLException {
        prep.setTime(parameterIndex, value);
    }

    public static ValueTime get(Time date) {
        return (ValueTime) Value.cache(new ValueTime(date));
    }

//    public String getJavaString() {
//        return "Time.valueOf(\"" + toString() + "\")";
//    }
    
    public int getDisplaySize() {
        return "23:59:59".length();
    }    
    
    protected boolean isEqual(Value v) {
        return v instanceof ValueTime && value.equals(((ValueTime)v).value);
    }    

}
