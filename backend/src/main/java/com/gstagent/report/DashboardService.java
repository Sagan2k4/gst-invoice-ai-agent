package com.gstagent.report;

import com.gstagent.invoice.InvoiceRepository;
import com.gstagent.invoice.InvoiceStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Dashboard numbers are read on every page load but change only on upload/review, so they are cached per business. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final GstSummaryService summaryService;
    private final InvoiceRepository invoices;

    public record MonthPoint(String month, BigDecimal taxableValue, BigDecimal totalTax, int invoiceCount)
            implements Serializable {
    }

    public record Dashboard(GstSummary.Totals currentMonth, BigDecimal eligibleItc, BigDecimal blockedItc,
                            Map<InvoiceStatus, Long> statusCounts, List<MonthPoint> trend,
                            List<GstSummary.CategoryRow> topCategories) implements Serializable {
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "dashboard", key = "#businessId")
    public Dashboard dashboard(Long businessId) {
        YearMonth now = YearMonth.now();
        GstSummary current = summaryService.monthly(businessId, now, false);

        List<MonthPoint> trend = new ArrayList<>();
        for (int back = 5; back >= 0; back--) {
            YearMonth m = now.minusMonths(back);
            GstSummary s = back == 0 ? current : summaryService.monthly(businessId, m, false);
            trend.add(new MonthPoint(m.toString(), s.totals().taxableValue(), s.totals().totalTax(), s.invoiceCount()));
        }

        Map<InvoiceStatus, Long> counts = new EnumMap<>(InvoiceStatus.class);
        for (InvoiceStatus st : InvoiceStatus.values()) {
            counts.put(st, invoices.countByBusinessIdAndStatus(businessId, st));
        }
        return new Dashboard(current.totals(), current.eligibleItc(), current.blockedItc(), counts, trend,
                current.byCategory().stream().limit(6).toList());
    }
}
