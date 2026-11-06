package org.joget.marketplace.dataimport.batchimport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A condition that an existing record must satisfy before the batch import is
 * allowed to delete it, e.g. Status != Approved.
 *
 * Comparison is case-insensitive and ignores leading/trailing whitespace.
 */
public class DeleteRule {

    public static final String EQUALS = "equals";
    public static final String NOT_EQUALS = "notEquals";
    public static final String IN = "in";
    public static final String NOT_IN = "notIn";
    public static final String EMPTY = "empty";
    public static final String NOT_EMPTY = "notEmpty";

    private final String field;
    private final String operator;
    private final String value;

    public DeleteRule(String field, String operator, String value) {
        this.field = field == null ? "" : field.trim();
        this.operator = operator == null ? "" : operator.trim();
        this.value = value == null ? "" : value;
    }

    public String getField() {
        return field;
    }

    public String getOperator() {
        return operator;
    }

    public String getValue() {
        return value;
    }

    /**
     * @param actual the current value of the field on the record to be deleted (null = empty)
     * @return true if the record passes this rule, i.e. it may be deleted
     */
    public boolean isSatisfiedBy(String actual) {
        String current = normalize(actual);
        String expected = normalize(value);

        if (EQUALS.equals(operator)) {
            return current.equals(expected);
        } else if (NOT_EQUALS.equals(operator)) {
            return !current.equals(expected);
        } else if (IN.equals(operator)) {
            return inList(current);
        } else if (NOT_IN.equals(operator)) {
            return !inList(current);
        } else if (EMPTY.equals(operator)) {
            return current.isEmpty();
        } else if (NOT_EMPTY.equals(operator)) {
            return !current.isEmpty();
        }
        // Unknown operator: fail safe by refusing the delete.
        return false;
    }

    /**
     * Builds the rule list from the "deleteRules" grid property. Incomplete rows are ignored.
     */
    public static List<DeleteRule> fromGrid(Object gridValue) {
        List<DeleteRule> rules = new ArrayList<DeleteRule>();
        if (!(gridValue instanceof Object[])) {
            return rules;
        }
        for (Object o : (Object[]) gridValue) {
            if (!(o instanceof java.util.Map)) {
                continue;
            }
            java.util.Map row = (java.util.Map) o;
            String field = row.get("field") == null ? "" : row.get("field").toString().trim();
            String operator = row.get("operator") == null ? "" : row.get("operator").toString().trim();
            String value = row.get("value") == null ? "" : row.get("value").toString();
            if (field.isEmpty() || operator.isEmpty()) {
                continue;
            }
            rules.add(new DeleteRule(field, operator, value));
        }
        return rules;
    }

    private boolean inList(String current) {
        for (String item : value.split(",")) {
            if (current.equals(normalize(item))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ENGLISH);
    }
}
