package com.gstagent.agent;

import com.gstagent.validation.GstinValidator;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tools the vision model can call while reading a bill. They let it verify what it read
 * (e.g. catch an 'O' misread as '0' in a GSTIN) instead of guessing.
 */
@Component
public class GstTools {

    public record GstinCheck(String gstin, boolean formatValid, boolean stateCodeValid, boolean checksumValid,
                             String stateName, String expectedChecksumChar) {
    }

    public record TaxCheck(BigDecimal expectedTax, BigDecimal expectedHalf, BigDecimal expectedTotal) {
    }

    @Tool(description = "Validate an Indian GSTIN: 15-character format, state code and mod-36 checksum. "
            + "Use it on every GSTIN you read; if the checksum fails, re-read ambiguous characters "
            + "(0/O, 1/I, 5/S, 8/B, 2/Z).")
    public GstinCheck validateGstin(@ToolParam(description = "The GSTIN as read from the bill") String gstin) {
        String g = GstinValidator.normalize(gstin);
        boolean format = GstinValidator.isValidFormat(g);
        return new GstinCheck(g, format,
                GstinValidator.isValidStateCode(g),
                GstinValidator.isValidChecksum(g),
                g != null && g.length() >= 2 ? GstinValidator.stateName(g.substring(0, 2)) : null,
                g != null && g.length() >= 14 ? String.valueOf(GstinValidator.checksumChar(g.substring(0, 14))) : null);
    }

    @Tool(description = "Compute the GST due on a taxable value at a given combined rate. Returns total tax, "
            + "each half for CGST/SGST, and taxable value plus tax.")
    public TaxCheck computeGst(@ToolParam(description = "Taxable value in rupees") BigDecimal taxableValue,
                               @ToolParam(description = "Combined GST rate in percent, e.g. 18") BigDecimal ratePercent) {
        BigDecimal tax = taxableValue.multiply(ratePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal half = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        return new TaxCheck(tax, half, taxableValue.add(tax).setScale(2, RoundingMode.HALF_UP));
    }

    @Tool(description = "Look up the Indian state or union territory name for a 2-digit GST state code")
    public String stateForCode(@ToolParam(description = "2-digit GST state code") String code) {
        String name = GstinValidator.stateName(code);
        return name == null ? "Unknown state code" : name;
    }
}
