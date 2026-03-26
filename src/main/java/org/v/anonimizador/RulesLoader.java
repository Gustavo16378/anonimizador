package org.v.anonimizador;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;

public class RulesLoader {

    private static RedactionRules cached;

    public static RedactionRules load() {
        if (cached != null) return cached;
        try (InputStream in = RulesLoader.class.getClassLoader().getResourceAsStream("redaction-rules.json")) {
            if (in == null) throw new RuntimeException("redaction-rules.json não encontrado");
            ObjectMapper mapper = new ObjectMapper();
            cached = mapper.readValue(in, RedactionRules.class);
            return cached;
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar redaction-rules.json", e);
        }
    }
}