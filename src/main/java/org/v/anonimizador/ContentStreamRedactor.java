package org.v.anonimizador;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDStream;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class ContentStreamRedactor {

    private final List<Pattern> patterns;
    private final Set<String> sensitiveNames;

    public ContentStreamRedactor(List<Pattern> patterns, Set<String> sensitiveNames) {
        this.patterns = patterns;
        this.sensitiveNames = sensitiveNames;
    }

    public void redact(PDDocument doc) throws IOException {
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            redactPage(doc, page);
        }
    }

    private void redactPage(PDDocument doc, PDPage page) throws IOException {
        PDFStreamParser parser = new PDFStreamParser(page);
        parser.parse();
        List<Object> tokens = parser.getTokens();
        List<Object> newTokens = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);

            if (token instanceof Operator) {
                Operator op = (Operator) token;
                String name = op.getName();

                if ("Tj".equals(name) || "'".equals(name) || "\"".equals(name)) {
                    if (!newTokens.isEmpty()) {
                        Object prev = newTokens.get(newTokens.size() - 1);
                        if (prev instanceof COSString) {
                            COSString cosStr = (COSString) prev;
                            String original = cosStr.getString();
                            String replaced = replaceIfSensitive(original);
                            if (!replaced.equals(original)) {
                                newTokens.set(newTokens.size() - 1, new COSString(replaced.getBytes("ISO-8859-1")));
                            }
                        }
                    }
                    newTokens.add(token);
                } else if ("TJ".equals(name)) {
                    if (!newTokens.isEmpty()) {
                        Object prev = newTokens.get(newTokens.size() - 1);
                        if (prev instanceof COSArray) {
                            COSArray arr = (COSArray) prev;
                            COSArray newArr = new COSArray();
                            for (int j = 0; j < arr.size(); j++) {
                                COSBase item = arr.get(j);
                                if (item instanceof COSString) {
                                    COSString cosStr = (COSString) item;
                                    String original = cosStr.getString();
                                    String replaced = replaceIfSensitive(original);
                                    if (!replaced.equals(original)) {
                                        newArr.add(new COSString(replaced.getBytes("ISO-8859-1")));
                                    } else {
                                        newArr.add(item);
                                    }
                                } else {
                                    newArr.add(item);
                                }
                            }
                            newTokens.set(newTokens.size() - 1, newArr);
                        }
                    }
                    newTokens.add(token);
                } else {
                    newTokens.add(token);
                }
            } else {
                newTokens.add(token);
            }
        }

        PDStream newStream = new PDStream(doc);
        OutputStream out = newStream.createOutputStream();
        ContentStreamWriter writer = new ContentStreamWriter(out);
        writer.writeTokens(newTokens);
        out.close();
        page.setContents(newStream);
    }

    private String replaceIfSensitive(String text) {
        if (text == null || text.trim().isEmpty()) return text;

        String result = text;

        for (Pattern p : patterns) {
            result = p.matcher(result).replaceAll("XXXX");
        }

        for (String name : sensitiveNames) {
            if (name.length() < 4) continue;
            String upper = result.toUpperCase();
            String nameUpper = name.toUpperCase();
            int idx = upper.indexOf(nameUpper);
            while (idx >= 0) {
                result = result.substring(0, idx) + "XXXX" + result.substring(idx + name.length());
                upper = result.toUpperCase();
                idx = upper.indexOf(nameUpper, idx + 4);
            }
        }

        return result;
    }
}