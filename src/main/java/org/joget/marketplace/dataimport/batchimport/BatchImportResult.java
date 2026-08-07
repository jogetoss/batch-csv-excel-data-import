package org.joget.marketplace.dataimport.batchimport;

public class BatchImportResult {

    private int inserted;
    private int updated;
    private int deleted;
    private int skipped;

    public int getInserted() {
        return inserted;
    }

    public int getUpdated() {
        return updated;
    }

    public int getDeleted() {
        return deleted;
    }

    public int getSkipped() {
        return skipped;
    }

    public void addInserted(int inserted) {
        this.inserted += inserted;
    }

    public void addUpdated(int updated) {
        this.updated += updated;
    }

    public void addDeleted(int deleted) {
        this.deleted += deleted;
    }

    public void addSkipped(int skipped) {
        this.skipped += skipped;
    }
}
