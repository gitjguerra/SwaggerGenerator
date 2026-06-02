package com.mercantil.swaggergenerator.component;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.mercantil.swaggergenerator.util.ParserUtil;

@Component
public class ResponseBuilder {

    @Autowired
    private HeaderExampleProvider headerProvider;

    @Autowired
    private ParserUtil parserUtil;

    @Autowired
    private ClassIndexer classIndexer;

    public Map<String, Object> build(
            MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap,
            List<String> ignoredTypes) {

        String bodyClass = null;
        String bodyName = null;

        String rawReturn = method.getType().asString();
        String responseType = parserUtil.extractGeneric(rawReturn);

        // ✅ 1. constructor
        Optional<String> inferred = inferBodyFromConstructor(responseType);

        if (inferred.isPresent()) {
            bodyClass = inferred.get();
            bodyName = bodyClass.replace("BodySalida", "");
        }

        // ✅ 2. schema
        if (bodyClass == null) {

            Map<String, Object> responseSchema = schemaMap.get(responseType);

            if (responseSchema != null && responseSchema.get("properties") instanceof Map) {

                Map<String, Object> props =
                        (Map<String, Object>) responseSchema.get("properties");

                for (Map.Entry<String, Object> entry : props.entrySet()) {

                    String key = entry.getKey();

                    if (key.toLowerCase().startsWith("bodysalida")) {

                        Map<String, Object> refObj =
                                (Map<String, Object>) entry.getValue();

                        if (refObj.containsKey("$ref")) {

                            String ref = refObj.get("$ref").toString();

                            bodyClass = ref.substring(ref.lastIndexOf("/") + 1);
                            bodyName = key.replace("bodySalida", "");
                            break;
                        }
                    }
                }
            }
        }

        // ✅ 3. match flexible
        if (bodyClass == null) {

            String methodName = method.getNameAsString().toLowerCase();

            Optional<String> match = schemaMap.keySet().stream()
                    .filter(k -> k.startsWith("BodySalida"))
                    .filter(k -> k.toLowerCase().contains(methodName))
                    .findFirst();

            if (match.isPresent()) {
                bodyClass = match.get();
                bodyName = bodyClass.replace("BodySalida", "");
            }
        }

        // ✅ 4. fallback
        if (bodyClass == null) {
            String op = capitalize(method.getNameAsString());
            bodyClass = "BodySalida" + op;
            bodyName = op;
        }

        // ✅ asegurar schema
        if (!schemaMap.containsKey(bodyClass)) {
            schemaMap.put(bodyClass, Map.of(
                    "type", "object",
                    "properties", new LinkedHashMap<>()
            ));
        }

        // ✅ ejemplo
        Object exampleBody = exampleMap.get(bodyClass);

        if (exampleBody == null) {
            exampleBody = buildExampleFromSchema(bodyClass, schemaMap);
        }

        // ✅ VALIDACIÓN CLAVE 🔥
        boolean hasBody = hasProperties(bodyClass, schemaMap);

        // =========================================================
        // ✅ SCHEMA RESPONSE
        // =========================================================
        Map<String, Object> responseSchemaFinal = new LinkedHashMap<>();
        Map<String, Object> propsFinal = new LinkedHashMap<>();

        propsFinal.put("headerSalida",
                Map.of("$ref", "#/components/schemas/HeaderSalida"));

        if (hasBody) {
            propsFinal.put("bodySalida" + bodyName,
                    Map.of("$ref", "#/components/schemas/" + bodyClass));
        }

        responseSchemaFinal.put("type", "object");
        responseSchemaFinal.put("properties", propsFinal);

        // =========================================================
        // ✅ EXAMPLE
        // =========================================================
        Map<String, Object> responseExample = new LinkedHashMap<>();

        responseExample.put("headerSalida",
                headerProvider.buildHeaderSalida());

        if (hasBody) {
            responseExample.put("bodySalida" + bodyName, exampleBody);
        }

        Map<String, Object> responseJson = new LinkedHashMap<>();

        responseJson.put("schema", responseSchemaFinal);
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

    // ✅ NUEVO 🔥🔥🔥
    private boolean hasProperties(String type,
                                  Map<String, Map<String, Object>> schemaMap) {

        Map<String, Object> schema = schemaMap.get(type);

        if (schema == null) return false;

        Object props = schema.get("properties");

        if (!(props instanceof Map)) return false;

        return !((Map<?, ?>) props).isEmpty();
    }

    private Optional<String> inferBodyFromConstructor(String responseType) {

        return classIndexer.findClass(responseType).flatMap(clazz ->
                clazz.getConstructors().stream()
                        .flatMap(c -> c.getBody().getStatements().stream())
                        .flatMap(stmt -> stmt.findAll(CastExpr.class).stream())
                        .map(cast -> cast.getType().asString())
                        .filter(t -> t.startsWith("BodySalida"))
                        .findFirst()
        );
    }

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

            if (def.containsKey("$ref")) {
                String ref = def.get("$ref").toString();
                String refType = ref.substring(ref.lastIndexOf("/") + 1);
                example.put(field, buildExampleFromSchema(refType, schemaMap, visited));
            }

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

            else {
                example.put(field, mockValue((String) def.get("type")));
            }
        }

        return example;
    }

    private Object mockValue(String type) {

        if (type == null) return "string";

        switch (type) {
            case "string": return "string";
            case "integer": return 12345;
            case "number": return 1500.75;
            case "boolean": return true;
            default: return "string";
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}