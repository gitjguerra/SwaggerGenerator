package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;

@Component
public class RequestBuilder {

    @Autowired
    private HeaderExampleProvider headerProvider;

    public Map<String, Object> build(
            MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap,
            List<String> ignoredTypes) {

        Map<String, Object> requestSchema = new LinkedHashMap<>();
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

        // ✅ detectar Request
        Optional<String> requestOpt = method.getParameters().stream()
                .map(p -> p.getType().asString())
                .filter(t -> t.startsWith("Request"))
                .findFirst();

        if (requestOpt.isPresent()) {

            requestType = requestOpt.get();

            Map<String, Object> reqSchema = schemaMap.get(requestType);

            if (reqSchema != null && reqSchema.get("properties") instanceof Map) {

                Map<String, Object> props = (Map<String, Object>) reqSchema.get("properties");

                for (Map.Entry<String, Object> entry : props.entrySet()) {

                    if (entry.getKey().toLowerCase().startsWith("bodyentrada")) {

                        Map<String, Object> refObj = (Map<String, Object>) entry.getValue();

                        if (refObj.containsKey("$ref")) {

                            String ref = refObj.get("$ref").toString();
                            bodyType = ref.substring(ref.lastIndexOf("/") + 1);

                            bodyFieldName = entry.getKey();
                            break;
                        }
                    }
                }
            }
        }

        // ✅ fallback
        if (bodyType == null && requestType != null) {

            bodyType = requestType.replace("Request", "BodyEntrada");

            bodyFieldName = "bodyEntrada" +
                    bodyType.replace("BodyEntrada", "");
        }

        // ✅ normalización
        if ("bodyEntrada".equals(bodyFieldName)) {

            if (bodyType != null && bodyType.startsWith("BodyEntrada")) {

                bodyFieldName = "bodyEntrada" +
                        bodyType.replace("BodyEntrada", "");

            } else {

                bodyFieldName = "bodyEntrada" + operationName;
            }
        }

        // ✅ asegurar schema
        if (bodyType != null && !ignoredTypes.contains(bodyType)) {
            ensureSchemaExists(bodyType, schemaMap);
        }

        // ✅ VALIDACIÓN CLAVE 🔥
        boolean hasBody = hasProperties(bodyType, schemaMap);

        // ✅ agregar body SOLO si tiene data
        if (bodyType != null && hasBody) {
            requestProps.put(bodyFieldName, safeRef.apply(bodyType));
        }

        requestSchema.put("type", "object");
        requestSchema.put("properties", requestProps);

        // ✅ example
        Map<String, Object> requestExample = new LinkedHashMap<>();

        requestExample.put("headerEntrada", headerProvider.buildHeaderEntrada());

        if (bodyType != null && hasBody) {

            Object bodyExample = exampleMap.get(bodyType);

            if (!(bodyExample instanceof Map) || ((Map<?, ?>) bodyExample).isEmpty()) {
                bodyExample = buildExampleFromSchema(bodyType, schemaMap);
            }

            requestExample.put(bodyFieldName, bodyExample);
        }

        // ✅ salida
        Map<String, Object> requestJson = new LinkedHashMap<>();
        requestJson.put("schema", requestSchema);
        requestJson.put("examples", Map.of(
                "default",
                Map.of(
                        "summary", "Ejemplo generado",
                        "value", requestExample
                )
        ));

        return Map.of(
                "required", true,
                "content", Map.of("application/json", requestJson)
        );
    }

    // ✅ NUEVO 🔥
    private boolean hasProperties(String type,
                                  Map<String, Map<String, Object>> schemaMap) {

        if (type == null) return false;

        Map<String, Object> schema = schemaMap.get(type);

        if (schema == null) return false;

        Object props = schema.get("properties");

        if (!(props instanceof Map)) return false;

        return !((Map<?, ?>) props).isEmpty();
    }

    private void ensureSchemaExists(String typeName,
            Map<String, Map<String, Object>> schemaMap) {

        if (typeName == null || typeName.isBlank()) return;

        if (schemaMap.containsKey(typeName)) return;

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());

        schemaMap.put(typeName, schema);
    }

    private Object buildExampleFromSchema(String type,
            Map<String, Map<String, Object>> schemaMap) {

        Map<String, Object> schema = schemaMap.get(type);

        if (schema == null) return new LinkedHashMap<>();

        if (Boolean.TRUE.equals(schema.get("additionalProperties"))) {

            Map<String, Object> dynamic = new LinkedHashMap<>();
            dynamic.put("campo1", "valor");
            dynamic.put("campo2", 123);

            return dynamic;
        }

        if (!(schema.get("properties") instanceof Map)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> example = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : props.entrySet()) {

            String field = entry.getKey();
            Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();

            if (fieldDef.containsKey("$ref")) {

                String ref = fieldDef.get("$ref").toString();
                String refType = ref.substring(ref.lastIndexOf("/") + 1);

                example.put(field, buildExampleFromSchema(refType, schemaMap));

            } else {

                String typeField = (String) fieldDef.get("type");

                example.put(field, mockValue(typeField));
            }
        }

        return example;
    }

    private Object mockValue(String type) {

        if (type == null) return "";

        switch (type) {
            case "string": return "string";
            case "integer": return 123;
            case "number": return 123.45;
            case "boolean": return true;
            case "array": return new java.util.ArrayList<>();
            default: return "";
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
