package com.mercantil.swaggergenerator.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.mercantil.swaggergenerator.util.ParserUtil;
import com.mercantil.swaggergenerator.util.TypeUtil;

@Component
public class SchemaBuilder {

    @Autowired
    private TypeUtil typeUtil;

    @Autowired
    private ParserUtil parserUtil;

    @Autowired
    private ClassIndexer classIndexer;

    private Map<String, Map<String, Object>> schemaMap;

    public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {
        this.schemaMap = schemaMap;
    }

    public Map<String, Object> build(ClassOrInterfaceDeclaration clazz) {

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        // =========================================================
        // ✅ ENUM
        // =========================================================
        if (clazz.isEnumDeclaration()) {
            List<String> values = clazz.asEnumDeclaration().getEntries().stream()
                    .map(e -> e.getNameAsString())
                    .collect(Collectors.toList());

            return Map.of("type", "string", "enum", values);
        }

        // =========================================================
        // ✅ PROCESAR CAMPOS
        // =========================================================
        clazz.getFields().forEach(field -> {

            field.getVariables().forEach(var -> {

                String name = parserUtil.resolveJsonName(field, var.getNameAsString());

                String rawType = field.getElementType().asString();
                boolean isOptional = rawType.startsWith("Optional<");

                String cleanType = isOptional ? parserUtil.extractGeneric(rawType) : rawType;
                String type = parserUtil.resolveFinalType(cleanType);

                Map<String, Object> prop = new LinkedHashMap<>();

                // =================================================
                // ✅ LIST
                // =================================================
                if (cleanType.startsWith("List<") || rawType.contains("List<")) {
                    handleList(prop, rawType);
                }

                // =================================================
                // ✅ PRIMITIVE
                // =================================================
                else if (typeUtil.isPrimitive(type)) {
                    prop.put("type", typeUtil.mapType(type));
                    applyFormat(prop, type);
                }

                // =================================================
                // ✅ OBJECT (🔥 AQUÍ ESTÁ EL FIX REAL)
                // =================================================
                else {
                    ensureSchemaWithParsing(type);
                    prop.put("$ref", "#/components/schemas/" + type);
                }

                if (isOptional) {
                    prop.put("nullable", true);
                }

                if (field.getAnnotationByName("NotNull").isPresent()) {
                    required.add(name);
                }

                properties.put(name, prop);
            });
        });

        // =========================================================
        // ✅ ARMAR SCHEMA FINAL
        // =========================================================
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        schema.put("properties", properties.isEmpty()
                ? new LinkedHashMap<>()
                : properties);

        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    // =========================================================
    // ✅ LIST
    // =========================================================
    private void handleList(Map<String, Object> prop, String rawType) {

        String generic = parserUtil.resolveFinalType(
                parserUtil.extractGeneric(rawType)
        );

        prop.put("type", "array");

        if (typeUtil.isPrimitive(generic)) {

            prop.put("items", Map.of(
                    "type", typeUtil.mapType(generic)
            ));

        } else {

            ensureSchemaWithParsing(generic);

            prop.put("items", Map.of(
                    "$ref", "#/components/schemas/" + generic
            ));
        }
    }

    // =========================================================
    // ✅ 🔥 FIX PRINCIPAL: PARSEO REAL DE CLASES
    // =========================================================
    private void ensureSchemaWithParsing(String type) {

        if (type == null || type.isBlank()) return;

        // ✅ ya existe
        if (schemaMap.containsKey(type)) return;

        // ✅ intentar encontrar clase real en el proyecto
        classIndexer.findClass(type).ifPresentOrElse(clazz -> {

            System.out.println("✅ Parsing real class: " + type);

            Map<String, Object> schema = build(clazz);
            schemaMap.put(type, schema);

        }, () -> {

            // ⚠️ fallback controlado (EVITA {})
            System.out.println("⚠️ No class found, fallback: " + type);

            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("type", "object");

            // 👇 evita que Swagger lo deje vacío
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("valor", Map.of("type", "string"));

            fallback.put("properties", props);

            schemaMap.put(type, fallback);
        });
    }

    // =========================================================
    private void applyFormat(Map<String, Object> prop, String type) {

        if ("UUID".equals(type))
            prop.put("format", "uuid");

        if ("LocalDate".equals(type))
            prop.put("format", "date");

        if ("LocalDateTime".equals(type) || "Date".equals(type))
            prop.put("format", "date-time");

        if ("BigDecimal".equals(type))
            prop.put("format", "double");
    }
}