/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: James Moger
 */
package org.h2.jaqu;

import static java.text.MessageFormat.format;
import static org.h2.jaqu.ValidationRemark.consider;
import static org.h2.jaqu.ValidationRemark.error;
import static org.h2.jaqu.ValidationRemark.warn;
import static org.h2.jaqu.util.JdbcUtils.closeSilently;
import static org.h2.jaqu.util.StringUtils.isNullOrEmpty;
import java.lang.reflect.Modifier;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.h2.jaqu.Table.IndexType;
import org.h2.jaqu.Table.JQColumn;
import org.h2.jaqu.Table.JQIndex;
import org.h2.jaqu.Table.JQSchema;
import org.h2.jaqu.Table.JQTable;
import org.h2.jaqu.TableDefinition.FieldDefinition;
import org.h2.jaqu.TableDefinition.IndexDefinition;
import org.h2.jaqu.util.StatementBuilder;
import org.h2.jaqu.util.Utils;

/**
 * Class to inspect the contents of a particular table including its indexes.
 * This class does the bulk of the work in terms of model generation and model
 * validation.
 */
public class TableInspector {

    private final static int todoReviewClass = 0;

    private String schema;
    private String table;
    private boolean forceUpperCase;
    private Class<? extends java.util.Date> dateClazz;
    private List<String> primaryKeys = Utils.newArrayList();
    private Map<String, IndexInspector> indexes;
    private Map<String, ColumnInspector> columns;
    private final String eol = "\n";

    TableInspector(String schema, String table, boolean forceUpperCase,
            Class<? extends java.util.Date> dateClazz) {
        this.schema = schema;
        this.table = table;
        this.forceUpperCase = forceUpperCase;
        this.dateClazz = dateClazz;
    }

    /**
     * Tests to see if this TableInspector represents schema.table.
     * <p>
     * @param schema the schema name
     * @param table the table name
     * @return true if the table matches
     */
    boolean matches(String schema, String table) {
        if (isNullOrEmpty(schema)) {
            // table name matching
            return this.table.equalsIgnoreCase(table);
        } else if (isNullOrEmpty(table)) {
            // schema name matching
            return this.schema.equalsIgnoreCase(schema);
        } else {
            // exact table matching
            return this.schema.equalsIgnoreCase(schema)
                && this.table.equalsIgnoreCase(table);
        }
    }

    /**
     * Reads the DatabaseMetaData for the details of this table including
     * primary keys and indexes.
     *
     * @param metaData the database meta data
     */
    void read(DatabaseMetaData metaData) throws SQLException {
        ResultSet rs = null;

        // primary keys
        try {
            rs = metaData.getPrimaryKeys(null, schema, table);
            while (rs.next()) {
                String c = rs.getString("COLUMN_NAME");
                primaryKeys.add(c);
            }
            closeSilently(rs);

            // indexes
            rs = metaData.getIndexInfo(null, schema, table, false, true);
            indexes = Utils.newHashMap();
            while (rs.next()) {
                IndexInspector info = new IndexInspector(rs);
                if (info.type.equals(IndexType.UNIQUE)
                        && info.name.toLowerCase().startsWith("primary")) {
                    // skip primary key indexes
                    continue;
                }
                if (indexes.containsKey(info.name)) {
                    indexes.get(info.name).addColumn(rs);
                } else {
                    indexes.put(info.name, info);
                }
            }
            closeSilently(rs);

            // columns
            rs = metaData.getColumns(null, schema, table, null);
            columns = Utils.newHashMap();
            while (rs.next()) {
                ColumnInspector col = new ColumnInspector();
                col.name = rs.getString("COLUMN_NAME");
                col.type = rs.getString("TYPE_NAME");
                col.clazz = ModelUtils.getClassType(col.type, dateClazz);
                col.size = rs.getInt("COLUMN_SIZE");
                col.allowNull = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                col.isAutoIncrement = rs.getBoolean("IS_AUTOINCREMENT");
                if (primaryKeys.size() == 1) {
                    if (col.name.equalsIgnoreCase(primaryKeys.get(0))) {
                        col.isPrimaryKey = true;
                    }
                }
                if (!col.isAutoIncrement) {
                    col.defaultValue = rs.getString("COLUMN_DEF");
                }
                columns.put(col.name, col);
            }
        } finally {
            closeSilently(rs);
        }
    }

    /**
     * Generates a model (class definition) from this table.
     * The model includes indexes, primary keys, default values, maxLengths,
     * and allowNull information.
     * <p>
     * The caller may optionally set a destination package name, whether or not
     * to include the schema name (setting schema can be a problem when using
     * the model between databases), and if to automatically trim strings for
     * those that have a maximum length.
     * <p>
     * @param packageName
     * @param annotateSchema
     * @param trimStrings
     * @return a complete model (class definition) for this table as a string
     */
    String generateModel(String packageName, boolean annotateSchema,
            boolean trimStrings) {

        // import statements
        Set<String> imports = Utils.newHashSet();
        imports.add(JQSchema.class.getCanonicalName());
        imports.add(JQTable.class.getCanonicalName());
        imports.add(JQIndex.class.getCanonicalName());
        imports.add(JQColumn.class.getCanonicalName());

        // fields
        StringBuilder fields = new StringBuilder();
        List<ColumnInspector> sortedColumns = Utils.newArrayList(columns.values());
        Collections.sort(sortedColumns);
        for (ColumnInspector col : sortedColumns) {
            fields.append(generateColumn(imports, col, trimStrings));
        }

        // build complete class definition
        StringBuilder model = new StringBuilder();
        if (!isNullOrEmpty(packageName)) {
            // package
            model.append("package " + packageName + ";");
            model.append(eol).append(eol);
        }

        // imports
        List<String> sortedImports = new ArrayList<String>(imports);
        Collections.sort(sortedImports);
        for (String imp : sortedImports) {
            model.append("import ").append(imp).append(';').append(eol);
        }
        model.append(eol);

        // @JQSchema
        if (annotateSchema && !isNullOrEmpty(schema)) {
            model.append('@').append(JQSchema.class.getSimpleName());
            model.append('(');
            AnnotationBuilder ap = new AnnotationBuilder();
            ap.addParameter("name", schema);
            model.append(ap);
            model.append(')').append(eol);
        }

        // @JQTable
        model.append('@').append(JQTable.class.getSimpleName());
        model.append('(');

        // JQTable annotation parameters
        AnnotationBuilder ap = new AnnotationBuilder();
        ap.addParameter("name", table);

        if (primaryKeys.size() > 1) {
            StringBuilder pk = new StringBuilder();
            for (String key : primaryKeys) {
                pk.append(key).append(' ');
            }
            pk.trimToSize();
            ap.addParameter("primaryKey", pk.toString());
        }

        // finish @JQTable annotation
        model.append(ap);
        model.append(')').append(eol);

        // @JQIndex
        ap = new AnnotationBuilder();
        generateIndexAnnotations(ap, "standard", IndexType.STANDARD);
        generateIndexAnnotations(ap, "unique", IndexType.UNIQUE);
        generateIndexAnnotations(ap, "hash", IndexType.HASH);
        generateIndexAnnotations(ap, "uniqueHash", IndexType.UNIQUE_HASH);
        if (ap.length() > 0) {
            model.append('@').append(JQIndex.class.getSimpleName());
            model.append('(');
            model.append(ap);
            model.append(')').append(eol);
        }

        // class declaration
        String clazzName = ModelUtils.createClassName(table);
        model.append(format("public class {0} '{'", clazzName)).append(eol);
        model.append(eol);

        // field declarations
        model.append(fields);

        // default constructor
        model.append("\t" + "public ").append(clazzName).append("() {").append(eol);
        model.append("\t}").append(eol);

        // end of class body
        model.append('}');
        model.trimToSize();
        return model.toString();
    }

    /**
     * Generates the specified index annotation.
     * @param ap
     */
    void generateIndexAnnotations(AnnotationBuilder ap, String parameter, IndexType type) {
        List<IndexInspector> list = getIndexes(type);
        if (list.size() == 0) {
            // no matching indexes
            return;
        }
        if (list.size() == 1) {
            ap.addParameter(parameter, list.get(0).getColumnsString());
        } else {
            List<String> parameters = Utils.newArrayList();
            for (IndexInspector index:list) {
                parameters.add(index.getColumnsString());
            }
            ap.addParameter(parameter, parameters);
        }

    }

    private List<IndexInspector> getIndexes(IndexType type) {
        List<IndexInspector> list = Utils.newArrayList();
        for (IndexInspector index:indexes.values()) {
            if (index.type.equals(type)) {
                list.add(index);
            }
        }
        return list;
    }

    private StatementBuilder generateColumn(Set<String> imports, ColumnInspector col,
            boolean trimStrings) {
        StatementBuilder sb = new StatementBuilder();
        Class<?> clazz = col.clazz;
        String column = ModelUtils.createFieldName(col.name.toLowerCase());
        sb.append('\t');
        if (clazz == null) {
            // unsupported type
            clazz = Object.class;
            sb.append("// unsupported type " + col.type);
        } else {
            // @JQColumn
            imports.add(clazz.getCanonicalName());
            sb.append('@').append(JQColumn.class.getSimpleName());

            // JQColumn annotation parameters
            AnnotationBuilder ap = new AnnotationBuilder();

            // JQColumn.name
            if (!col.name.equalsIgnoreCase(column)) {
                ap.addParameter("name", col.name);
            }

            // JQColumn.primaryKey
            // composite primary keys are annotated on the table
            if (col.isPrimaryKey && primaryKeys.size() == 1) {
                ap.addParameter("primaryKey=true");
            }

            // JQColumn.maxLength
            if ((clazz == String.class) && (col.size > 0)
                    && (col.size < Integer.MAX_VALUE)) {
                ap.addParameter("maxLength", col.size);

                // JQColumn.trimStrings
                if (trimStrings) {
                    ap.addParameter("trimString=true");
                }
            } else {
                // JQColumn.AutoIncrement
                if (col.isAutoIncrement) {
                    ap.addParameter("autoIncrement=true");
                }
            }

            // JQColumn.allowNull
            if (!col.allowNull) {
                ap.addParameter("allowNull=false");
            }

            // JQColumn.defaultValue
            if (!isNullOrEmpty(col.defaultValue)) {
                ap.addParameter("defaultValue=\"" + col.defaultValue + "\"");
            }

            // add leading and trailing ()
            if (ap.length() > 0) {
                ap.insert(0, '(');
                ap.append(')');
            }
            sb.append(ap);
        }
        sb.append(eol);

        // variable declaration
        sb.append("\t" + "public ");
        sb.append(clazz.getSimpleName());
        sb.append(' ');
        sb.append(column);
        sb.append(';');
        sb.append(eol).append(eol);
        return sb;
    }

    /**
     * Validates that a table definition (annotated, interface, or both) matches
     * the current state of the table and indexes in the database. Results are
     * returned as a list of validation remarks which includes recommendations,
     * warnings, and errors about the model. The caller may choose to have
     * validate throw an exception on any validation ERROR.
     *
     * @param <T>
     * @param def
     * @param throwError whether or not to throw an exception if an error was
     *            found
     * @return a list if validation remarks
     */
    <T> List<ValidationRemark> validate(TableDefinition<T> def,
            boolean throwError) {
        List<ValidationRemark> remarks = Utils.newArrayList();

        // model class definition validation
        if (!Modifier.isPublic(def.getModelClass().getModifiers())) {
            remarks.add(error(table, "SCHEMA",
                    format("Class {0} MUST BE PUBLIC!",
                            def.getModelClass().getCanonicalName())).throwError(throwError));
        }

        // Schema Validation
        if (!isNullOrEmpty(schema)) {
            if (isNullOrEmpty(def.schemaName)) {
                remarks.add(consider(table, "SCHEMA",
                        format("@{0}(name={1})",
                                JQSchema.class.getSimpleName(), schema)));
            } else if (!schema.equalsIgnoreCase(def.schemaName)) {
                remarks.add(error(table, "SCHEMA",
                        format("@{0}(name={1}) != {2}",
                                JQSchema.class.getSimpleName(), def.schemaName,
                                    schema)).throwError(throwError));
            }
        }

        // index validation
        for (IndexInspector index : indexes.values()) {
            validate(remarks, def, index, throwError);
        }

        // field column validation
        for (FieldDefinition fieldDef : def.getFields()) {
            validate(remarks, fieldDef, throwError);
        }
        return remarks;
    }

    /**
     * Validates an inspected index from the database against the IndexDefinition
     * within the TableDefinition.
     */
    private <T> void validate(List<ValidationRemark> remarks, TableDefinition<T> def,
            IndexInspector index, boolean throwError) {
        List<IndexDefinition> defIndexes = def.getIndexes(IndexType.STANDARD);
        List<IndexInspector> dbIndexes = getIndexes(IndexType.STANDARD);
        if (defIndexes.size() > dbIndexes.size()) {
            remarks.add(warn(table, IndexType.STANDARD.name(), "More model indexes  than database indexes"));
        } else if (defIndexes.size() < dbIndexes.size()) {
            remarks.add(warn(table, IndexType.STANDARD.name(), "Model class is missing indexes"));
        }
        // TODO complete index validation.
        // need to actually compare index types and columns within each index.
    }

    /**
     * Validates a column against the model's field definition.  Checks for
     * existence, supported type, type mapping, default value, defined lengths,
     * primary key, autoincrement.
     */
    private void validate(List<ValidationRemark> remarks, FieldDefinition fieldDef,
            boolean throwError) {
        // unknown field
        String field = forceUpperCase ?
                fieldDef.columnName.toUpperCase() : fieldDef.columnName;
        if (!columns.containsKey(field)) {
            // unknown column mapping
            remarks.add(error(table, fieldDef,
                    "Does not exist in database!").throwError(throwError));
            return;
        }
        ColumnInspector col = columns.get(field);
        Class<?> fieldClazz = fieldDef.field.getType();
        Class<?> jdbcClazz = ModelUtils.getClassType(col.type, dateClazz);

        // supported type check
        // JaQu maps to VARCHAR for unsupported types.
        if (fieldDef.dataType.equals("VARCHAR")
                && (fieldClazz != String.class)) {
                    remarks.add(error(table, fieldDef,
                    "JaQu does not currently implement support for "
                            + fieldClazz.getName()).throwError(throwError));
        }
        // number types
        if (!fieldClazz.equals(jdbcClazz)) {
            if (Number.class.isAssignableFrom(fieldClazz)) {
                remarks.add(warn(table, col,
                        format("Precision mismatch: ModelObject={0}, ColumnObject={1}",
                                fieldClazz.getSimpleName(), jdbcClazz.getSimpleName())));
            } else {
                if (!Date.class.isAssignableFrom(jdbcClazz)) {
                    remarks.add(warn(table, col,
                            format("Object Mismatch: ModelObject={0}, ColumnObject={1}",
                                    fieldClazz.getSimpleName(), jdbcClazz.getSimpleName())));
                }
            }
        }

        // string types
        if (fieldClazz == String.class) {
            if ((fieldDef.maxLength != col.size)
                    && (col.size < Integer.MAX_VALUE)) {
                remarks.add(warn(table, col,
                        format("{0}.maxLength={1}, ColumnMaxLength={2}",
                                JQColumn.class.getSimpleName(),
                                fieldDef.maxLength, col.size)));
            }
            if (fieldDef.maxLength > 0 && !fieldDef.trimString) {
                remarks.add(consider(table, col,
                        format("{0}.truncateToMaxLength=true"
                                + " will prevent RuntimeExceptions on"
                                + " INSERT or UPDATE, but will clip data!",
                                JQColumn.class.getSimpleName())));
            }
        }

        // numeric autoIncrement
        if (fieldDef.isAutoIncrement != col.isAutoIncrement) {
            remarks.add(warn(table, col, format("{0}.isAutoIncrement={1}"
                    + " while Column autoIncrement={2}",
                    JQColumn.class.getSimpleName(), fieldDef.isAutoIncrement,
                    col.isAutoIncrement)));
        }
        // last check
        // default value...
        if (!col.isAutoIncrement && !col.isPrimaryKey) {
            // check Model.defaultValue format
            if (!ModelUtils.isProperlyFormattedDefaultValue(fieldDef.defaultValue)) {
                remarks.add(error(table, col, format("{0}.defaultValue=\"{1}\""
                        + " is improperly formatted!",
                        JQColumn.class.getSimpleName(),
                        fieldDef.defaultValue)).throwError(throwError));
                // next field
                return;
            }
            // compare Model.defaultValue to Column.defaultValue
            if (isNullOrEmpty(fieldDef.defaultValue)
                    && !isNullOrEmpty(col.defaultValue)) {
                // Model.defaultValue is NULL, Column.defaultValue is NOT NULL
                remarks.add(warn(table, col, format("{0}.defaultValue=\"\""
                        + " while Column default=\"{1}\"",
                        JQColumn.class.getSimpleName(), col.defaultValue)));
            } else if (!isNullOrEmpty(fieldDef.defaultValue)
                    && isNullOrEmpty(col.defaultValue)) {
                // Column.defaultValue is NULL, Model.defaultValue is NOT NULL
                remarks.add(warn(table, col, format("{0}.defaultValue=\"{1}\""
                        + " while Column default=\"\"",
                        JQColumn.class.getSimpleName(), fieldDef.defaultValue)));
            } else if (!isNullOrEmpty(fieldDef.defaultValue)
                    && !isNullOrEmpty(col.defaultValue)) {
                if (!fieldDef.defaultValue.equals(col.defaultValue)) {
                    // Model.defaultValue != Column.defaultValue
                    remarks.add(warn(table, col, format("{0}.defaultValue=\"{1}\""
                            + " while Column default=\"{2}\"",
                            JQColumn.class.getSimpleName(), fieldDef.defaultValue,
                            col.defaultValue)));
                }
            }

            // sanity check Model.defaultValue literal value
            if (!ModelUtils.isValidDefaultValue(fieldDef.field.getType(),
                    fieldDef.defaultValue)) {
                remarks.add(error(table, col,
                        format("{0}.defaultValue=\"{1}\" is invalid!!",
                                JQColumn.class.getSimpleName(),
                                fieldDef.defaultValue)));
            }
        }
    }

    /**
     * Represents an index as it exists in the database.
     */
    private static class IndexInspector  {

        String name;
        IndexType type;
        private List<String> columns = new ArrayList<String>();

        public IndexInspector(ResultSet rs) throws SQLException {
            name = rs.getString("INDEX_NAME");

            // determine index type
            boolean hash = rs.getInt("TYPE") == DatabaseMetaData.tableIndexHashed;
            boolean unique = !rs.getBoolean("NON_UNIQUE");

            if (!hash && !unique) {
                type = IndexType.STANDARD;
            } else if (hash && unique) {
                type = IndexType.UNIQUE_HASH;
            } else if (unique) {
                type = IndexType.UNIQUE;
            } else if (hash) {
                type = IndexType.HASH;
            }
            columns.add(rs.getString("COLUMN_NAME"));
        }

        public void addColumn(ResultSet rs) throws SQLException {
            columns.add(rs.getString("COLUMN_NAME"));
        }

        public String getColumnsString() {
            StatementBuilder sb = new StatementBuilder();
            for (String col : columns) {
                sb.appendExceptFirst(", ");
                sb.append(col);
            }
            return sb.toString().trim();
        }
    }

    /**
     * Represents a column as it exists in the database.
     */
    static class ColumnInspector implements Comparable<ColumnInspector> {
        String name;
        String type;
        int size;
        boolean allowNull;
        Class<?> clazz;
        boolean isPrimaryKey;
        boolean isAutoIncrement;
        String defaultValue;

        public int compareTo(ColumnInspector o) {
            if (isPrimaryKey && o.isPrimaryKey) {
                // both primary sort by name
                return name.compareTo(o.name);
            } else if (isPrimaryKey && !o.isPrimaryKey) {
                // primary first
                return -1;
            } else if (!isPrimaryKey && o.isPrimaryKey) {
                // primary first
                return 1;
            } else {
                // neither primary, sort by name
                return name.compareTo(o.name);
            }
        }
    }

    /**
     * Convenience class based on StatementBuilder for creating the
     * annotation parameter list.
     */
    private static class AnnotationBuilder extends StatementBuilder {
        AnnotationBuilder() {
            super();
        }

        void addParameter(String parameter) {
            appendExceptFirst(", ");
            append(parameter);
        }

        <T> void addParameter(String parameter, T value) {
            appendExceptFirst(", ");
            append(parameter);
            append('=');
            if (value instanceof List) {
                append("{ ");
                List list = (List) value;
                StatementBuilder flat = new StatementBuilder();
                for (Object o:list) {
                    flat.appendExceptFirst(", ");
                    if (o instanceof String) {
                        flat.append('\"');
                    }
                    int todoEscape;
                    flat.append(o.toString().trim());
                    if (o instanceof String) {
                        flat.append('\"');
                    }
                }
                append(flat);
                append(" }");
            } else {
                if (value instanceof String) {
                    append('\"');
                }
                int todoEscape;
                append(value.toString().trim());
                if (value instanceof String) {
                    append('\"');
                }
            }
        }
    }
}