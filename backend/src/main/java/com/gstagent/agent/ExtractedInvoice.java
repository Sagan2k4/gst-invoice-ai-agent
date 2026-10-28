package com.gstagent.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.util.List;

/** Structured output the vision model must return. Field descriptions are sent to the model as JSON schema. */
public record ExtractedInvoice(
        @JsonPropertyDescription("Legal/trade name of the seller issuing the bill") String supplierName,
        @JsonPropertyDescription("Seller's 15-character GSTIN exactly as printed, or null") String supplierGstin,
        @JsonPropertyDescription("Name of the buyer the bill is addressed to") String buyerName,
        @JsonPropertyDescription("Buyer's 15-character GSTIN, or null if not printed") String buyerGstin,
        @JsonPropertyDescription("Invoice / bill number exactly as printed") String invoiceNumber,
        @JsonPropertyDescription("Invoice date in ISO format yyyy-MM-dd") String invoiceDate,
        @JsonPropertyDescription("2-digit GST state code of the place of supply, e.g. 29 for Karnataka, or null")
        String placeOfSupplyStateCode,
        @JsonPropertyDescription("Total taxable value before GST, in rupees") BigDecimal taxableValue,
        @JsonPropertyDescription("Total CGST amount in rupees, 0 if not charged") BigDecimal cgst,
        @JsonPropertyDescription("Total SGST/UTGST amount in rupees, 0 if not charged") BigDecimal sgst,
        @JsonPropertyDescription("Total IGST amount in rupees, 0 if not charged") BigDecimal igst,
        @JsonPropertyDescription("Total compensation cess in rupees, 0 if not charged") BigDecimal cess,
        @JsonPropertyDescription("Grand total payable including taxes and round-off, in rupees") BigDecimal totalAmount,
        @JsonPropertyDescription("Every billed line item") List<LineItem> lineItems,
        @JsonPropertyDescription("Best-fit expense category, one of: RAW_MATERIALS, INVENTORY, OFFICE_SUPPLIES, "
                + "IT_AND_SOFTWARE, EQUIPMENT_AND_ASSETS, FUEL_AND_TRAVEL, LOGISTICS, RENT_AND_UTILITIES, "
                + "TELECOM_AND_INTERNET, PROFESSIONAL_SERVICES, MARKETING, REPAIRS_AND_MAINTENANCE, "
                + "FOOD_AND_BEVERAGES, MOTOR_VEHICLES, OTHER") String suggestedCategory,
        @JsonPropertyDescription("Your confidence that every field is read correctly, 0.0 to 1.0") Double confidence,
        @JsonPropertyDescription("Anything unreadable, ambiguous or unusual about the bill") String notes) {

    public record LineItem(
            String description,
            @JsonPropertyDescription("HSN (goods) or SAC (services) code, digits only") String hsnSac,
            BigDecimal quantity,
            BigDecimal unitPrice,
            @JsonPropertyDescription("Taxable value of this line before GST") BigDecimal taxableValue,
            @JsonPropertyDescription("Combined GST rate in percent, e.g. 18 for 9% CGST + 9% SGST") BigDecimal gstRate,
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst) {
    }
}
