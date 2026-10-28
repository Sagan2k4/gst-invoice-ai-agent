package com.gstagent.report;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Monthly GST purchase summary: the numbers an accountant needs to reconcile against GSTR-2B and file GSTR-3B. */
public record GstSummary(
        String businessName,
        String businessGstin,
        YearMonth period,
        boolean approvedOnly,
        int invoiceCount,
        long pendingReviewCount,
        Totals totals,
        BigDecimal eligibleItc,
        BigDecimal blockedItc,
        List<CategoryRow> byCategory,
        List<RateRow> byRate,
        List<HsnRow> byHsn,
        List<SupplierRow> bySupplier,
        List<InvoiceRow> invoices) implements Serializable {

    public record Totals(BigDecimal taxableValue, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal cess,
                         BigDecimal totalTax, BigDecimal totalAmount) implements Serializable {
    }

    public record CategoryRow(String category, String label, boolean itcEligible, int invoiceCount,
                              BigDecimal taxableValue, BigDecimal totalTax) implements Serializable {
    }

    public record RateRow(BigDecimal gstRate, BigDecimal taxableValue, BigDecimal cgst, BigDecimal sgst,
                          BigDecimal igst) implements Serializable {
    }

    public record HsnRow(String hsnSac, String description, BigDecimal quantity, BigDecimal taxableValue,
                         BigDecimal gstRate, BigDecimal cgst, BigDecimal sgst, BigDecimal igst) implements Serializable {
    }

    public record SupplierRow(String supplierGstin, String supplierName, int invoiceCount, BigDecimal taxableValue,
                              BigDecimal totalTax, BigDecimal totalAmount) implements Serializable {
    }

    public record InvoiceRow(Long id, LocalDate invoiceDate, String invoiceNumber, String supplierName,
                             String supplierGstin, String supplyType, String category, String status,
                             BigDecimal taxableValue, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
                             BigDecimal cess, BigDecimal totalAmount) implements Serializable {
    }
}
