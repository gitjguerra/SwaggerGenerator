package com.mercantil.swaggergenerator.util;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class TypeUtil {

    // =========================================================
    // ✅ CONSTANTES DE TIPOS
    // =========================================================

    private static final String STRING = "String";

    private static final String INTEGER = "Integer";
    private static final String INT = "int";

    private static final String LONG = "Long";
    private static final String LONG_PRIMITIVE = "long";

    private static final String DOUBLE = "Double";
    private static final String DOUBLE_PRIMITIVE = "double";

    private static final String FLOAT = "Float";
    private static final String FLOAT_PRIMITIVE = "float";

    private static final String SHORT = "Short";
    private static final String SHORT_PRIMITIVE = "short";

    private static final String BOOLEAN = "Boolean";
    private static final String BOOLEAN_PRIMITIVE = "boolean";

    private static final String BYTE = "Byte";
    private static final String BYTE_PRIMITIVE = "byte";

    private static final String CHARACTER = "Character";
    private static final String CHAR_PRIMITIVE = "char";

    private static final String BIG_DECIMAL = "BigDecimal";
    private static final String BIG_INTEGER = "BigInteger";

    private static final String UUID = "UUID";

    private static final String LOCAL_DATE = "LocalDate";
    private static final String LOCAL_DATE_TIME = "LocalDateTime";
    private static final String LOCAL_TIME = "LocalTime";

    private static final String DATE = "Date";

    // =========================================================
    // ✅ TIPOS PRIMITIVOS
    // =========================================================

    private static final Set<String> PRIMITIVES = Set.of(

            STRING,

            INTEGER,
            INT,

            LONG,
            LONG_PRIMITIVE,

            DOUBLE,
            DOUBLE_PRIMITIVE,

            FLOAT,
            FLOAT_PRIMITIVE,

            BOOLEAN,
            BOOLEAN_PRIMITIVE,

            BYTE,
            BYTE_PRIMITIVE,

            SHORT,
            SHORT_PRIMITIVE,

            CHARACTER,
            CHAR_PRIMITIVE,

            BIG_DECIMAL,
            BIG_INTEGER,

            UUID,

            LOCAL_DATE,
            LOCAL_DATE_TIME,
            LOCAL_TIME,

            DATE
    );

    // =========================================================
    // ✅ TIPOS NUMÉRICOS
    // =========================================================

    private static final Set<String> NUMERIC_TYPES = Set.of(

            INTEGER,
            INT,

            LONG,
            LONG_PRIMITIVE,

            DOUBLE,
            DOUBLE_PRIMITIVE,

            FLOAT,
            FLOAT_PRIMITIVE,

            SHORT,
            SHORT_PRIMITIVE,

            BIG_DECIMAL
    );

    // =========================================================
    // ✅ IS PRIMITIVE
    // =========================================================

    public boolean isPrimitive(String type) {

        if (type == null || type.isBlank()) {
            return true;
        }

        String clean = type.trim();

        if (clean.startsWith("Optional<")
                && clean.endsWith(">")) {

            clean = clean.substring(
                    clean.indexOf('<') + 1,
                    clean.lastIndexOf('>'));
        }

        if (clean.startsWith("List<")
                || clean.startsWith("Set<")
                || clean.startsWith("Map<")) {

            return false;
        }

        if (clean.startsWith("Bean")
                || clean.startsWith("Body")
                || clean.startsWith("Header")) {

            return false;
        }

        if (Character.isUpperCase(clean.charAt(0))
                && !PRIMITIVES.contains(clean)) {

            return false;
        }

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

            case STRING:
            case UUID:
            case LOCAL_DATE:
            case LOCAL_DATE_TIME:
            case LOCAL_TIME:
            case DATE:
                return "string";

            case INTEGER:
            case INT:
            case LONG:
            case LONG_PRIMITIVE:
            case SHORT:
            case SHORT_PRIMITIVE:
                return "integer";

            case DOUBLE:
            case DOUBLE_PRIMITIVE:
            case FLOAT:
            case FLOAT_PRIMITIVE:
            case BIG_DECIMAL:
                return "number";

            case BOOLEAN:
            case BOOLEAN_PRIMITIVE:
                return BOOLEAN_PRIMITIVE;

            default:
                return "object";
        }
    }

    // =========================================================
    // ✅ IS NUMERIC TYPE
    // =========================================================

    public boolean isNumericType(String type) {

        return NUMERIC_TYPES.contains(type);
    }
}