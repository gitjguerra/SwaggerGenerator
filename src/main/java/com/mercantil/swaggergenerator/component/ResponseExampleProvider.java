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
    // =========================================================
    public Map<String, Object> build(
            String endpointPath,
            String bodyType,
            String operationName,
            Map<String, Map<String, Object>> schemaMap,
            Map<String, Object> exampleMap) {

        Map<String, Object> response =
                new LinkedHashMap<>();

        // =====================================================
        // ✅ VALIDAR BODY
        // =====================================================
        if (bodyType == null) {

            return response;
        }

        // =====================================================
        // ✅ PRIORIDAD 1
        // ✅ RULES.XML
        // ✅ path-based lookup
        // =====================================================
        Map<String, String> rules =
                ruleEngine.getResponseRules(
                        endpointPath);

        if (!rules.isEmpty()) {

            buildFromRules(
                    response,
                    rules);

            return response;
        }

        // =====================================================
        // ✅ PRIORIDAD 2
        // ✅ FALLBACK AUTOMÁTICO
        // =====================================================
        Object generated =
                exampleMap.get(bodyType);

        if (generated == null) {

            generated =
                    exampleGenerator
                            .buildExampleFromType(
                                    bodyType);
        }

        response.put(
                "bodySalida" + operationName,
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