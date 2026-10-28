package com.gstagent.validation;

import com.gstagent.invoice.Invoice;
import com.gstagent.invoice.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Runs every check on an invoice and derives its status. Used after AI extraction and after manual edits. */
@Component
@RequiredArgsConstructor
public class ComplianceChecker {

    private final InvoiceValidator validator;
    private final DuplicateDetector duplicateDetector;

    public void check(Invoice inv) {
        List<ValidationIssue> issues = validator.validate(inv);

        if (inv.getCategory() != null && !inv.getCategory().isItcEligible()) {
            issues.add(ValidationIssue.warning("ITC_BLOCKED", "category",
                    inv.getCategory().getLabel() + " — input tax credit is generally blocked under Section 17(5)"));
        }

        var duplicate = duplicateDetector.findDuplicate(inv);
        inv.setDuplicateOfId(duplicate.map(DuplicateDetector.Match::invoiceId).orElse(null));
        duplicate.ifPresent(d -> issues.add(0, ValidationIssue.error("DUPLICATE_BILL", "invoiceNumber",
                d.reason() + " (bill #" + d.invoiceId() + ")")));

        inv.setValidationIssues(issues);
        if (duplicate.isPresent()) {
            inv.setStatus(InvoiceStatus.DUPLICATE);
        } else if (errorCount(issues) > 0) {
            inv.setStatus(InvoiceStatus.NEEDS_REVIEW);
        } else {
            inv.setStatus(InvoiceStatus.VALIDATED);
        }
    }

    public static long errorCount(List<ValidationIssue> issues) {
        return issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.ERROR).count();
    }
}
