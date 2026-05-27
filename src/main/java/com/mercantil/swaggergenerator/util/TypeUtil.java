package com.mercantil.swaggergenerator.util;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TypeUtil {

    private static final Set<String> PRIMITIVES = Set.of(
        "String",
        "Integer", "int",
        "Long", "long",
        "Double", "double",
        "Boolean", "boolean",
        "BigDecimal", "BigInteger",
        "UUID",
        "LocalDate", "LocalDateTime", "Date"
    );

    public boolean isPrimitive(String type) {
        return type != null && PRIMITIVES.contains(type);
    }

    public String mapType(String type) {

        if (type == null) return "string";

        switch (type) {

            case "String":
            case "UUID":
            case "LocalDate":
            case "LocalDateTime":
            case "Date":
                return "string";

            case "Integer":
            case "int":
            case "Long":
            case "long":
                return "integer";

            case "Double":
            case "double":
            case "BigDecimal":
                return "number";

            case "Boolean":
            case "boolean":
                return "boolean";

            default:
                return "string";
        }
    }

    public boolean isNumericType(String type) {
        return "Integer".equals(type) || "int".equals(type)
                || "Long".equals(type) || "long".equals(type)
                || "Double".equals(type) || "double".equals(type)
                || "BigDecimal".equals(type);
    }
}