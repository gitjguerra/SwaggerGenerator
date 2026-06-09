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

    @Autowired
    private ResponseExampleProvider responseExampleProvider;

    @Autowired
    private RequestResponseResolver requestResponseResolver;

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
        // ✅ RESOLVE BODIES
        // =====================================================
        String operationName =
                capitalize(method.getNameAsString());

        Map<String, String> bodies =
                requestResponseResolver.resolveResponseBodies(
                        responseType,
                        operationName,
                        schemaMap);

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