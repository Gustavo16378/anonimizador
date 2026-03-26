package org.v.anonimizador;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.*;
import java.util.*;

public class PdfRedactor {

    public static byte[] redact(InputStream pdfInput, boolean safeMode) throws IOException {
        RedactionRules rules = RulesLoader.load();

        byte[] overlayed;
        try (PDDocument doc = PDDocument.load(pdfInput)) {

            List<TextLocationStripper.Hit> hits = TextLocationStripper.findHits(doc, rules);
            Map<Integer, List<TextLocationStripper.Hit>> hitsByPage = new LinkedHashMap<>();
            for (TextLocationStripper.Hit hit : hits) {
                hitsByPage.computeIfAbsent(hit.pageIndex, k -> new ArrayList<>()).add(hit);
            }

            for (var entry : hitsByPage.entrySet()) {
                int pageIdx = entry.getKey();
                PDPage page = doc.getPage(pageIdx);
                float pageHeight = page.getMediaBox().getHeight();

                try (PDPageContentStream cs = new PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                )) {
                    for (TextLocationStripper.Hit hit : entry.getValue()) {
                        float rectX = hit.x - 2;
                        float rectW = hit.w + 6;

                        float rectH = Math.max(hit.h, 10) + 6;
                        float rectY = pageHeight - hit.y - rectH + 2;

                        cs.setNonStrokingColor(255, 255, 255);
                        cs.addRect(rectX, rectY, rectW, rectH);
                        cs.fill();

                        float fontSize = Math.max(7, Math.min(rectH * 0.75f, 14));
                        cs.beginText();
                        cs.setNonStrokingColor(0, 0, 0);
                        cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
                        cs.newLineAtOffset(rectX + 2, rectY + 2);
                        cs.showText("XXXX");
                        cs.endText();
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            overlayed = baos.toByteArray();
        }

        if (safeMode) {
            return PdfRasterRedactor.rasterize(new ByteArrayInputStream(overlayed), 250);
        }
        return overlayed;
    }
}