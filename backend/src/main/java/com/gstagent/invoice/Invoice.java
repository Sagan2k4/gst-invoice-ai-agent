package com.gstagent.invoice;

import com.gstagent.business.Business;
import com.gstagent.category.ExpenseCategory;
import com.gstagent.user.User;
import com.gstagent.validation.ValidationIssue;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    // --- source document ---
    @Column(nullable = false)
    private String fileName;
    @Column(nullable = false)
    private String filePath;
    @Column(nullable = false)
    private String contentType;
    @Column(nullable = false, length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.PROCESSING;

    // --- extracted header ---
    private String supplierName;
    private String supplierGstin;
    private String buyerName;
    private String buyerGstin;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    /** 2-digit state code of the place of supply. */
    private String placeOfSupply;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private SupplyType supplyType;

    // --- amounts (INR) ---
    private BigDecimal taxableValue;
    private BigDecimal cgst;
    private BigDecimal sgst;
    private BigDecimal igst;
    private BigDecimal cess;
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ExpenseCategory category;

    /** Model's self-reported confidence, 0..1. */
    private BigDecimal aiConfidence;

    private Long duplicateOfId;

    @Convert(converter = ValidationIssuesConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<ValidationIssue> validationIssues = new ArrayList<>();

    /** Model's remarks about unreadable or unusual parts of the bill. */
    private String aiNotes;

    /** Why extraction failed (status FAILED). */
    private String errorMessage;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void replaceLineItems(List<InvoiceLineItem> items) {
        lineItems.clear();
        int n = 1;
        for (InvoiceLineItem item : items) {
            item.setInvoice(this);
            item.setLineNo(n++);
            lineItems.add(item);
        }
    }

    public BigDecimal totalTax() {
        return nz(cgst).add(nz(sgst)).add(nz(igst)).add(nz(cess));
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
