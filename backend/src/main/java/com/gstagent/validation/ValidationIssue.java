package com.gstagent.validation;

public record ValidationIssue(String code, Severity severity, String field, String message) {

    public enum Severity {
        /** Blocks auto-validation; a human must review the bill. */
        ERROR,
        /** Worth a look but does not block. */
        WARNING
    }

    public static ValidationIssue error(String code, String field, String message) {
        return new ValidationIssue(code, Severity.ERROR, field, message);
    }

    public static ValidationIssue warning(String code, String field, String message) {
        return new ValidationIssue(code, Severity.WARNING, field, message);
    }
}
