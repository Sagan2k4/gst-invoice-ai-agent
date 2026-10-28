package com.gstagent.agent;

import com.gstagent.agent.InvoiceExtractor.PageImage;
import com.gstagent.category.ExpenseCategorizer;
import com.gstagent.config.AppProperties;
import com.gstagent.invoice.Invoice;
import com.gstagent.invoice.InvoiceRepository;
import com.gstagent.invoice.InvoiceStatus;
import com.gstagent.storage.FileStorageService;
import com.gstagent.validation.ComplianceChecker;
import com.gstagent.validation.ValidationIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The bill-processing agent. For each upload it:
 * <ol>
 *   <li>renders the document to images,</li>
 *   <li>asks the vision model to extract the bill (the model can call {@link GstTools} to self-check),</li>
 *   <li>runs deterministic GST compliance checks,</li>
 *   <li>if checks fail, feeds the failures back to the model for a focused re-read (reflection step),
 *       keeping whichever reading has fewer errors,</li>
 *   <li>detects duplicates, categorizes the expense and sets the final status.</li>
 * </ol>
 * Model calls run outside DB transactions so a slow API call never holds a connection.
 */
@Slf4j
@Service
public class InvoiceAgent {

    private final InvoiceRepository invoices;
    private final FileStorageService storage;
    private final DocumentRasterizer rasterizer;
    private final InvoiceExtractor extractor;
    private final ComplianceChecker complianceChecker;
    private final ExpenseCategorizer categorizer;
    private final TransactionTemplate tx;
    private final CacheManager cacheManager;
    private final int maxCorrections;

    public InvoiceAgent(InvoiceRepository invoices, FileStorageService storage, DocumentRasterizer rasterizer,
                        InvoiceExtractor extractor, ComplianceChecker complianceChecker,
                        ExpenseCategorizer categorizer, TransactionTemplate tx, CacheManager cacheManager,
                        AppProperties props) {
        this.invoices = invoices;
        this.storage = storage;
        this.rasterizer = rasterizer;
        this.extractor = extractor;
        this.complianceChecker = complianceChecker;
        this.categorizer = categorizer;
        this.tx = tx;
        this.cacheManager = cacheManager;
        this.maxCorrections = props.ai().maxCorrectionAttempts();
    }

    @Async
    public void processAsync(Long invoiceId) {
        process(invoiceId);
    }

    public void process(Long invoiceId) {
        try {
            record Source(String path, String contentType) {
            }
            Source src = tx.execute(s -> invoices.findById(invoiceId)
                    .map(i -> new Source(i.getFilePath(), i.getContentType()))
                    .orElseThrow());
            List<PageImage> pages = rasterizer.toImages(storage.read(src.path()), src.contentType());

            ExtractedInvoice best = extractor.extract(pages, null);
            List<ValidationIssue> issues = assess(invoiceId, best);
            log.info("Invoice {} pass 1: {} error(s)", invoiceId, ComplianceChecker.errorCount(issues));

            for (int attempt = 1; attempt <= maxCorrections && hasCorrectableErrors(issues); attempt++) {
                ExtractedInvoice retry = extractor.extract(pages, describe(issues));
                List<ValidationIssue> retryIssues = assess(invoiceId, retry);
                log.info("Invoice {} correction pass {}: {} error(s)", invoiceId, attempt,
                        ComplianceChecker.errorCount(retryIssues));
                if (ComplianceChecker.errorCount(retryIssues) <= ComplianceChecker.errorCount(issues)) {
                    best = retry;
                    issues = retryIssues;
                } else {
                    assess(invoiceId, best); // the earlier reading was better; restore it
                }
            }
        } catch (Exception e) {
            log.warn("Invoice {} extraction failed", invoiceId, e);
            tx.executeWithoutResult(s -> invoices.findById(invoiceId).ifPresent(inv -> {
                inv.setStatus(InvoiceStatus.FAILED);
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                inv.setErrorMessage(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
            }));
        } finally {
            var dashboard = cacheManager.getCache("dashboard");
            if (dashboard != null) {
                dashboard.clear();
            }
        }
    }

    /** Applies an extraction to the invoice, categorizes it, runs compliance checks and persists the result. */
    private List<ValidationIssue> assess(Long invoiceId, ExtractedInvoice extraction) {
        return tx.execute(s -> {
            Invoice inv = invoices.findById(invoiceId).orElseThrow();
            ExtractionMapper.apply(extraction, inv);
            inv.setCategory(categorizer.categorize(inv, extraction.suggestedCategory()));
            inv.setAiNotes(extraction.notes() == null ? null
                    : extraction.notes().substring(0, Math.min(1000, extraction.notes().length())));
            complianceChecker.check(inv);
            return inv.getValidationIssues();
        });
    }

    /** Duplicates and dates in the future are facts about the bill, not misreadings — re-reading won't help. */
    private static boolean hasCorrectableErrors(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(i -> i.severity() == ValidationIssue.Severity.ERROR
                && !List.of("DUPLICATE_BILL", "INVOICE_DATE_FUTURE").contains(i.code()));
    }

    private static String describe(List<ValidationIssue> issues) {
        return issues.stream()
                .filter(i -> i.severity() == ValidationIssue.Severity.ERROR)
                .map(i -> "- [" + i.field() + "] " + i.message())
                .collect(Collectors.joining("\n"));
    }
}
