/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License, Version
 * 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html). Initial Developer: H2 Group
 */
package org.h2.android;

/**
 * TODO
 */
public class H2MisuseException extends H2Exception {
    private static final long serialVersionUID = 1L;

    H2MisuseException() {
        super();
    }

    H2MisuseException(String error) {
        super(error);
    }
}
