package com.mercantil.swaggergenerator.component;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
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
    private Map<String, EnumDeclaration> enumMap;

    public void setSchemaMap(Map<String, Map<String, Object>> schemaMap) {
        this.schemaMap = schemaMap;
    }

    public void setEnumMap(Map<String, EnumDeclaration> enumMap) {
        this.enumMap = enumMap;
    }

    // =========================================================
    // ✅ BUILD PRINCIPAL
    // =========================================================
    public Map<String, Object> build(ClassOrInterfaceDeclaration clazz) {

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        // ✅ ENUM
        if (clazz.isEnumDeclaration()) {
            List<String> values = clazz.asEnumDeclaration().getEntries()
                    .stream()
                    .map(e -> e.getNameAsString())
                    .collect(Collectors.toList());

            return Map.of("type", "string", "enum", values);
        }

        // =========================================================
        // ✅ HERENCIA
        // =========================================================
        clazz.getExtendedTypes().forEach(ext -> {

            String parent = ext.getNameAsString();

            classIndexer.findClass(parent).ifPresent(parentClazz -> {

                Map<String, Object> parentSchema = build(parentClazz);

                if (parentSchema.get("properties") instanceof Map) {
                    properties.putAll((Map<String, Object>) parentSchema.get("properties"));
                }

                if (parentSchema.get("required") instanceof List) {
                    required.addAll((List<String>) parentSchema.get("required"));
                }
            });
        });

        // =========================================================
        // ✅ CAMPOS
        // =========================================================
        clazz.getFields().forEach(field -> {

            field.getVariables().forEach(var -> {

                String name = parserUtil.resolveJsonName(field, var.getNameAsString());

                Map<String, Object> prop = new LinkedHashMap<>();

                var typeNode = field.getElementType();
                boolean isArray = typeNode.isArrayType();

                String rawType = typeNode.asString();

                boolean isOptional = rawType.startsWith("Optional<");

                String cleanType = isOptional
                        ? parserUtil.extractGeneric(rawType)
                        : rawType;

                String simpleType = parserUtil.resolveFinalType(cleanType);

                String resolvedType = resolveFullType(simpleType, clazz);
                String type = extractSimpleName(resolvedType);

                // =================================================
                // ✅ ARRAY (byte[]) 🔥
                // =================================================
                if (isArray) {

                    String elementType = typeNode.asArrayType()
                            .getComponentType().asString();

                    if ("byte".equals(elementType)) {

                        prop.put("type", "string");
                        prop.put("format", "byte");

                    } else {

                        prop.put("type", "array");

                        String resolved = extractSimpleName(
                                resolveFullType(elementType, clazz)
                        );

                        if (typeUtil.isPrimitive(resolved)) {

                            prop.put("items", Map.of(
                                    "type", typeUtil.mapType(resolved)
                            ));

                        } else {

                            ensureSchemaWithParsing(resolved);

                            prop.put("items", Map.of(
                                    "$ref", "#/components/schemas/" + resolved
                            ));
                        }
                    }
                }

                // =================================================
                // ✅ LIST<T>
                // =================================================
                else if (rawType.contains("List<")) {

                    String generic = parserUtil.extractGeneric(rawType);

                    String resolved = extractSimpleName(
                            resolveFullType(generic, clazz)
                    );

                    prop.put("type", "array");

                    if (typeUtil.isPrimitive(resolved)) {

                        prop.put("items", Map.of(
                                "type", typeUtil.mapType(resolved)
                        ));

                    } else {

                        ensureSchemaWithParsing(resolved);

                        prop.put("items", Map.of(
                                "$ref", "#/components/schemas/" + resolved
                        ));
                    }
                }

                // =================================================
                // ✅ PRIMITIVO
                // =================================================
                else if (typeUtil.isPrimitive(type)) {

                    prop.put("type", typeUtil.mapType(type));
                    applyFormat(prop, type);
                }

                // =================================================
                // ✅ OBJETO
                // =================================================
                else {

                    if (enumMap != null && enumMap.containsKey(type)) {

                        ensureEnumSchema(type);

                        prop.put("$ref", "#/components/schemas/" + type);
                    } else {

                        ensureSchemaWithParsing(type);

                        prop.put("$ref", "#/components/schemas/" + type);
                    }
                }

                if (isOptional) {
                    prop.put("nullable", true);
                }

                applyAnnotations(field, prop);

                properties.put(name, prop);
            });
        });

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "object");
        schema.put("properties", properties);

        if (properties.isEmpty()) {
            schema.put("description", "Clase sin propiedades o no detectadas");
        }

        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        return schema;
    }

    // =========================================================
    // ✅ RESOLVER IMPORTS 🔥
    // =========================================================
    private String resolveFullType(String simpleType, ClassOrInterfaceDeclaration clazz) {

        if (simpleType == null) return null;

        if (simpleType.contains(".")) return simpleType;

        Optional<CompilationUnit> cuOpt = clazz.findCompilationUnit();

        if (cuOpt.isEmpty()) return simpleType;

        CompilationUnit cu = cuOpt.get();

        return cu.getImports().stream()
                .filter(i -> !i.isAsterisk())
                .filter(i -> i.getName().getIdentifier().equals(simpleType))
                .map(i -> i.getNameAsString())
                .findFirst()
                .orElse(simpleType);
    }

    private String extractSimpleName(String fullType) {

        if (fullType == null) return null;

        if (fullType.contains(".")) {
            return fullType.substring(fullType.lastIndexOf(".") + 1);
        }

        return fullType;
    }

    // =========================================================
    // ✅ ENSURE SCHEMA
    // =========================================================
    private void ensureSchemaWithParsing(String type) {

        if (type == null || type.isBlank()) return;

        if (schemaMap.containsKey(type)) return;

        if (!isValidModel(type)) return;

        classIndexer.findClass(type).ifPresent(clazz -> {

            System.out.println("✅ Parsing real class: " + type);

            Map<String, Object> schema = build(clazz);

            schemaMap.put(type, schema);
        });
    }

    // =========================================================
    // ✅ ENUM
    // =========================================================
    private void ensureEnumSchema(String enumName) {

        if (schemaMap.containsKey(enumName)) return;

        if (enumMap == null || !enumMap.containsKey(enumName)) return;

        EnumDeclaration enumDecl = enumMap.get(enumName);

        List<String> values = enumDecl.getEntries()
                .stream()
                .map(e -> e.getNameAsString())
                .collect(Collectors.toList());

        Map<String, Object> schema = new LinkedHashMap<>();

        schema.put("type", "string");
        schema.put("enum", values);

        schemaMap.put(enumName, schema);
    }

    private void applyAnnotations(FieldDeclaration field, Map<String, Object> prop) {

        for (AnnotationExpr ann : field.getAnnotations()) {

            if (ann.getNameAsString().equals("JsonProperty")) {
                ann.ifSingleMemberAnnotationExpr(a -> {
                    prop.put("name", a.getMemberValue().toString().replace("\"", ""));
                });
            }
        }
    }

    private boolean isValidModel(String name) {
        return !(name.endsWith("Impl")
                || name.contains("Logger")
                || name.contains("Client")
                || name.contains("Config")
                || name.contains("Filter"));
    }

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