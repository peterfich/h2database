/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: James Moger
 */
package org.h2.jaqu;

/**
 * This class represents a "column=value" token for a SET statement.
 *
 * @param <A> the new value data type
 */
//## Java 1.5 begin ##
public class SetColumn<T, A> implements Declaration {

    private Query<T> query;
    private A x;
    private A y;

    SetColumn(Query<T> query, A x) {
        this.query = query;
        this.x = x;
    }
    
    public Query<T> to(A y) {
        query.addDeclarationToken(this);
        this.y = y;
        return query;
    }

    @Override
    public void appendSQL(SQLStatement stat) {
        query.appendSQL(stat, x);
        stat.appendSQL("=?");
        stat.addParameter(y);
    }
}
//## Java 1.5 end ##
