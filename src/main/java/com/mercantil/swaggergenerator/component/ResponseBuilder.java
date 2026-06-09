package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Autowired
    private ResponseExampleProvider responseExampleProvider;

    // =========================================================
    // ✅ BUILD RESPONSE
    // =========================================================
    public Map<String, Object> build(
            MethodDeclaration method,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap,
            List<String> ignoredTypes) {

        String rawReturn =
                method.getType().asString();

        String responseType =
                parserUtil.extractGeneric(rawReturn);

        // =====================================================
        // ✅ DETECTAR BODY*
        // =====================================================
        Map<String, String> bodies =
                new LinkedHashMap<>();

        Map<String, Object> responseSchema =
                schemaMap.get(responseType);

        if (responseSchema != null) {

            Object propsObj =
                    responseSchema.get("properties");

            if (propsObj instanceof Map) {

                Map<String, Object> props =
                        (Map<String, Object>) propsObj;

                for (Map.Entry<String, Object> entry
                        : props.entrySet()) {

                    String key =
                            entry.getKey();

                    if (!key.toLowerCase().startsWith("body")) {
                        continue;
                    }

                    Map<String, Object> refObj =
                            (Map<String, Object>) entry.getValue();

                    if (!refObj.containsKey("$ref")) {
                        continue;
                    }

                    String ref =
                            refObj.get("$ref").toString();

                    String refType =
                            ref.substring(
                                    ref.lastIndexOf("/") + 1);

                    bodies.put(
                            key,
                            refType);
                }
            }
        }

        // =====================================================
        // ✅ FALLBACK LEGACY
        // =====================================================
        if (bodies.isEmpty()) {

            Optional<String> inferred =
                    inferBodyFromConstructor(responseType);

            if (inferred.isPresent()) {

                String bodyClass =
                        inferred.get();

                String bodyName =
                        bodyClass.replace(
                                "BodySalida",
                                "");

                bodies.put(
                        "bodySalida" + bodyName,
                        bodyClass);

            } else {

                String op =
                        capitalize(
                                method.getNameAsString());

                String bodyClass =
                        "BodySalida" + op;

                bodies.put(
                        "bodySalida" + op,
                        bodyClass);

                schemaMap.computeIfAbsent(
                        bodyClass,

                        k -> Map.of(
                                "type",
                                "object",

                                "properties",
                                new LinkedHashMap<>()));
            }
        }

        // =====================================================
        // ✅ SCHEMA RESPONSE
        // =====================================================
        Map<String, Object> propsFinal =
                new LinkedHashMap<>();

        propsFinal.put(
                "headerSalida",

                Map.of(
                        "$ref",
                        "#/components/schemas/HeaderSalida"));

        Map<String, Object> responseExample =
                new LinkedHashMap<>();

        responseExample.put(
                "headerSalida",
                headerProvider.buildHeaderSalida());

        String operationName =
                capitalize(
                        method.getNameAsString());

        // =====================================================
        // ✅ BODY*
        // =====================================================
        for (Map.Entry<String, String> entry
                : bodies.entrySet()) {

            String bodyKey =
                    entry.getKey();

            String bodyType =
                    entry.getValue();

            // ✅ asegurar schema
            schemaMap.computeIfAbsent(
                    bodyType,

                    k -> Map.of(
                            "type",
                            "object",

                            "properties",
                            new LinkedHashMap<>()));

            // ✅ schema
            propsFinal.put(
                    bodyKey,

                    Map.of(
                            "$ref",
                            "#/components/schemas/" + bodyType));

            // ✅ example
            Map<String, Object> generated =
                    responseExampleProvider.build(
                            bodyType,
                            operationName,
                            schemaMap,
                            exampleMap);

            // =================================================
            // ✅ CASO SIMPLE
            // =================================================
            if (generated.containsKey("bodySalida")) {

                responseExample.put(
                        bodyKey,
                        generated.get("bodySalida"));

                continue;
            }

            // =================================================
            // ✅ CASO MULTI BODY
            // =================================================
            if (generated.containsKey(bodyKey)) {

                responseExample.put(
                        bodyKey,
                        generated.get(bodyKey));

                continue;
            }

            // =================================================
            // ✅ FALLBACK
            // =================================================
            Object fallback =
                    exampleMap.get(bodyType);

            if (fallback == null) {

                fallback =
                        new LinkedHashMap<>();
            }

            responseExample.put(
                    bodyKey,
                    fallback);
        }

        // =====================================================
        // ✅ RESPONSE JSON
        // =====================================================
        Map<String, Object> responseJson =
                Map.of(

                        "schema",
                        Map.of(
                                "type",
                                "object",

                                "properties",
                                propsFinal),

                        "examples",
                        Map.of(
                                "default",

                                Map.of(
                                        "summary",
                                        "Ejemplo generado",

                                        "value",
                                        responseExample)));

        return Map.of(
                "200",

                Map.of(
                        "description",
                        "Operación exitosa",

                        "content",
                        Map.of(
                                "application/json",
                                responseJson)));
    }

    // =========================================================
    // ✅ INFER BODY
    // =========================================================
    private Optional<String> inferBodyFromConstructor(
            String responseType) {

        return classIndexer.findClass(responseType)

                .flatMap(clazz -> clazz.getConstructors()
                        .stream()

                        .flatMap(c -> c.getBody()
                                .getStatements()
                                .stream())

                        .flatMap(stmt -> stmt.findAll(CastExpr.class)
                                .stream())

                        .map(cast -> cast.getType().asString())

                        .filter(t -> t.startsWith("BodySalida"))

                        .findFirst());
    }

    // =========================================================
    // ✅ CAPITALIZE
    // =========================================================
    private String capitalize(String str) {

        if (str == null
                || str.isEmpty()) {

            return str;
        }

        return Character.toUpperCase(str.charAt(0))
                + str.substring(1);
    }
}