package com.gstagent.agent;

import java.util.List;

public interface InvoiceExtractor {

    record PageImage(byte[] bytes, String mimeType) {
    }

    /**
     * @param pages    rendered pages of the bill
     * @param feedback validation problems from a previous attempt that the model should re-check, or null
     */
    ExtractedInvoice extract(List<PageImage> pages, String feedback);
}
