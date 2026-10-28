package com.gstagent.validation;

import com.gstagent.business.Business;
import com.gstagent.config.AppProperties;
import com.gstagent.invoice.Invoice;
import com.gstagent.invoice.InvoiceLineItem;
import com.gstagent.invoice.SupplyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceValidatorTest {

    private static final String KA_SUPPLIER = gstin("29AABFS1234L1Z");
    private static final String MH_SUPPLIER = gstin("27AADCC4567K1Z");
    private static final String KA_BUYER = gstin("29AAACB1111C1Z");

    private InvoiceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new InvoiceValidator(new AppProperties(null, "memory", null, null,
                new AppProperties.Validation(BigDecimal.ONE), null));
    }

    @Test
    void cleanIntraStateBillHasNoErrors() {
        Invoice inv = intraStateBill();
        assertThat(codes(validator.validate(inv))).isEmpty();
        assertThat(InvoiceValidator.expectedSupplyType(inv)).isEqualTo(SupplyType.INTRA);
    }

    @Test
    void flagsIgstOnIntraStateBill() {
        Invoice inv = intraStateBill();
        inv.setCgst(BigDecimal.ZERO);
        inv.setSgst(BigDecimal.ZERO);
        inv.setIgst(new BigDecimal("180.00"));
        inv.getLineItems().getFirst().setCgst(null);
        inv.getLineItems().getFirst().setSgst(null);
        inv.getLineItems().getFirst().setIgst(new BigDecimal("180.00"));
        assertThat(codes(validator.validate(inv))).contains("WRONG_TAX_TYPE");
    }

    @Test
    void flagsCgstSgstOnInterStateBill() {
        Invoice inv = intraStateBill();
        inv.setSupplierGstin(MH_SUPPLIER);
        assertThat(codes(validator.validate(inv))).contains("WRONG_TAX_TYPE");
    }

    @Test
    void flagsTotalAndLineMismatches() {
        Invoice inv = intraStateBill();
        inv.setTotalAmount(new BigDecimal("1300.00"));
        inv.getLineItems().getFirst().setGstRate(new BigDecimal("12"));
        assertThat(codes(validator.validate(inv))).contains("TOTAL_MISMATCH", "LINE_TAX_MISMATCH");
    }

    @Test
    void toleratesRupeeRoundOff() {
        Invoice inv = intraStateBill();
        inv.setTotalAmount(new BigDecimal("1180.49"));
        assertThat(codes(validator.validate(inv))).doesNotContain("TOTAL_MISMATCH");
    }

    @Test
    void flagsBadGstinChecksumAndInvalidHsn() {
        Invoice inv = intraStateBill();
        String bad = KA_SUPPLIER.substring(0, 14) + (KA_SUPPLIER.charAt(14) == 'A' ? 'B' : 'A');
        inv.setSupplierGstin(bad);
        inv.getLineItems().getFirst().setHsnSac("123");
        assertThat(codes(validator.validate(inv))).contains("GSTIN_BAD_CHECKSUM", "HSN_INVALID");
    }

    @Test
    void flagsFutureDateAndMissingFields() {
        Invoice inv = intraStateBill();
        inv.setInvoiceDate(LocalDate.now().plusDays(3));
        inv.setInvoiceNumber(null);
        assertThat(codes(validator.validate(inv))).contains("INVOICE_DATE_FUTURE", "INVOICE_NUMBER_MISSING");
    }

    private static Invoice intraStateBill() {
        Business b = new Business();
        b.setGstin(KA_BUYER);
        b.setStateCode("29");
        Invoice inv = new Invoice();
        inv.setBusiness(b);
        inv.setSupplierGstin(KA_SUPPLIER);
        inv.setBuyerGstin(KA_BUYER);
        inv.setInvoiceNumber("INV-1001");
        inv.setInvoiceDate(LocalDate.now().minusDays(2));
        inv.setPlaceOfSupply("29");
        inv.setTaxableValue(new BigDecimal("1000.00"));
        inv.setCgst(new BigDecimal("90.00"));
        inv.setSgst(new BigDecimal("90.00"));
        inv.setIgst(BigDecimal.ZERO);
        inv.setTotalAmount(new BigDecimal("1180.00"));
        InvoiceLineItem line = new InvoiceLineItem();
        line.setDescription("A4 paper");
        line.setHsnSac("4802");
        line.setTaxableValue(new BigDecimal("1000.00"));
        line.setGstRate(new BigDecimal("18"));
        line.setCgst(new BigDecimal("90.00"));
        line.setSgst(new BigDecimal("90.00"));
        inv.replaceLineItems(List.of(line));
        return inv;
    }

    private static List<String> codes(List<ValidationIssue> issues) {
        return issues.stream().filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .map(ValidationIssue::code).toList();
    }

    private static String gstin(String first14) {
        return first14 + GstinValidator.checksumChar(first14);
    }
}
