package com.gstagent.agent;

import com.gstagent.invoice.Invoice;
import com.gstagent.invoice.InvoiceLineItem;
import com.gstagent.validation.GstinValidator;
import com.gstagent.validation.InvoiceValidator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Copies model output onto the entity, normalizing the formatting quirks LLMs and OCR introduce. */
final class ExtractionMapper {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"));

    private ExtractionMapper() {
    }

    static void apply(ExtractedInvoice x, Invoice inv) {
        inv.setSupplierName(trim(x.supplierName(), 255));
        inv.setSupplierGstin(gstin(x.supplierGstin()));
        inv.setBuyerName(trim(x.buyerName(), 255));
        inv.setBuyerGstin(gstin(x.buyerGstin()));
        inv.setInvoiceNumber(trim(x.invoiceNumber(), 64));
        inv.setInvoiceDate(date(x.invoiceDate()));
        String pos = x.placeOfSupplyStateCode();
        inv.setPlaceOfSupply(pos != null && pos.trim().matches("\\d{1,2}") ? String.format("%02d", Integer.parseInt(pos.trim())) : null);
        inv.setTaxableValue(money(x.taxableValue()));
        inv.setCgst(money(x.cgst()));
        inv.setSgst(money(x.sgst()));
        inv.setIgst(money(x.igst()));
        inv.setCess(money(x.cess()));
        inv.setTotalAmount(money(x.totalAmount()));
        inv.setAiConfidence(x.confidence() == null ? null
                : BigDecimal.valueOf(Math.max(0, Math.min(1, x.confidence()))).setScale(3, RoundingMode.HALF_UP));

        List<InvoiceLineItem> items = x.lineItems() == null ? List.of() : x.lineItems().stream().map(l -> {
            InvoiceLineItem item = new InvoiceLineItem();
            item.setDescription(trim(l.description(), 500));
            item.setHsnSac(l.hsnSac() == null ? null : trim(l.hsnSac().replaceAll("\\D", ""), 8));
            item.setQuantity(l.quantity());
            item.setUnitPrice(money(l.unitPrice()));
            item.setTaxableValue(money(l.taxableValue()));
            item.setGstRate(l.gstRate());
            item.setCgst(money(l.cgst()));
            item.setSgst(money(l.sgst()));
            item.setIgst(money(l.igst()));
            return item;
        }).toList();
        inv.replaceLineItems(items);
        inv.setSupplyType(InvoiceValidator.expectedSupplyType(inv));
    }

    private static String gstin(String raw) {
        return GstinValidator.normalize(raw);
    }

    static LocalDate date(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw.trim(), f);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
