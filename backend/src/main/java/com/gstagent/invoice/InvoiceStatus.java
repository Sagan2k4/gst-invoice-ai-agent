package com.gstagent.invoice;

public enum InvoiceStatus {
    /** Uploaded; the agent is extracting and validating. */
    PROCESSING,
    /** Extracted but has validation errors a human must resolve. */
    NEEDS_REVIEW,
    /** Extracted and passed every blocking check. */
    VALIDATED,
    /** Confirmed by an accountant/admin; included in GST summaries. */
    APPROVED,
    /** Matches a bill that was already recorded. */
    DUPLICATE,
    /** Extraction failed (unreadable file, AI error). */
    FAILED
}
