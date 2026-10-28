package com.gstagent.invoice;

import com.gstagent.category.ExpenseCategory;
import com.gstagent.validation.ValidationIssue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    public record InvoiceSummary(Long id, String fileName, InvoiceStatus status, String supplierName,
                                 String supplierGstin, String invoiceNumber, LocalDate invoiceDate,
                                 BigDecimal taxableValue, BigDecimal totalTax, BigDecimal totalAmount,
                                 ExpenseCategory category, int errorCount, int warningCount, Instant createdAt) {

        static InvoiceSummary of(Invoice i) {
            List<ValidationIssue> issues = i.getValidationIssues();
            int errors = (int) issues.stream().filter(x -> x.severity() == ValidationIssue.Severity.ERROR).count();
            return new InvoiceSummary(i.getId(), i.getFileName(), i.getStatus(), i.getSupplierName(),
                    i.getSupplierGstin(), i.getInvoiceNumber(), i.getInvoiceDate(), i.getTaxableValue(),
                    i.getStatus() == InvoiceStatus.PROCESSING ? null : i.totalTax(), i.getTotalAmount(),
                    i.getCategory(), errors, issues.size() - errors, i.getCreatedAt());
        }
    }

    public record LineItemDto(String description, String hsnSac, BigDecimal quantity, BigDecimal unitPrice,
                              BigDecimal taxableValue, BigDecimal gstRate, BigDecimal cgst, BigDecimal sgst,
                              BigDecimal igst) {

        static LineItemDto of(InvoiceLineItem l) {
            return new LineItemDto(l.getDescription(), l.getHsnSac(), l.getQuantity(), l.getUnitPrice(),
                    l.getTaxableValue(), l.getGstRate(), l.getCgst(), l.getSgst(), l.getIgst());
        }
    }

    public record InvoiceDetail(Long id, String fileName, String contentType, InvoiceStatus status,
                                String supplierName, String supplierGstin, String buyerName, String buyerGstin,
                                String invoiceNumber, LocalDate invoiceDate, String placeOfSupply,
                                SupplyType supplyType, BigDecimal taxableValue, BigDecimal cgst, BigDecimal sgst,
                                BigDecimal igst, BigDecimal cess, BigDecimal totalAmount, ExpenseCategory category,
                                boolean itcEligible, BigDecimal aiConfidence, String aiNotes, Long duplicateOfId,
                                List<ValidationIssue> validationIssues, String errorMessage,
                                List<LineItemDto> lineItems, String uploadedBy, Instant createdAt,
                                Instant updatedAt) {

        static InvoiceDetail of(Invoice i) {
            return new InvoiceDetail(i.getId(), i.getFileName(), i.getContentType(), i.getStatus(),
                    i.getSupplierName(), i.getSupplierGstin(), i.getBuyerName(), i.getBuyerGstin(),
                    i.getInvoiceNumber(), i.getInvoiceDate(), i.getPlaceOfSupply(), i.getSupplyType(),
                    i.getTaxableValue(), i.getCgst(), i.getSgst(), i.getIgst(), i.getCess(), i.getTotalAmount(),
                    i.getCategory(), i.getCategory() == null || i.getCategory().isItcEligible(),
                    i.getAiConfidence(), i.getAiNotes(), i.getDuplicateOfId(), i.getValidationIssues(),
                    i.getErrorMessage(), i.getLineItems().stream().map(LineItemDto::of).toList(),
                    i.getUploadedBy().getFullName(), i.getCreatedAt(), i.getUpdatedAt());
        }
    }

    /** Manual corrections from the review screen. All fields are replaced. */
    public record InvoiceUpdateRequest(
            @Size(max = 255) String supplierName,
            @Size(max = 20) String supplierGstin,
            @Size(max = 255) String buyerName,
            @Size(max = 20) String buyerGstin,
            @Size(max = 64) String invoiceNumber,
            LocalDate invoiceDate,
            @Size(max = 2) String placeOfSupply,
            @DecimalMin("0") BigDecimal taxableValue,
            @DecimalMin("0") BigDecimal cgst,
            @DecimalMin("0") BigDecimal sgst,
            @DecimalMin("0") BigDecimal igst,
            @DecimalMin("0") BigDecimal cess,
            @DecimalMin("0") BigDecimal totalAmount,
            ExpenseCategory category,
            @Valid List<LineItemDto> lineItems) {
    }
}
