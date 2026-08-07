package org.joget.marketplace.dataimport.batchimport;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDaoImpl;

public class BatchImportDaoImpl implements BatchImportDao {

    private static final String ID = "id";
    private static final String DATE_CREATED = "dateCreated";
    private static final String DATE_MODIFIED = "dateModified";
    private static final String CREATED_BY = "createdBy";
    private static final String CREATED_BY_NAME = "createdByName";
    private static final String MODIFIED_BY = "modifiedBy";
    private static final String MODIFIED_BY_NAME = "modifiedByName";
    private static final int EXISTENCE_LOOKUP_CHUNK_SIZE = 900;

    @Override
    public void prepareTable(String tableName, Set<String> fields) throws SQLException {
        DataSource dataSource = getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            String product = getProductName(connection);
            String quote = getIdentifierQuote(connection);
            if (!tableExists(connection, tableName)) {
                createTable(connection, tableName, fields, product, quote);
                return;
            }

            Set<String> existingColumns = getExistingColumns(connection, tableName);
            for (String field : fields) {
                String column = fieldColumn(field);
                if (!existingColumns.contains(column.toLowerCase(Locale.ENGLISH))) {
                    addTextColumn(connection, tableName, column, product, quote);
                }
            }
        }
    }

    @Override
    public BatchImportResult saveBatch(String tableName, Set<String> fields, List<ImportRow> rows, String mode, String username, String userFullName) throws SQLException {
        BatchImportResult result = new BatchImportResult();
        if (rows == null || rows.isEmpty()) {
            return result;
        }

        DataSource dataSource = getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Set<String> existingIds = findExistingIds(connection, tableName, rows);
                if (BatchImportMenu.MODE_DELETE.equals(mode)) {
                    result = deleteBatch(connection, tableName, rows, existingIds);
                } else {
                    result = upsertBatch(connection, tableName, fields, rows, existingIds, mode, username, userFullName);
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
        return result;
    }

    protected BatchImportResult upsertBatch(Connection connection, String tableName, Set<String> fields, List<ImportRow> rows, Set<String> existingIds, String mode, String username, String userFullName) throws SQLException {
        BatchImportResult result = new BatchImportResult();
        List<ImportRow> inserts = new ArrayList<ImportRow>();
        List<ImportRow> updates = new ArrayList<ImportRow>();

        for (ImportRow row : rows) {
            if (row.getId() == null || row.getId().trim().isEmpty()) {
                row.setId(UUID.randomUUID().toString());
            }

            boolean exists = existingIds.contains(row.getId());
            if (exists && BatchImportMenu.MODE_NEW.equals(mode)) {
                result.addSkipped(1);
            } else if (exists) {
                updates.add(row);
            } else {
                inserts.add(row);
            }
        }

        result.addInserted(executeInsertBatch(connection, tableName, fields, inserts, username, userFullName));
        result.addUpdated(executeUpdateBatch(connection, tableName, fields, updates, username, userFullName));
        return result;
    }

    protected BatchImportResult deleteBatch(Connection connection, String tableName, List<ImportRow> rows, Set<String> existingIds) throws SQLException {
        BatchImportResult result = new BatchImportResult();
        String sql = "DELETE FROM " + quoteIdentifier(connection, tableName) + " WHERE " + quoteIdentifier(connection, ID) + " = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int queued = 0;
            for (ImportRow row : rows) {
                if (row.getId() == null || row.getId().trim().isEmpty() || !existingIds.contains(row.getId())) {
                    result.addSkipped(1);
                    continue;
                }
                statement.setString(1, row.getId());
                statement.addBatch();
                queued++;
            }
            if (queued > 0) {
                statement.executeBatch();
                result.addDeleted(queued);
            }
        }
        return result;
    }

    protected int executeInsertBatch(Connection connection, String tableName, Set<String> fields, List<ImportRow> rows, String username, String userFullName) throws SQLException {
        if (rows.isEmpty()) {
            return 0;
        }

        List<String> columns = new ArrayList<String>();
        columns.add(ID);
        columns.add(DATE_CREATED);
        columns.add(DATE_MODIFIED);
        columns.add(CREATED_BY);
        columns.add(CREATED_BY_NAME);
        columns.add(MODIFIED_BY);
        columns.add(MODIFIED_BY_NAME);
        for (String field : fields) {
            columns.add(fieldColumn(field));
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(quoteIdentifier(connection, tableName)).append(" (");
        appendIdentifierList(connection, sql, columns);
        sql.append(") VALUES (");
        appendPlaceholders(sql, columns.size());
        sql.append(")");

        Timestamp now = new Timestamp(System.currentTimeMillis());
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (ImportRow row : rows) {
                int index = 1;
                statement.setString(index++, row.getId());
                statement.setTimestamp(index++, now);
                statement.setTimestamp(index++, now);
                statement.setString(index++, username);
                statement.setString(index++, userFullName);
                statement.setString(index++, username);
                statement.setString(index++, userFullName);
                for (String field : fields) {
                    statement.setString(index++, row.getValues().get(field));
                }
                statement.addBatch();
            }
            statement.executeBatch();
            return rows.size();
        }
    }

    protected int executeUpdateBatch(Connection connection, String tableName, Set<String> fields, List<ImportRow> rows, String username, String userFullName) throws SQLException {
        if (rows.isEmpty()) {
            return 0;
        }

        StringBuilder sql = new StringBuilder("UPDATE ");
        sql.append(quoteIdentifier(connection, tableName)).append(" SET ");
        sql.append(quoteIdentifier(connection, DATE_MODIFIED)).append(" = ?, ");
        sql.append(quoteIdentifier(connection, MODIFIED_BY)).append(" = ?, ");
        sql.append(quoteIdentifier(connection, MODIFIED_BY_NAME)).append(" = ?");
        for (String field : fields) {
            sql.append(", ").append(quoteIdentifier(connection, fieldColumn(field))).append(" = ?");
        }
        sql.append(" WHERE ").append(quoteIdentifier(connection, ID)).append(" = ?");

        Timestamp now = new Timestamp(System.currentTimeMillis());
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (ImportRow row : rows) {
                int index = 1;
                statement.setTimestamp(index++, now);
                statement.setString(index++, username);
                statement.setString(index++, userFullName);
                for (String field : fields) {
                    statement.setString(index++, row.getValues().get(field));
                }
                statement.setString(index++, row.getId());
                statement.addBatch();
            }
            statement.executeBatch();
            return rows.size();
        }
    }

    protected Set<String> findExistingIds(Connection connection, String tableName, List<ImportRow> rows) throws SQLException {
        Set<String> ids = new HashSet<String>();
        List<String> lookupIds = new ArrayList<String>();
        for (ImportRow row : rows) {
            if (row.getId() != null && !row.getId().trim().isEmpty()) {
                lookupIds.add(row.getId());
            }
        }

        for (int start = 0; start < lookupIds.size(); start += EXISTENCE_LOOKUP_CHUNK_SIZE) {
            int end = Math.min(start + EXISTENCE_LOOKUP_CHUNK_SIZE, lookupIds.size());
            List<String> chunk = lookupIds.subList(start, end);
            StringBuilder sql = new StringBuilder("SELECT ");
            sql.append(quoteIdentifier(connection, ID)).append(" FROM ").append(quoteIdentifier(connection, tableName));
            sql.append(" WHERE ").append(quoteIdentifier(connection, ID)).append(" IN (");
            appendPlaceholders(sql, chunk.size());
            sql.append(")");

            try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
                int index = 1;
                for (String id : chunk) {
                    statement.setString(index++, id);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        ids.add(resultSet.getString(1));
                    }
                }
            }
        }
        return ids;
    }

    protected void createTable(Connection connection, String tableName, Set<String> fields, String product, String quote) throws SQLException {
        List<String> definitions = new ArrayList<String>();
        definitions.add(q(quote, ID) + " VARCHAR(255) NOT NULL");
        definitions.add(q(quote, DATE_CREATED) + " TIMESTAMP");
        definitions.add(q(quote, DATE_MODIFIED) + " TIMESTAMP");
        definitions.add(q(quote, CREATED_BY) + " VARCHAR(255)");
        definitions.add(q(quote, CREATED_BY_NAME) + " VARCHAR(255)");
        definitions.add(q(quote, MODIFIED_BY) + " VARCHAR(255)");
        definitions.add(q(quote, MODIFIED_BY_NAME) + " VARCHAR(255)");
        for (String field : fields) {
            definitions.add(q(quote, fieldColumn(field)) + " " + textType(product));
        }
        definitions.add("PRIMARY KEY (" + q(quote, ID) + ")");

        String sql = "CREATE TABLE " + q(quote, tableName) + " (" + join(definitions, ", ") + ")";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    protected void addTextColumn(Connection connection, String tableName, String column, String product, String quote) throws SQLException {
        String sql = "ALTER TABLE " + q(quote, tableName) + " ADD " + q(quote, column) + " " + textType(product);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    protected boolean tableExists(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName, null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName.toUpperCase(Locale.ENGLISH), null)) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(connection.getCatalog(), null, tableName.toLowerCase(Locale.ENGLISH), null)) {
            return resultSet.next();
        }
    }

    protected Set<String> getExistingColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new HashSet<String>();
        readColumns(connection, tableName, columns);
        if (columns.isEmpty()) {
            readColumns(connection, tableName.toUpperCase(Locale.ENGLISH), columns);
        }
        if (columns.isEmpty()) {
            readColumns(connection, tableName.toLowerCase(Locale.ENGLISH), columns);
        }
        return columns;
    }

    protected void readColumns(Connection connection, String tableName, Set<String> columns) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableName, null)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ENGLISH));
            }
        }
    }

    protected String quoteIdentifier(Connection connection, String identifier) throws SQLException {
        return q(getIdentifierQuote(connection), identifier);
    }

    protected String getIdentifierQuote(Connection connection) throws SQLException {
        String quote = connection.getMetaData().getIdentifierQuoteString();
        if (quote == null || quote.trim().isEmpty()) {
            return "";
        }
        return quote.trim();
    }

    protected String q(String quote, String identifier) {
        return quote + identifier.replace(quote, quote + quote) + quote;
    }

    protected String fieldColumn(String field) {
        return FormDataDaoImpl.FORM_PREFIX_COLUMN + field;
    }

    protected String getProductName(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        return product == null ? "" : product.toLowerCase(Locale.ENGLISH);
    }

    protected String textType(String product) {
        if (product.contains("mysql") || product.contains("mariadb")) {
            return "LONGTEXT";
        } else if (product.contains("postgres")) {
            return "TEXT";
        } else if (product.contains("sql server") || product.contains("microsoft")) {
            return "NVARCHAR(MAX)";
        } else if (product.contains("oracle")) {
            return "CLOB";
        }
        return "CLOB";
    }

    protected DataSource getDataSource() {
        return (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
    }

    protected void appendIdentifierList(Connection connection, StringBuilder sql, List<String> columns) throws SQLException {
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(quoteIdentifier(connection, columns.get(i)));
        }
    }

    protected void appendPlaceholders(StringBuilder sql, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }
    }

    protected int countAffected(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result >= 0) {
                count += result;
            } else if (result == Statement.SUCCESS_NO_INFO) {
                count++;
            }
        }
        return count;
    }

    protected String join(List<String> values, String separator) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(separator);
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }
}
