package com.gstagent.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

/** Reads bills with an OpenAI vision model through Spring AI, with GST tools available for self-checks. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
public class OpenAiVisionExtractor implements InvoiceExtractor {

    static final String SYSTEM_PROMPT = """
            You are a meticulous accounts assistant for a small Indian business. You read photographed or
            scanned GST purchase bills (tax invoices) and transcribe them into structured data.

            Rules:
            - Transcribe only what is printed. Never invent values; use null for anything absent or unreadable.
            - Amounts are in Indian rupees. Return plain numbers: no currency symbols, no thousands separators.
            - Indian bills write numbers in lakh grouping (1,25,000.00 = 125000.00).
            - Dates are usually printed DD/MM/YYYY or DD-MM-YY; return yyyy-MM-dd.
            - A GSTIN is 15 characters: 2-digit state code, 10-char PAN, 1 entity char, 'Z', 1 checksum char.
              Call validateGstin on every GSTIN you read. If it fails, look again at commonly confused
              characters (0/O, 1/I, 5/S, 8/B, 2/Z) and try the corrected reading.
            - Intra-state bills charge CGST + SGST (equal halves); inter-state bills charge IGST only.
            - HSN codes for goods are 4, 6 or 8 digits; SAC codes for services are 6 digits starting with 99.
            - Use computeGst to double-check line and total tax amounts when the print is unclear.
            - Lower your confidence for blurry, cropped or handwritten bills, and explain why in notes.
            """;

    private final ChatClient chatClient;
    private final GstTools gstTools;

    public OpenAiVisionExtractor(ChatClient.Builder builder, GstTools gstTools) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.gstTools = gstTools;
    }

    @Override
    public ExtractedInvoice extract(List<PageImage> pages, String feedback) {
        String instruction = feedback == null
                ? "Extract this GST purchase bill. It has " + pages.size() + " page image(s)."
                : """
                  Extract this GST purchase bill again. A previous reading failed these compliance checks:
                  %s
                  Look closely at the bill for each problem. If the bill itself is genuinely wrong
                  (e.g. its own arithmetic is off), keep the values as printed and say so in notes.
                  """.formatted(feedback);

        log.debug("Calling vision model with {} page(s), correction pass: {}", pages.size(), feedback != null);
        return chatClient.prompt()
                .user(u -> {
                    u.text(instruction);
                    for (PageImage page : pages) {
                        u.media(MimeTypeUtils.parseMimeType(page.mimeType()), new ByteArrayResource(page.bytes()));
                    }
                })
                .tools(gstTools)
                .call()
                .entity(ExtractedInvoice.class);
    }
}
