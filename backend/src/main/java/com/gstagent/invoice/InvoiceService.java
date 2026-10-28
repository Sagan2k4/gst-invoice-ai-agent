package com.gstagent.invoice;

import com.gstagent.agent.InvoiceAgent;
import com.gstagent.auth.AuthUser;
import com.gstagent.business.BusinessRepository;
import com.gstagent.common.ApiException;
import com.gstagent.invoice.InvoiceDtos.*;
import com.gstagent.storage.FileStorageService;
import com.gstagent.user.UserRepository;
import com.gstagent.validation.ComplianceChecker;
import com.gstagent.validation.GstinValidator;
import com.gstagent.validation.InvoiceValidator;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final BusinessRepository businesses;
    private final UserRepository users;
    private final FileStorageService storage;
    private final InvoiceAgent agent;
    private final ComplianceChecker complianceChecker;

    @Transactional
    @CacheEvict(value = "dashboard", key = "#user.businessId()")
    public List<InvoiceSummary> upload(AuthUser user, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("Attach at least one bill");
        }
        if (files.size() > 20) {
            throw ApiException.badRequest("Upload at most 20 bills at a time");
        }
        List<Invoice> created = new ArrayList<>();
        for (MultipartFile file : files) {
            FileStorageService.StoredFile stored = storage.store(user.businessId(), file);
            Invoice inv = new Invoice();
            inv.setBusiness(businesses.getReferenceById(user.businessId()));
            inv.setUploadedBy(users.getReferenceById(user.userId()));
            String name = file.getOriginalFilename() == null ? "bill" : file.getOriginalFilename();
            inv.setFileName(name.length() > 255 ? name.substring(name.length() - 255) : name);
            inv.setFilePath(stored.relativePath());
            inv.setContentType(stored.contentType());
            inv.setFileHash(stored.sha256());
            created.add(invoices.save(inv));
        }
        // Start the agent only once the rows are committed, so the async worker can see them.
        List<Long> ids = created.stream().map(Invoice::getId).toList();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ids.forEach(agent::processAsync);
            }
        });
        return created.stream().map(InvoiceSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public Page<InvoiceSummary> list(AuthUser user, InvoiceStatus status, String q, LocalDate from, LocalDate to,
                                     Pageable pageable) {
        Specification<Invoice> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("business").get("id"), user.businessId()));
            if (status != null) {
                p.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("invoiceDate"), from));
            }
            if (to != null) {
                p.add(cb.lessThanOrEqualTo(root.get("invoiceDate"), to));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("supplierName")), like),
                        cb.like(cb.lower(root.get("invoiceNumber")), like),
                        cb.like(cb.lower(root.get("supplierGstin")), like),
                        cb.like(cb.lower(root.get("fileName")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return invoices.findAll(spec, pageable).map(InvoiceSummary::of);
    }

    @Transactional(readOnly = true)
    public InvoiceDetail get(AuthUser user, Long id) {
        return InvoiceDetail.of(load(user, id));
    }

    @Transactional(readOnly = true)
    public FileDownload file(AuthUser user, Long id) {
        Invoice inv = load(user, id);
        return new FileDownload(inv.getFileName(), inv.getContentType(), storage.read(inv.getFilePath()));
    }

    public record FileDownload(String fileName, String contentType, byte[] bytes) {
    }

    /** Applies a reviewer's corrections and re-runs every compliance check. */
    @Transactional
    @CacheEvict(value = "dashboard", key = "#user.businessId()")
    public InvoiceDetail update(AuthUser user, Long id, InvoiceUpdateRequest req) {
        Invoice inv = load(user, id);
        if (inv.getStatus() == InvoiceStatus.PROCESSING) {
            throw ApiException.conflict("The agent is still reading this bill");
        }
        inv.setSupplierName(req.supplierName());
        inv.setSupplierGstin(GstinValidator.normalize(req.supplierGstin()));
        inv.setBuyerName(req.buyerName());
        inv.setBuyerGstin(GstinValidator.normalize(req.buyerGstin()));
        inv.setInvoiceNumber(req.invoiceNumber());
        inv.setInvoiceDate(req.invoiceDate());
        inv.setPlaceOfSupply(req.placeOfSupply());
        inv.setTaxableValue(money(req.taxableValue()));
        inv.setCgst(money(req.cgst()));
        inv.setSgst(money(req.sgst()));
        inv.setIgst(money(req.igst()));
        inv.setCess(money(req.cess()));
        inv.setTotalAmount(money(req.totalAmount()));
        if (req.category() != null) {
            inv.setCategory(req.category());
        }
        if (req.lineItems() != null) {
            inv.replaceLineItems(req.lineItems().stream().map(l -> {
                InvoiceLineItem item = new InvoiceLineItem();
                item.setDescription(l.description());
                item.setHsnSac(l.hsnSac());
                item.setQuantity(l.quantity());
                item.setUnitPrice(money(l.unitPrice()));
                item.setTaxableValue(money(l.taxableValue()));
                item.setGstRate(l.gstRate());
                item.setCgst(money(l.cgst()));
                item.setSgst(money(l.sgst()));
                item.setIgst(money(l.igst()));
                return item;
            }).toList());
        }
        inv.setSupplyType(InvoiceValidator.expectedSupplyType(inv));
        inv.setErrorMessage(null);
        complianceChecker.check(inv); // edits clear any earlier approval
        return InvoiceDetail.of(inv);
    }

    @Transactional
    @CacheEvict(value = "dashboard", key = "#user.businessId()")
    public InvoiceDetail approve(AuthUser user, Long id, boolean acceptIssues) {
        Invoice inv = load(user, id);
        if (inv.getStatus() != InvoiceStatus.VALIDATED && inv.getStatus() != InvoiceStatus.NEEDS_REVIEW) {
            throw ApiException.conflict("Only validated or reviewed bills can be approved (current: "
                    + inv.getStatus() + ")");
        }
        // Some bills are genuinely wrong as printed (e.g. the supplier's own arithmetic); a reviewer may accept them.
        if (inv.getStatus() == InvoiceStatus.NEEDS_REVIEW && !acceptIssues) {
            throw ApiException.conflict("Fix the validation errors, or approve with acceptIssues=true");
        }
        inv.setStatus(InvoiceStatus.APPROVED);
        return InvoiceDetail.of(inv);
    }

    /** Re-runs the full AI agent on the original file (e.g. after a failure). */
    @Transactional
    public InvoiceDetail reprocess(AuthUser user, Long id) {
        Invoice inv = load(user, id);
        if (inv.getStatus() == InvoiceStatus.PROCESSING) {
            throw ApiException.conflict("The agent is already reading this bill");
        }
        inv.setStatus(InvoiceStatus.PROCESSING);
        inv.setErrorMessage(null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                agent.processAsync(id);
            }
        });
        return InvoiceDetail.of(inv);
    }

    @Transactional
    @CacheEvict(value = "dashboard", key = "#user.businessId()")
    public void delete(AuthUser user, Long id) {
        invoices.delete(load(user, id));
    }

    private Invoice load(AuthUser user, Long id) {
        return invoices.findByIdAndBusinessId(id, user.businessId())
                .orElseThrow(() -> ApiException.notFound("Invoice"));
    }

    private static BigDecimal money(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }
}
