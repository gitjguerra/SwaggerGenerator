package com.mercantil.swaggergenerator.component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
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
    private Map<String, EnumDeclaration> enumMap; // ✅ NUEVO

    public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {
        this.schemaMap = schemaMap;
    }

    public void setEnumMap(Map<String, EnumDeclaration> enumMap) {
        this.enumMap = enumMap;
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
        // 🔥 HERENCIA
        // =========================================================
        clazz.getExtendedTypes().forEach(ext -> {

            String parentName = ext.getNameAsString();

            classIndexer.findClass(parentName).ifPresent(parentClazz -> {

                Map<String, Object> parentSchema = build(parentClazz);

                if (parentSchema.get("properties") != null) {
                    properties.putAll((Map<String, Object>) parentSchema.get("properties"));
                }

                if (parentSchema.get("required") != null) {
                    required.addAll((List<String>) parentSchema.get("required"));
                }
            });
        });

        // =========================================================
        // ✅ PROCESAR CAMPOS
        // =========================================================
        clazz.getFields().forEach(field -> {

            field.getVariables().forEach(var -> {

                String name = parserUtil.resolveJsonName(field, var.getNameAsString());

                String rawType = field.getElementType().asString();
                boolean isOptional = rawType.startsWith("Optional<");

                String cleanType = isOptional
                        ? parserUtil.extractGeneric(rawType)
                        : rawType;

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
                // ✅ OBJECT (ENTERPRISE)
                // =================================================
                else {

                    // ✅ ENUM
                    if (enumMap != null && enumMap.containsKey(type)) {

                        ensureEnumSchema(type);
                        prop.put("$ref", "#/components/schemas/" + type);
                    }

                    // ✅ MAP
                    else if (rawType.startsWith("Map<")) {

                        String valueType = parserUtil.extractMapValue(rawType);

                        prop.put("type", "object");

                        if (typeUtil.isPrimitive(valueType)) {

                            prop.put("additionalProperties",
                                    Map.of("type", typeUtil.mapType(valueType)));

                        } else {

                            ensureSchemaWithParsing(valueType);

                            prop.put("additionalProperties",
                                    Map.of("$ref", "#/components/schemas/" + valueType));
                        }
                    }

                    // ✅ OBJETO NORMAL
                    else {
                        ensureSchemaWithParsing(type);
                        prop.put("$ref", "#/components/schemas/" + type);
                    }
                }

                // ✅ OPTIONAL
                if (isOptional) {
                    prop.put("nullable", true);
                }

                // ✅ REQUIRED
                if (field.getAnnotationByName("NotNull").isPresent()
                        || field.getAnnotationByName("NotEmpty").isPresent()
                        || field.getAnnotationByName("NotBlank").isPresent()) {

                    required.add(name);
                }

                // ✅ ANNOTATIONS
                applyAnnotations(field, prop);

                properties.put(name, prop);
            });
        });

        // =========================================================
        // ✅ SCHEMA FINAL
        // =========================================================
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        if (properties.isEmpty()) {
            schema.put("description", "Clase sin propiedades definidas");
        } else {
            schema.put("properties", properties);
        }

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
    // ✅ ENUM BUILDER
    // =========================================================
    private void ensureEnumSchema(String enumName) {

        if (schemaMap.containsKey(enumName)) return;

        if (enumMap == null || !enumMap.containsKey(enumName)) return;

        EnumDeclaration enumDecl = enumMap.get(enumName);

        List<String> values = enumDecl.getEntries().stream()
                .map(e -> e.getNameAsString())
                .collect(Collectors.toList());

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("enum", values);

        schemaMap.put(enumName, schema);
    }

    // =========================================================
    // ✅ PARSEO REAL
    // =========================================================
    private void ensureSchemaWithParsing(String type) {

        if (type == null || type.isBlank()) return;

        if (schemaMap.containsKey(type)) return;

        if (!isValidModel(type)) return;

        classIndexer.findClass(type).ifPresentOrElse(clazz -> {

            System.out.println("✅ Parsing real class: " + type);

            Map<String, Object> schema = build(clazz);
            schemaMap.put(type, schema);

        }, () -> {

            System.out.println("⚠️ No class found, fallback: " + type);

            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("type", "object");
            fallback.put("description", "Objeto no resuelto dinámicamente");
            fallback.put("properties", new LinkedHashMap<>());

            schemaMap.put(type, fallback);
        });
    }

    // =========================================================
    // ✅ ANNOTATIONS
    // =========================================================
    private void applyAnnotations(FieldDeclaration field, Map<String, Object> prop) {

        for (AnnotationExpr ann : field.getAnnotations()) {

            String name = ann.getNameAsString();

            // ✅ SIZE
            if (name.equals("Size")) {
                ann.ifNormalAnnotationExpr(a -> {
                    for (MemberValuePair pair : a.getPairs()) {

                        if (pair.getNameAsString().equals("min")) {
                            prop.put("minLength", Integer.parseInt(pair.getValue().toString()));
                        }
                        if (pair.getNameAsString().equals("max")) {
                            prop.put("maxLength", Integer.parseInt(pair.getValue().toString()));
                        }
                    }
                });
            }

            // ✅ SCHEMA / APIMODEL
            if (name.equals("Schema") || name.equals("ApiModelProperty")) {

                ann.ifNormalAnnotationExpr(a -> {
                    for (MemberValuePair p : a.getPairs()) {

                        if (p.getNameAsString().equals("description")) {
                            prop.put("description", p.getValue().toString().replace("\"", ""));
                        }

                        if (p.getNameAsString().equals("example")) {
                            prop.put("example", p.getValue().toString().replace("\"", ""));
                        }
                    }
                });
            }
        }
    }

    // =========================================================
    // ✅ FILTRO BASURA
    // =========================================================
    private boolean isValidModel(String name) {
        return !(name.endsWith("Impl")
                || name.contains("Logger")
                || name.contains("Client")
                || name.contains("Config")
                || name.contains("Filter")
                || name.contains("Finder"));
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