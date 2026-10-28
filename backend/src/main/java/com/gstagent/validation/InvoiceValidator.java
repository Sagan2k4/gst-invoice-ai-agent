package com.gstagent.validation;

import com.gstagent.config.AppProperties;
import com.gstagent.invoice.Invoice;
import com.gstagent.invoice.InvoiceLineItem;
import com.gstagent.invoice.SupplyType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic GST compliance checks run on every extraction. The LLM reads the bill;
 * this class decides whether what it read is internally consistent and GST-valid.
 */
@Component
public class InvoiceValidator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Notified GST rates, including the pre- and post-September-2025 slabs and special rates. */
    private static final Set<BigDecimal> KNOWN_RATES = Set.of(
            "0", "0.1", "0.25", "1", "1.5", "3", "5", "6", "7.5", "12", "18", "28", "40").stream()
            .map(BigDecimal::new).map(BigDecimal::stripTrailingZeros)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Pattern HSN = Pattern.compile("^(\\d{4}|\\d{6}|\\d{8})$");
    private static final Pattern SAC = Pattern.compile("^99\\d{4}$");

    private final BigDecimal tolerance;

    public InvoiceValidator(AppProperties props) {
        this.tolerance = props.validation().amountTolerance();
    }

    public List<ValidationIssue> validate(Invoice inv) {
        List<ValidationIssue> issues = new ArrayList<>();
        checkParties(inv, issues);
        checkHeader(inv, issues);
        checkTaxType(inv, issues);
        checkTotals(inv, issues);
        checkLineItems(inv, issues);
        return issues;
    }

    private void checkParties(Invoice inv, List<ValidationIssue> issues) {
        String supplier = inv.getSupplierGstin();
        if (supplier == null) {
            issues.add(ValidationIssue.error("SUPPLIER_GSTIN_MISSING", "supplierGstin",
                    "Supplier GSTIN not found — input tax credit cannot be claimed without it"));
        } else {
            gstinIssue(supplier, "supplierGstin", "Supplier", issues);
        }

        String buyer = inv.getBuyerGstin();
        if (buyer != null) {
            gstinIssue(buyer, "buyerGstin", "Buyer", issues);
            String ownGstin = inv.getBusiness().getGstin();
            if (ownGstin != null && !ownGstin.equals(buyer)) {
                issues.add(ValidationIssue.warning("BUYER_GSTIN_MISMATCH", "buyerGstin",
                        "Bill is addressed to " + buyer + ", not your GSTIN " + ownGstin));
            }
        }
        if (supplier != null && supplier.equals(buyer)) {
            issues.add(ValidationIssue.error("SAME_SUPPLIER_BUYER", "buyerGstin",
                    "Supplier and buyer GSTIN are identical"));
        }
    }

    private void gstinIssue(String gstin, String field, String who, List<ValidationIssue> issues) {
        if (!GstinValidator.isValidFormat(gstin)) {
            issues.add(ValidationIssue.error("GSTIN_INVALID_FORMAT", field,
                    who + " GSTIN " + gstin + " does not match the 15-character GSTIN format"));
        } else if (!GstinValidator.isValidStateCode(gstin)) {
            issues.add(ValidationIssue.error("GSTIN_INVALID_STATE", field,
                    who + " GSTIN " + gstin + " has unknown state code " + gstin.substring(0, 2)));
        } else if (!GstinValidator.isValidChecksum(gstin)) {
            issues.add(ValidationIssue.error("GSTIN_BAD_CHECKSUM", field,
                    who + " GSTIN " + gstin + " fails the checksum — likely a misread character"));
        }
    }

    private void checkHeader(Invoice inv, List<ValidationIssue> issues) {
        if (inv.getInvoiceNumber() == null || inv.getInvoiceNumber().isBlank()) {
            issues.add(ValidationIssue.error("INVOICE_NUMBER_MISSING", "invoiceNumber", "Invoice number not found"));
        } else if (inv.getInvoiceNumber().length() > 16) {
            issues.add(ValidationIssue.warning("INVOICE_NUMBER_TOO_LONG", "invoiceNumber",
                    "GST invoice numbers are limited to 16 characters"));
        }
        LocalDate date = inv.getInvoiceDate();
        if (date == null) {
            issues.add(ValidationIssue.error("INVOICE_DATE_MISSING", "invoiceDate", "Invoice date not found"));
        } else if (date.isAfter(LocalDate.now())) {
            issues.add(ValidationIssue.error("INVOICE_DATE_FUTURE", "invoiceDate", "Invoice date is in the future"));
        } else if (date.isBefore(LocalDate.now().minusYears(2))) {
            issues.add(ValidationIssue.warning("INVOICE_DATE_OLD", "invoiceDate",
                    "Bill is over two years old — the ITC claim window has likely passed"));
        }
        if (inv.getTotalAmount() == null) {
            issues.add(ValidationIssue.error("TOTAL_MISSING", "totalAmount", "Invoice total not found"));
        }
    }

    private void checkTaxType(Invoice inv, List<ValidationIssue> issues) {
        BigDecimal cgst = nz(inv.getCgst());
        BigDecimal sgst = nz(inv.getSgst());
        BigDecimal igst = nz(inv.getIgst());
        boolean hasSplit = cgst.signum() > 0 || sgst.signum() > 0;
        boolean hasIgst = igst.signum() > 0;

        if (hasSplit && hasIgst) {
            issues.add(ValidationIssue.error("MIXED_TAX_TYPES", "igst",
                    "Bill charges both IGST and CGST/SGST — a supply is either intra-state or inter-state"));
        }
        if (hasSplit && !close(cgst, sgst)) {
            issues.add(ValidationIssue.error("CGST_SGST_UNEQUAL", "sgst",
                    "CGST (" + cgst + ") and SGST (" + sgst + ") must be equal"));
        }

        SupplyType expected = expectedSupplyType(inv);
        if (expected == SupplyType.INTRA && hasIgst) {
            issues.add(ValidationIssue.error("WRONG_TAX_TYPE", "igst",
                    "Supplier and place of supply are in the same state, so CGST+SGST applies, not IGST"));
        } else if (expected == SupplyType.INTER && hasSplit) {
            issues.add(ValidationIssue.error("WRONG_TAX_TYPE", "cgst",
                    "Supplier and place of supply are in different states, so IGST applies, not CGST+SGST"));
        }
    }

    /** Intra/inter-state is decided by supplier state vs. place of supply (falling back to buyer's state). */
    public static SupplyType expectedSupplyType(Invoice inv) {
        String supplier = inv.getSupplierGstin();
        if (supplier == null || supplier.length() < 2) {
            return null;
        }
        String pos = inv.getPlaceOfSupply();
        if (pos == null && inv.getBuyerGstin() != null && inv.getBuyerGstin().length() >= 2) {
            pos = inv.getBuyerGstin().substring(0, 2);
        }
        if (pos == null && inv.getBusiness() != null) {
            pos = inv.getBusiness().getStateCode();
        }
        if (pos == null) {
            return null;
        }
        return supplier.substring(0, 2).equals(pos) ? SupplyType.INTRA : SupplyType.INTER;
    }

    private void checkTotals(Invoice inv, List<ValidationIssue> issues) {
        if (inv.getTaxableValue() == null || inv.getTotalAmount() == null) {
            return;
        }
        BigDecimal computed = inv.getTaxableValue().add(inv.totalTax());
        // Indian bills often round the grand total to the nearest rupee, so allow the configured tolerance.
        if (!close(computed, inv.getTotalAmount())) {
            issues.add(ValidationIssue.error("TOTAL_MISMATCH", "totalAmount",
                    "Taxable value + taxes = " + scale(computed) + " but the bill total is "
                            + scale(inv.getTotalAmount())));
        }
    }

    private void checkLineItems(Invoice inv, List<ValidationIssue> issues) {
        List<InvoiceLineItem> items = inv.getLineItems();
        if (items.isEmpty()) {
            issues.add(ValidationIssue.warning("NO_LINE_ITEMS", "lineItems",
                    "No line items extracted — HSN-wise summary will be incomplete"));
            return;
        }
        BigDecimal taxableSum = BigDecimal.ZERO;
        BigDecimal taxSum = BigDecimal.ZERO;
        for (InvoiceLineItem item : items) {
            String field = "lineItems[" + (item.getLineNo() - 1) + "]";
            String code = item.getHsnSac();
            if (code == null || code.isBlank()) {
                issues.add(ValidationIssue.warning("HSN_MISSING", field + ".hsnSac",
                        "Line " + item.getLineNo() + " has no HSN/SAC code"));
            } else if (!HSN.matcher(code).matches() && !SAC.matcher(code).matches()) {
                issues.add(ValidationIssue.error("HSN_INVALID", field + ".hsnSac",
                        "Line " + item.getLineNo() + ": '" + code + "' is not a valid 4/6/8-digit HSN or SAC code"));
            }
            BigDecimal rate = item.getGstRate();
            if (rate != null && !KNOWN_RATES.contains(rate.stripTrailingZeros())) {
                issues.add(ValidationIssue.warning("UNUSUAL_GST_RATE", field + ".gstRate",
                        "Line " + item.getLineNo() + ": " + rate + "% is not a standard GST rate"));
            }
            BigDecimal lineTax = nz(item.getCgst()).add(nz(item.getSgst())).add(nz(item.getIgst()));
            if (rate != null && item.getTaxableValue() != null && lineTax.signum() > 0) {
                BigDecimal expected = item.getTaxableValue().multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                if (!close(expected, lineTax)) {
                    issues.add(ValidationIssue.error("LINE_TAX_MISMATCH", field,
                            "Line " + item.getLineNo() + ": " + rate + "% of " + scale(item.getTaxableValue())
                                    + " should be " + expected + " but tax charged is " + scale(lineTax)));
                }
            }
            taxableSum = taxableSum.add(nz(item.getTaxableValue()));
            taxSum = taxSum.add(lineTax);
        }
        if (inv.getTaxableValue() != null && !close(taxableSum, inv.getTaxableValue())) {
            issues.add(ValidationIssue.error("LINE_SUM_MISMATCH", "taxableValue",
                    "Line items add up to " + scale(taxableSum) + " but invoice taxable value is "
                            + scale(inv.getTaxableValue())));
        }
        BigDecimal headerTax = inv.totalTax().subtract(nz(inv.getCess()));
        if (taxSum.signum() > 0 && !close(taxSum, headerTax)) {
            issues.add(ValidationIssue.warning("LINE_TAX_SUM_MISMATCH", "cgst",
                    "Line-level tax adds up to " + scale(taxSum) + " but header tax is " + scale(headerTax)));
        }
    }

    private boolean close(BigDecimal a, BigDecimal b) {
        return a.subtract(b).abs().compareTo(tolerance) <= 0;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
