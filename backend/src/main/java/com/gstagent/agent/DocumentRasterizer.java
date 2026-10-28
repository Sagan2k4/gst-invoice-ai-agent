package com.gstagent.agent;

import com.gstagent.agent.InvoiceExtractor.PageImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Vision models take images, so PDFs are rendered page-by-page to PNG. Images pass through unchanged. */
@Component
public class DocumentRasterizer {

    private static final int MAX_PAGES = 3;
    private static final float DPI = 150f;

    public List<PageImage> toImages(byte[] bytes, String contentType) throws IOException {
        if (!"application/pdf".equals(contentType)) {
            return List.of(new PageImage(bytes, contentType));
        }
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pages = Math.min(doc.getNumberOfPages(), MAX_PAGES);
            List<PageImage> images = new ArrayList<>(pages);
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                images.add(new PageImage(out.toByteArray(), "image/png"));
            }
            if (images.isEmpty()) {
                throw new IOException("PDF has no pages");
            }
            return images;
        }
    }
}
