package org.v.anonimizador;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.awt.image.BufferedImage;
import java.io.*;

public class PdfRasterRedactor {

    public static byte[] rasterize(InputStream pdfInput, int dpi) throws IOException {
        try (PDDocument src = PDDocument.load(pdfInput);
             PDDocument outDoc = new PDDocument()) {

            PDFRenderer renderer = new PDFRenderer(src);

            for (int i = 0; i < src.getNumberOfPages(); i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                PDPage outPage = new PDPage(new PDRectangle(img.getWidth(), img.getHeight()));
                outDoc.addPage(outPage);

                PDImageXObject ximage = LosslessFactory.createFromImage(outDoc, img);

                try (PDPageContentStream cs = new PDPageContentStream(outDoc, outPage)) {
                    cs.drawImage(ximage, 0, 0, img.getWidth(), img.getHeight());
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            outDoc.save(baos);
            return baos.toByteArray();
        }
    }
}