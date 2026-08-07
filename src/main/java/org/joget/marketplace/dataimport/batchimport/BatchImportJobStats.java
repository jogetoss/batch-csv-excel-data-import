package org.joget.marketplace.dataimport.batchimport;

public class BatchImportJobStats {

    private long totalRows;
    private long processedRows;
    private long insertedRows;
    private long updatedRows;
    private long deletedRows;
    private long skippedRows;
    private long errorRows;
    private long batchCount;

    public long getTotalRows() {
        return totalRows;
    }

    public long getProcessedRows() {
        return processedRows;
    }

    public long getInsertedRows() {
        return insertedRows;
    }

    public long getUpdatedRows() {
        return updatedRows;
    }

    public long getDeletedRows() {
        return deletedRows;
    }

    public long getSkippedRows() {
        return skippedRows;
    }

    public long getErrorRows() {
        return errorRows;
    }

    public long getBatchCount() {
        return batchCount;
    }

    public void addBatch(long rows, BatchImportResult result) {
        totalRows += rows;
        processedRows += rows;
        batchCount++;
        if (result != null) {
            insertedRows += result.getInserted();
            updatedRows += result.getUpdated();
            deletedRows += result.getDeleted();
            skippedRows += result.getSkipped();
        }
    }

    public void addErrorRows(long errorRows) {
        this.errorRows += errorRows;
    }
}
