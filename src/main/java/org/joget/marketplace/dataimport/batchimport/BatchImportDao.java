package org.joget.marketplace.dataimport.batchimport;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public interface BatchImportDao {

    void prepareTable(String tableName, Set<String> fields) throws SQLException;

    /**
     * @param deleteRules only used in DELETE mode: an existing record is deleted only if it satisfies
     *                    every rule. Records that fail are skipped. May be null or empty (no restriction).
     */
    BatchImportResult saveBatch(String tableName, Set<String> fields, List<ImportRow> rows, String mode, String username, String userFullName, List<DeleteRule> deleteRules) throws SQLException;
}
