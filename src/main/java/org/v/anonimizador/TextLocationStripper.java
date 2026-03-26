package org.v.anonimizador;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextLocationStripper extends PDFTextStripper {

    public static class Hit {
        public final int pageIndex;
        public final float x, y, w, h;
        public final String reason;

        public Hit(int pageIndex, float x, float y, float w, float h, String reason) {
            this.pageIndex = pageIndex;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.reason = reason;
        }
    }

    private static class Glyph {
        final TextPosition tp;
        final String s;
        final float x, y, w, h;

        Glyph(TextPosition tp) {
            this.tp = tp;
            this.s = tp.getUnicode();
            this.x = tp.getXDirAdj();
            this.y = tp.getYDirAdj();
            this.w = tp.getWidthDirAdj();
            this.h = tp.getHeightDir();
        }

        boolean isDigit() {
            return s != null && s.length() == 1 && Character.isDigit(s.charAt(0));
        }
    }

    private static class Line {
        final int pageIndex;
        final List<Glyph> glyphs = new ArrayList<>();
        Line(int pageIndex) { this.pageIndex = pageIndex; }
    }

    private final List<List<Line>> pages = new ArrayList<>();
    private List<Line> currentLines;
    private Line currentLine;
    private int pageIndex = -1;
    private float lastY = Float.NaN;

    public TextLocationStripper() throws IOException {
        setSortByPosition(true);
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        super.startPage(page);
        pageIndex++;
        currentLines = new ArrayList<>();
        pages.add(currentLines);
        currentLine = null;
        lastY = Float.NaN;
    }

    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        for (TextPosition tp : textPositions) {
            Glyph g = new Glyph(tp);
            float y = g.y;

            if (currentLine == null || Float.isNaN(lastY) || Math.abs(y - lastY) > 2.0f) {
                currentLine = new Line(pageIndex);
                currentLines.add(currentLine);
            }
            currentLine.glyphs.add(g);
            lastY = y;
        }
        super.writeString(string, textPositions);
    }

    private static Hit bbox(int pageIndex, List<Glyph> glyphs, int fromInclusive, int toExclusive, String reason) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        for (int i = fromInclusive; i < toExclusive; i++) {
            Glyph g = glyphs.get(i);

            float x = g.x;
            float yTop = g.y;
            float yBottom = g.y - g.h;

            minX = Math.min(minX, x);
            minY = Math.min(minY, yBottom);
            maxX = Math.max(maxX, x + g.w);
            maxY = Math.max(maxY, yTop);
        }
        if (minX == Float.MAX_VALUE) return null;

        float width = maxX - minX;
        float height = maxY - minY;
        return new Hit(pageIndex, minX, minY, width, height, reason);
    }

    private static String lineText(Line line) {
        StringBuilder sb = new StringBuilder();
        for (Glyph g : line.glyphs) sb.append(g.s);
        return sb.toString();
    }

    private static List<Hit> findRegexHitsOnLine(Line line, List<Pattern> patterns) {
        String text = lineText(line);
        List<Hit> hits = new ArrayList<>();
        if (text.trim().isEmpty()) return hits;

        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                int start = Math.max(0, m.start());
                int end = Math.min(line.glyphs.size(), m.end());
                if (start < end) {
                    Hit h = bbox(line.pageIndex, line.glyphs, start, end, "REGEX");
                    if (h != null) hits.add(h);
                }
            }
        }
        return hits;
    }

    private static List<Hit> findNumericHitsOnLine(Line line, List<Integer> lengths) {
        List<Hit> hits = new ArrayList<>();
        List<Glyph> g = line.glyphs;

        StringBuilder digits = new StringBuilder();
        List<Integer> digitToGlyph = new ArrayList<>();

        for (int i = 0; i < g.size(); i++) {
            if (g.get(i).isDigit()) {
                digits.append(g.get(i).s);
                digitToGlyph.add(i);
            }
        }

        if (digits.length() == 0) return hits;

        for (Integer len : lengths) {
            if (digits.length() < len) continue;

            for (int start = 0; start <= digits.length() - len; start++) {
                int end = start + len;
                int gStart = digitToGlyph.get(start);
                int gEnd = digitToGlyph.get(end - 1);
                Hit h = bbox(line.pageIndex, g, Math.min(gStart, gEnd), Math.max(gStart, gEnd) + 1, "NUMERIC");
                if (h != null) hits.add(h);
            }
        }
        return hits;
    }

    private static List<Hit> findBalancedNameHits(List<Line> pageLines,
                                                  Set<String> stopWords,
                                                  List<String> nameLabels) {
        List<Hit> hits = new ArrayList<>();

        Pattern titleCase = Pattern.compile(
                "\\b([A-ZÁÉÍÓÚÂÊÔÃÕÇ][a-záéíóúâêôãõç]+(?:\\s+(?:de|da|do|das|dos|e))?(?:\\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ][a-záéíóúâêôãõç]+){1,7})\\b"
        );
        Pattern allCaps = Pattern.compile(
                "\\b([A-ZÁÉÍÓÚÂÊÔÃÕÇ]{2,}(?:\\s+(?:DE|DA|DO|DAS|DOS|E))?(?:\\s+[A-ZÁÉÍÓÚÂÊÔÃÕÇ]{2,}){2,})\\b"
        );

        String labelRegex = "(?im)\\b(" + String.join("|", nameLabels) + ")\\b\\s*:?\\s*(.+)$";
        Pattern labelPattern = Pattern.compile(labelRegex);

        for (Line line : pageLines) {
            String text = lineText(line);

            Matcher ml = labelPattern.matcher(text);
            if (ml.find()) {
                int start = ml.start(2);
                int end = ml.end(2);
                Hit h = bbox(line.pageIndex, line.glyphs, start, end, "NAME_LABEL");
                if (h != null) hits.add(h);
            }

            Matcher mCaps = allCaps.matcher(text);
            while (mCaps.find()) {
                String candidate = mCaps.group(1).trim();
                if (isLikelyName(candidate, stopWords)) {
                    Hit h = bbox(line.pageIndex, line.glyphs, mCaps.start(), mCaps.end(), "NAME_CAPS");
                    if (h != null) hits.add(h);
                }
            }

            if (text.matches(".*\\b\\d{4,6}\\s*-\\s*[A-ZÁÉÍÓÚÂÊÔÃÕÇ].*")) {
                Matcher m = titleCase.matcher(text);
                while (m.find()) {
                    String candidate = m.group(1).trim();
                    if (isLikelyName(candidate, stopWords)) {
                        Hit h = bbox(line.pageIndex, line.glyphs, m.start(), m.end(), "NAME_BENEF");
                        if (h != null) hits.add(h);
                    }
                }
            }
        }

        for (int i = 0; i < pageLines.size() - 1; i++) {
            String line = lineText(pageLines.get(i)).trim();
            String next = lineText(pageLines.get(i + 1)).toLowerCase();

            boolean hasCargo = next.contains("chefe") || next.contains("coordenador") ||
                    next.contains("diretor") || next.contains("secretário") ||
                    next.contains("assessor") || next.contains("analista") ||
                    next.contains("técnic") || next.contains("seção");

            if (hasCargo) {
                Matcher m = titleCase.matcher(line);
                if (m.find()) {
                    String candidate = m.group(1).trim();
                    if (isLikelyName(candidate, stopWords)) {
                        Hit h = bbox(pageLines.get(i).pageIndex, pageLines.get(i).glyphs, m.start(), m.end(), "NAME_SIGNATURE");
                        if (h != null) hits.add(h);
                    }
                }
            }
        }

        return hits;
    }

    private static boolean isLikelyName(String candidate, Set<String> stopWords) {
        if (candidate == null || candidate.length() < 5) return false;
        String[] words = candidate.split("\\s+");
        if (words.length < 2) return false;

        int nameWords = 0;
        for (String w : words) {
            String up = w.toUpperCase();
            if (stopWords.contains(up)) return false;
            if (!Set.of("DE","DA","DO","DAS","DOS","E").contains(up)) {
                nameWords++;
            }
        }
        return nameWords >= 2;
    }

    public static List<Hit> findHits(PDDocument doc, RedactionRules rules) throws IOException {
        TextLocationStripper stripper = new TextLocationStripper();
        stripper.getText(doc);

        List<Pattern> regexPatterns = new ArrayList<>();
        if (rules.regex != null) {
            for (String r : rules.regex) regexPatterns.add(Pattern.compile(r, Pattern.MULTILINE));
        }
        if (rules.labels != null) {
            for (String label : rules.labels) {
                String regex = "(?im)\\b" + Pattern.quote(label) + "\\b\\s*:?\\s*.+$";
                regexPatterns.add(Pattern.compile(regex));
            }
        }

        Set<String> stopWords = new HashSet<>();
        if (rules.stopWords != null) stopWords.addAll(rules.stopWords);

        List<String> nameLabels = rules.nameLabels != null ? rules.nameLabels : List.of();

        List<Hit> hits = new ArrayList<>();

        for (List<Line> pageLines : stripper.pages) {
            for (Line line : pageLines) {
                hits.addAll(findRegexHitsOnLine(line, regexPatterns));
                hits.addAll(findNumericHitsOnLine(line, rules.numericLengths));
            }
            hits.addAll(findBalancedNameHits(pageLines, stopWords, nameLabels));
        }

        return hits;
    }
}