package com.gstagent.agent;

import com.gstagent.validation.GstinValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline stand-in for the vision model so the whole pipeline (validation, duplicates, categorization,
 * reports) can be demoed without an API key. Picks one of a few realistic bills based on the file bytes,
 * so re-uploading the same file yields the same bill and different files yield different bills.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockInvoiceExtractor implements InvoiceExtractor {

    private record Line(String description, String hsn, int qty, String unitPrice, int rate) {
    }

    private record Template(String supplier, String gstinPrefix14, String pos, String category, boolean interState,
                            BigDecimal totalSkew, List<Line> lines) {
    }

    private static final List<Template> TEMPLATES = List.of(
            new Template("Sri Lakshmi Stationers", gstin14("29", "AABFS1234L", '1'), "29", "OFFICE_SUPPLIES", false,
                    BigDecimal.ZERO, List.of(
                    new Line("A4 copier paper 75 GSM (500 sheets)", "48025690", 10, "245.00", 18),
                    new Line("Ball pens, blue (box of 50)", "96081019", 4, "180.00", 18))),
            new Template("CloudNet Hosting Pvt Ltd", gstin14("27", "AADCC4567K", '1'), "29", "IT_AND_SOFTWARE", true,
                    BigDecimal.ZERO, List.of(
                    new Line("Cloud VPS 4 vCPU / 8 GB - monthly", "998315", 1, "3499.00", 18),
                    new Line("Managed backup add-on", "998315", 1, "799.00", 18))),
            new Template("Udupi Grand Restaurant", gstin14("29", "AAKPU7788M", '1'), "29", "FOOD_AND_BEVERAGES", false,
                    BigDecimal.ZERO, List.of(
                    new Line("Team lunch - 12 meals", "996331", 12, "260.00", 5))),
            // Deliberately inconsistent: printed total is ₹100 higher than taxable + tax, to demo human review.
            new Template("Metro Electronics", gstin14("29", "AAHFM2468Q", '1'), "29", "EQUIPMENT_AND_ASSETS", false,
                    new BigDecimal("100.00"), List.of(
                    new Line("Laptop 14\" i5 16GB/512GB", "84713010", 1, "52500.00", 18),
                    new Line("Wireless mouse", "84716060", 2, "650.00", 18))));

    @Override
    public ExtractedInvoice extract(List<PageImage> pages, String feedback) {
        byte[] bytes = pages.getFirst().bytes();
        int seed = seed(bytes);
        Template t = TEMPLATES.get(seed % TEMPLATES.size());

        List<ExtractedInvoice.LineItem> items = new ArrayList<>();
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (int n = 0; n < t.lines().size(); n++) {
            Line base = t.lines().get(n);
            // Vary the last line's quantity per file so different uploads don't look like the same bill.
            Line l = n == t.lines().size() - 1
                    ? new Line(base.description(), base.hsn(), base.qty() + (seed / 4) % 5, base.unitPrice(), base.rate())
                    : base;
            BigDecimal value = new BigDecimal(l.unitPrice()).multiply(BigDecimal.valueOf(l.qty()));
            BigDecimal rate = BigDecimal.valueOf(l.rate());
            BigDecimal lineTax = value.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal half = lineTax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            items.add(t.interState()
                    ? new ExtractedInvoice.LineItem(l.description(), l.hsn(), BigDecimal.valueOf(l.qty()),
                    new BigDecimal(l.unitPrice()), value, rate, BigDecimal.ZERO, BigDecimal.ZERO, lineTax)
                    : new ExtractedInvoice.LineItem(l.description(), l.hsn(), BigDecimal.valueOf(l.qty()),
                    new BigDecimal(l.unitPrice()), value, rate, half, half, BigDecimal.ZERO));
            taxable = taxable.add(value);
            tax = tax.add(lineTax);
        }
        BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal total = taxable.add(tax).setScale(0, RoundingMode.HALF_UP).add(t.totalSkew());

        String gstin = t.gstinPrefix14() + GstinValidator.checksumChar(t.gstinPrefix14());
        LocalDate date = LocalDate.now().minusDays((seed / 20) % 20);
        return new ExtractedInvoice(
                t.supplier(), gstin, null, null,
                "INV-" + (1000 + seed % 9000), date.toString(), t.pos(),
                taxable,
                t.interState() ? BigDecimal.ZERO : half,
                t.interState() ? BigDecimal.ZERO : half,
                t.interState() ? tax : BigDecimal.ZERO,
                BigDecimal.ZERO, total, items, t.category(), 0.93,
                "Mock extraction (AI_PROVIDER=mock). Set AI_PROVIDER=openai and OPENAI_API_KEY for real reading.");
    }

    private static int seed(byte[] bytes) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            return Math.floorMod(ByteBuffer.wrap(d).getInt(), 1_000_000);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String gstin14(String state, String pan, char entity) {
        return state + pan + entity + "Z";
    }
}
