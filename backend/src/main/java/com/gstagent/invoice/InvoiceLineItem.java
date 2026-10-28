package com.gstagent.invoice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "invoice_line_items")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false)
    private int lineNo;

    private String description;
    /** HSN code for goods (4/6/8 digits) or SAC for services (6 digits, starts with 99). */
    private String hsnSac;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal taxableValue;
    /** Combined GST rate in percent, e.g. 18 for 9% CGST + 9% SGST. */
    private BigDecimal gstRate;
    private BigDecimal cgst;
    private BigDecimal sgst;
    private BigDecimal igst;
}
