/*
 * Copyright 2004-2008 H2 Group. Multiple-Licensed under the H2 License, 
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.ddl;

import java.sql.SQLException;
import java.util.HashSet;

import org.h2.constant.ErrorCode;
import org.h2.constraint.Constraint;
import org.h2.constraint.ConstraintCheck;
import org.h2.constraint.ConstraintReferential;
import org.h2.constraint.ConstraintUnique;
import org.h2.engine.Constants;
import org.h2.engine.Database;
import org.h2.engine.DbObject;
import org.h2.engine.Right;
import org.h2.engine.Session;
import org.h2.expression.Expression;
import org.h2.index.Index;
import org.h2.index.IndexType;
import org.h2.message.Message;
import org.h2.schema.Schema;
import org.h2.table.Column;
import org.h2.table.IndexColumn;
import org.h2.table.Table;
import org.h2.table.TableFilter;
import org.h2.util.ObjectArray;

/**
 * This class represents the statement
 * ALTER TABLE ADD CONSTRAINT
 */
public class AlterTableAddConstraint extends SchemaCommand {

    /**
     * The type of a ALTER TABLE ADD CHECK statement.
     */
    public static final int CHECK = 0;
    
    /**
     * The type of a ALTER TABLE ADD UNIQUE statement.
     */
    public static final int UNIQUE = 1;
    
    /**
     * The type of a ALTER TABLE ADD FOREIGN KEY statement.
     */
    public static final int REFERENTIAL = 2;
    
    /**
     * The type of a ALTER TABLE ADD PRIMARY KEY statement.
     */
    public static final int PRIMARY_KEY = 3;
    
    private int type;
    private String constraintName;
    private String tableName;
    private IndexColumn[] indexColumns;
    private int deleteAction;
    private int updateAction;
    private Schema refSchema;
    private String refTableName;
    private IndexColumn[] refIndexColumns;
    private Expression checkExpression;
    private Index index, refIndex;
    private String comment;
    private boolean checkExisting;
    private boolean primaryKeyHash;

    public AlterTableAddConstraint(Session session, Schema schema) {
        super(session, schema);
    }

    private String generateConstraintName(DbObject obj, int id) throws SQLException {
        if (constraintName == null) {
            constraintName = getSchema().getUniqueConstraintName(obj);
        }
        return constraintName;
    }

    public int update() throws SQLException {
        try {
            return tryUpdate();
        } finally {
            getSchema().freeUniqueName(constraintName);
        }
    }

    /**
     * Try to execute the statement.
     * 
     * @return the update count
     */
    public int tryUpdate() throws SQLException {
        session.commit(true);
        Database db = session.getDatabase();
        Table table = getSchema().getTableOrView(session, tableName);
        if (getSchema().findConstraint(constraintName) != null) {
            throw Message.getSQLException(ErrorCode.CONSTRAINT_ALREADY_EXISTS_1, constraintName);
        }
        session.getUser().checkRight(table, Right.ALL);
        table.lock(session, true, true);
        Constraint constraint;
        switch (type) {
        case PRIMARY_KEY: {
            IndexColumn.mapColumns(indexColumns, table);
            index = table.findPrimaryKey();
            ObjectArray constraints = table.getConstraints();
            for (int i = 0; constraints != null && i < constraints.size(); i++) {
                Constraint c = (Constraint) constraints.get(i);
                if (Constraint.PRIMARY_KEY.equals(c.getConstraintType())) {
                    throw Message.getSQLException(ErrorCode.SECOND_PRIMARY_KEY);
                }
            }
            if (index != null) {
                // if there is an index, it must match with the one declared
                // we don't test ascending / descending
                IndexColumn[] pkCols = index.getIndexColumns();
                if (pkCols.length != indexColumns.length) {
                    throw Message.getSQLException(ErrorCode.SECOND_PRIMARY_KEY);
                }
                for (int i = 0; i < pkCols.length; i++) {
                    if (pkCols[i].column != indexColumns[i].column) {
                        throw Message.getSQLException(ErrorCode.SECOND_PRIMARY_KEY);
                    }
                }
            }
            if (index == null) {
                IndexType indexType = IndexType.createPrimaryKey(table.isPersistent(), primaryKeyHash);
                String indexName = table.getSchema().getUniqueIndexName(table, Constants.PREFIX_PRIMARY_KEY);
                int id = getObjectId(true, false);
                try {
                    index = table.addIndex(session, indexName, id, indexColumns, indexType, Index.EMPTY_HEAD, null);
                } finally {
                    getSchema().freeUniqueName(indexName);
                }
            }
            index.getIndexType().setBelongsToConstraint(true);
            int constraintId = getObjectId(true, true);
            String name = generateConstraintName(table, constraintId);
            ConstraintUnique pk = new ConstraintUnique(getSchema(), constraintId, name, table, true);
            pk.setColumns(indexColumns);
            pk.setIndex(index, true);
            constraint = pk;
            break;
        }
        case UNIQUE: {
            IndexColumn.mapColumns(indexColumns, table);
            boolean isOwner = false;
            if (index != null && canUseUniqueIndex(index, table, indexColumns)) {
                isOwner = true;
                index.getIndexType().setBelongsToConstraint(true);
            } else {
                index = getUniqueIndex(table, indexColumns);
                if (index == null) {
                    index = createIndex(table, indexColumns, true);
                    isOwner = true;
                }
            }
            int id = getObjectId(true, true);
            String name = generateConstraintName(table, id);
            ConstraintUnique unique = new ConstraintUnique(getSchema(), id, name, table, false);
            unique.setColumns(indexColumns);
            unique.setIndex(index, isOwner);
            constraint = unique;
            break;
        }
        case CHECK: {
            int id = getObjectId(true, true);
            String name = generateConstraintName(table, id);
            ConstraintCheck check = new ConstraintCheck(getSchema(), id, name, table);
            TableFilter filter = new TableFilter(session, table, null, false, null);
            checkExpression.mapColumns(filter, 0);
            checkExpression = checkExpression.optimize(session);
            check.setExpression(checkExpression);
            check.setTableFilter(filter);
            constraint = check;
            if (checkExisting) {
                check.checkExistingData(session);
            }
            break;
        }
        case REFERENTIAL: {
            Table refTable = refSchema.getTableOrView(session, refTableName);
            session.getUser().checkRight(refTable, Right.ALL);
            boolean isOwner = false;
            IndexColumn.mapColumns(indexColumns, table);
            if (index != null && canUseIndex(index, table, indexColumns)) {
                isOwner = true;
                index.getIndexType().setBelongsToConstraint(true);
            } else {
                index = getIndex(table, indexColumns);
                if (index == null) {
                    index = createIndex(table, indexColumns, false);
                    isOwner = true;
                }
            }
            if (refIndexColumns == null) {
                Index refIdx = refTable.getPrimaryKey();
                refIndexColumns = refIdx.getIndexColumns();
            } else {
                IndexColumn.mapColumns(refIndexColumns, refTable);
            }
            if (refIndexColumns.length != indexColumns.length) {
                throw Message.getSQLException(ErrorCode.COLUMN_COUNT_DOES_NOT_MATCH);
            }
            boolean isRefOwner = false;
            if (refIndex != null && refIndex.getTable() == refTable) {
                isRefOwner = true;
                refIndex.getIndexType().setBelongsToConstraint(true);
            } else {
                refIndex = null;
            }
            if (refIndex == null) {
                refIndex = getUniqueIndex(refTable, refIndexColumns);
                if (refIndex == null) {
                    refIndex = createIndex(refTable, refIndexColumns, true);
                    isRefOwner = true;
                }
            }
            int id = getObjectId(true, true);
            String name = generateConstraintName(table, id);
            ConstraintReferential ref = new ConstraintReferential(getSchema(), id, name, table);
            ref.setColumns(indexColumns);
            ref.setIndex(index, isOwner);
            ref.setRefTable(refTable);
            ref.setRefColumns(refIndexColumns);
            ref.setRefIndex(refIndex, isRefOwner);
            if (checkExisting) {
                ref.checkExistingData(session);
            }
            constraint = ref;
            refTable.addConstraint(constraint);
            ref.setDeleteAction(session, deleteAction);
            ref.setUpdateAction(session, updateAction);
            break;
        }
        default:
            throw Message.getInternalError("type=" + type);
        }
        // parent relationship is already set with addConstraint
        constraint.setComment(comment);
        db.addSchemaObject(session, constraint);
        table.addConstraint(constraint);
        return 0;
    }

    private Index createIndex(Table t, IndexColumn[] cols, boolean unique) throws SQLException {
        int indexId = getObjectId(true, false);
        IndexType indexType;
        if (unique) {
            // TODO default index (hash or not; memory or not or same as table)
            // for unique constraints
            indexType = IndexType.createUnique(t.isPersistent(), false);
        } else {
            // TODO default index (memory or not or same as table) for unique
            // constraints
            indexType = IndexType.createNonUnique(t.isPersistent());
        }
        indexType.setBelongsToConstraint(true);
        String prefix = constraintName == null ? "CONSTRAINT" : constraintName;
        String indexName = t.getSchema().getUniqueIndexName(t, prefix + "_INDEX_");
        Index idx;
        try {
            idx = t.addIndex(session, indexName, indexId, cols, indexType, Index.EMPTY_HEAD, null);
        } finally {
            getSchema().freeUniqueName(indexName);
        }
        return idx;
    }

    public void setDeleteAction(int action) {
        this.deleteAction = action;
    }

    public void setUpdateAction(int action) {
        this.updateAction = action;
    }

    private Index getUniqueIndex(Table t, IndexColumn[] cols) {
        ObjectArray list = t.getIndexes();
        for (int i = 0; i < list.size(); i++) {
            Index index = (Index) list.get(i);
            if (canUseUniqueIndex(index, t, cols)) {
                return index;
            }
        }
        return null;
    }

    private Index getIndex(Table t, IndexColumn[] cols) {
        ObjectArray list = t.getIndexes();
        for (int i = 0; i < list.size(); i++) {
            Index index = (Index) list.get(i);
            if (canUseIndex(index, t, cols)) {
                return index;
            }
        }
        return null;
    }

    private boolean canUseUniqueIndex(Index index, Table table, IndexColumn[] cols) {
        if (index.getTable() != table || !index.getIndexType().isUnique()) {
            return false;
        }
        Column[] indexCols = index.getColumns();
        if (indexCols.length > cols.length) {
            return false;
        }
        HashSet set = new HashSet();
        for (int i = 0; i < cols.length; i++) {
            set.add(cols[i].column);
        }
        for (int j = 0; j < indexCols.length; j++) {
            // all columns of the index must be part of the list,
            // but not all columns of the list need to be part of the index
            if (!set.contains(indexCols[j])) {
                return false;
            }
        }
        return true;
    }

    private boolean canUseIndex(Index index, Table table, IndexColumn[] cols) {
        if (index.getTable() != table || index.getCreateSQL() == null) {
            // can't use the scan index or index of another table
            return false;
        }
        Column[] indexCols = index.getColumns();
        if (indexCols.length < cols.length) {
            return false;
        }
        for (int j = 0; j < cols.length; j++) {
            // all columns of the list must be part of the index,
            // but not all columns of the index need to be part of the list
            // holes are not allowed (index=a,b,c & list=a,b is ok; but list=a,c
            // is not)
            int idx = index.getColumnIndex(cols[j].column);
            if (idx < 0 || idx >= cols.length) {
                return false;
            }
        }
        return true;
    }

    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public void setCheckExpression(Expression expression) {
        this.checkExpression = expression;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setIndexColumns(IndexColumn[] indexColumns) {
        this.indexColumns = indexColumns;
    }

    public IndexColumn[] getIndexColumns() {
        return indexColumns;
    }

    /**
     * Set the referenced table.
     * 
     * @param refSchema the schema
     * @param ref the table name
     */
    public void setRefTableName(Schema refSchema, String ref) {
        this.refSchema = refSchema;
        this.refTableName = ref;
    }

    public void setRefIndexColumns(IndexColumn[] indexColumns) {
        this.refIndexColumns = indexColumns;
    }

    public void setIndex(Index index) {
        this.index = index;
    }

    public void setRefIndex(Index refIndex) {
        this.refIndex = refIndex;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setCheckExisting(boolean b) {
        this.checkExisting = b;
    }

    public void setPrimaryKeyHash(boolean b) {
        this.primaryKeyHash = b;
    }

}
