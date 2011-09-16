/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.store.fs;

import java.io.IOException;

/**
 * This file system stores files on disk and uses java.nio to access the files.
 * This class used memory mapped files.
 */
public class FilePathNioMapped extends FilePathNio {

    protected FileObject open(String mode) throws IOException {
        return new FileObjectNioMapped(name, mode);
    }

    /**
     * Get the prefix for this file system.
     *
     * @return the prefix
     */
    public String getScheme() {
        return "nioMapped";
    }

}
