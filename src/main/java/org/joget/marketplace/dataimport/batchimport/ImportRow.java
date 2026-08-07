package org.joget.marketplace.dataimport.batchimport;

import java.util.LinkedHashMap;
import java.util.Map;

public class ImportRow {

    private final int rowNumber;
    private String id;
    private final Map<String, String> values = new LinkedHashMap<String, String>();

    public ImportRow(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, String> getValues() {
        return values;
    }

    public void put(String field, String value) {
        values.put(field, value);
    }
}
