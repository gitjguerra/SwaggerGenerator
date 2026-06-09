package com.mercantil.swaggergenerator.component;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResponseExampleProvider {

    @Autowired
    private RuleEngine ruleEngine;

    @Autowired
    private ExampleGenerator exampleGenerator;

    @Autowired
    private ExamplePathResolver examplePathResolver;

    // =========================================================
    // ✅ BUILD RESPONSE EXAMPLE
    // ✅ lookup usando endpointPath
    // ✅ usa bodyKey REAL del schema
    // ✅ evita contaminación por operationName
    // =========================================================
    public Map<String, Object> build(
            String endpointPath,
            String bodyKey,
            String bodyType,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        // =====================================================
        // ✅ VALIDAR BODY
        // =====================================================
        if (bodyType == null
                || bodyType.isBlank()) {

            return response;
        }

        // =====================================================
        // ✅ PRIORIDAD 1
        // ✅ RULES.XML
        // ✅ PATH-BASED LOOKUP
        // =====================================================
        Map<String, String> rules =
                ruleEngine.getResponseRules(
                        endpointPath);

        // =====================================================
        // ✅ SI EXISTEN RULES
        // =====================================================
        if (!rules.isEmpty()) {

            buildFromRules(
                    response,
                    rules);

            return response;
        }

        // =====================================================
        // ✅ PRIORIDAD 2
        // ✅ EXAMPLE MAP
        // =====================================================
        Object generated =
                exampleMap.get(bodyType);

        // =====================================================
        // ✅ PRIORIDAD 3
        // ✅ GENERACIÓN AUTOMÁTICA
        // =====================================================
        if (generated == null) {

            generated =
                    exampleGenerator
                            .buildExampleFromType(
                                    bodyType);
        }

        // =====================================================
        // ✅ USAR bodyKey REAL
        // ✅ NO:
        // ✅ bodySalida + operationName
        // =====================================================
        response.put(
                bodyKey,
                generated);

        return response;
    }

    // =========================================================
    // ✅ BUILD FROM RULES
    // =========================================================
    private void buildFromRules(
            Map<String, Object> response,
            Map<String, String> rules) {

        for (Map.Entry<String, String> entry
                : rules.entrySet()) {

            String path =
                    entry.getKey();

            String value =
                    entry.getValue();

            examplePathResolver.setNestedValue(

                    response,

                    path,

                    examplePathResolver.parseValue(
                            path,
                            value));
        }
    }
}