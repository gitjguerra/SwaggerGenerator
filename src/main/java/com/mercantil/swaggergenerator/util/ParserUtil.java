package com.mercantil.swaggergenerator.util;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;

@Component
public class ParserUtil {

    // =========================================================
    // ✅ JSON PROPERTY
    // =========================================================
    public String resolveJsonName(FieldDeclaration field, String defaultName) {

        Optional<AnnotationExpr> opt = field.getAnnotationByName("JsonProperty");

        if (opt.isPresent()) {

            AnnotationExpr ann = opt.get();

            try {

                // ✅ @JsonProperty("name")
                if (ann.isSingleMemberAnnotationExpr()) {
                    Expression value =
                        ann.asSingleMemberAnnotationExpr().getMemberValue();

                    return value.asStringLiteralExpr().asString();
                }

                // ✅ @JsonProperty(value = "name")
                if (ann.isNormalAnnotationExpr()) {

                    for (MemberValuePair pair :
                            ann.asNormalAnnotationExpr().getPairs()) {

                        if ("value".equals(pair.getNameAsString())) {
                            return pair.getValue()
                                       .asStringLiteralExpr()
                                       .asString();
                        }
                    }
                }

            } catch (Exception ignored) {
            }
        }

        return defaultName;
    }

    // =========================================================
    // ✅ GENERIC SIMPLE
    // =========================================================
    public String extractGeneric(String input) {

        if (input == null) return null;

        int start = input.indexOf("<");
        int end = input.lastIndexOf(">");

        if (start == -1 || end == -1 || end <= start) {
            return input;
        }

        String inner = input.substring(start + 1, end);

        // 🔥 soporta generics anidados
        if (inner.contains("<")) {
            return extractGeneric(inner);
        }

        return inner.trim();
    }

    // =========================================================
    // ✅ EXTRAER VALOR DE MAP<K,V>
    // =========================================================
    public String extractMapValue(String input) {

        if (input == null || !input.contains(",")) return "Object";

        int start = input.indexOf("<");
        int end = input.lastIndexOf(">");

        if (start == -1 || end == -1) return "Object";

        String inside = input.substring(start + 1, end);

        String[] parts = inside.split(",");

        if (parts.length < 2) return "Object";

        return resolveFinalType(parts[1].trim());
    }

    // =========================================================
    // ✅ LIMPIAR TIPO FINAL
    // =========================================================
    public String resolveFinalType(String type) {

        if (type == null) return null;

        // 🔥 quitar Optional
        if (type.startsWith("Optional<")) {
            type = extractGeneric(type);
        }

        // 🔥 quitar paquetes
        if (type.contains(".")) {
            type = type.substring(type.lastIndexOf(".") + 1);
        }

        // 🔥 quitar generics restantes
        if (type.contains("<")) {
            type = extractGeneric(type);
        }

        return type.trim();
    }

    // =========================================================
    // ✅ HELPERS PRO
    // =========================================================
    public boolean isList(String type) {
        return type != null && (type.contains("List<") || type.contains("Set<"));
    }

    public boolean isMap(String type) {
        return type != null && type.startsWith("Map<");
    }

    public boolean isOptional(String type) {
        return type != null && type.startsWith("Optional<");
    }

    public String unwrapOptional(String type) {
        if (isOptional(type)) {
            return extractGeneric(type);
        }
        return type;
    }

    // =========================================================
    // 🔥 GENERIC COMPLEJO (NIVEL ENTERPRISE)
    // =========================================================
    public String extractDeepestType(String type) {

        if (type == null) return null;

        // ejemplos:
        // List<Map<String, User>> -> User
        // Optional<List<Account>> -> Account

        while (type.contains("<")) {
            type = extractGeneric(type);
        }

        return resolveFinalType(type);
    }
}