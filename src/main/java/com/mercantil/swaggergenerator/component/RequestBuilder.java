package com.mercantil.swaggergenerator.component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;

@Component
public class RequestBuilder {

    @Autowired
    private HeaderExampleProvider headerProvider;

    @Autowired
    private ClassIndexer classIndexer;

    private final Map<String, Object> exampleCache = new HashMap<>();

    public Map<String, Object> build(
            MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap,
            List<String> ignoredTypes) {

        Map<String, Object> requestProps = new LinkedHashMap<>();

        // ✅ SAFE REF
        java.util.function.Function<String, Map<String, Object>> safeRef = type -> {
            if (type == null || ignoredTypes.contains(type)) {
                return Map.of("type", "object");
            }
            return Map.of("$ref", "#/components/schemas/" + type);
        };

        // ✅ HEADER
        requestProps.put("headerEntrada", safeRef.apply("HeaderEntrada"));

        String requestType = null;
        String bodyType = null;
        String bodyFieldName = null;

        String operationName = capitalize(method.getNameAsString());

        // =========================================================
        // ✅ detectar Request
        // =========================================================
        Optional<String> requestOpt = method.getParameters().stream()
                .map(p -> p.getType().asString())
                .filter(t -> t.startsWith("Request"))
                .findFirst();

        if (requestOpt.isPresent()) {

            requestType = requestOpt.get();
            Map<String, Object> reqSchema = schemaMap.get(requestType);

            if (reqSchema != null) {

                Object propsObj = reqSchema.get("properties");

                if (propsObj instanceof Map) {

                    Map<String, Object> props = (Map<String, Object>) propsObj;

                    for (Map.Entry<String, Object> entry : props.entrySet()) {

                        String key = entry.getKey();

                        if (!key.toLowerCase().startsWith("bodyentrada"))
                            continue;

                        Map<String, Object> refObj = (Map<String, Object>) entry.getValue();

                        if (!refObj.containsKey("$ref"))
                            continue;

                        String ref = refObj.get("$ref").toString();
                        bodyType = ref.substring(ref.lastIndexOf("/") + 1);
                        bodyFieldName = key;

                        break;
                    }
                }
            }
        }

        // =========================================================
        // ✅ fallback
        // =========================================================
        if (bodyType == null && requestType != null) {

            bodyType = requestType.replace("Request", "BodyEntrada");

            bodyFieldName = "bodyEntrada" + bodyType.replace("BodyEntrada", "");
        }

        // =========================================================
        // ✅ normalización
        // =========================================================
        if ("bodyEntrada".equals(bodyFieldName)) {

            if (bodyType != null && bodyType.startsWith("BodyEntrada")) {

                bodyFieldName = "bodyEntrada" + bodyType.replace("BodyEntrada", "");

            } else {
                bodyFieldName = "bodyEntrada" + operationName;
            }
        }

        // =========================================================
        // ✅ asegurar schema
        // =========================================================
        if (bodyType != null && !ignoredTypes.contains(bodyType)) {
            ensureSchemaExists(bodyType, schemaMap);
        }

        // =========================================================
        // ✅ validar contenido primero
        // =========================================================
        boolean hasBody = hasProperties(bodyType, schemaMap);

        if (bodyType != null && hasBody) {
            requestProps.put(bodyFieldName, safeRef.apply(bodyType));
        }

        // =========================================================
        // ✅ example
        // =========================================================
        Map<String, Object> requestExample = new LinkedHashMap<>();
        requestExample.put("headerEntrada", headerProvider.buildHeaderEntrada());

        if (bodyType != null && hasBody) {

            Object bodyExample = exampleMap.get(bodyType);
            bodyExample = flattenIfWrapper(bodyExample);

            if (!(bodyExample instanceof Map) || ((Map<?, ?>) bodyExample).isEmpty()) {
                bodyExample = buildExampleFromSchema(bodyType, schemaMap);
                bodyExample = flattenIfWrapper(bodyExample);
            }

            requestExample.put(bodyFieldName, flattenIfWrapper(bodyExample));
        }

        Map<String, Object> requestJson = Map.of(
                "schema", Map.of(
                        "type", "object",
                        "properties", requestProps
                ),
                "examples", Map.of(
                        "default", Map.of(
                                "summary", "Ejemplo generado",
                                "value", requestExample
                        )
                )
        );

        return Map.of(
                "required", true,
                "content", Map.of("application/json", requestJson)
        );
    }

    // =========================================================
    private boolean hasProperties(String type,
                                 Map<String, Map<String, Object>> schemaMap) {

        if (type == null)
            return false;

        Map<String, Object> schema = schemaMap.get(type);
        if (schema == null)
            return false;

        Object props = schema.get("properties");
        return props instanceof Map && !((Map<?, ?>) props).isEmpty();
    }

    private void ensureSchemaExists(String typeName,
                                    Map<String, Map<String, Object>> schemaMap) {

        if (typeName == null || typeName.isBlank())
            return;

        schemaMap.computeIfAbsent(typeName,
                k -> Map.of(
                        "type", "object",
                        "properties", new LinkedHashMap<>()
                ));
    }

    // =========================================================
    // ✅ CACHE + SAFE COPY
    // =========================================================
    private Object buildExampleFromSchema(String type,
            Map<String, Map<String, Object>> schemaMap) {

        if (exampleCache.containsKey(type)) {
            return new LinkedHashMap<>((Map<String, Object>) exampleCache.get(type));
        }

        Object result = buildExampleFromSchema(type, schemaMap, new HashSet<>());

        if (result instanceof Map) {
            exampleCache.put(type, new LinkedHashMap<>((Map<String, Object>) result));
        }

        return result;
    }

    // =========================================================
    // ✅ AQUÍ ESTÁ EL FIX REAL (JsonProperty + flatten)
    // =========================================================
    private Object buildExampleFromSchema(String type,
            Map<String, Map<String, Object>> schemaMap,
            Set<String> visited) {

        if (type == null)
            return new LinkedHashMap<>();

        if (visited.contains(type))
            return "(circular)";

        visited.add(type);

        Map<String, Object> schema = schemaMap.get(type);
        if (schema == null)
            return new LinkedHashMap<>();

        Object propsObj = schema.get("properties");
        if (!(propsObj instanceof Map))
            return new LinkedHashMap<>();

        Map<String, Object> props = (Map<String, Object>) propsObj;

        Map<String, Object> example = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : props.entrySet()) {

            String fieldName = entry.getKey();
            String jsonName = resolveJsonName(type, fieldName);

            Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();

            if (fieldDef.containsKey("$ref")) {

                String ref = fieldDef.get("$ref").toString();
                String refType = ref.substring(ref.lastIndexOf("/") + 1);

                example.put(jsonName,
                        buildExampleFromSchema(refType, schemaMap, visited));

                continue;
            }

            example.put(jsonName,
                    mockValue((String) fieldDef.get("type")));
        }

        // ✅ flatten automático
        if (example.size() == 1) {
            Object onlyValue = example.values().iterator().next();
            if (onlyValue instanceof Map) {
                return onlyValue;
            }
        }

        return example;
    }

    // =========================================================
    // ✅ RESOLVER @JsonProperty
    // =========================================================
    private String resolveJsonName(String className, String fieldName) {

        return classIndexer.findClass(className)
                .map(clazz -> clazz.getConstructors().stream()
                        .flatMap(c -> c.getParameters().stream())
                        .filter(p -> p.getNameAsString().equals(fieldName))
                        .map(p -> p.getAnnotationByName("JsonProperty"))
                        .flatMap(Optional::stream)
                        .map(a -> a.asSingleMemberAnnotationExpr()
                                .getMemberValue()
                                .toString()
                                .replace("\"", ""))
                        .findFirst()
                        .orElse(fieldName)
                )
                .orElse(fieldName);
    }

    private Object mockValue(String type) {

        if (type == null)
            return "";

        switch (type) {
            case "string": return "";
            case "integer": return 123;
            case "number": return 123.45;
            case "boolean": return true;
            case "array": return new java.util.ArrayList<>();
            default: return "";
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private Object flattenIfWrapper(Object example) {

        if (!(example instanceof Map))
            return example;

        Map<?, ?> map = (Map<?, ?>) example;

        if (map.size() == 1) {

            Object onlyValue = map.values().iterator().next();

            if (onlyValue instanceof Map) {
                return onlyValue;
            }
        }

        return example;
    }
}