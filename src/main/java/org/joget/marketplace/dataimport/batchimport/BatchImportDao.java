package org.joget.marketplace.dataimport.batchimport;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public interface BatchImportDao {

    void prepareTable(String tableName, Set<String> fields) throws SQLException;

    BatchImportResult saveBatch(String tableName, Set<String> fields, List<ImportRow> rows, String mode, String username, String userFullName) throws SQLException;
}
