package com.mercantil.swaggergenerator.util;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class TypeUtil {

    private static final Set<String> PRIMITIVES = Set.of(

            "String",

            "Integer",
            "int",

            "Long",
            "long",

            "Double",
            "double",

            "Float",
            "float",

            "Boolean",
            "boolean",

            "Byte",
            "byte",

            "Short",
            "short",

            "Character",
            "char",

            "BigDecimal",
            "BigInteger",

            "UUID",

            "LocalDate",
            "LocalDateTime",
            "LocalTime",

            "Date"
    );

    // =========================================================
    // ✅ IS PRIMITIVE
    // =========================================================
    public boolean isPrimitive(String type) {

        if (type == null || type.isBlank()) {

            return true;
        }

        String clean =
                type.trim();

        // =====================================================
        // ✅ OPTIONAL
        // =====================================================
        if (clean.startsWith("Optional<")
                && clean.endsWith(">")) {

            clean =
                    clean.substring(
                            clean.indexOf("<") + 1,
                            clean.lastIndexOf(">"));
        }

        // =====================================================
        // ✅ LIST / SET / MAP NO SON PRIMITIVE
        // =====================================================
        if (clean.startsWith("List<")
                || clean.startsWith("Set<")
                || clean.startsWith("Map<")) {

            return false;
        }

        // =====================================================
        // ✅ BODY / HEADER / BEANS JAMAS SON PRIMITIVE
        // =====================================================
        if (clean.startsWith("Bean")
                || clean.startsWith("Body")
                || clean.startsWith("Header")) {

            return false;
        }

        // =====================================================
        // ✅ CLASES CUSTOM JAVA
        // PascalCase → probablemente bean
        // =====================================================
        if (Character.isUpperCase(clean.charAt(0))
                && !PRIMITIVES.contains(clean)) {

            return false;
        }

        // =====================================================
        // ✅ PRIMITIVE REAL
        // =====================================================
        return PRIMITIVES.contains(clean);
    }

    // =========================================================
    // ✅ MAP TYPE OPENAPI
    // =========================================================
    public String mapType(String type) {

        if (type == null) {

            return "string";
        }

        switch (type) {

            case "String":
            case "UUID":
            case "LocalDate":
            case "LocalDateTime":
            case "LocalTime":
            case "Date":

                return "string";

            case "Integer":
            case "int":
            case "Long":
            case "long":
            case "Short":
            case "short":

                return "integer";

            case "Double":
            case "double":
            case "Float":
            case "float":
            case "BigDecimal":

                return "number";

            case "Boolean":
            case "boolean":

                return "boolean";

            default:

                // ✅ custom beans
                return "object";
        }
    }

    // =========================================================
    // ✅ IS NUMERIC TYPE
    // =========================================================
    public boolean isNumericType(String type) {

        return "Integer".equals(type)
                || "int".equals(type)

                || "Long".equals(type)
                || "long".equals(type)

                || "Double".equals(type)
                || "double".equals(type)

                || "Float".equals(type)
                || "float".equals(type)

                || "Short".equals(type)
                || "short".equals(type)

                || "BigDecimal".equals(type);
    }
}