package com.gstagent.report;

import com.gstagent.report.GstSummary.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** GST-ready workbook: summary, invoice register, HSN-wise, rate-wise and supplier-wise sheets. */
@Component
public class ExcelReportGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public byte[] generate(GstSummary s) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            summarySheet(wb, st, s);
            invoiceSheet(wb, st, s.invoices());
            hsnSheet(wb, st, s.byHsn());
            rateSheet(wb, st, s.byRate());
            supplierSheet(wb, st, s.bySupplier());
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void summarySheet(Workbook wb, Styles st, GstSummary s) {
        Sheet sh = wb.createSheet("Summary");
        int r = 0;
        Cell title = sh.createRow(r++).createCell(0);
        title.setCellValue("GST Purchase Summary — " + s.period().format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        title.setCellStyle(st.title);
        sh.createRow(r++).createCell(0).setCellValue(s.businessName()
                + (s.businessGstin() == null ? "" : "  |  GSTIN " + s.businessGstin()));
        sh.createRow(r++).createCell(0).setCellValue(s.approvedOnly()
                ? "Includes approved bills only" : "Includes validated and approved bills");
        r++;
        Totals t = s.totals();
        Object[][] rows = {
                {"Bills included", s.invoiceCount()},
                {"Bills pending review", s.pendingReviewCount()},
                {"Taxable value", t.taxableValue()},
                {"CGST", t.cgst()},
                {"SGST / UTGST", t.sgst()},
                {"IGST", t.igst()},
                {"Cess", t.cess()},
                {"Total tax", t.totalTax()},
                {"Eligible ITC", s.eligibleItc()},
                {"Blocked ITC (Sec 17(5))", s.blockedItc()},
                {"Total purchases (incl. tax)", t.totalAmount()},
        };
        for (Object[] row : rows) {
            Row x = sh.createRow(r++);
            Cell label = x.createCell(0);
            label.setCellValue((String) row[0]);
            label.setCellStyle(st.bold);
            set(x.createCell(1), row[1], st);
        }
        r++;
        r = header(sh, r, st, "Category", "ITC eligible", "Bills", "Taxable value", "Total tax");
        for (CategoryRow c : s.byCategory()) {
            fill(sh.createRow(r++), st, c.label(), c.itcEligible() ? "Yes" : "No", c.invoiceCount(),
                    c.taxableValue(), c.totalTax());
        }
        autosize(sh, 5);
    }

    private void invoiceSheet(Workbook wb, Styles st, List<InvoiceRow> rows) {
        Sheet sh = wb.createSheet("Invoices");
        int r = header(sh, 0, st, "Date", "Invoice No", "Supplier", "Supplier GSTIN", "Supply", "Category",
                "Status", "Taxable", "CGST", "SGST", "IGST", "Cess", "Total");
        for (InvoiceRow i : rows) {
            fill(sh.createRow(r++), st, i.invoiceDate() == null ? "" : i.invoiceDate().format(DATE), i.invoiceNumber(),
                    i.supplierName(), i.supplierGstin(), i.supplyType(), i.category(), i.status(), i.taxableValue(),
                    i.cgst(), i.sgst(), i.igst(), i.cess(), i.totalAmount());
        }
        sh.createFreezePane(0, 1);
        autosize(sh, 13);
    }

    private void hsnSheet(Workbook wb, Styles st, List<HsnRow> rows) {
        Sheet sh = wb.createSheet("HSN Summary");
        int r = header(sh, 0, st, "HSN/SAC", "Description", "Quantity", "Rate %", "Taxable", "CGST", "SGST", "IGST");
        for (HsnRow h : rows) {
            fill(sh.createRow(r++), st, h.hsnSac(), h.description(), h.quantity(), h.gstRate(), h.taxableValue(),
                    h.cgst(), h.sgst(), h.igst());
        }
        autosize(sh, 8);
    }

    private void rateSheet(Workbook wb, Styles st, List<RateRow> rows) {
        Sheet sh = wb.createSheet("Rate-wise");
        int r = header(sh, 0, st, "GST rate %", "Taxable", "CGST", "SGST", "IGST");
        for (RateRow x : rows) {
            fill(sh.createRow(r++), st, x.gstRate(), x.taxableValue(), x.cgst(), x.sgst(), x.igst());
        }
        autosize(sh, 5);
    }

    private void supplierSheet(Workbook wb, Styles st, List<SupplierRow> rows) {
        Sheet sh = wb.createSheet("Supplier-wise");
        int r = header(sh, 0, st, "Supplier GSTIN", "Supplier", "Bills", "Taxable", "Total tax", "Total");
        for (SupplierRow x : rows) {
            fill(sh.createRow(r++), st, x.supplierGstin(), x.supplierName(), x.invoiceCount(), x.taxableValue(),
                    x.totalTax(), x.totalAmount());
        }
        autosize(sh, 6);
    }

    private int header(Sheet sh, int r, Styles st, String... titles) {
        Row row = sh.createRow(r);
        for (int c = 0; c < titles.length; c++) {
            Cell cell = row.createCell(c);
            cell.setCellValue(titles[c]);
            cell.setCellStyle(st.header);
        }
        return r + 1;
    }

    private void fill(Row row, Styles st, Object... values) {
        for (int c = 0; c < values.length; c++) {
            set(row.createCell(c), values[c], st);
        }
    }

    private void set(Cell cell, Object v, Styles st) {
        switch (v) {
            case null -> cell.setBlank();
            case BigDecimal b -> {
                cell.setCellValue(b.doubleValue());
                cell.setCellStyle(st.money);
            }
            case Number n -> cell.setCellValue(n.doubleValue());
            default -> cell.setCellValue(v.toString());
        }
    }

    private void autosize(Sheet sh, int cols) {
        for (int c = 0; c < cols; c++) {
            sh.autoSizeColumn(c);
        }
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle money;
        final CellStyle bold;
        final CellStyle title;

        Styles(Workbook wb) {
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            bold = wb.createCellStyle();
            bold.setFont(boldFont);

            header = wb.createCellStyle();
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(titleFont);
        }
    }
}
