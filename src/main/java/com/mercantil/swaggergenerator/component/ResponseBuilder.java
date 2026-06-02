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

    private final Map<String, Object> exampleCache = new HashMap<>();

    public Map<String, Object> build(
            MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap,
            List<String> ignoredTypes) {

        String bodyClass = null;
        String bodyName = null;

        String rawReturn = method.getType().asString();
        String responseType = parserUtil.extractGeneric(rawReturn);

        // =========================================================
        // ✅ 1. constructor (FAST PATH)
        // =========================================================
        Optional<String> inferred = inferBodyFromConstructor(responseType);

        if (inferred.isPresent()) {
            bodyClass = inferred.get();
            bodyName = bodyClass.replace("BodySalida", "");
        }

        // =========================================================
        // ✅ 2. schema (ONLY IF NEEDED)
        // =========================================================
        if (bodyClass == null) {

            Map<String, Object> responseSchema = schemaMap.get(responseType);

            if (responseSchema != null) {

                Object propsObj = responseSchema.get("properties");

                if (propsObj instanceof Map) {

                    Map<String, Object> props = (Map<String, Object>) propsObj;

                    for (Map.Entry<String, Object> entry : props.entrySet()) {

                        String key = entry.getKey();

                        if (!key.toLowerCase().startsWith("bodysalida")) continue;

                        Map<String, Object> refObj = (Map<String, Object>) entry.getValue();

                        if (!refObj.containsKey("$ref")) continue;

                        String ref = refObj.get("$ref").toString();

                        bodyClass = ref.substring(ref.lastIndexOf("/") + 1);
                        bodyName = key.replace("bodySalida", "");
                        break;
                    }
                }
            }
        }

        // =========================================================
        // ✅ 3. MATCH FLEXIBLE (LAZY)
        // =========================================================
        if (bodyClass == null) {

            String methodName = method.getNameAsString().toLowerCase();

            for (String k : schemaMap.keySet()) {

                if (!k.startsWith("BodySalida")) continue;

                if (k.toLowerCase().contains(methodName)) {
                    bodyClass = k;
                    bodyName = bodyClass.replace("BodySalida", "");
                    break;
                }
            }
        }

        // =========================================================
        // ✅ 4. FALLBACK
        // =========================================================
        if (bodyClass == null) {
            String op = capitalize(method.getNameAsString());
            bodyClass = "BodySalida" + op;
            bodyName = op;
        }

        // =========================================================
        // ✅ asegurar schema (lazy creation)
        // =========================================================
        schemaMap.computeIfAbsent(bodyClass,
                k -> Map.of("type", "object", "properties", new LinkedHashMap<>()));

        // =========================================================
        // ✅ validar contenido (ANTES de generar ejemplo 🔥)
        // =========================================================
        boolean hasBody = hasProperties(bodyClass, schemaMap);

        Object exampleBody = null;

        if (hasBody) {
            exampleBody = exampleMap.get(bodyClass);

            if (!(exampleBody instanceof Map) || ((Map<?, ?>) exampleBody).isEmpty()) {
                exampleBody = buildExampleFromSchema(bodyClass, schemaMap);
            }
        }

        // =========================================================
        // ✅ SCHEMA RESPONSE
        // =========================================================
        Map<String, Object> propsFinal = new LinkedHashMap<>();
        propsFinal.put("headerSalida",
                Map.of("$ref", "#/components/schemas/HeaderSalida"));

        if (hasBody) {
            propsFinal.put("bodySalida" + bodyName,
                    Map.of("$ref", "#/components/schemas/" + bodyClass));
        }

        Map<String, Object> responseExample = new LinkedHashMap<>();
        responseExample.put("headerSalida", headerProvider.buildHeaderSalida());

        if (hasBody) {
            responseExample.put("bodySalida" + bodyName, exampleBody);
        }

        Map<String, Object> responseJson = Map.of(
                "schema", Map.of(
                        "type", "object",
                        "properties", propsFinal
                ),
                "examples", Map.of(
                        "default", Map.of(
                                "summary", "Ejemplo generado",
                                "value", responseExample
                        )
                )
        );

        return Map.of(
                "200",
                Map.of(
                        "description", "Operación exitosa",
                        "content", Map.of("application/json", responseJson)
                )
        );
    }

    // =========================================================
    // ✅ optimized
    // =========================================================
    private boolean hasProperties(String type,
                                 Map<String, Map<String, Object>> schemaMap) {

        Map<String, Object> schema = schemaMap.get(type);
        if (schema == null) return false;

        Object props = schema.get("properties");
        return props instanceof Map && !((Map<?, ?>) props).isEmpty();
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

    private Object buildExampleFromSchema(String type,
            Map<String, Map<String, Object>> schemaMap,
            Set<String> visited) {

        if (type == null) return new LinkedHashMap<>();

        if (visited.contains(type)) return "(circular)";

        visited.add(type);

        Map<String, Object> schema = schemaMap.get(type);

        if (schema == null) return new LinkedHashMap<>();

        Object propsObj = schema.get("properties");

        if (!(propsObj instanceof Map)) return new LinkedHashMap<>();

        Map<String, Object> props = (Map<String, Object>) propsObj;

        Map<String, Object> example = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : props.entrySet()) {

            Map<String, Object> def = (Map<String, Object>) entry.getValue();

            if (def.containsKey("$ref")) {

                String ref = def.get("$ref").toString();
                String refType = ref.substring(ref.lastIndexOf("/") + 1);

                example.put(entry.getKey(),
                        buildExampleFromSchema(refType, schemaMap, visited));

                continue;
            }

            if ("array".equals(def.get("type"))) {

                Object items = def.get("items");

                if (items instanceof Map && ((Map<?, ?>) items).containsKey("$ref")) {

                    String ref = ((Map<?, ?>) items).get("$ref").toString();
                    String refType = ref.substring(ref.lastIndexOf("/") + 1);

                    example.put(entry.getKey(),
                            List.of(buildExampleFromSchema(refType, schemaMap, visited)));

                } else {
                    example.put(entry.getKey(),
                            List.of(mockValue((String) ((Map<?, ?>) items).get("type"))));
                }

                continue;
            }

            example.put(entry.getKey(),
                    mockValue((String) def.get("type")));
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
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}