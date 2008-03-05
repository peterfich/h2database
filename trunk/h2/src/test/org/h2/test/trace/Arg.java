/*
 * Copyright 2004-2008 H2 Group. Licensed under the H2 License, Version 1.0
 * (license2)
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.h2.test.trace;

import java.math.BigDecimal;

import org.h2.util.StringUtils;

/**
 * An argument of a statement.
 */
class Arg {
    private Class clazz;
    private Object obj;
    private Statement stat;

    Arg(Player player, Class clazz, Object obj) {
        this.clazz = clazz;
        this.obj = obj;
    }

    Arg(Statement stat) {
        this.stat = stat;
    }

    public String toString() {
        if (stat != null) {
            return stat.toString();
        } else {
            return quote(clazz, getValue());
        }
    }

    void execute() throws Exception {
        if (stat != null) {
            obj = stat.execute();
            clazz = stat.getReturnClass();
            stat = null;
        }
    }

    Class getValueClass() {
        return clazz;
    }

    Object getValue() {
        return obj;
    }

    String quote(Class clazz, Object value) {
        if (value == null) {
            return null;
        } else if (clazz == String.class) {
            return StringUtils.quoteJavaString(value.toString());
        } else if (clazz == BigDecimal.class) {
            return "new BigDecimal(\"" + value.toString() + "\")";
        } else if (clazz.isArray()) {
            if (clazz == String[].class) {
                return StringUtils.quoteJavaStringArray((String[]) value);
            } else if (clazz == int[].class) {
                return StringUtils.quoteJavaIntArray((int[]) value);
            }
        }
        return value.toString();
    }

}
