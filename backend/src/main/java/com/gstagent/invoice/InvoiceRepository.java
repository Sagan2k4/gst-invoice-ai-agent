package com.gstagent.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    Optional<Invoice> findByIdAndBusinessId(Long id, Long businessId);

    Page<Invoice> findByBusinessId(Long businessId, Pageable pageable);

    @EntityGraph(attributePaths = "lineItems")
    @Query("select i from Invoice i where i.business.id = :businessId and i.status in :statuses "
            + "and i.invoiceDate between :from and :to order by i.invoiceDate, i.id")
    List<Invoice> findForPeriod(@Param("businessId") Long businessId,
                                @Param("statuses") Collection<InvoiceStatus> statuses,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    // --- duplicate detection: only earlier bills (lower id) count as originals, so concurrent
    // processing of two copies never marks both as duplicates of each other ---

    Optional<Invoice> findFirstByBusinessIdAndFileHashAndIdLessThanOrderByIdAsc(Long businessId, String fileHash, Long id);

    @Query("select i from Invoice i where i.business.id = :businessId and i.id < :id "
            + "and i.supplierGstin = :supplierGstin and upper(i.invoiceNumber) = upper(:invoiceNumber) "
            + "and i.status <> com.gstagent.invoice.InvoiceStatus.DUPLICATE")
    List<Invoice> findSameSupplierInvoiceNumber(@Param("businessId") Long businessId, @Param("id") Long id,
                                                @Param("supplierGstin") String supplierGstin,
                                                @Param("invoiceNumber") String invoiceNumber);

    @Query("select i from Invoice i where i.business.id = :businessId and i.id < :id "
            + "and i.supplierGstin = :supplierGstin and i.invoiceDate = :invoiceDate "
            + "and i.totalAmount between :minTotal and :maxTotal "
            + "and i.status <> com.gstagent.invoice.InvoiceStatus.DUPLICATE")
    List<Invoice> findSameSupplierDateAndTotal(@Param("businessId") Long businessId, @Param("id") Long id,
                                               @Param("supplierGstin") String supplierGstin,
                                               @Param("invoiceDate") LocalDate invoiceDate,
                                               @Param("minTotal") BigDecimal minTotal,
                                               @Param("maxTotal") BigDecimal maxTotal);

    long countByBusinessIdAndStatus(Long businessId, InvoiceStatus status);
}
