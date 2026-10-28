package com.gstagent.report;

import com.gstagent.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final GstSummaryService summaryService;
    private final DashboardService dashboardService;
    private final PdfReportGenerator pdf;
    private final ExcelReportGenerator excel;

    @GetMapping("/dashboard")
    public DashboardService.Dashboard dashboard(@AuthenticationPrincipal AuthUser user) {
        return dashboardService.dashboard(user.businessId());
    }

    @GetMapping("/monthly")
    public GstSummary monthly(@AuthenticationPrincipal AuthUser user,
                              @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                              @RequestParam(defaultValue = "false") boolean approvedOnly) {
        return summaryService.monthly(user.businessId(), month, approvedOnly);
    }

    @GetMapping("/monthly/pdf")
    public ResponseEntity<byte[]> monthlyPdf(@AuthenticationPrincipal AuthUser user,
                                             @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                             @RequestParam(defaultValue = "false") boolean approvedOnly) {
        GstSummary s = summaryService.monthly(user.businessId(), month, approvedOnly);
        return download(pdf.generate(s), MediaType.APPLICATION_PDF, "gst-summary-" + month + ".pdf");
    }

    @GetMapping("/monthly/excel")
    public ResponseEntity<byte[]> monthlyExcel(@AuthenticationPrincipal AuthUser user,
                                               @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                               @RequestParam(defaultValue = "false") boolean approvedOnly) {
        GstSummary s = summaryService.monthly(user.businessId(), month, approvedOnly);
        return download(excel.generate(s), XLSX, "gst-summary-" + month + ".xlsx");
    }

    private ResponseEntity<byte[]> download(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
