package com.mercantil.swaggergenerator.component;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;

@Component
public class ResponseBuilder {

    @Autowired
    private HeaderExampleProvider headerProvider;

    public Map<String, Object> build(
            MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap,
            List<String> ignoredTypes) {

        String operationName = capitalize(method.getNameAsString());
        String bodyClass = "BodySalida" + operationName;
        String bodyName = operationName;

        // =========================================================
        // ✅ ASEGURAR SCHEMA
        // =========================================================
        if (!schemaMap.containsKey(bodyClass)) {
            schemaMap.put(bodyClass, Map.of(
                    "type", "object",
                    "properties", new LinkedHashMap<>()
            ));
        }

        // =========================================================
        // ✅ GENERAR EJEMPLO REAL (CLAVE 🔥)
        // =========================================================
        Object exampleBody = exampleMap.get(bodyClass);

        if (exampleBody == null) {
            exampleBody = buildExampleFromSchema(bodyClass, schemaMap);
        }

        // =========================================================
        // ✅ SCHEMA RESPONSE
        // =========================================================
        Map<String, Object> responseSchema = new LinkedHashMap<>();
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("headerSalida",
                Map.of("$ref", "#/components/schemas/HeaderSalida"));

        // ✅ SIEMPRE incluir body
        props.put("bodySalida" + bodyName,
                Map.of("$ref", "#/components/schemas/" + bodyClass));

        responseSchema.put("type", "object");
        responseSchema.put("properties", props);

        // =========================================================
        // ✅ EXAMPLE RESPONSE COMPLETO 🔥
        // =========================================================
        Map<String, Object> responseExample = new LinkedHashMap<>();

        responseExample.put("headerSalida",
                headerProvider.buildHeaderSalida());

        responseExample.put(
                "bodySalida" + bodyName,
                exampleBody != null ? exampleBody : new LinkedHashMap<>()
        );

        Map<String, Object> responseJson = new LinkedHashMap<>();

        responseJson.put("schema", responseSchema);
        responseJson.put("examples", Map.of(
                "default",
                Map.of(
                        "summary", "Ejemplo generado",
                        "value", responseExample
                )
        ));

        return Map.of(
                "200",
                Map.of(
                        "description", "Operación exitosa",
                        "content", Map.of("application/json", responseJson)
                )
        );
    }

    // =========================================================
    // ✅ GENERADOR RECURSIVO (IGUAL QUE REQUEST 🔥🔥🔥)
    // =========================================================
    private Object buildExampleFromSchema(String type,
                                          Map<String, Map<String, Object>> schemaMap) {
        return buildExampleFromSchema(type, schemaMap, new HashSet<>());
    }

    private Object buildExampleFromSchema(String type,
                                          Map<String, Map<String, Object>> schemaMap,
                                          Set<String> visited) {

        if (type == null || visited.contains(type)) {
            return new LinkedHashMap<>();
        }

        visited.add(type);

        Map<String, Object> schema = schemaMap.get(type);

        if (schema == null || !(schema.get("properties") instanceof Map)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> props =
                (Map<String, Object>) schema.get("properties");

        Map<String, Object> example = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : props.entrySet()) {

            String field = entry.getKey();
            Map<String, Object> def = (Map<String, Object>) entry.getValue();

            // =====================================================
            // ✅ OBJETO ($ref)
            // =====================================================
            if (def.containsKey("$ref")) {

                String ref = def.get("$ref").toString();
                String refType = ref.substring(ref.lastIndexOf("/") + 1);

                example.put(field,
                        buildExampleFromSchema(refType, schemaMap, visited));
            }

            // =====================================================
            // ✅ ARRAY
            // =====================================================
            else if ("array".equals(def.get("type"))) {

                Object items = def.get("items");

                if (items instanceof Map) {

                    Map<String, Object> itemMap = (Map<String, Object>) items;

                    if (itemMap.containsKey("$ref")) {

                        String ref = itemMap.get("$ref").toString();
                        String refType = ref.substring(ref.lastIndexOf("/") + 1);

                        example.put(field, List.of(
                                buildExampleFromSchema(refType, schemaMap, visited)
                        ));

                    } else {

                        example.put(field, List.of(
                                mockValue((String) itemMap.get("type"))
                        ));
                    }

                } else {
                    example.put(field, List.of());
                }
            }

            // =====================================================
            // ✅ PRIMITIVOS
            // =====================================================
            else {

                example.put(field,
                        mockValue((String) def.get("type")));
            }
        }

        return example;
    }

    // =========================================================
    // ✅ VALORES MOCK (IGUAL QUE REQUEST)
    // =========================================================
    private Object mockValue(String type) {

        if (type == null) return "string";

        switch (type) {
            case "string":
                return "string";
            case "integer":
                return 12345;
            case "number":
                return 1500.75;
            case "boolean":
                return true;
            default:
                return "string";
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}