package org.v.anonimizador;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameDetector {

    public static Set<String> detectNames(PDDocument doc, RedactionRules rules) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        String fullText = stripper.getText(doc);

        Set<String> names = new LinkedHashSet<>();
        Set<String> stop = new HashSet<>();
        if (rules.stopWords != null) stop.addAll(rules.stopWords);

        String labelRegex = "(?im)(?:" + String.join("|", rules.nameLabels) + ")\\s*:?\\s+(.+?)(?:\\s{3,}|\\n|$)";
        Pattern labelPattern = Pattern.compile(labelRegex);

        Matcher m = labelPattern.matcher(fullText);
        while (m.find()) {
            String candidate = cleanCandidate(m.group(m.groupCount()).trim());
            if (isLikelyName(candidate, stop)) names.add(candidate);
        }

        return names;
    }

    private static String cleanCandidate(String candidate) {
        candidate = candidate.replaceAll("\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}", "").trim();
        candidate = candidate.replaceAll("\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}", "").trim();
        candidate = candidate.replaceAll("\\b\\d+\\b", "").trim();
        candidate = candidate.replaceAll("[,;.:\\-]+$", "").trim();
        return candidate;
    }

    private static boolean isLikelyName(String candidate, Set<String> stopWords) {
        if (candidate == null || candidate.length() < 5) return false;
        String[] words = candidate.split("\\s+");
        if (words.length < 2) return false;

        for (String w : words) {
            if (stopWords.contains(w.toUpperCase())) return false;
        }
        return true;
    }
}