/*
 * Copyright 2004-2006 H2 Group. Licensed under the H2 License, Version 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.util;

import java.sql.SQLException;

import org.h2.message.Message;


public class ByteUtils {
    
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    
    public static int readInt(byte[] buff, int pos) {
        return (buff[pos++]<< 24) + ((buff[pos++] & 0xff) << 16)
        + ((buff[pos++] & 0xff) << 8) + (buff[pos++] & 0xff);
    }

    public static long readLong(byte[] buff, int pos) {
        return ((long)(readInt(buff, pos)) << 32) + (readInt(buff, pos+4) & 0xffffffffL);
    }

    public static int indexOf(byte[] bytes, byte[] pattern, int start) {
        if (pattern.length == 0) {
            return start;
        }
        if (start > bytes.length) {
            return -1;
        }
        int last = bytes.length - pattern.length + 1;
        next:
        for(;start < last; start++) {
            for(int i=0; i<pattern.length; i++) {
                if(bytes[start + i] != pattern[i]) {
                    continue next;
                }
            }
            return start;
        }
        return -1;
    }
    
    public static byte[] convertStringToBytes(String s) throws SQLException {
        int len = s.length();
        if (len % 2 == 1) {
            throw Message.getSQLException(Message.HEX_STRING_ODD_1, s);
        }
        len /= 2;
        byte[] buff = new byte[len];
        try {
            for (int i = 0; i < len; i++) {
                buff[i] = (byte) ((Character.digit(s.charAt(i+i), 16) << 4) | (Character.digit(s.charAt(i+i+1), 16)));
            }
        } catch (NumberFormatException e) {
            throw Message.getSQLException(Message.HEX_STRING_WRONG_1, s);
        }
        return buff;
    }
    
    public static int getByteArrayHash(byte[] value) {
        int h = 1;
        for (int i = 0; i < value.length;) {
            h = 31 * h + value[i++];
        }
        return h;
    }
    
    public static String convertBytesToString(byte[] value) {
        return convertBytesToString(value, value.length);
    }

    public static String convertBytesToString(byte[] value, int len) {
        char[] buff = new char[len+len];
        char[] hex = HEX;
        for (int i = 0; i < len; i++) {
            int c = value[i] & 0xff;
            buff[i+i] = hex[c >> 4];
            buff[i+i+1] = hex[c & 0xf];
        }
        return new String(buff);
    }       
    
    public static boolean compareSecure(byte[] test, byte[] good) {
        if((test==null) || (good==null)) {
            return (test == null) && (good == null);
        }
        if(test.length != good.length) {
            return false;
        }
        if(test.length == 0) {
            return true;
        }
        // silly loop: this should help a little against timing attacks
        boolean correct = true, correct2 = false;
        for(int i=0; i<good.length; i++) {
            if(test[i] != good[i]) {
                correct = false;
            } else {
                correct2 = true;
            }
        }
        return correct && correct2;
    }
    
    public static void clear(byte[] buff) {
        for(int i=0; i<buff.length; i++) {
            buff[i] = 0;
        }
    }
    
    public static int compareNotNull(byte[] data1, byte[] data2) {
        int len = Math.min(data1.length, data2.length);
        for (int i = 0; i < len; i++) {
            byte b = data1[i];
            byte b2 = data2[i];
            if (b != b2) {
                return b > b2 ? 1 : -1;
            }
        }
        int c = data1.length - data2.length;
        return c == 0 ? 0 : (c < 0 ? -1 : 1);
    }

    public static String convertToBinString(byte[] buff) {
        char[] chars = new char[buff.length];
        for(int i=0; i<buff.length; i++) {
            chars[i] = (char) (buff[i] & 0xff);
        }
        return new String(chars);
    }

    public static byte[] convertBinStringToBytes(String data) {
        byte[] buff = new byte[data.length()];
        for(int i=0; i<data.length(); i++) {
            buff[i] = (byte) (data.charAt(i) & 0xff);
        }
        return buff;
    }

    public static byte[] copy(byte[] source, byte[] target) {
        int len = source.length;
        if(len > target.length) {
            target = new byte[len];
        }
        System.arraycopy(source, 0, target, 0, len);
        return target;
    }

}
