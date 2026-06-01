package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.mercantil.swaggergenerator.util.ParserUtil;

@Component
public class ResponseBuilder {

    @Autowired
    private HeaderExampleProvider headerProvider;

    @Autowired
    private ParserUtil parserUtil;

    public Map<String, Object> build(
            MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap,
            List<String> ignoredTypes) {

        // =========================================================
        // ✅ SAFE REF
        // =========================================================
        java.util.function.Function<String, Map<String, Object>> safeRef = type -> {
            if (type == null || ignoredTypes.contains(type)) {
                return Map.of("type", "object");
            }
            return Map.of("$ref", "#/components/schemas/" + type);
        };

        // =========================================================
        // ✅ DETECTAR RESPONSE TYPE
        // =========================================================
        String rawReturn = method.getType().asString();
        String responseType = unwrapResponseType(rawReturn);

        if (responseType == null || ignoredTypes.contains(responseType)
                || "ClientResponse".equals(responseType)) {
            responseType = null;
        }

        ensureSchemaExists(responseType, schemaMap);

        String operationName = capitalize(method.getNameAsString());

        // =========================================================
        // ✅ NOMBRE BODY
        // =========================================================
        String responseBodyName;

        if (responseType != null && responseType.startsWith("Response")) {
            responseBodyName = capitalize(responseType.replace("Response", ""));
        } else {
            responseBodyName = operationName;
        }

        // =========================================================
        // ✅ CLASE REAL BODY
        // =========================================================
        String expectedBodyClass = "BodySalida" + responseBodyName;

        if (!schemaMap.containsKey(expectedBodyClass) && responseType != null) {

            String fallback = "BodySalida" + responseType.replace("Response", "");

            if (schemaMap.containsKey(fallback)) {
                expectedBodyClass = fallback;
                responseBodyName = capitalize(responseType.replace("Response", ""));
            }
        }

        if (expectedBodyClass != null && !schemaMap.containsKey(expectedBodyClass)) {
            ensureSchemaExists(expectedBodyClass, schemaMap);
        }

        // =========================================================
        // ✅ GENERAR EJEMPLO BODY
        // =========================================================
        Object exampleBody = null;

        if (expectedBodyClass != null) {

            exampleBody = exampleMap.get(expectedBodyClass);

            // generar si no existe o está vacío
            if (!(exampleBody instanceof Map) || ((Map<?, ?>) exampleBody).isEmpty()) {
                exampleBody = buildExampleFromSchema(expectedBodyClass, schemaMap);
            }
        }

        // =========================================================
        // ✅ SCHEMA RESPONSE
        // =========================================================
        Map<String, Object> responseSchema = new LinkedHashMap<>();
        Map<String, Object> responseProps = new LinkedHashMap<>();

        responseProps.put("headerSalida", safeRef.apply("HeaderSalida"));

        // ✅ SOLO agregar body si tiene contenido
        if (exampleBody instanceof Map) {

            Map<?, ?> map = (Map<?, ?>) exampleBody;

            if (!map.isEmpty()) {
                responseProps.put("bodySalida" + responseBodyName,
                        safeRef.apply(expectedBodyClass));
            } else {
                System.out.println("⚠️ Schema bodySalida omitido: " + expectedBodyClass);
            }

        } else if (exampleBody != null) {

            responseProps.put("bodySalida" + responseBodyName,
                    safeRef.apply(expectedBodyClass));
        }

        responseSchema.put("type", "object");
        responseSchema.put("properties", responseProps);

        // =========================================================
        // ✅ EJEMPLO RESPONSE
        // =========================================================
        Map<String, Object> responseExample = new LinkedHashMap<>();

        responseExample.put("headerSalida", headerProvider.buildHeaderSalida());

        // ✅ SOLO agregar body si tiene contenido
        if (exampleBody instanceof Map) {

            Map<?, ?> map = (Map<?, ?>) exampleBody;

            if (!map.isEmpty()) {
                responseExample.put("bodySalida" + responseBodyName, exampleBody);
            } else {
                System.out.println("⚠️ bodySalida omitido en example: " + expectedBodyClass);
            }

        } else if (exampleBody != null) {

            responseExample.put("bodySalida" + responseBodyName, exampleBody);
        }

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
    // ✅ GENERADOR DE EJEMPLOS DESDE SCHEMA
    // =========================================================
    private Object buildExampleFromSchema(String type,
            Map<String, Map<String, Object>> schemaMap) {

        Map<String, Object> schema = schemaMap.get(type);

        if (schema == null) return new LinkedHashMap<>();

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

    // =========================================================
    private Object mockValue(String type) {

        if (type == null) return "";

        switch (type) {
            case "string":
                return "string";
            case "integer":
                return 123;
            case "number":
                return 123.45;
            case "boolean":
                return true;
            case "array":
                return new java.util.ArrayList<>();
            default:
                return "";
        }
    }

    // =========================================================
    private void ensureSchemaExists(String typeName,
            Map<String, Map<String, Object>> schemaMap) {

        if (typeName == null || typeName.isBlank()) return;

        if (schemaMap.containsKey(typeName)) return;

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());

        schemaMap.put(typeName, schema);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private String unwrapResponseType(String type) {

        List<String> wrappers = List.of("ResponseEntity", "ClientResponse", "Optional");

        String current = type;

        while (current.contains("<") && current.contains(">")) {

            String outer = current.substring(0, current.indexOf("<")).trim();

            if (!wrappers.contains(outer)) break;

            current = parserUtil.extractGeneric(current);
        }

        return parserUtil.resolveFinalType(current);
    }
}