package com.example.demo.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class CodeGenerator {

    private CodeGenerator() {
    }

    public static String slug(String source) {
        String normalized = Normalizer.normalize(source == null ? "" : source, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.isBlank()) {
            normalized = "item";
        }
        return normalized + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String code(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
    }
}
