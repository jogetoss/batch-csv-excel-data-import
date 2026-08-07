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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.joget.apps.app.service.AppUtil;

public class BatchImportJobHelper {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private static final String TABLE_NAME = "batch_import_job";
    private static final int MAX_VARCHAR_LENGTH = 255;
    private volatile boolean tableReady = false;

    public String createQueuedJob(String fileName, String fileType, String mode, String targetTableName, int batchSize, int rowStart, String username, String userFullName) throws SQLException {
        ensureTable();
        String jobId = UUID.randomUUID().toString();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        String sql = "INSERT INTO " + TABLE_NAME
                + " (id, status, file_name, file_type, import_mode, target_table, batch_size, row_start, username, user_full_name,"
                + " created_at, updated_at, total_rows, processed_rows, inserted_rows, updated_rows, deleted_rows, skipped_rows, error_rows, batch_count)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, 0, 0, 0, 0)";

        try (Connection connection = getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, jobId);
            statement.setString(index++, STATUS_QUEUED);
            statement.setString(index++, limit(fileName));
            statement.setString(index++, limit(fileType));
            statement.setString(index++, limit(mode));
            statement.setString(index++, limit(targetTableName));
            statement.setInt(index++, batchSize);
            statement.setInt(index++, rowStart);
            statement.setString(index++, limit(username));
            statement.setString(index++, limit(userFullName));
            statement.setTimestamp(index++, now);
            statement.setTimestamp(index++, now);
            statement.executeUpdate();
        }
        return jobId;
    }

    public void markRunning(String jobId, long startMillis) throws SQLException {
        ensureTable();
        String sql = "UPDATE " + TABLE_NAME + " SET status = ?, started_at = ?, updated_at = ? WHERE id = ?";
        Timestamp started = new Timestamp(startMillis);
        try (Connection connection = getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, STATUS_RUNNING);
            statement.setTimestamp(2, started);
            statement.setTimestamp(3, started);
            statement.setString(4, jobId);
            statement.executeUpdate();
        }
    }

    public void updateProgress(String jobId, BatchImportJobStats stats) throws SQLException {
        ensureTable();
        String sql = "UPDATE " + TABLE_NAME
                + " SET status = ?, updated_at = ?, total_rows = ?, processed_rows = ?, inserted_rows = ?, updated_rows = ?,"
                + " deleted_rows = ?, skipped_rows = ?, error_rows = ?, batch_count = ? WHERE id = ?";
        try (Connection connection = getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, STATUS_RUNNING);
            statement.setTimestamp(index++, new Timestamp(System.currentTimeMillis()));
            setLong(statement, index++, stats.getTotalRows());
            setLong(statement, index++, stats.getProcessedRows());
            setLong(statement, index++, stats.getInsertedRows());
            setLong(statement, index++, stats.getUpdatedRows());
            setLong(statement, index++, stats.getDeletedRows());
            setLong(statement, index++, stats.getSkippedRows());
            setLong(statement, index++, stats.getErrorRows());
            setLong(statement, index++, stats.getBatchCount());
            statement.setString(index++, jobId);
            statement.executeUpdate();
        }
    }

    public void markCompleted(String jobId, long endMillis, long durationMs, BatchImportJobStats stats, String summaryMessage) throws SQLException {
        markFinished(jobId, STATUS_COMPLETED, endMillis, durationMs, stats, null, summaryMessage);
    }

    public void markFailed(String jobId, long endMillis, long durationMs, BatchImportJobStats stats, String errorMessage, String summaryMessage) throws SQLException {
        if (stats != null) {
            stats.addErrorRows(1);
        }
        markFinished(jobId, STATUS_FAILED, endMillis, durationMs, stats, errorMessage, summaryMessage);
    }

    public Map<String, Object> loadJob(String jobId) throws SQLException {
        ensureTable();
        String sql = "SELECT id, status, file_name, file_type, import_mode, target_table, batch_size, row_start,"
                + " username, user_full_name, created_at, started_at, completed_at, updated_at, duration_ms,"
                + " total_rows, processed_rows, inserted_rows, updated_rows, deleted_rows, skipped_rows, error_rows,"
                + " batch_count, error_message, summary_message FROM " + TABLE_NAME + " WHERE id = ?";
        try (Connection connection = getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, jobId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Map<String, Object> job = new LinkedHashMap<String, Object>();
                    job.put("id", resultSet.getString("id"));
                    job.put("status", resultSet.getString("status"));
                    job.put("fileName", resultSet.getString("file_name"));
                    job.put("fileType", resultSet.getString("file_type"));
                    job.put("mode", resultSet.getString("import_mode"));
                    job.put("targetTable", resultSet.getString("target_table"));
                    job.put("batchSize", resultSet.getObject("batch_size"));
                    job.put("rowStart", resultSet.getObject("row_start"));
                    job.put("username", resultSet.getString("username"));
                    job.put("userFullName", resultSet.getString("user_full_name"));
                    job.put("createdAt", resultSet.getTimestamp("created_at"));
                    job.put("startedAt", resultSet.getTimestamp("started_at"));
                    job.put("completedAt", resultSet.getTimestamp("completed_at"));
                    job.put("updatedAt", resultSet.getTimestamp("updated_at"));
                    job.put("durationMs", resultSet.getObject("duration_ms"));
                    job.put("totalRows", resultSet.getObject("total_rows"));
                    job.put("processedRows", resultSet.getObject("processed_rows"));
                    job.put("insertedRows", resultSet.getObject("inserted_rows"));
                    job.put("updatedRows", resultSet.getObject("updated_rows"));
                    job.put("deletedRows", resultSet.getObject("deleted_rows"));
                    job.put("skippedRows", resultSet.getObject("skipped_rows"));
                    job.put("errorRows", resultSet.getObject("error_rows"));
                    job.put("batchCount", resultSet.getObject("batch_count"));
                    job.put("errorMessage", resultSet.getString("error_message"));
                    job.put("summaryMessage", resultSet.getString("summary_message"));
                    return job;
                }
            }
        }
        return null;
    }

    protected void markFinished(String jobId, String status, long endMillis, long durationMs, BatchImportJobStats stats, String errorMessage, String summaryMessage) throws SQLException {
        ensureTable();
        String sql = "UPDATE " + TABLE_NAME
                + " SET status = ?, completed_at = ?, updated_at = ?, duration_ms = ?, total_rows = ?, processed_rows = ?,"
                + " inserted_rows = ?, updated_rows = ?, deleted_rows = ?, skipped_rows = ?, error_rows = ?, batch_count = ?,"
                + " error_message = ?, summary_message = ? WHERE id = ?";
        Timestamp completed = new Timestamp(endMillis);
        try (Connection connection = getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, status);
            statement.setTimestamp(index++, completed);
            statement.setTimestamp(index++, completed);
            setLong(statement, index++, durationMs);
            setLong(statement, index++, stats == null ? 0 : stats.getTotalRows());
            setLong(statement, index++, stats == null ? 0 : stats.getProcessedRows());
            setLong(statement, index++, stats == null ? 0 : stats.getInsertedRows());
            setLong(statement, index++, stats == null ? 0 : stats.getUpdatedRows());
            setLong(statement, index++, stats == null ? 0 : stats.getDeletedRows());
            setLong(statement, index++, stats == null ? 0 : stats.getSkippedRows());
            setLong(statement, index++, stats == null ? 0 : stats.getErrorRows());
            setLong(statement, index++, stats == null ? 0 : stats.getBatchCount());
            statement.setString(index++, limitText(errorMessage));
            statement.setString(index++, limitText(summaryMessage));
            statement.setString(index++, jobId);
            statement.executeUpdate();
        }
    }

    protected synchronized void ensureTable() throws SQLException {
        if (tableReady) {
            return;
        }

        try (Connection connection = getDataSource().getConnection()) {
            String product = getProductName(connection);
            String quote = getIdentifierQuote(connection);
            if (!tableExists(connection, TABLE_NAME)) {
                createTable(connection, product, quote);
            } else {
                addMissingColumns(connection, product, quote);
            }
            tableReady = true;
        }
    }

    protected void createTable(Connection connection, String product, String quote) throws SQLException {
        List<String> definitions = new ArrayList<String>();
        definitions.add(q(quote, "id") + " VARCHAR(255) NOT NULL");
        definitions.add(q(quote, "status") + " VARCHAR(30)");
        definitions.add(q(quote, "file_name") + " VARCHAR(255)");
        definitions.add(q(quote, "file_type") + " VARCHAR(30)");
        definitions.add(q(quote, "import_mode") + " VARCHAR(50)");
        definitions.add(q(quote, "target_table") + " VARCHAR(255)");
        definitions.add(q(quote, "batch_size") + " INTEGER");
        definitions.add(q(quote, "row_start") + " INTEGER");
        definitions.add(q(quote, "username") + " VARCHAR(255)");
        definitions.add(q(quote, "user_full_name") + " VARCHAR(255)");
        definitions.add(q(quote, "created_at") + " TIMESTAMP");
        definitions.add(q(quote, "started_at") + " TIMESTAMP");
        definitions.add(q(quote, "completed_at") + " TIMESTAMP");
        definitions.add(q(quote, "updated_at") + " TIMESTAMP");
        definitions.add(q(quote, "duration_ms") + " " + longType(product));
        definitions.add(q(quote, "total_rows") + " " + longType(product));
        definitions.add(q(quote, "processed_rows") + " " + longType(product));
        definitions.add(q(quote, "inserted_rows") + " " + longType(product));
        definitions.add(q(quote, "updated_rows") + " " + longType(product));
        definitions.add(q(quote, "deleted_rows") + " " + longType(product));
        definitions.add(q(quote, "skipped_rows") + " " + longType(product));
        definitions.add(q(quote, "error_rows") + " " + longType(product));
        definitions.add(q(quote, "batch_count") + " " + longType(product));
        definitions.add(q(quote, "error_message") + " " + textType(product));
        definitions.add(q(quote, "summary_message") + " " + textType(product));
        definitions.add("PRIMARY KEY (" + q(quote, "id") + ")");

        String sql = "CREATE TABLE " + q(quote, TABLE_NAME) + " (" + join(definitions, ", ") + ")";
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    protected void addMissingColumns(Connection connection, String product, String quote) throws SQLException {
        Set<String> columns = getExistingColumns(connection, TABLE_NAME);
        addColumnIfMissing(connection, quote, columns, "summary_message", textType(product));
        addColumnIfMissing(connection, quote, columns, "error_message", textType(product));
        addColumnIfMissing(connection, quote, columns, "batch_count", longType(product));
    }

    protected void addColumnIfMissing(Connection connection, String quote, Set<String> columns, String column, String type) throws SQLException {
        if (!columns.contains(column.toLowerCase(Locale.ENGLISH))) {
            String sql = "ALTER TABLE " + q(quote, TABLE_NAME) + " ADD " + q(quote, column) + " " + type;
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
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

    protected void setLong(PreparedStatement statement, int index, long value) throws SQLException {
        statement.setLong(index, value);
    }

    protected String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_VARCHAR_LENGTH ? value.substring(0, MAX_VARCHAR_LENGTH) : value;
    }

    protected String limitText(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 4000 ? value.substring(0, 4000) : value;
    }

    protected String getProductName(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        return product == null ? "" : product.toLowerCase(Locale.ENGLISH);
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

    protected String longType(String product) {
        if (product.contains("oracle")) {
            return "NUMBER(19)";
        }
        return "BIGINT";
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

    protected DataSource getDataSource() {
        return (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
    }
}
