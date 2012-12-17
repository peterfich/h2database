/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.mvstore.type;

/**
 * A factory for maps and data types.
 */
public interface DataTypeFactory {

    /**
     * Set the parent factory.
     *
     * @param parent the parent factory
     */
    void setParent(DataTypeFactory parent);

    /**
     * Parse the data type.
     *
     * @param dataType the string and type specific meta data
     * @return the type, or null if unknown
     */
    DataType buildDataType(String dataType);

}
