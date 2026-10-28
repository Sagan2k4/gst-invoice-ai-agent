package com.gstagent.validation;

import com.gstagent.invoice.Invoice;
import com.gstagent.invoice.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Three passes, strongest signal first:
 * 1. identical file (SHA-256) — the same scan/PDF uploaded twice;
 * 2. same supplier GSTIN + invoice number — the same bill photographed again;
 * 3. same supplier + date + total within ₹1 — invoice number misread but clearly the same bill.
 */
@Component
@RequiredArgsConstructor
public class DuplicateDetector {

    private static final BigDecimal FUZZ = BigDecimal.ONE;

    private final InvoiceRepository invoices;

    public record Match(Long invoiceId, String reason) {
    }

    public Optional<Match> findDuplicate(Invoice inv) {
        Long businessId = inv.getBusiness().getId();

        var sameFile = invoices.findFirstByBusinessIdAndFileHashAndIdLessThanOrderByIdAsc(businessId, inv.getFileHash(), inv.getId());
        if (sameFile.isPresent()) {
            return Optional.of(new Match(sameFile.get().getId(), "The same file was already uploaded"));
        }
        if (inv.getSupplierGstin() == null) {
            return Optional.empty();
        }
        if (inv.getInvoiceNumber() != null) {
            var sameNumber = invoices.findSameSupplierInvoiceNumber(
                    businessId, inv.getId(), inv.getSupplierGstin(), inv.getInvoiceNumber().trim());
            if (!sameNumber.isEmpty()) {
                return Optional.of(new Match(sameNumber.getFirst().getId(),
                        "Invoice " + inv.getInvoiceNumber() + " from this supplier is already recorded"));
            }
        }
        if (inv.getInvoiceDate() != null && inv.getTotalAmount() != null) {
            var similar = invoices.findSameSupplierDateAndTotal(businessId, inv.getId(), inv.getSupplierGstin(),
                    inv.getInvoiceDate(), inv.getTotalAmount().subtract(FUZZ), inv.getTotalAmount().add(FUZZ));
            if (!similar.isEmpty()) {
                return Optional.of(new Match(similar.getFirst().getId(),
                        "A bill from this supplier with the same date and amount already exists"));
            }
        }
        return Optional.empty();
    }
}
