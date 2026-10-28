package com.gstagent.report;

import com.gstagent.business.Business;
import com.gstagent.business.BusinessRepository;
import com.gstagent.category.ExpenseCategory;
import com.gstagent.common.ApiException;
import com.gstagent.invoice.Invoice;
import com.gstagent.invoice.InvoiceLineItem;
import com.gstagent.invoice.InvoiceRepository;
import com.gstagent.invoice.InvoiceStatus;
import com.gstagent.report.GstSummary.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class GstSummaryService {

    private final InvoiceRepository invoices;
    private final BusinessRepository businesses;

    @Transactional(readOnly = true)
    public GstSummary monthly(Long businessId, YearMonth period, boolean approvedOnly) {
        Business business = businesses.findById(businessId).orElseThrow(() -> ApiException.notFound("Business"));
        Set<InvoiceStatus> statuses = approvedOnly
                ? EnumSet.of(InvoiceStatus.APPROVED)
                : EnumSet.of(InvoiceStatus.APPROVED, InvoiceStatus.VALIDATED);
        List<Invoice> list = invoices.findForPeriod(businessId, statuses, period.atDay(1), period.atEndOfMonth());
        long pending = invoices.findForPeriod(businessId, EnumSet.of(InvoiceStatus.NEEDS_REVIEW, InvoiceStatus.PROCESSING),
                period.atDay(1), period.atEndOfMonth()).size();

        Totals totals = totals(list);
        BigDecimal blocked = list.stream()
                .filter(i -> i.getCategory() != null && !i.getCategory().isItcEligible())
                .map(Invoice::totalTax).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new GstSummary(business.getName(), business.getGstin(), period, approvedOnly, list.size(), pending,
                totals, totals.totalTax().subtract(blocked), blocked,
                byCategory(list), byRate(list), byHsn(list), bySupplier(list),
                list.stream().map(GstSummaryService::row).toList());
    }

    static Totals totals(List<Invoice> list) {
        return new Totals(
                sum(list, Invoice::getTaxableValue), sum(list, Invoice::getCgst), sum(list, Invoice::getSgst),
                sum(list, Invoice::getIgst), sum(list, Invoice::getCess), sum(list, Invoice::totalTax),
                sum(list, Invoice::getTotalAmount));
    }

    private static List<CategoryRow> byCategory(List<Invoice> list) {
        Map<ExpenseCategory, List<Invoice>> groups = new EnumMap<>(ExpenseCategory.class);
        list.forEach(i -> groups.computeIfAbsent(
                i.getCategory() == null ? ExpenseCategory.OTHER : i.getCategory(), k -> new ArrayList<>()).add(i));
        return groups.entrySet().stream()
                .map(e -> new CategoryRow(e.getKey().name(), e.getKey().getLabel(), e.getKey().isItcEligible(),
                        e.getValue().size(), sum(e.getValue(), Invoice::getTaxableValue),
                        sum(e.getValue(), Invoice::totalTax)))
                .sorted(Comparator.comparing(CategoryRow::taxableValue).reversed())
                .toList();
    }

    private static List<RateRow> byRate(List<Invoice> list) {
        Map<BigDecimal, BigDecimal[]> acc = new TreeMap<>();
        lines(list).forEach(l -> {
            BigDecimal rate = l.getGstRate() == null ? BigDecimal.ZERO : l.getGstRate().stripTrailingZeros();
            BigDecimal[] a = acc.computeIfAbsent(rate, k -> zeros(4));
            add(a, nz(l.getTaxableValue()), nz(l.getCgst()), nz(l.getSgst()), nz(l.getIgst()));
        });
        return acc.entrySet().stream()
                .map(e -> new RateRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
    }

    private static List<HsnRow> byHsn(List<Invoice> list) {
        record Key(String hsn, BigDecimal rate) {
        }
        Map<Key, BigDecimal[]> acc = new LinkedHashMap<>();
        Map<Key, String> descriptions = new HashMap<>();
        lines(list).forEach(l -> {
            Key key = new Key(l.getHsnSac() == null ? "-" : l.getHsnSac(),
                    l.getGstRate() == null ? BigDecimal.ZERO : l.getGstRate().stripTrailingZeros());
            descriptions.putIfAbsent(key, l.getDescription());
            add(acc.computeIfAbsent(key, k -> zeros(5)), nz(l.getQuantity()), nz(l.getTaxableValue()),
                    nz(l.getCgst()), nz(l.getSgst()), nz(l.getIgst()));
        });
        return acc.entrySet().stream()
                .map(e -> new HsnRow(e.getKey().hsn(), descriptions.get(e.getKey()), e.getValue()[0],
                        e.getValue()[1], e.getKey().rate(), e.getValue()[2], e.getValue()[3], e.getValue()[4]))
                .sorted(Comparator.comparing(HsnRow::hsnSac))
                .toList();
    }

    private static List<SupplierRow> bySupplier(List<Invoice> list) {
        Map<String, List<Invoice>> groups = new LinkedHashMap<>();
        list.forEach(i -> groups.computeIfAbsent(
                i.getSupplierGstin() == null ? "UNREGISTERED" : i.getSupplierGstin(), k -> new ArrayList<>()).add(i));
        return groups.entrySet().stream()
                .map(e -> new SupplierRow(e.getKey(), e.getValue().getFirst().getSupplierName(), e.getValue().size(),
                        sum(e.getValue(), Invoice::getTaxableValue), sum(e.getValue(), Invoice::totalTax),
                        sum(e.getValue(), Invoice::getTotalAmount)))
                .sorted(Comparator.comparing(SupplierRow::totalAmount).reversed())
                .toList();
    }

    private static InvoiceRow row(Invoice i) {
        return new InvoiceRow(i.getId(), i.getInvoiceDate(), i.getInvoiceNumber(), i.getSupplierName(),
                i.getSupplierGstin(), i.getSupplyType() == null ? null : i.getSupplyType().name(),
                i.getCategory() == null ? null : i.getCategory().getLabel(), i.getStatus().name(),
                nz(i.getTaxableValue()), nz(i.getCgst()), nz(i.getSgst()), nz(i.getIgst()), nz(i.getCess()),
                nz(i.getTotalAmount()));
    }

    private static List<InvoiceLineItem> lines(List<Invoice> list) {
        return list.stream().flatMap(i -> i.getLineItems().stream()).toList();
    }

    private static BigDecimal sum(List<Invoice> list, Function<Invoice, BigDecimal> f) {
        return list.stream().map(f).map(GstSummaryService::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static void add(BigDecimal[] acc, BigDecimal... values) {
        for (int i = 0; i < values.length; i++) {
            acc[i] = acc[i].add(values[i]);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
