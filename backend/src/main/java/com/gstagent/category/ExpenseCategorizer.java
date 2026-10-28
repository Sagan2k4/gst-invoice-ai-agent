package com.gstagent.category;

import com.gstagent.invoice.Invoice;
import com.gstagent.invoice.InvoiceLineItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Categorizes a bill by, in order of reliability:
 * 1. HSN/SAC codes (weighted by line taxable value),
 * 2. the category the vision model suggested,
 * 3. keywords in the supplier name and item descriptions.
 */
@Component
public class ExpenseCategorizer {

    /** Longest-prefix match wins, so more specific prefixes override chapter-level ones. */
    private static final Map<String, ExpenseCategory> CODE_PREFIXES = new LinkedHashMap<>();
    private static final Map<String, ExpenseCategory> KEYWORDS = new LinkedHashMap<>();

    static {
        for (int chapter = 1; chapter <= 24; chapter++) {
            CODE_PREFIXES.put(String.format("%02d", chapter), ExpenseCategory.FOOD_AND_BEVERAGES);
        }
        CODE_PREFIXES.put("27", ExpenseCategory.FUEL_AND_TRAVEL);
        CODE_PREFIXES.put("48", ExpenseCategory.OFFICE_SUPPLIES);
        CODE_PREFIXES.put("49", ExpenseCategory.OFFICE_SUPPLIES);
        CODE_PREFIXES.put("9608", ExpenseCategory.OFFICE_SUPPLIES);
        CODE_PREFIXES.put("84", ExpenseCategory.EQUIPMENT_AND_ASSETS);
        CODE_PREFIXES.put("85", ExpenseCategory.EQUIPMENT_AND_ASSETS);
        CODE_PREFIXES.put("8517", ExpenseCategory.TELECOM_AND_INTERNET);
        CODE_PREFIXES.put("94", ExpenseCategory.EQUIPMENT_AND_ASSETS);
        CODE_PREFIXES.put("87", ExpenseCategory.MOTOR_VEHICLES);
        CODE_PREFIXES.put("8714", ExpenseCategory.REPAIRS_AND_MAINTENANCE);
        // SAC (services, chapter 99)
        CODE_PREFIXES.put("99631", ExpenseCategory.FUEL_AND_TRAVEL);       // accommodation
        CODE_PREFIXES.put("99633", ExpenseCategory.FOOD_AND_BEVERAGES);    // restaurants, catering
        CODE_PREFIXES.put("9964", ExpenseCategory.FUEL_AND_TRAVEL);        // passenger transport
        CODE_PREFIXES.put("9965", ExpenseCategory.LOGISTICS);              // goods transport
        CODE_PREFIXES.put("9967", ExpenseCategory.LOGISTICS);              // supporting transport
        CODE_PREFIXES.put("9968", ExpenseCategory.LOGISTICS);              // postal & courier
        CODE_PREFIXES.put("9969", ExpenseCategory.RENT_AND_UTILITIES);     // electricity, water, gas
        CODE_PREFIXES.put("9972", ExpenseCategory.RENT_AND_UTILITIES);     // real estate / rent
        CODE_PREFIXES.put("9973", ExpenseCategory.IT_AND_SOFTWARE);        // leasing, software licences
        CODE_PREFIXES.put("9982", ExpenseCategory.PROFESSIONAL_SERVICES);  // legal & accounting
        CODE_PREFIXES.put("9983", ExpenseCategory.PROFESSIONAL_SERVICES);  // other professional services
        CODE_PREFIXES.put("99831", ExpenseCategory.IT_AND_SOFTWARE);       // IT consulting & support
        CODE_PREFIXES.put("99836", ExpenseCategory.MARKETING);             // advertising
        CODE_PREFIXES.put("9984", ExpenseCategory.TELECOM_AND_INTERNET);
        CODE_PREFIXES.put("9987", ExpenseCategory.REPAIRS_AND_MAINTENANCE);

        KEYWORDS.put("petrol", ExpenseCategory.FUEL_AND_TRAVEL);
        KEYWORDS.put("diesel", ExpenseCategory.FUEL_AND_TRAVEL);
        KEYWORDS.put("fuel", ExpenseCategory.FUEL_AND_TRAVEL);
        KEYWORDS.put("hotel", ExpenseCategory.FUEL_AND_TRAVEL);
        KEYWORDS.put("airline", ExpenseCategory.FUEL_AND_TRAVEL);
        KEYWORDS.put("restaurant", ExpenseCategory.FOOD_AND_BEVERAGES);
        KEYWORDS.put("cafe", ExpenseCategory.FOOD_AND_BEVERAGES);
        KEYWORDS.put("food", ExpenseCategory.FOOD_AND_BEVERAGES);
        KEYWORDS.put("stationery", ExpenseCategory.OFFICE_SUPPLIES);
        KEYWORDS.put("paper", ExpenseCategory.OFFICE_SUPPLIES);
        KEYWORDS.put("software", ExpenseCategory.IT_AND_SOFTWARE);
        KEYWORDS.put("cloud", ExpenseCategory.IT_AND_SOFTWARE);
        KEYWORDS.put("hosting", ExpenseCategory.IT_AND_SOFTWARE);
        KEYWORDS.put("laptop", ExpenseCategory.EQUIPMENT_AND_ASSETS);
        KEYWORDS.put("printer", ExpenseCategory.EQUIPMENT_AND_ASSETS);
        KEYWORDS.put("furniture", ExpenseCategory.EQUIPMENT_AND_ASSETS);
        KEYWORDS.put("courier", ExpenseCategory.LOGISTICS);
        KEYWORDS.put("freight", ExpenseCategory.LOGISTICS);
        KEYWORDS.put("logistics", ExpenseCategory.LOGISTICS);
        KEYWORDS.put("rental", ExpenseCategory.RENT_AND_UTILITIES);
        KEYWORDS.put("office rent", ExpenseCategory.RENT_AND_UTILITIES);
        KEYWORDS.put("electricity", ExpenseCategory.RENT_AND_UTILITIES);
        KEYWORDS.put("broadband", ExpenseCategory.TELECOM_AND_INTERNET);
        KEYWORDS.put("airtel", ExpenseCategory.TELECOM_AND_INTERNET);
        KEYWORDS.put("jio", ExpenseCategory.TELECOM_AND_INTERNET);
        KEYWORDS.put("consult", ExpenseCategory.PROFESSIONAL_SERVICES);
        KEYWORDS.put("audit", ExpenseCategory.PROFESSIONAL_SERVICES);
        KEYWORDS.put("advertis", ExpenseCategory.MARKETING);
        KEYWORDS.put("repair", ExpenseCategory.REPAIRS_AND_MAINTENANCE);
        KEYWORDS.put("service charge", ExpenseCategory.REPAIRS_AND_MAINTENANCE);
    }

    public ExpenseCategory categorize(Invoice inv, String modelSuggestion) {
        return byCodes(inv)
                .or(() -> parse(modelSuggestion))
                .or(() -> byKeywords(inv))
                .orElse(ExpenseCategory.OTHER);
    }

    Optional<ExpenseCategory> byCodes(Invoice inv) {
        Map<ExpenseCategory, BigDecimal> weights = new EnumMap<>(ExpenseCategory.class);
        for (InvoiceLineItem item : inv.getLineItems()) {
            lookup(item.getHsnSac()).ifPresent(cat -> weights.merge(cat,
                    item.getTaxableValue() == null ? BigDecimal.ONE : item.getTaxableValue(), BigDecimal::add));
        }
        return weights.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    static Optional<ExpenseCategory> lookup(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        for (int len = Math.min(code.length(), 6); len >= 2; len--) {
            ExpenseCategory cat = CODE_PREFIXES.get(code.substring(0, len));
            if (cat != null) {
                return Optional.of(cat);
            }
        }
        return Optional.empty();
    }

    private Optional<ExpenseCategory> byKeywords(Invoice inv) {
        StringBuilder text = new StringBuilder(String.valueOf(inv.getSupplierName()));
        inv.getLineItems().forEach(i -> text.append(' ').append(i.getDescription()));
        String haystack = text.toString().toLowerCase(Locale.ROOT);
        return KEYWORDS.entrySet().stream()
                .filter(e -> haystack.contains(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private static Optional<ExpenseCategory> parse(String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return Optional.empty();
        }
        try {
            ExpenseCategory cat = ExpenseCategory.valueOf(suggestion.trim().toUpperCase(Locale.ROOT));
            return cat == ExpenseCategory.OTHER ? Optional.empty() : Optional.of(cat);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
